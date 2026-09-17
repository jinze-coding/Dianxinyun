"""Exercise the real deployment orchestrator without host services or databases."""
import importlib.util
import io
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from contextlib import ExitStack, redirect_stdout

SPEC = importlib.util.spec_from_file_location('deploy_under_test', Path(__file__).with_name('deploy.py'))
deploy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(deploy)


class OrchestrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='dxy-deploy-orchestration-')
        self.root = Path(self.temp.name).resolve()
        self.stack = ExitStack()
        self.old_umask = os.umask(0o022)
        self.events = []
        self.active, self.enabled, self.configured = True, True, False
        self.fail_preflight = self.fail_migrate = False
        parent, app = self.root / 'release-parent', self.root / 'app'
        envdir = self.root / 'etc-site-platform'
        for directory in (parent, app / 'backend', app / 'releases', envdir,
                          self.root / 'backups', self.root / 'units'):
            directory.mkdir(parents=True, exist_ok=True)
        paths = dict(PARENT=parent, BUNDLE=parent / 'bundle', STATE=parent / 'formal-switch',
                     BACKUP=self.root / 'backups/release', APP=app, DEST=app / 'releases/new',
                     JAR_LINK=app / 'backend/site-platform.jar', WEB_LINK=app / 'frontend',
                     UPLOAD_LINK=app / 'uploads', ENV=envdir / 'site-platform.env',
                     MAINT=envdir / 'maintenance.lock', DROPIN=self.root / 'units/70-preview.conf')
        for key, value in paths.items():
            self.stack.enter_context(patch.object(deploy, key, value))
        self.stack.enter_context(patch.object(deploy, 'sequence', 0))
        self.old = app / 'releases/old'
        (self.old / 'web').mkdir(parents=True)
        (self.old / 'uploads').mkdir()
        (self.old / 'old.jar').write_bytes(b'old JAR unchanged')
        (self.old / 'web/index.html').write_text('old web')
        (self.old / 'uploads/original.txt').write_text('original upload')
        deploy.JAR_LINK.symlink_to(self.old / 'old.jar')
        deploy.WEB_LINK.symlink_to(self.old / 'web')
        deploy.UPLOAD_LINK.symlink_to(self.old / 'uploads')
        deploy.ENV.write_text('DB_PASSWORD=synthetic-original\n')
        deploy.ENV.chmod(0o600)
        for relative, content in (
            ('runtime-validation-r2', {'complete': True, 'productionChanged': False,
                 'jarSha256': deploy.JAR_SHA, 'sourceManifest': deploy.SOURCE_SHA, 'imageId': 'verified-image'}),
            ('migration-rehearsal', {'complete': True, 'tables': 114, 'markers': 37,
                 'originalRowsPreserved': True})):
            report = parent / 'validation-093640' / relative
            report.mkdir(parents=True)
            (report / 'result.json').write_text(json.dumps(content))
        self.stack.enter_context(patch.object(deploy.os, 'getuid', return_value=0))
        self.stack.enter_context(patch.object(deploy.sys, 'argv', ['deploy.py', '--apply']))
        self.stack.enter_context(patch.object(deploy, 'lock', side_effect=self.fake_lock))
        self.stack.enter_context(patch.object(deploy, 'prop', side_effect=self.fake_prop))
        self.stack.enter_context(patch.object(deploy, 'run', side_effect=self.fake_run))
        self.stack.enter_context(patch.object(deploy, 'health', return_value=True))
        self.stack.enter_context(patch.object(deploy, 'wait_health', side_effect=self.fake_wait_health))
        self.stack.enter_context(patch.object(deploy, 'verify_web', side_effect=lambda *_: self.events.append('verified-final-web')))
        self.stack.enter_context(patch.object(deploy, 'backup', side_effect=self.fake_backup))
        self.stack.enter_context(patch.object(deploy, 'sha', return_value=deploy.JAR_SHA))
        self.stack.enter_context(patch.object(deploy.shutil, 'disk_usage', return_value=SimpleNamespace(free=10**12)))
        self.stack.enter_context(patch.object(deploy.subprocess, 'check_output', side_effect=self.fake_check_output))
        self.stack.enter_context(patch.object(deploy.subprocess, 'run', side_effect=self.fake_subprocess_run))
        # UID metadata only is mocked; temp-file contents, modes, atomic writes,
        # live-link swaps and maintenance-marker creation/removal remain real.
        original_stat, original_bytes, original_exists = Path.stat, Path.read_bytes, Path.exists
        def fake_stat(path, *args, **kwargs):
            value = original_stat(path, *args, **kwargs)
            if path in (deploy.ENV, deploy.MAINT):
                fields = list(value)
                fields[4] = 0
                return os.stat_result(fields)
            return value
        def fake_bytes(path):
            if str(path) == '/proc/123/cmdline':
                return b'java\0-jar\0' + str(deploy.JAR_LINK).encode() + b'\0'
            return original_bytes(path)
        def fake_exists(path):
            if str(path).startswith(('/run/', '/var/lib/dpkg/', '/var/lib/apt/', '/var/cache/apt/')):
                return False
            return original_exists(path)
        self.stack.enter_context(patch.object(Path, 'stat', fake_stat))
        self.stack.enter_context(patch.object(Path, 'read_bytes', fake_bytes))
        self.stack.enter_context(patch.object(Path, 'exists', fake_exists))
        db = SimpleNamespace(socket='/fixture/mysql.sock', preflight=self.fake_preflight, snapshot=self.fake_snapshot, migrate=self.fake_migrate)
        self.stack.enter_context(patch.dict('sys.modules', {
            'database_ops': SimpleNamespace(ProductionDB=lambda *_: db, reference=SimpleNamespace(load_payload=lambda *a: self.events.append('verify-sql'))),
            'runtime_ops': SimpleNamespace(prepare_env=lambda *a: ('', {}), stage=self.fake_stage, configure=self.fake_configure),
            'nginx_ops': SimpleNamespace(prepare=lambda *_: {'fixture': True}, apply=lambda *_: self.events.append('nginx-apply')),
        }))
        self.output = io.StringIO()
        self.stack.enter_context(redirect_stdout(self.output))

    def tearDown(self):
        self.stack.close()
        os.umask(self.old_umask)
        self.temp.cleanup()

    def fake_lock(self, path, posix=False):
        self.events.append('lock:' + path.name)
        return io.StringIO()

    def fake_prop(self, name, service=deploy.SERVICE):
        values = {'ActiveState': 'active' if self.active else 'inactive',
                  'MainPID': '123' if self.active else '0', 'ControlPID': '0', 'Job': '',
                  'User': 'site-platform', 'Group': 'site-platform',
                  'FragmentPath': '/etc/systemd/system/site-platform.service',
                  'WorkingDirectory': str(deploy.APP), 'PrivateTmp': 'yes', 'ProtectSystem': 'strict',
                  'SupplementaryGroups': 'docker' if self.configured else '', 'InvocationID': 'new-invocation'}
        return values[name]

    def fake_check_output(self, args, **kwargs):
        if args == ['apt-config', 'dump']:
            return ''
        if args == ['systemctl', 'is-enabled', deploy.SERVICE]:
            return 'enabled\n' if self.enabled else 'disabled\n'
        if args[:3] == ['docker', 'image', 'inspect']:
            return json.dumps([{'Id': 'verified-image'}])
        self.fail('Unexpected external check_output: ' + str(args))

    def fake_subprocess_run(self, args, **kwargs):
        if args == ['systemctl', 'is-enabled', deploy.SERVICE]:
            return SimpleNamespace(returncode=0 if self.enabled else 1,
                                   stdout='enabled\n' if self.enabled else 'disabled\n')
        self.fail('Unexpected external subprocess.run: ' + str(args))

    def fake_run(self, args, label, **kwargs):
        self.events.append(label)
        if args[:2] == ['systemctl', 'disable']:
            self.enabled = False
        if args[:2] == ['systemctl', 'stop']:
            self.active = False
        if args[:2] == ['systemctl', 'start']:
            self.assertFalse(self.enabled)
            self.assertTrue(deploy.MAINT.exists())
            self.assertEqual(deploy.JAR_LINK.resolve(), deploy.DEST / 'backend/site-platform.jar')
            self.active = True
        if args[:2] == ['systemctl', 'enable']:
            self.assertIn('healthy-new-release', self.events)
            self.enabled = True
        if args[0] == 'journalctl':
            kwargs['output'].write(b'Started SitePlatformApplication\nHikariPool-1 - Start completed\n')
        return 0

    def fake_preflight(self):
        self.events.append('db-preflight')
        if self.fail_preflight:
            raise RuntimeError('synthetic preflight failure')

    def fake_snapshot(self):
        self.events.append('snapshot')
        self.assertFalse(self.active)
        if '--apply-no-new-backup' not in deploy.sys.argv:
            self.assertTrue((deploy.BACKUP / 'BACKUP_COMPLETE').exists())
        return {'original': True}

    def fake_migrate(self, bundle, original):
        self.events.append('migrate')
        self.assertEqual(original, {'original': True})
        self.assertFalse(self.active)
        self.assertFalse(self.enabled)
        self.assertTrue(deploy.MAINT.exists())
        if self.fail_migrate:
            raise RuntimeError('synthetic migration failure')

    def fake_backup(self, db, targets):
        self.events.append('backup')
        self.assertFalse(self.active)
        deploy.BACKUP.mkdir()
        (deploy.BACKUP / 'BACKUP_COMPLETE').write_text('fixture')

    def fake_stage(self, bundle, dest, variant):
        self.events.append('stage')
        self.assertEqual(variant, 'final')
        self.assertTrue(self.active)
        (dest / 'backend').mkdir(parents=True)
        (dest / 'web-final/dist').mkdir(parents=True)
        (dest / 'runtime').mkdir()
        (dest / 'backend/site-platform.jar').write_bytes(b'new JAR fixture')
        (dest / 'web-final/dist/index.html').write_text('new web fixture')
        return {'release_dir': str(dest)}

    def fake_configure(self, *args):
        self.events.append('configure')
        self.assertFalse(self.active)
        self.configured = True

    def fake_wait_health(self):
        self.assertTrue(self.active)
        self.assertEqual(deploy.WEB_LINK.resolve(), deploy.DEST / 'web-final/dist')
        self.events.append('healthy-new-release')

    def test_success_migrates_once_then_switches_and_unlocks_after_health(self):
        deploy.main()
        self.assertEqual(self.events.count('snapshot'), 1)
        self.assertEqual(self.events.count('migrate'), 1)
        ordered = ['backup', 'snapshot', 'configure', 'migrate', 'start-backend',
                   'healthy-new-release', 'verified-final-web', 'enable-startup']
        self.assertEqual(sorted(ordered, key=self.events.index), ordered)
        self.assertTrue(self.active and self.enabled)
        self.assertFalse(deploy.MAINT.exists())
        self.assertEqual(deploy.JAR_LINK.resolve(), deploy.DEST / 'backend/site-platform.jar')
        self.assertEqual(json.loads((deploy.STATE / 'state.json').read_text())['phase'], 'COMPLETE')
        self.assertEqual(deploy.UPLOAD_LINK.resolve(), self.old / 'uploads')

    def test_user_requested_skip_backup_still_migrates_and_checks_new_release(self):
        with patch.object(deploy.sys, 'argv', ['deploy.py', '--apply-no-new-backup']), \
             patch.object(deploy.shutil, 'disk_usage', side_effect=AssertionError('must not size full backup')), \
             patch.object(deploy, 'state', wraps=deploy.state) as states:
            deploy.main()
        self.assertNotIn('backup', self.events)
        self.assertEqual(self.events.count('snapshot'), 1)
        self.assertEqual(self.events.count('migrate'), 1)
        self.assertIn('healthy-new-release', self.events)
        self.assertIn('verified-final-web', self.events)
        self.assertTrue(self.active and self.enabled)
        self.assertFalse(deploy.BACKUP.exists())
        self.assertFalse(deploy.MAINT.exists())
        self.assertTrue((deploy.STATE / 'NEW_BACKUP_SKIPPED_USER_REQUEST').exists())
        self.assertNotIn('BACKUP_COMPLETE', [call.args[0] for call in states.call_args_list])
        result = json.loads((deploy.STATE / 'state.json').read_text())
        self.assertEqual(result['phase'], 'COMPLETE')
        self.assertTrue(result['newBackupSkippedByUser'])
        self.assertFalse(result['newBackupCreated'])
        self.assertNotIn('备份目录：', self.output.getvalue())

    def test_migration_failure_leaves_maintenance_and_no_jar_is_started(self):
        self.fail_migrate = True
        with self.assertRaises(SystemExit):
            deploy.main()
        self.assertEqual(self.events.count('snapshot'), 1)
        self.assertEqual(self.events.count('migrate'), 1)
        self.assertNotIn('start-backend', self.events)
        self.assertNotIn('enable-startup', self.events)
        self.assertFalse(self.active or self.enabled)
        self.assertTrue(deploy.MAINT.exists())
        self.assertTrue((deploy.BACKUP / 'BACKUP_COMPLETE').exists())
        self.assertEqual(deploy.JAR_LINK.resolve(), self.old / 'old.jar')
        self.assertEqual(deploy.WEB_LINK.resolve(), self.old / 'web')
        result = json.loads((deploy.STATE / 'state.json').read_text())
        self.assertEqual(result['phase'], 'FAILED')
        self.assertTrue(result['databaseMayHaveChanged'])
        self.assertTrue(result['maintenanceCreated'])

    def test_preflight_failure_does_not_stage_stop_or_switch(self):
        self.fail_preflight = True
        with self.assertRaises(SystemExit):
            deploy.main()
        for event in ('stage', 'disable-startup', 'stop-backend', 'backup', 'snapshot',
                      'migrate', 'start-backend', 'configure'):
            self.assertNotIn(event, self.events)
        self.assertTrue(self.active and self.enabled)
        self.assertFalse(deploy.MAINT.exists())
        self.assertFalse(deploy.BACKUP.exists())
        self.assertFalse(deploy.DEST.exists())
        self.assertEqual(deploy.JAR_LINK.resolve(), self.old / 'old.jar')
        result = json.loads((deploy.STATE / 'state.json').read_text())
        self.assertFalse(result['databaseMayHaveChanged'])
        self.assertFalse(result['maintenanceCreated'])


if __name__ == '__main__':
    unittest.main()
