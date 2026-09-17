import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('installer', Path(__file__).with_name('install.py'))
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class CodeUpdateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.old_jar = self.root / 'old.jar'
        self.old_jar.write_bytes(b'old backend')
        self.old_web = self.root / 'old-web'
        (self.old_web / 'assets').mkdir(parents=True)
        (self.old_web / 'index.html').write_text('old homepage')
        (self.old_web / 'assets/old-hash.js').write_bytes(b'old js')
        self.jar_link, self.web_link = self.root / 'app.jar', self.root / 'frontend'
        self.jar_link.symlink_to(self.old_jar)
        self.web_link.symlink_to(self.old_web)
        self.dest = self.root / 'release'
        self.state = self.root / 'state.json'
        self.maint = self.root / 'maintenance.lock'
        for name, value in [('JAR', self.jar_link), ('WEB', self.web_link), ('MAINT', self.maint)]:
            p = patch.object(installer, name, value)
            p.start()
            self.addCleanup(p.stop)

    def activate(self, health_effect=None, swap_effect=None):
        with patch.object(installer, 'run') as run, patch.object(installer, 'health', side_effect=health_effect), \
                patch.object(installer, 'prop', return_value='200'), patch.object(installer, 'verify_web'), \
                patch.object(installer, 'swap', side_effect=swap_effect or installer.swap):
            installer.activate(self.dest, self.old_jar, self.old_web, '100', self.state)
            return run

    def test_success_switches_both_entries_without_touching_old_code(self):
        run = self.activate()
        self.assertEqual(self.jar_link.readlink(), self.dest / 'backend/site-platform.jar')
        self.assertEqual(self.web_link.readlink(), self.dest / 'web')
        self.assertEqual(json.loads(self.state.read_text())['status'], 'COMPLETE')
        self.assertFalse(self.maint.exists())
        self.assertEqual(self.old_jar.read_bytes(), b'old backend')
        self.assertEqual([c.args for c in run.call_args_list], [('systemctl', 'stop', installer.SERVICE), ('systemctl', 'start', installer.SERVICE)])

    def test_failed_health_restores_both_entries(self):
        with self.assertRaisesRegex(RuntimeError, 'new health failure'):
            self.activate([RuntimeError('new health failure'), None])
        self.assertEqual(self.jar_link.readlink(), self.old_jar)
        self.assertEqual(self.web_link.readlink(), self.old_web)
        self.assertEqual(json.loads(self.state.read_text())['status'], 'ROLLED_BACK')
        self.assertFalse(self.maint.exists())

    def test_partial_switch_restores_original_entries(self):
        original_swap = installer.swap
        count = 0
        def fail_second(link, target):
            nonlocal count
            count += 1
            if count == 2:
                raise RuntimeError('web swap failure')
            original_swap(link, target)
        with self.assertRaisesRegex(RuntimeError, 'web swap failure'):
            self.activate(swap_effect=fail_second)
        self.assertEqual(self.jar_link.readlink(), self.old_jar)
        self.assertEqual(self.web_link.readlink(), self.old_web)
        self.assertEqual(json.loads(self.state.read_text())['status'], 'ROLLED_BACK')

    def test_failed_recovery_keeps_state_and_maintenance(self):
        with self.assertRaisesRegex(RuntimeError, 'new failed'):
            self.activate([RuntimeError('new failed'), RuntimeError('old failed')])
        self.assertTrue(self.maint.exists())
        self.assertEqual(json.loads(self.state.read_text())['status'], 'RECOVERY_REQUIRED')

    def test_existing_maintenance_prevents_any_switch(self):
        self.maint.write_text('another operation')
        with self.assertRaises(FileExistsError):
            self.activate()
        self.assertEqual(self.jar_link.readlink(), self.old_jar)
        self.assertEqual(self.maint.read_text(), 'another operation')

    def make_bundle(self):
        (self.root / 'artifacts/web/assets').mkdir(parents=True)
        (self.root / 'artifacts/backend.jar').write_bytes(b'new backend')
        (self.root / 'artifacts/web/index.html').write_text('new homepage')
        (self.root / 'artifacts/web/assets/new-hash.js').write_bytes(b'new js')
        (self.root / 'RELEASE.json').write_text('{}')
        (self.root / 'install.py').write_text('installer')
        payload = [p for p in (self.root / 'artifacts').rglob('*') if p.is_file()] + [self.root / 'RELEASE.json', self.root / 'install.py']
        (self.root / 'SHA256SUMS').write_text(''.join(installer.sha(p) + '  ' + str(p.relative_to(self.root)) + '\n' for p in payload))

    def test_staging_retains_old_assets_and_keeps_live_entries(self):
        self.make_bundle()
        with patch.object(installer.os, 'chown'):
            installer.stage(self.root, self.dest, self.old_web, os.getgid())
        self.assertEqual((self.dest / 'web/assets/old-hash.js').read_bytes(), b'old js')
        self.assertEqual((self.dest / 'web/assets/new-hash.js').read_bytes(), b'new js')
        self.assertEqual(self.jar_link.readlink(), self.old_jar)
        self.assertEqual(self.web_link.readlink(), self.old_web)
        self.assertEqual((self.dest / 'web').stat().st_mode & 0o777, 0o755)
        self.assertEqual((self.dest / 'backend/site-platform.jar').stat().st_mode & 0o777, 0o640)

    def test_checksum_tampering_and_unlisted_artifacts_rejected(self):
        self.make_bundle()
        installer.checksum_files(self.root.resolve())
        extra = self.root / 'artifacts/web/unlisted.js'
        extra.write_bytes(b'extra')
        with self.assertRaisesRegex(RuntimeError, '产物清单不完整'):
            installer.checksum_files(self.root.resolve())
        extra.unlink()
        (self.root / 'artifacts/backend.jar').write_bytes(b'tampered')
        with self.assertRaisesRegex(RuntimeError, '校验失败'):
            installer.checksum_files(self.root.resolve())

    def test_static_asset_collision_stops_before_switch(self):
        self.make_bundle()
        (self.root / 'artifacts/web/assets/old-hash.js').write_bytes(b'unexpected changed content')
        with self.assertRaisesRegex(RuntimeError, '同名静态资源'):
            installer.stage(self.root, self.dest, self.old_web, os.getgid())
        self.assertEqual(self.jar_link.readlink(), self.old_jar)


if __name__ == '__main__':
    unittest.main()
