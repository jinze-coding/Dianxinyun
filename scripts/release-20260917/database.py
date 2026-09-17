"""Only the additive administrator-correction migration, on the known local database."""
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import urlparse

MIGRATION = '20260916_administrator_data_correction.sql'
MARKER = '20260916_ADMINISTRATOR_DATA_CORRECTION'
TABLES = ('sys_data_correction_log', 'sys_data_correction_attachment')


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


class Database:
    def __init__(self, root, pid):
        self.root = Path(root)
        self.log = self.root / 'DATABASE-UPDATE.log'
        values = dict(part.split(b'=', 1) for part in Path('/proc/' + str(pid) + '/environ').read_bytes().split(b'\0') if b'=' in part)
        url = values.get(b'DB_URL', b'').decode()
        target = urlparse(url.removeprefix('jdbc:'))
        require(url.startswith('jdbc:mysql:') and target.hostname in ('localhost', '127.0.0.1')
                and target.path == '/dianxinyun', '应用数据库不是已核定的本机 dianxinyun，未执行迁移')
        self.port = target.port or 3306
        self.env = {k: v for k, v in os.environ.items() if not k.startswith('MYSQL_')}
        self.command = ['mysql', '--no-defaults', '--user=root', '--protocol=SOCKET',
                        '--database=dianxinyun', '--batch', '--raw', '--skip-column-names',
                        '--binary-mode=1', '--local-infile=0', '--skip-reconnect',
                        '--connect-timeout=10', '--default-character-set=utf8mb4']
        self.sql = (self.root / 'database' / MIGRATION).read_text()

    def query(self, sql):
        guard = ("SET @g=IF(DATABASE()='dianxinyun' AND CURRENT_USER()='root@localhost' "
                 f"AND @@port={self.port},'DO 0','SELECT dxy_wrong_database_STOP');"
                 "PREPARE gs FROM @g; EXECUTE gs; DEALLOCATE PREPARE gs;"
                 "SET SESSION lock_wait_timeout=30; SET SESSION innodb_lock_wait_timeout=30;\n")
        result = subprocess.run(self.command, input=guard + sql, text=True, encoding='utf-8',
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=self.env, timeout=120)
        if result.returncode != 0:
            with self.log.open('a') as stream:
                os.chmod(self.log, 0o600)
                stream.write(result.stderr + '\n')
            raise RuntimeError('数据库操作失败，详情保留在私有 DATABASE-UPDATE.log；未输出业务数据或凭据')
        return result.stdout.strip().splitlines()

    def marked(self):
        return self.query("SELECT COUNT(*) FROM sys_data_migration WHERE migration_key='" + MARKER + "';") == ['1']

    def schema(self, allow_absent=False):
        for table in TABLES:
            block = re.search(r'CREATE TABLE IF NOT EXISTS ' + table + r' \((.*?)\) ENGINE=', self.sql, re.S).group(1)
            definitions = [line.strip() for line in block.splitlines() if line.strip() and not line.strip().startswith(('UNIQUE KEY ', 'KEY '))]
            expected = {}
            for line in definitions:
                name, definition = line.split(' ', 1)
                kind = definition.split()[0].lower()
                expected[name] = (kind, 'NO' if 'NOT NULL' in definition or 'PRIMARY KEY' in definition else 'YES')
            rows = self.query("SELECT column_name,column_type,is_nullable FROM information_schema.columns "
                              "WHERE table_schema=DATABASE() AND table_name='" + table + "';")
            if not rows and allow_absent:
                continue
            actual = {parts[0]: (parts[1], parts[2]) for parts in (row.split('\t') for row in rows)}
            require(actual == expected, '数据纠错表结构不符：' + table)
            require(self.query("SELECT engine FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='" + table + "';") == ['InnoDB'], '数据纠错表必须使用 InnoDB')
            required_indexes = {'PRIMARY': ('0', 'id')}
            for name, columns in re.findall(r'UNIQUE KEY (\w+)\(([^)]+)\)', block):
                required_indexes[name] = ('0', columns)
            indexes = self.query("SELECT index_name,MIN(non_unique),GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') "
                                 "FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='" + table + "' GROUP BY index_name;")
            found = {parts[0]: tuple(parts[1:]) for parts in (row.split('\t') for row in indexes)}
            require(all(found.get(key) == value for key, value in required_indexes.items()), '数据纠错唯一索引不符：' + table)

    def catalogs(self, allow_absent=False):
        permission = self.query("SELECT permission_code,module_code FROM sys_permission WHERE permission_code='system.data.correct';")
        menu = self.query("SELECT menu_code,client_type,route_path,permission_code FROM sys_menu WHERE menu_code='SYSTEM_DATA_CORRECTION';")
        require(permission == ['system.data.correct\tSYSTEM'] or (allow_absent and not permission), '数据纠错权限目录不符')
        require(menu == ['SYSTEM_DATA_CORRECTION\tWEB\tSYSTEM_DATA_CORRECTION\tsystem.data.correct'] or (allow_absent and not menu), '数据纠错菜单目录不符')

    def preflight(self):
        # The connection guard runs on every invocation, including read-only preflight.
        require(self.query("SELECT COUNT(*) FROM sys_menu WHERE menu_code='WEB_SYSTEM' AND deleted=0;") == ['1'], '缺少原系统管理目录')
        done = self.marked()
        self.schema(allow_absent=not done)
        self.catalogs(allow_absent=not done)
        return done

    def apply(self):
        if self.preflight():
            return 'ALREADY_APPLIED'
        self.query(self.sql)
        require(self.marked(), '迁移标记未保存')
        self.schema()
        self.catalogs()
        return 'APPLIED'
