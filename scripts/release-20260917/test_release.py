import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
spec = importlib.util.spec_from_file_location('new_installer', HERE / 'install.py')
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)
from database import Database, MIGRATION, TABLES


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.old_jar = self.root / 'old.jar'
        self.old_jar.write_bytes(b'original')
        self.old_web = self.root / 'old-web'
        self.old_web.mkdir()
        self.jar = self.root / 'app.jar'
        self.web = self.root / 'frontend'
        self.jar.symlink_to(self.old_jar)
        self.web.symlink_to(self.old_web)
        self.dest = self.root / 'new'
        self.state = self.root / 'result.json'
        self.maint = self.root / 'maintenance'
        for name, value in [('JAR', self.jar), ('WEB', self.web), ('MAINT', self.maint)]:
            p = patch.object(installer.code, name, value)
            p.start()
            self.addCleanup(p.stop)

    def activate(self, database, health=None):
        with patch.object(installer.code, 'run'), patch.object(installer.code, 'prop', return_value='200'), \
                patch.object(installer.code, 'health', side_effect=health), patch.object(installer.code, 'verify_web'):
            installer.activate(self.dest, self.old_jar, self.old_web, '100', self.state, database)

    def test_migration_precedes_new_code_and_complete_includes_result(self):
        def apply():
            self.assertEqual(self.jar.readlink(), self.old_jar)
            self.assertEqual(self.web.readlink(), self.old_web)
            return 'APPLIED'
        database = Mock(apply=Mock(side_effect=apply))
        self.activate(database)
        self.assertEqual(self.jar.readlink(), self.dest / 'backend/site-platform.jar')
        self.assertEqual(self.web.readlink(), self.dest / 'web')
        self.assertEqual(json.loads(self.state.read_text())['databaseMigration'], 'APPLIED')
        self.assertEqual(json.loads(self.state.read_text())['status'], 'COMPLETE')
        self.assertFalse(self.maint.exists())

    def test_migration_failure_restores_old_code_without_database_rollback(self):
        database = Mock(apply=Mock(side_effect=RuntimeError('migration failed')))
        with self.assertRaisesRegex(RuntimeError, 'migration failed'):
            self.activate(database)
        self.assertEqual(self.jar.readlink(), self.old_jar)
        self.assertEqual(self.web.readlink(), self.old_web)
        self.assertEqual(json.loads(self.state.read_text())['status'], 'ROLLED_BACK')
        self.assertEqual(database.method_calls, [('apply', (), {})])

    def test_failed_new_health_keeps_additive_tables_and_recovers_old_code(self):
        database = Mock(apply=Mock(return_value='APPLIED'))
        with self.assertRaisesRegex(RuntimeError, 'new failed'):
            self.activate(database, [RuntimeError('new failed'), None])
        self.assertEqual(json.loads(self.state.read_text())['databaseMigration'], 'APPLIED')
        self.assertEqual(json.loads(self.state.read_text())['status'], 'ROLLED_BACK')
        self.assertEqual(self.old_jar.read_bytes(), b'original')
        self.assertEqual(database.method_calls, [('apply', (), {})])

    def test_recovery_failure_preserves_maintenance(self):
        with self.assertRaisesRegex(RuntimeError, 'new failed'):
            self.activate(Mock(apply=Mock(return_value='APPLIED')), [RuntimeError('new failed'), RuntimeError('old failed')])
        self.assertTrue(self.maint.exists())
        self.assertEqual(json.loads(self.state.read_text())['status'], 'RECOVERY_REQUIRED')

    def test_existing_maintenance_blocks_migration(self):
        self.maint.write_text('another task')
        database = Mock()
        with self.assertRaises(FileExistsError):
            self.activate(database)
        database.apply.assert_not_called()


class DatabaseTests(unittest.TestCase):
    def setUp(self):
        self.db = Database.__new__(Database)
        self.db.sql = (HERE.parents[1] / 'backend/src/main/resources/sql/migrations' / MIGRATION).read_text()

    def test_migration_is_only_two_new_tables_and_catalogs(self):
        import re
        sql = re.sub(r'^--.*$', '', self.db.sql, flags=re.M)
        statements = [part.strip() for part in sql.split(';') if part.strip()]
        self.assertEqual(len(statements), 5)
        self.assertTrue(statements[0].startswith('CREATE TABLE IF NOT EXISTS ' + TABLES[0]))
        self.assertTrue(statements[1].startswith('CREATE TABLE IF NOT EXISTS ' + TABLES[1]))
        self.assertTrue(statements[2].startswith('INSERT INTO sys_permission'))
        self.assertTrue(statements[3].startswith('INSERT INTO sys_menu'))
        self.assertTrue(statements[4].startswith('INSERT IGNORE INTO sys_data_migration'))
        self.assertNotRegex(sql, r'(?i)\b(DROP|DELETE|TRUNCATE|ALTER|REPLACE)\b')

    def test_already_applied_skips_sql(self):
        self.db.preflight = Mock(return_value=True)
        self.db.query = Mock()
        self.assertEqual(self.db.apply(), 'ALREADY_APPLIED')
        self.db.query.assert_not_called()

    def test_new_migration_verifies_marker_schema_and_catalog(self):
        self.db.preflight = Mock(return_value=False)
        self.db.query = Mock()
        self.db.marked = Mock(return_value=True)
        self.db.schema = Mock()
        self.db.catalogs = Mock()
        self.assertEqual(self.db.apply(), 'APPLIED')
        self.db.query.assert_called_once_with(self.db.sql)
        self.db.schema.assert_called_once_with()
        self.db.catalogs.assert_called_once_with()

    def test_missing_marker_after_execution_is_failure(self):
        self.db.preflight = Mock(return_value=False)
        self.db.query = Mock()
        self.db.marked = Mock(return_value=False)
        with self.assertRaisesRegex(RuntimeError, '标记未保存'):
            self.db.apply()

    def test_wrong_existing_table_is_rejected(self):
        self.db.query = Mock(return_value=['id\tint\tNO'])
        with self.assertRaisesRegex(RuntimeError, '表结构不符'):
            self.db.schema(allow_absent=True)

    def test_absent_tables_allowed_before_migration_only(self):
        self.db.query = Mock(return_value=[])
        self.db.schema(allow_absent=True)
        with self.assertRaisesRegex(RuntimeError, '表结构不符'):
            self.db.schema()

    def test_existing_catalog_disablement_is_not_overwritten(self):
        self.db.query = Mock(side_effect=[['system.data.correct\tSYSTEM'], ['SYSTEM_DATA_CORRECTION\tWEB\tSYSTEM_DATA_CORRECTION\tsystem.data.correct']])
        self.db.catalogs()
        self.assertTrue(all('UPDATE' not in call.args[0] for call in self.db.query.call_args_list))


if __name__ == '__main__':
    unittest.main()
