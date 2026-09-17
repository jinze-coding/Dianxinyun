#!/usr/bin/env python3
"""Offline guard tests; these do not establish target-database compatibility."""
import collections
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('rehearsal', Path(__file__).with_name('rehearse-release-20260915.py'))
R = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(R)


class RehearsalTests(unittest.TestCase):
    def test_frozen_plan_has_exact_monotonic_tables_and_unique_markers(self):
        tables = set(R.PLAN_DATA['baseline_tables'])
        markers = set(R.PLAN_DATA['baseline_markers'])
        self.assertEqual((len(tables), len(markers)), (98, 25))
        for item in R.PLAN_DATA['migrations']:
            self.assertNotIn(item['marker'], markers)
            self.assertFalse(tables & set(item['new_tables']))
            tables.update(item['new_tables'])
            markers.add(item['marker'])
            self.assertEqual(len(tables), item['expected_count'])
        self.assertEqual((len(tables), len(markers)), (114, 37))

    def test_corrupted_sql_is_rejected_before_any_database_call(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'source-reference').mkdir()
            (root / 'source-reference/SOURCE_MANIFEST.txt').write_bytes(b'test source')
            (root / 'database/migrations').mkdir(parents=True)
            first = R.PLAN_DATA['migrations'][0]
            (root / 'database/migrations' / first['file']).write_text('DELETE FROM sys_user;')
            source_hash = R.digest(b'test source')
            (root / 'RELEASE-MANIFEST.json').write_text(json.dumps({
                'releaseId': R.RELEASE_ID, 'sourceManifestSha256': source_hash}))
            with patch.object(R, 'SOURCE_SHA', source_hash), patch.object(subprocess, 'run') as run:
                with self.assertRaisesRegex(R.Stop, 'SQL 摘要不符'):
                    R.load_payload(root)
                run.assert_not_called()

    def test_business_row_change_loss_or_addition_is_rejected(self):
        before = {'sys_user': collections.Counter({'row1': 1, 'row2': 2})}
        for rows in ({'row1': 1, 'row2': 1}, {'row1': 1, 'changed': 2}, {'row1': 1, 'row2': 2, 'extra': 1}):
            with self.assertRaises(R.Stop):
                R.check_original(before, {'sys_user': collections.Counter(rows)})
        R.check_original(before, before)

    def test_append_only_catalog_still_rejects_changed_old_rows(self):
        before = {'sys_menu': collections.Counter({'disabled menu': 1})}
        R.check_original(before, {'sys_menu': collections.Counter({'disabled menu': 1, 'new menu': 1})})
        with self.assertRaises(R.Stop):
            R.check_original(before, {'sys_menu': collections.Counter({'enabled menu': 1, 'new menu': 1})})

    def test_warnings_do_not_allow_truncation_or_first_pass_duplicates(self):
        allowed = "Warning (Code 1287): 'VALUES function' is deprecated and will be removed in a future release.\n"
        self.assertEqual(R.check_warnings(allowed, 1), {'1287': 1})
        repeated = "Warning (Code 1062): Duplicate entry 'marker' for key 'sys_data_migration.uk_key'\nNote (Code 1050): Table 'example' already exists\n"
        self.assertEqual(R.check_warnings(repeated, 2), {'1062': 1, '1050': 1})
        for output, number in ((repeated, 1), ('Warning (Code 1265): Data truncated for column x', 2),
                               ('Error (Code 1050): unexpected', 2), ('Warning: unexpected output', 1)):
            with self.assertRaises(R.Stop):
                R.check_warnings(output, number)

    def test_every_connection_includes_identity_guard_before_sql(self):
        with tempfile.TemporaryDirectory() as temp:
            db = R.MySQL.__new__(R.MySQL)
            db.output, db.sequence = Path(temp), 0
            db.command, db.environment = ['mysql'], {}
            completed = subprocess.CompletedProcess([], 0, '', '')
            with patch.object(subprocess, 'run', return_value=completed) as run:
                db.raw('ALTER TABLE example ADD COLUMN test INT;', 'test')
            submitted = run.call_args.kwargs['input']
            self.assertIn("DATABASE()='dxycheck20260915093640'", submitted)
            self.assertIn("CURRENT_USER()='dxycheck0915093640@localhost'", submitted)
            self.assertLess(submitted.index('EXECUTE dxy_guard_stmt'), submitted.index('ALTER TABLE'))
            self.assertNotIn('--force', run.call_args.args[0])

    def test_wrong_database_or_production_read_access_is_rejected(self):
        db = R.MySQL.__new__(R.MySQL)
        with patch.object(db, 'scalar', return_value='dianxinyun'), patch.object(db, 'raw') as raw:
            with self.assertRaisesRegex(R.Stop, '验证库不符'):
                db.isolation()
            raw.assert_not_called()
        with patch.object(db, 'scalar', side_effect=[R.DATABASE, R.ACCOUNT + '@localhost']), \
                patch.object(db, 'raw', return_value=subprocess.CompletedProcess([], 0, '1', '')):
            with self.assertRaisesRegex(R.Stop, '隔离检查'):
                db.isolation()

    def test_identifiers_cannot_inject_sql_and_row_order_does_not_change_digest(self):
        for name in ('sys_user;DELETE', 'a`b', 'dianxinyun.sys_user'):
            with self.assertRaises(R.Stop):
                R.identifier(name)
        self.assertEqual(R.summary_rows({'a': collections.Counter(['a', 'b', 'a'])}),
                         R.summary_rows({'a': collections.Counter(['b', 'a', 'a'])}))

    def test_no_root_fallback_and_no_password_in_command(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'client.cnf').write_text('[client]\nuser=' + R.ACCOUNT + '\npassword=Aa1!' + 'a'*48 + '\nsocket=/run/mysqld/mysqld.sock\nprotocol=SOCKET\n')
            with patch.object(R, 'private_path'), patch.object(Path, 'is_socket', return_value=True), \
                    patch.object(R.shutil, 'which', return_value='/usr/bin/mysql'):
                db = R.MySQL(root, root)
            self.assertIn('--user=' + R.ACCOUNT, db.command)
            self.assertIn('--database=' + R.DATABASE, db.command)
            self.assertNotIn('Aa1!', ' '.join(db.command))
            self.assertNotIn('--no-login-paths', db.command)  # Not supported by MySQL 8.0.
            self.assertFalse(any(k.startswith('MYSQL_') for k in db.environment))


if __name__ == '__main__':
    unittest.main()
