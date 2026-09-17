import base64
import importlib.util
import io
from pathlib import Path
import tarfile
import unittest

spec = importlib.util.spec_from_file_location('runtime_ops', Path(__file__).with_name('runtime_ops.py'))
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)
CONVERTER = Path('/opt/site-platform/releases/20260915-0912/runtime/meeting-material-convert.sh')

class EnvironmentTests(unittest.TestCase):
    def test_preserves_all_old_secrets_and_valid_import_key(self):
        key = base64.b64encode(b'k' * 32).decode()
        raw = '# comment\nDB_PASSWORD="existing # secret"\nJWT_SECRET=untouched\nUSER_IMPORT_CREDENTIAL_KEY=' + key + '\n'
        result, metadata = ops.prepare_env(raw, CONVERTER)
        self.assertTrue(result.startswith(raw))
        self.assertFalse(metadata['credential_key_generated'])
        self.assertIn('APP_SCHEDULING_ENABLED=true\n', result)
        self.assertNotIn(key, str(metadata))

    def test_generate_once_and_preserve_on_rerender(self):
        first, meta = ops.prepare_env('DB_PASSWORD=keep', CONVERTER)
        second, again = ops.prepare_env(first, CONVERTER)
        self.assertEqual(first, second)
        self.assertTrue(meta['credential_key_generated'])
        self.assertFalse(again['credential_key_generated'])
        key = ops.parse_pairs(first)['USER_IMPORT_CREDENTIAL_KEY']
        self.assertEqual(len(base64.b64decode(key)), 32)

    def test_refuses_duplicate_or_qa_or_one_time_config(self):
        for raw in ('DB_PASSWORD=a\nDB_PASSWORD=b\n', 'APP_SCHEDULING_ENABLED=false\n',
                    'SITE_ACCESS_LEGACY_REENCRYPTION_ENABLED=false\n',
                    'ADMIN_RESET_PASSWORD=private\n', 'SPRING_PROFILES_ACTIVE=visitor-legacy-reencrypt\n'):
            with self.subTest(raw=raw):
                with self.assertRaises(ValueError):
                    ops.prepare_env(raw, CONVERTER)

    def test_bad_key_never_replaced_or_printed(self):
        for secret in ('invalid-private-key', base64.b64encode(b'x' * 16).decode(), 'bad!base64'):
            with self.assertRaises(ValueError) as error:
                ops.prepare_env('USER_IMPORT_CREDENTIAL_KEY=' + secret, CONVERTER)
            self.assertNotIn(secret, str(error.exception))

    def test_only_converter_line_is_replaced(self):
        raw = 'MEETING_MATERIAL_CONVERTER=/old/path\nDB_PASSWORD="ab$#cd"\nAPP_SCHEDULING_ENABLED="true"\n'
        result, _ = ops.prepare_env(raw, CONVERTER)
        self.assertIn('DB_PASSWORD="ab$#cd"\nAPP_SCHEDULING_ENABLED="true"\n', result)
        self.assertNotIn('/old/path', result)

class ArchiveTests(unittest.TestCase):
    def malicious(self, name, member_type=tarfile.REGTYPE):
        buf = io.BytesIO()
        with tarfile.open(fileobj=buf, mode='w') as archive:
            item = tarfile.TarInfo(name)
            item.type, item.uid, item.gid, item.uname, item.gname, item.mode = member_type, 0, 0, 'root', 'root', 0o644
            archive.addfile(item)
        buf.seek(0)
        return tarfile.open(fileobj=buf, mode='r')

    def test_rejects_escape_link_special_and_extra_root(self):
        for name, kind in [('../escape', tarfile.REGTYPE), ('/absolute', tarfile.REGTYPE),
                           ('dist/link', tarfile.SYMTYPE), ('dist/hard', tarfile.LNKTYPE),
                           ('dist/dev', tarfile.CHRTYPE), ('secret', tarfile.REGTYPE),
                           ('dist/./file', tarfile.REGTYPE)]:
            with self.subTest(name=name, kind=kind), self.malicious(name, kind) as archive:
                with self.assertRaises(ValueError):
                    ops._verify_web_members(archive, 'transition')

    def test_real_frozen_archives(self):
        artifacts = Path('/Users/js/Downloads/Dianxinyun-20260915-build/artifacts')
        if not artifacts.exists():
            self.skipTest('Local frozen artifacts absent')
        for variant, (name, _) in ops.WEB.items():
            result = ops.verify_web(artifacts / name, variant)
            self.assertEqual(result['SOURCE_MANIFEST_SHA256'], ops.SOURCE_SHA)
            self.assertEqual(result['FILE_COUNT'], '6')

if __name__ == '__main__':
    unittest.main()
