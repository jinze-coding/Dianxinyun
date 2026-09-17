#!/usr/bin/env python3
"""Offline safety checks only; never invokes a real service or database."""
import importlib.util
import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('runtime_validation', Path(__file__).with_name('validate-release-runtime-20260915.py'))
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class RuntimeSafety(unittest.TestCase):
    def test_private_umask_does_not_block_service_group(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'validation'
            work = root / 'work'
            with patch.object(runtime, 'ROOT', root), patch.object(runtime, 'WORK', work), \
                    patch.object(runtime.os, 'chown') as chown, patch.object(runtime, 'run') as run:
                old_mask = os.umask(0o077)
                try:
                    runtime.create_work_directories(1234, 1234)
                finally:
                    os.umask(old_mask)
                self.assertEqual(stat.S_IMODE(root.stat().st_mode), 0o750)
                self.assertEqual(stat.S_IMODE(work.stat().st_mode), 0o700)
                self.assertEqual(chown.call_args_list[0].args, (root, 0, 1234))
                self.assertEqual(chown.call_args_list[1].args, (work, 1234, 1234))
                self.assertEqual(run.call_args.args[0][:5], ['runuser', '-u', 'site-platform', '-g', 'site-platform'])
                self.assertEqual(run.call_args.args[0][-1], str(work))

    def test_work_directory_probe_failure_stops_before_start(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'validation'
            with patch.object(runtime, 'ROOT', root), patch.object(runtime, 'WORK', root / 'work'), \
                    patch.object(runtime.os, 'chown'), patch.object(runtime, 'run', side_effect=runtime.CheckFailed('no access')):
                with self.assertRaisesRegex(runtime.CheckFailed, 'no access'):
                    runtime.create_work_directories(1234, 1234)

    def environment(self):
        keys = ('JWT_SECRET', 'VISITOR_DATA_ENCRYPTION_KEY', 'SEAL_SCENE_ENCRYPTION_KEY',
                'DOCUMENT_CIRCULATION_SCENE_ENCRYPTION_KEY', 'WECHAT_MINI_PROGRAM_APP_ID',
                'WECHAT_MINI_PROGRAM_APP_SECRET', 'WECHAT_MINI_PROGRAM_LEGAL_DOMAIN',
                'WECHAT_MINI_PROGRAM_PUBLIC_FALLBACK_URL')
        return {**dict.fromkeys(keys, 'synthetic-only'),
                'DB_URL': 'jdbc:mysql://localhost:3306/dianxinyun?sslMode=REQUIRED&serverTimezone=Asia/Shanghai'}

    def test_only_database_name_changes(self):
        original = self.environment()['DB_URL']
        self.assertEqual(runtime.qa_url(original), original.replace('/dianxinyun?', '/' + runtime.DB + '?'))

    def test_unsafe_or_unexpected_connection_refused(self):
        for url in ('jdbc:mysql://remote:3306/dianxinyun?sslMode=REQUIRED',
                    'jdbc:mysql://localhost:3306/other?sslMode=REQUIRED',
                    'jdbc:mysql://localhost:3306/dianxinyun?useSSL=false',
                    'jdbc:mysql://localhost:3306/dianxinyun?sslMode=REQUIRED&allowPublicKeyRetrieval=true',
                    'jdbc:mysql://root:secret@localhost/dianxinyun?sslMode=REQUIRED',
                    'jdbc:mysql://localhost/dianxinyun?sslMode=REQUIRED&sslMode=DISABLED',
                    'jdbc:mysql://localhost/dianxinyun?sslMode=REQUIRED&SSLMODE=DISABLED',
                    'jdbc:mysql://localhost/dianxinyun?sslMode=REQUIRED&user=root'):
            with self.subTest(url=url), self.assertRaises(runtime.CheckFailed):
                runtime.qa_url(url)

    def test_production_reset_and_external_configuration_not_inherited(self):
        live = self.environment()
        live.update(DB_PASSWORD='production-password', REDIS_PASSWORD='production-redis',
                    ADMIN_RESET_USERNAME='administrator', ADMIN_RESET_PASSWORD='never-use-this',
                    SPRING_APPLICATION_JSON='danger', JAVA_TOOL_OPTIONS='danger',
                    SPRING_CONFIG_LOCATION='/production', SITE_ACCESS_LEGACY_REENCRYPTION_ENABLED='true')
        result = runtime.isolated_environment(live, 'qa-password', 'qa-redis')
        self.assertEqual(result['DB_USERNAME'], runtime.ACCOUNT)
        self.assertEqual(result['DB_PASSWORD'], 'qa-password')
        self.assertEqual(result['REDIS_PASSWORD'], 'qa-redis')
        for key in ('ADMIN_RESET_USERNAME', 'ADMIN_RESET_PASSWORD', 'JAVA_TOOL_OPTIONS',
                    'SPRING_CONFIG_LOCATION', 'SITE_ACCESS_LEGACY_REENCRYPTION_ENABLED'):
            self.assertNotIn(key, result)
        self.assertEqual(result['SPRING_PROFILES_ACTIVE'], 'prod')
        self.assertEqual(result['WECHAT_MINI_PROGRAM_MOCK_ENABLED'], 'false')
        config = json.loads(result['SPRING_APPLICATION_JSON'])
        self.assertFalse(config['app.scheduling.enabled'])
        self.assertFalse(config['site-access.legacy-reencryption.enabled'])
        self.assertEqual(config['server.address'], '127.0.0.1')
        self.assertTrue(config['file.upload.path'].startswith(str(runtime.WORK)))

    def test_missing_production_setting_is_explicit_and_secret_free(self):
        live = self.environment()
        del live['JWT_SECRET']
        with self.assertRaisesRegex(runtime.CheckFailed, 'JWT_SECRET'):
            runtime.isolated_environment(live, 'qa-password', 'qa-redis')

    def test_only_completed_rehearsal_accepted(self):
        valid = dict(complete=True, tables=114, markers=37, originalRowsPreserved=True,
                     repeatDataAndSchemaUnchanged=True, productionMigrated=False)
        self.assertTrue(runtime.rehearsal_complete(valid))
        for key, value in [('complete', False), ('tables', 98), ('markers', 25),
                           ('originalRowsPreserved', False), ('productionMigrated', True)]:
            self.assertFalse(runtime.rehearsal_complete({**valid, key: value}))

    def test_unit_does_not_pass_secrets_on_command_line(self):
        with patch.object(runtime, 'prop', return_value='not-found'), patch.object(runtime, 'run') as run:
            runtime.start_unit('app')
            argv = run.call_args.args[0]
            self.assertIn('--property=MemoryMax=704M', argv)
            self.assertIn('--property=IPAddressDeny=any', argv)
            self.assertIn('--property=LoadCredential=runtime-config:' + str(runtime.REPORT / 'child-config.json'), argv)
            self.assertFalse(any('DB_PASSWORD' in word or 'SECRET=' in word for word in argv))
            self.assertFalse(any('SupplementaryGroups=docker' in word for word in argv))
            inaccessible = [word for word in argv if word.startswith('--property=InaccessiblePaths=')]
            self.assertEqual(len(inaccessible), 1)
            self.assertIn('/run/docker.sock', inaccessible[0])

    def test_existing_unit_not_started_or_stopped(self):
        with patch.object(runtime, 'prop', return_value='loaded'), patch.object(runtime, 'run') as run:
            with self.assertRaises(runtime.CheckFailed):
                runtime.start_unit('app')
            run.assert_not_called()

    def test_production_health_requires_same_process(self):
        with patch.object(runtime, 'prop', side_effect=['active', 'old-invocation']), patch.object(runtime, 'health') as health:
            with self.assertRaises(runtime.CheckFailed):
                runtime.production_healthy('new-invocation', '123')
            health.assert_not_called()

    def test_png_fixture_can_decode(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'input.png'
            runtime.make_png(path)
            data = path.read_bytes()
            self.assertEqual(data[:8], b'\x89PNG\r\n\x1a\n')
            self.assertEqual(int.from_bytes(data[16:20], 'big'), 160)
            self.assertEqual(int.from_bytes(data[20:24], 'big'), 96)


if __name__ == '__main__':
    unittest.main()
