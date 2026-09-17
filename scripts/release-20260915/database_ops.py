#!/usr/bin/env python3
"""Fixed production database operations for the already rehearsed 2026-09-15 release.

The caller owns maintenance, stopping writers, the verified same-point backup,
and rollback. This module has no CLI or database/account override parameters.
"""
import collections
import datetime
import functools
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess

_spec = importlib.util.spec_from_file_location(
    'release_20260915_plan_reference', Path(__file__).with_name('plan_reference.py'))
reference = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(reference)
PLAN_DATA = reference.PLAN_DATA
DATABASE = 'dianxinyun'
ACCOUNT = 'root@localhost'
BASELINE_TABLES = set(PLAN_DATA['baseline_tables'])
BASELINE_MARKERS = set(PLAN_DATA['baseline_markers'])


class ProductionError(RuntimeError):
    """Safe for display: never contains SQL output, credentials or business data."""


def _protected(method):
    @functools.wraps(method)
    def wrapped(self, *args, **kwargs):
        try:
            return method(self, *args, **kwargs)
        except ProductionError:
            raise
        except (reference.Stop, OSError, ValueError, KeyError, TypeError,
                subprocess.SubprocessError) as error:
            self._fail(method.__name__, error)
    return wrapped


class ProductionDB(reference.MySQL):
    """ProductionDB(log_dir): new private log directory; fixed root/socket/DB.

    Reuses only the reference's read/hash data methods. Never calls its
    constructor, isolation() or main(). command/environment/socket are exposed
    for the caller's same-point dump and rollback commands.
    """
    @_protected
    def __init__(self, log_dir):
        self.output = Path(log_dir)
        self.sequence = 0
        self._snapshot = None
        reference.require(os.geteuid() == 0, 'Production operations require root')
        reference.require(self.output.is_absolute(), 'Log directory must be absolute')
        reference.require(not self.output.exists() and not self.output.is_symlink(),
                          'Log directory must be new')
        self.output.mkdir(mode=0o700)
        self.output.chmod(0o700)
        reference.private_path(self.output, directory=True)
        mysql = shutil.which('mysql')
        reference.require(mysql is not None, 'mysql client is missing')
        self.environment = {k: v for k, v in os.environ.items() if not k.startswith('MYSQL_')}
        # no-defaults suppresses init-command/force/host overrides from my.cnf.
        # MySQL still reads .mylogin.cnf; identity/protocol/database are explicit.
        base = [mysql, '--no-defaults', '--user=root', '--protocol=SOCKET',
                '--database=' + DATABASE, '--batch', '--raw', '--skip-column-names',
                '--binary-mode=1', '--local-infile=0', '--skip-reconnect',
                '--connect-timeout=10', '--default-character-set=utf8mb4']
        discovered = subprocess.run(base, input=self._guard() + 'SELECT @@socket;\n',
                                    text=True, encoding='utf-8', stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE, timeout=20,
                                    env=self.environment, check=False)
        self._write('socket-discovery.log', discovered.stdout + discovered.stderr)
        reference.require(discovered.returncode == 0 and not discovered.stderr,
                          'Socket discovery failed')
        self.socket = discovered.stdout.strip()
        reference.require(re.fullmatch(r'/[A-Za-z0-9_./-]+', self.socket)
                          and Path(self.socket).is_socket(), 'Invalid local MySQL socket')
        self.command = base[:4] + ['--socket=' + self.socket] + base[4:]
        reference.require(self.scalar('SELECT DATABASE();') == DATABASE
                          and self.scalar('SELECT CURRENT_USER();') == ACCOUNT,
                          'Production connection identity failed')

    @staticmethod
    def _guard():
        return (
            "SET @dxy_guard=IF(DATABASE()='dianxinyun' AND CURRENT_USER()='root@localhost',"
            "'DO 0','SELECT dxy_wrong_connection_STOP FROM information_schema.schemata');"
            "PREPARE dxy_guard_stmt FROM @dxy_guard; EXECUTE dxy_guard_stmt;"
            "DEALLOCATE PREPARE dxy_guard_stmt; SET time_zone='+00:00';"
            "SET SESSION lock_wait_timeout=30; SET SESSION innodb_lock_wait_timeout=30;\n"
        )

    def _write(self, name, data):
        reference.require(re.fullmatch(r'[A-Za-z0-9_.-]+', name), 'Invalid log name')
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, 'O_NOFOLLOW', 0)
        fd = os.open(self.output / name, flags, 0o600)
        with os.fdopen(fd, 'w', encoding='utf-8') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())

    def _json(self, name, value):
        self._write(name, json.dumps(value, ensure_ascii=False, indent=2) + '\n')

    def _fail(self, stage, error):
        # Reference errors contain only fixed identifiers; all detail remains
        # private even if a subprocess/OS error includes an unexpected value.
        try:
            self._write('failure-%s-%04d.log' % (stage, self.sequence),
                        type(error).__name__ + ': ' + str(error) + '\n')
        except (OSError, reference.Stop):
            pass
        raise ProductionError('数据库操作未完成，请查看私有日志：' + str(self.output)) from None

    @_protected
    def raw(self, sql, label='read', warnings=False):
        reference.require(re.fullmatch(r'[A-Za-z0-9_.-]+', label), 'Invalid SQL log label')
        self.sequence += 1
        name = '%04d-%s.log' % (self.sequence, label)
        command = self.command + (['--show-warnings'] if warnings else [])
        result = subprocess.run(command, input=self._guard() + sql, text=True,
                                encoding='utf-8', stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=600,
                                env=self.environment, check=False)
        self._write(name, result.stdout + result.stderr)
        return result

    @_protected
    def query(self, sql, label='read'):
        result = self.raw(sql, label)
        reference.require(result.returncode == 0 and not result.stderr, 'Database query failed')
        return result.stdout.rstrip('\n').splitlines() if result.stdout else []

    @_protected
    def scalar(self, sql):
        result = self.query(sql)
        reference.require(len(result) == 1, 'Scalar query returned unexpected rows')
        return result[0]

    @_protected
    def verify_baseline(self):
        reference.require(self.tables() == BASELINE_TABLES
                          and self.markers() == BASELINE_MARKERS,
                          'Exact 98-table/25-marker baseline mismatch')
        reference.require(self.scalar('SELECT COUNT(*) FROM sys_data_migration;') == '25',
                          'Duplicate baseline migration markers')
        return True

    @_protected
    def preflight(self):
        self.verify_baseline()
        checks = (
            "SELECT COUNT(*) FROM information_schema.views WHERE table_schema=DATABASE();",
            "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE();",
            "SELECT COUNT(*) FROM information_schema.routines WHERE routine_schema=DATABASE();",
            "SELECT COUNT(*) FROM information_schema.events WHERE event_schema=DATABASE();",
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
            "AND table_type='BASE TABLE' AND (engine IS NULL OR engine<>'InnoDB');",
        )
        for sql in checks:
            reference.require(self.scalar(sql) == '0', 'Unsupported database object or storage engine')
        columns = self.columns(BASELINE_TABLES)
        for table, additions in reference.ADDED_COLUMNS.items():
            reference.require(table not in BASELINE_TABLES
                              or not (set(additions) & set(columns[table])),
                              'Candidate fields already exist: ' + table)
        for table, (field, additions) in reference.CATALOGS.items():
            codes = self.query('SELECT %s FROM %s;' %
                               (reference.identifier(field), reference.identifier(table)))
            reference.require(not (set(codes) & additions), 'Candidate catalog already exists: ' + table)
        return True

    @_protected
    def snapshot(self):
        reference.require(self._snapshot is None, 'Snapshot may only be captured once')
        self.preflight()
        columns = self.columns(BASELINE_TABLES)
        codes = {table: collections.Counter(self.query('SELECT %s FROM %s;' %
                 (reference.identifier(field), reference.identifier(table))))
                 for table, (field, _) in reference.CATALOGS.items()}
        snap = {'columns': columns, 'rows': self.rows(columns), 'codes': codes,
                'schema': self.schema(BASELINE_TABLES)}
        self._json('before-columns.json', columns)
        self._json('before-data.json', reference.summary_rows(snap['rows']))
        self._json('before-schema.json', snap['schema'])
        self._snapshot = snap
        return snap

    @_protected
    def migrate(self, release_dir, snapshot):
        reference.require(snapshot is self._snapshot and snapshot is not None,
                          'Migration requires this connection owner\'s snapshot')
        reference.require(not (self.output / 'migration-started.json').exists(),
                          'Migration already started; automatic retry is forbidden')
        payload = reference.load_payload(Path(release_dir))
        reference.require(len(payload) == 12, 'Frozen migration plan must contain 12 steps')
        self.verify_baseline()
        reference.require(self.columns(BASELINE_TABLES) == snapshot['columns']
                          and self.rows(snapshot['columns']) == snapshot['rows']
                          and self.schema(BASELINE_TABLES) == snapshot['schema'],
                          'Production data/schema changed after the stopped snapshot')
        report = {'database': DATABASE, 'releaseId': reference.RELEASE_ID,
                  'sourceManifestSha256': reference.SOURCE_SHA, 'complete': False,
                  'productionMigrated': False, 'startedAt': self._now(), 'steps': []}
        self._json('migration-started.json', report)
        tables, markers = set(BASELINE_TABLES), set(BASELINE_MARKERS)
        for number, (item, sql) in enumerate(zip(PLAN_DATA['migrations'], payload), 1):
            label = 'migration-%02d-%s' % (number, item['file'])
            result = self.raw(sql, label, warnings=True)
            reference.require(result.returncode == 0 and not result.stderr, 'Migration SQL failed: ' + item['file'])
            warnings = reference.check_warnings(result.stdout, 1)
            tables.update(item['new_tables'])
            markers.add(item['marker'])
            reference.require(len(tables) == item['expected_count'] and self.tables() == tables
                              and self.markers() == markers,
                              'Migration table/marker set mismatch: ' + item['file'])
            reference.require(self.scalar('SELECT COUNT(*) FROM sys_data_migration;') == str(len(markers)),
                              'Migration marker multiplicity mismatch')
            step = {'file': item['file'], 'sha256': item['sha256'], 'warnings': warnings,
                    'tables': len(tables), 'markers': len(markers)}
            self._json('step-%02d.json' % number, step)
            report['steps'].append(step)
        reference.check_original(snapshot['rows'], self.rows(snapshot['columns']))
        final_columns = reference.check_final(self, snapshot['codes'], tables)
        reference.require((len(tables), len(markers)) == (114, 37), 'Unexpected final baseline')
        self._json('after-data.json', reference.summary_rows(self.rows(final_columns)))
        self._json('after-schema.json', self.schema(tables))
        report.update(complete=True, productionMigrated=True, tables=114, markers=37,
                      originalRowsPreserved=True, completedAt=self._now())
        self._json('migration-result.json', report)
        return report

    @staticmethod
    def _now():
        return datetime.datetime.now(datetime.timezone.utc).isoformat()
