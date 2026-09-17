#!/usr/bin/env python3
"""Offline tests only; no database or production host connection."""
import collections
from contextlib import ExitStack
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('production_database_ops', Path(__file__).with_name('database_ops.py'))
D = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(D)


def completed(stdout='', stderr='', code=0):
    return subprocess.CompletedProcess([], code, stdout, stderr)


class DatabaseOpsTests(unittest.TestCase):
    def bare(self, output):
        db = D.ProductionDB.__new__(D.ProductionDB)
        db.output, db.sequence, db._snapshot = Path(output), 0, None
        db.command, db.environment = ['mysql', '--user=root', '--database=dianxinyun'], {}
        return db

    def test_plan_reference_is_exact_original_bytes(self):
        root = Path(__file__).parent
        self.assertEqual((root / 'plan_reference.py').read_bytes(),
                         (root.parent / 'rehearse-release-20260915.py').read_bytes())
        self.assertEqual((len(D.BASELINE_TABLES), len(D.BASELINE_MARKERS)), (98, 25))

    def test_constructor_binds_socket_user_database_and_discards_mysql_env(self):
        with tempfile.TemporaryDirectory() as temp, \
                patch.object(D.os, 'geteuid', return_value=0), \
                patch.object(D.reference, 'private_path'), \
                patch.object(Path, 'is_socket', return_value=True), \
                patch.object(D.shutil, 'which', return_value='/usr/bin/mysql'), \
                patch.dict(os.environ, {'MYSQL_HOST': 'other', 'MYSQL_PWD': 'secret'}), \
                patch.object(D.subprocess, 'run', side_effect=[
                    completed('/run/mysqld/mysqld.sock\n'), completed('dianxinyun\n'),
                    completed('root@localhost\n')]) as run:
            db = D.ProductionDB(Path(temp) / 'logs')
            self.assertEqual(db.command[:2], ['/usr/bin/mysql', '--no-defaults'])
            for flag in ('--user=root', '--protocol=SOCKET', '--database=dianxinyun',
                         '--socket=/run/mysqld/mysqld.sock', '--skip-reconnect'):
                self.assertIn(flag, db.command)
            self.assertFalse(any(key.startswith('MYSQL_') for key in db.environment))
            self.assertNotIn('--force', db.command)
            self.assertEqual(db.output.stat().st_mode & 0o777, 0o700)
            for call in run.call_args_list:
                self.assertIn("CURRENT_USER()='root@localhost'", call.kwargs['input'])
                self.assertIn("DATABASE()='dianxinyun'", call.kwargs['input'])
            self.assertTrue(all(p.stat().st_mode & 0o777 == 0o600 for p in db.output.iterdir()))

    def test_guard_is_before_every_statement_and_errors_do_not_leak(self):
        with tempfile.TemporaryDirectory() as temp:
            db = self.bare(temp)
            with patch.object(D.subprocess, 'run', return_value=completed()) as run:
                db.raw('ALTER TABLE sys_user ADD COLUMN example INT;', 'test')
            sql = run.call_args.kwargs['input']
            self.assertLess(sql.index('EXECUTE dxy_guard_stmt'), sql.index('ALTER TABLE'))
            with patch.object(D.subprocess, 'run', return_value=completed('sensitive-business-value', 'secret-password', 1)):
                with self.assertRaises(D.ProductionError) as caught:
                    db.query('SELECT 1;')
            self.assertNotIn('sensitive', str(caught.exception))
            self.assertNotIn('secret', str(caught.exception))
            self.assertIn(temp, str(caught.exception))

    def test_verify_baseline_rejects_wrong_table_set_and_duplicate_markers(self):
        with tempfile.TemporaryDirectory() as temp:
            db = self.bare(temp)
            with patch.object(db, 'tables', return_value=D.BASELINE_TABLES - {'sys_user'}), \
                    patch.object(db, 'markers', return_value=D.BASELINE_MARKERS), \
                    patch.object(db, 'scalar') as scalar:
                with self.assertRaises(D.ProductionError):
                    db.verify_baseline()
                scalar.assert_not_called()
            with patch.object(db, 'tables', return_value=D.BASELINE_TABLES), \
                    patch.object(db, 'markers', return_value=D.BASELINE_MARKERS), \
                    patch.object(db, 'scalar', return_value='26'):
                with self.assertRaises(D.ProductionError):
                    db.verify_baseline()

    def test_preflight_rejects_triggers_and_partially_applied_fields(self):
        with tempfile.TemporaryDirectory() as temp:
            db = self.bare(temp)
            with patch.object(db, 'verify_baseline'), \
                    patch.object(db, 'scalar', side_effect=['0', '1']), \
                    patch.object(db, 'columns') as columns:
                with self.assertRaises(D.ProductionError):
                    db.preflight()
                columns.assert_not_called()
            columns = {table: ['id'] for table in D.BASELINE_TABLES}
            columns['sys_user'].append('must_change_password')
            with patch.object(db, 'verify_baseline'), patch.object(db, 'scalar', return_value='0'), \
                    patch.object(db, 'columns', return_value=columns):
                with self.assertRaises(D.ProductionError):
                    db.preflight()

    def configure_migration(self, stack, db, fail_at=None):
        snapshot = {'columns': {'sys_user': ['id']}, 'rows': {'sys_user': collections.Counter({'old': 1})},
                    'codes': {}, 'schema': {'sys_user': 'original schema'}}
        db._snapshot = snapshot
        state = {'step': 0, 'tables': set(D.BASELINE_TABLES), 'markers': set(D.BASELINE_MARKERS)}
        def raw(sql, label, warnings=False):
            state['step'] += 1
            if state['step'] == fail_at:
                return completed('', 'sensitive database failure detail', 1)
            item = D.PLAN_DATA['migrations'][state['step'] - 1]
            state['tables'].update(item['new_tables'])
            state['markers'].add(item['marker'])
            return completed()
        stack.enter_context(patch.object(D.reference, 'load_payload', return_value=['SQL'] * 12))
        stack.enter_context(patch.object(db, 'verify_baseline', return_value=True))
        stack.enter_context(patch.object(db, 'columns', return_value=snapshot['columns']))
        stack.enter_context(patch.object(db, 'rows', return_value=snapshot['rows']))
        stack.enter_context(patch.object(db, 'schema', return_value=snapshot['schema']))
        stack.enter_context(patch.object(db, 'tables', side_effect=lambda: state['tables']))
        stack.enter_context(patch.object(db, 'markers', side_effect=lambda: state['markers']))
        stack.enter_context(patch.object(db, 'scalar', side_effect=lambda _: str(len(state['markers']))))
        run = stack.enter_context(patch.object(db, 'raw', side_effect=raw))
        original = stack.enter_context(patch.object(D.reference, 'check_original'))
        final = stack.enter_context(patch.object(D.reference, 'check_final', return_value=snapshot['columns']))
        return snapshot, state, run, original, final

    def test_success_runs_12_once_and_requires_original_and_final_data_checks(self):
        with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
            db = self.bare(temp)
            snapshot, state, run, original, final = self.configure_migration(stack, db)
            report = db.migrate('/release', snapshot)
            self.assertEqual((report['tables'], report['markers'], run.call_count), (114, 37, 12))
            original.assert_called_once_with(snapshot['rows'], snapshot['rows'])
            final.assert_called_once()
            self.assertTrue(json.loads((Path(temp) / 'migration-result.json').read_text())['complete'])
            with self.assertRaises(D.ProductionError):
                db.migrate('/release', snapshot)
            self.assertEqual(run.call_count, 12)

    def test_step_failure_stops_and_cannot_retry_or_claim_complete(self):
        with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
            db = self.bare(temp)
            snapshot, state, run, original, final = self.configure_migration(stack, db, fail_at=3)
            with self.assertRaises(D.ProductionError) as caught:
                db.migrate('/release', snapshot)
            self.assertEqual(run.call_count, 3)
            self.assertNotIn('sensitive', str(caught.exception))
            self.assertFalse((Path(temp) / 'migration-result.json').exists())
            with self.assertRaises(D.ProductionError):
                db.migrate('/release', snapshot)
            self.assertEqual(run.call_count, 3)
            original.assert_not_called()
            final.assert_not_called()

    def test_hash_rejection_and_snapshot_drift_occur_before_sql(self):
        with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
            db = self.bare(temp)
            snapshot, _, run, _, _ = self.configure_migration(stack, db)
            with patch.object(D.reference, 'load_payload', side_effect=D.reference.Stop('SQL hash mismatch')):
                with self.assertRaises(D.ProductionError):
                    db.migrate('/release', snapshot)
            with patch.object(db, 'rows', return_value={'sys_user': collections.Counter({'new': 1})}):
                with self.assertRaises(D.ProductionError):
                    db.migrate('/release', snapshot)
            run.assert_not_called()
            self.assertFalse((Path(temp) / 'migration-started.json').exists())


if __name__ == '__main__':
    unittest.main()
