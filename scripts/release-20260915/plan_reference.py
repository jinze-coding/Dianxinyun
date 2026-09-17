#!/usr/bin/env python3
"""Fixed 2026-09-15 rehearsal. Never connects as root or migrates dianxinyun.

Run on the server only after restore-release-validation-copy.sh succeeds.
The embedded plan binds to the already uploaded, immutable candidate SQL.
No application is started; no credentials or business values are printed.
"""
import collections
import configparser
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys

RELEASE_ID = 'Dianxinyun-update-20260915-0912'
RELEASE = Path('/root/releases/20260915-0912') / RELEASE_ID
STATE = Path('/root/releases/20260915-0912/validation-093640')
BACKUP = '/root/backups/dianxinyun/precheck-20260915-093640'
DATABASE = 'dxycheck20260915093640'
ACCOUNT = 'dxycheck0915093640'
SOURCE_SHA = '95e6a1fc24f569b3c9224d01971d3193b4dd40044401023e9b94393ea408c9af'
PLAN_DATA = json.loads(r'''
{
  "baseline_tables": [
    "camera_resource",
    "device_info",
    "device_status_record",
    "document_circulation_event",
    "document_distribution_batch",
    "document_distribution_item",
    "document_distribution_recipient",
    "document_distribution_recipient_item",
    "document_folder",
    "document_incoming_batch",
    "document_incoming_item",
    "electric_box",
    "electric_box_inspection_scope",
    "electric_box_qr_log",
    "external_system_config",
    "file_resource",
    "general_inspection_action_log",
    "general_inspection_event_outbox",
    "general_inspection_export_job",
    "general_inspection_export_job_task",
    "general_inspection_plan",
    "general_inspection_plan_version",
    "general_inspection_point",
    "general_inspection_point_category",
    "general_inspection_project_setting",
    "general_inspection_rectification",
    "general_inspection_task",
    "general_inspection_task_item",
    "general_inspection_template",
    "general_inspection_template_item",
    "general_inspection_template_version",
    "inspection_permission_template",
    "inspection_record",
    "inspection_record_item",
    "inspection_rectification",
    "inspection_rectification_review_log",
    "inspection_review_log",
    "inspection_template",
    "inspection_template_item",
    "person_certificate",
    "person_entry_exit_log",
    "project_document",
    "project_document_version",
    "project_info",
    "project_inspection_setting",
    "quality_issue",
    "quality_issue_export_job",
    "quality_issue_export_job_item",
    "quality_issue_log",
    "quality_weekly_inspection",
    "quality_weekly_inspection_draft_item",
    "quality_weekly_reminder_setting",
    "registration_application",
    "safety_education_batch",
    "safety_education_person",
    "seal_application",
    "seal_application_file",
    "seal_application_item",
    "seal_application_log",
    "seal_definition",
    "site_guard_visit_audit_log",
    "site_guard_visit_person",
    "site_guard_visit_qr",
    "site_guard_visit_registration",
    "site_meeting_visit_audit_log",
    "site_meeting_visit_person",
    "site_meeting_visit_registration",
    "site_visit_audit_log",
    "site_visit_invitation",
    "site_visit_person",
    "site_visitor_profile",
    "site_visitor_profile_audit_log",
    "site_visitor_profile_person",
    "sys_data_migration",
    "sys_menu",
    "sys_operation_log",
    "sys_permission",
    "sys_role",
    "sys_role_business_module",
    "sys_role_menu",
    "sys_role_permission",
    "sys_user",
    "sys_user_project",
    "sys_user_project_role",
    "sys_user_role",
    "sys_user_wechat_binding",
    "temporary_person",
    "user_notification",
    "video_access_log",
    "video_layout_config",
    "wechat_access_application",
    "wechat_message_log",
    "wechat_subscription_state",
    "workflow_approval_config",
    "workflow_approval_config_user",
    "workflow_approval_instance",
    "workflow_approval_task",
    "workflow_cc_recipient"
  ],
  "baseline_markers": [
    "20260728_UNIFIED_REGISTRATION_RBAC_SEED_V1",
    "20260729_PROJECT_MULTI_ROLE_MEMBER_MANAGEMENT_V1",
    "20260729_SHARED_BUSINESS_MODULE_ACCESS_V1",
    "20260803_ROLE_MENU_PERMISSION_HIERARCHY_V1",
    "20260807_INSPECTION_RECTIFICATION_CLOSURE_V1",
    "20260807_RETIRE_PROJECT_MEMBER_MANAGEMENT_PAGE_V1",
    "20260807_SITE_ACCESS_VISITOR_INVITATION_V1",
    "20260808_SEAL_APPLICATION_V1",
    "20260810_INSPECTION_ELECTRICIAN_SUBMIT_PERMISSION_V1",
    "20260810_SITE_ACCESS_REUSABLE_VISITOR_PROFILE_V1",
    "20260810_SITE_ACCESS_VISITOR_IDENTITY_HMAC_V1",
    "20260814_SITE_ACCESS_COMPANION_OPTIONAL_CONTACT_V1",
    "20260814_SITE_ACCESS_GUARD_VISITOR_REGISTRATION_V1",
    "20260814_SITE_ACCESS_REMOVE_ID_CARD_REQUIREMENT_V1",
    "20260826_DOCUMENT_CIRCULATION_V1",
    "20260826_GENERAL_INSPECTION_FIXED_EDGE_V1",
    "20260826_GENERAL_INSPECTION_V1",
    "20260826_QUALITY_WEEKLY_INSPECTION_V1",
    "20260826_SITE_ACCESS_MEETING_INVITATION_V1",
    "20260828_EDGE_INSPECTION_EXPORT_V1",
    "20260828_QUALITY_ISSUE_DAILY_EXPORT_V1",
    "20260829_INSPECTION_PERMISSION_SEPARATION_V1",
    "20260829_INSPECTION_SUBMISSION_REMINDER_V1",
    "20260829_ROLE_AUTHORIZATION_CONSISTENCY_V1",
    "20260901_SITE_ACCESS_REENCRYPT_LEGACY_DEVELOPMENT_KEY_V1"
  ],
  "migrations": [
    {
      "file": "20260902_site_access_meeting_checkin.sql",
      "sha256": "31fe03ae3dcd70bce3c55bb5b4ccd23c6a22d8340cae4471ab4728b9fe38ea7a",
      "marker": "20260902_SITE_ACCESS_MEETING_CHECKIN_V1",
      "new_tables": [
        "site_meeting_checkin_qr",
        "site_meeting_attendance"
      ],
      "expected_count": 100
    },
    {
      "file": "20260910_visitor_reuse_guard_meeting.sql",
      "sha256": "f78e3559473d9dbdc2d17925861645b411d040f3bc8bf7af333f07c7d13bee73",
      "marker": "20260910_VISITOR_REUSE_GUARD_MEETING_V1",
      "new_tables": [
        "site_visitor_personal_profile",
        "site_visitor_personal_profile_audit",
        "site_guard_meeting_registration"
      ],
      "expected_count": 103
    },
    {
      "file": "20260911_visitor_identity_autofill.sql",
      "sha256": "195dad21f9a7f1e54e3fb764d8057f08609216be58fcfb161a90b4231bd12c81",
      "marker": "20260911_VISITOR_IDENTITY_AUTOFILL_V1",
      "new_tables": [],
      "expected_count": 103
    },
    {
      "file": "20260911_meeting_materials.sql",
      "sha256": "d1689cd1a3b024ae9777a825b7a333db5f96a5f6d7d994d87458c449963ca75b",
      "marker": "20260911_MEETING_MATERIALS_V1",
      "new_tables": [
        "site_meeting_material",
        "site_meeting_material_version",
        "site_meeting_material_preview"
      ],
      "expected_count": 106
    },
    {
      "file": "20260911_seal_optional_attachments.sql",
      "sha256": "e8dfdf8157b0e43cb77f0e617bf2bf7b0e0a63ebc41579d2dc1b5d68c31dc816",
      "marker": "20260911_SEAL_OPTIONAL_ATTACHMENTS_V1",
      "new_tables": [],
      "expected_count": 106
    },
    {
      "file": "20260911_seal_form_merge_export.sql",
      "sha256": "31afadad02e3497f44c36ec983800e8ba936fa0182c24ef3dead52fb763173eb",
      "marker": "20260911_SEAL_FORM_MERGE_EXPORT_V1",
      "new_tables": [
        "seal_form_export_job",
        "seal_form_export_item"
      ],
      "expected_count": 108
    },
    {
      "file": "20260914_safety_committee_inspection.sql",
      "sha256": "fc83addd186e91441edfb8dcfd09f44ebca2b47ece11edab8a6a99df2f4521a9",
      "marker": "20260914_SAFETY_COMMITTEE_V1",
      "new_tables": [
        "safety_committee_record",
        "safety_committee_attachment",
        "safety_committee_log"
      ],
      "expected_count": 111
    },
    {
      "file": "20260914_project_business_modules.sql",
      "sha256": "1eff1efb9a2075cbf983b652e1dc0c434dc075ea2fe025947a5579d0e4273e16",
      "marker": "20260914_PROJECT_BUSINESS_MODULES",
      "new_tables": [
        "project_business_module"
      ],
      "expected_count": 112
    },
    {
      "file": "20260914_user_batch_import.sql",
      "sha256": "ff76bb2d66f6cd400ce83ae083c5e7e54c493aa36ee9d40a3b45d9ec75e1d1b6",
      "marker": "20260914_USER_BATCH_IMPORT",
      "new_tables": [
        "system_user_import_batch",
        "system_user_import_item"
      ],
      "expected_count": 114
    },
    {
      "file": "20260914_committee_attachment_rotation.sql",
      "sha256": "2f2320e44495cd3a74b977cee85c386252d0c6809ea75989b985cc8c41ca93bf",
      "marker": "20260914_COMMITTEE_ATTACHMENT_ROTATION",
      "new_tables": [],
      "expected_count": 114
    },
    {
      "file": "20260915_project_inbox_entry.sql",
      "sha256": "ba81c595ea92993878cc669cd8f80cb8b81c2f1c776dca02e0ddeaf9c8bf2fb3",
      "marker": "20260915_PROJECT_INBOX_ENTRY",
      "new_tables": [],
      "expected_count": 114
    },
    {
      "file": "20260915_user_import_admin_password.sql",
      "sha256": "0768fd715911b9b28953fcfa0384c7cce1abad5813f4465d6b7cb20a5e865a54",
      "marker": "20260915_USER_IMPORT_ADMIN_PASSWORD",
      "new_tables": [],
      "expected_count": 114
    }
  ]
}
''')

CATALOGS = {
    'sys_menu': ('menu_code', {
        'WEB_SAFETY_COMMITTEE', 'MINI_SAFETY_COMMITTEE',
        'SAFETY_COMMITTEE_RECORDS', 'SYSTEM_PROJECT_MODULE'}),
    'sys_permission': ('permission_code', {
        'seal.application.export', 'safety_committee.view',
        'safety_committee.submit', 'safety_committee.edit_own'}),
}
ADDED_COLUMNS = {
    'site_meeting_visit_registration': ['registration_source'],
    'site_meeting_visit_audit_log': ['person_id', 'checkin_qr_id'],
    'site_visit_invitation': ['wechat_app_id', 'visitor_identity_hash'],
    'seal_application': ['stamped_result_required'],
    'sys_user': ['must_change_password', 'temporary_password_expires_at'],
    'project_info': ['inbox_entry_visible'],
    'safety_committee_attachment': ['rotation_degrees', 'rotation_version'],
    'system_user_import_batch': ['temporary_password_cipher', 'confirmation_password_hash'],
}
ADDED_INDEXES = {
    'site_meeting_visit_audit_log': {'idx_site_meeting_visit_audit_person': 'person_id,create_time'},
    'site_visit_invitation': {
        'idx_site_visit_owner_window': 'project_id,wechat_app_id,visitor_identity_hash,status,visit_start_time,visit_end_time,deleted',
        'idx_single_app_identity_latest': 'wechat_app_id,visitor_identity_hash,deleted,invite_type,status,submitted_time,id'},
    'site_visitor_personal_profile': {'idx_personal_app_identity_latest': 'wechat_app_id,owner_identity_hash,deleted,last_submitted_time,id'},
    'site_meeting_visit_registration': {'idx_meeting_app_identity_latest': 'wechat_app_id,visitor_identity_hash,deleted,status,registered_time,id'},
    'site_guard_visit_registration': {'idx_guard_app_identity_latest': 'wechat_app_id,visitor_identity_hash,deleted,status,registered_time,id'},
    'site_visitor_profile': {'idx_named_app_identity_latest': 'wechat_app_id,owner_openid_hash,deleted,status,last_used_time,id'},
}


class Stop(RuntimeError):
    pass


def require(ok, message):
    if not ok:
        raise Stop(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def identifier(name):
    require(re.fullmatch(r'[A-Za-z0-9_]+', name), '非法数据库标识')
    return '`' + name + '`'


def private_path(path, directory=False):
    info = path.lstat()
    require(not path.is_symlink() and info.st_uid == 0 and info.st_mode & 0o077 == 0,
            '验证文件权限异常：' + path.name)
    require(stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode),
            '验证文件类型异常：' + path.name)


def load_payload(root):
    manifest = json.loads((root / 'RELEASE-MANIFEST.json').read_text())
    require(manifest['releaseId'] == RELEASE_ID and manifest['sourceManifestSha256'] == SOURCE_SHA,
            '更新包归属不符')
    require(digest((root / 'source-reference/SOURCE_MANIFEST.txt').read_bytes()) == SOURCE_SHA,
            '源码清单摘要不符')
    payload = []
    for item in PLAN_DATA['migrations']:
        path = root / 'database/migrations' / item['file']
        require(not path.is_symlink(), '迁移文件不能是符号链接')
        data = path.read_bytes()
        require(digest(data) == item['sha256'], 'SQL 摘要不符：' + item['file'])
        payload.append(data.decode('utf-8'))
    return payload


def check_warnings(output, pass_number):
    """Only audited no-op/deprecation diagnostics; never blanket-ignore warnings."""
    codes = collections.Counter()
    for line in output.splitlines():
        if not re.match(r'^(Warning|Note|Error)(?:\s|:)', line):
            continue
        match = re.match(r'^(Warning|Note|Error)\s*\(Code (\d+)\):\s*(.*)$', line)
        require(match is not None, '出现无法识别的 SQL 诊断，请反馈日志')
        level, number, message = match.groups()
        harmless = (
            (level == 'Note' and number == '1050' and pass_number == 2 and 'already exists' in message)
            or (level == 'Warning' and number == '1062' and pass_number == 2 and message.startswith('Duplicate entry '))
            or (level == 'Warning' and number == '1287' and "'VALUES function' is deprecated" in message)
            or (level == 'Warning' and number == '1681' and message == 'Integer display width is deprecated and will be removed in a future release.')
        )
        require(harmless, '出现未放行 SQL 诊断，代码 ' + number + '；请反馈日志')
        codes[number] += 1
    return dict(codes)


class MySQL:
    def __init__(self, state, output):
        self.output = output
        self.sequence = 0
        cnf = state / 'client.cnf'
        private_path(cnf)
        config = configparser.ConfigParser(interpolation=None)
        config.read(cnf)
        require(config.sections() == ['client'] and not config.defaults(), '隔离连接配置段不符')
        settings = config['client']
        require(set(settings) == {'user', 'password', 'socket', 'protocol'}, '隔离连接配置项不符')
        require(settings['user'] == ACCOUNT and settings['protocol'] == 'SOCKET', '隔离账号不符')
        require(re.fullmatch(r'/[A-Za-z0-9_./-]+', settings['socket']) and Path(settings['socket']).is_socket(),
                '本机 MySQL socket 不符')
        require(re.fullmatch(r'Aa1![0-9a-f]{48}', settings['password']), '隔离凭据格式不符')
        mysql = shutil.which('mysql')
        require(mysql is not None, '缺少 mysql 客户端')
        # Explicit identity/socket override any login-path options. MySQL 8.0 still
        # reads .mylogin.cnf with --defaults-file; it has no --no-login-paths option.
        self.command = [mysql, '--defaults-file=' + str(cnf), '--user=' + ACCOUNT,
                        '--protocol=SOCKET', '--socket=' + settings['socket'],
                        '--database=' + DATABASE, '--batch', '--raw', '--skip-column-names',
                        '--binary-mode=1', '--local-infile=0', '--connect-timeout=10',
                        '--default-character-set=utf8mb4']
        self.environment = {k: v for k, v in os.environ.items() if not k.startswith('MYSQL_')}

    def raw(self, sql, label, warnings=False):
        self.sequence += 1
        log = self.output / ('%04d-%s.log' % (self.sequence, label))
        # Abort inside every fresh connection before user SQL, without needing
        # procedure privileges or using a root connection for migrations.
        guard = (
            "SET @dxy_guard=IF(DATABASE()='%s' AND CURRENT_USER()='%s@localhost',"
            "'DO 0','SELECT dxy_wrong_connection_STOP FROM information_schema.schemata');"
            "PREPARE dxy_guard_stmt FROM @dxy_guard; EXECUTE dxy_guard_stmt;"
            "DEALLOCATE PREPARE dxy_guard_stmt; SET time_zone='+00:00';"
            "SET SESSION lock_wait_timeout=30; SET SESSION innodb_lock_wait_timeout=30;\n"
        ) % (DATABASE, ACCOUNT)
        command = self.command + (['--show-warnings'] if warnings else [])
        try:
            result = subprocess.run(command, input=guard + sql, text=True, encoding='utf-8',
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                    timeout=600, env=self.environment, check=False)
        except subprocess.TimeoutExpired:
            raise Stop('SQL 超时，请反馈；不要重跑或清理副本') from None
        log.write_text(result.stdout + result.stderr, encoding='utf-8')
        return result

    def query(self, sql, label='read'):
        result = self.raw(sql, label)
        require(result.returncode == 0 and not result.stderr, '数据库读取失败，日志：' + label)
        return result.stdout.rstrip('\n').splitlines() if result.stdout else []

    def scalar(self, sql):
        rows = self.query(sql)
        require(len(rows) == 1, '标量核验返回数量不符')
        return rows[0]

    def isolation(self):
        require(self.scalar('SELECT DATABASE();') == DATABASE, '验证库不符')
        require(self.scalar('SELECT CURRENT_USER();') == ACCOUNT + '@localhost', '验证账号不符')
        denied = self.raw('SELECT 1 FROM dianxinyun.sys_user LIMIT 1;', 'isolation-denied')
        require(denied.returncode != 0 and re.search(r'ERROR 1142 .*SELECT command denied', denied.stderr),
                '正式库隔离检查未通过')
        require(self.scalar('SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE();') == '0',
                '验证库存在非预期触发器')

    def tables(self):
        return set(self.query("SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name;"))

    def markers(self):
        return set(self.query('SELECT migration_key FROM sys_data_migration ORDER BY migration_key;'))

    def columns(self, tables):
        columns = {}
        for table in sorted(tables):
            columns[table] = self.query("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='%s' ORDER BY ordinal_position;" % table)
            require(columns[table], '未读取到表字段：' + table)
        return columns

    def rows(self, columns):
        snapshots = {}
        for table, names in sorted(columns.items()):
            # JSON encodes NULL separately from empty text and field boundaries;
            # HEX preserves binary/text bytes. Only row digests leave MySQL.
            fields = ','.join('HEX(CAST(%s AS BINARY))' % identifier(name) for name in names)
            rows = self.query('SELECT SHA2(CAST(JSON_ARRAY(%s) AS CHAR),256) FROM %s;' % (fields, identifier(table)), 'hash-' + table)
            require(all(re.fullmatch(r'[0-9a-f]{64}', row) for row in rows), '行摘要格式不符：' + table)
            snapshots[table] = collections.Counter(rows)
        return snapshots

    def schema(self, tables):
        result = {}
        for table in sorted(tables):
            ddl = '\n'.join(self.query('SHOW CREATE TABLE ' + identifier(table) + ';', 'schema-' + table))
            # INSERT IGNORE may reserve unused IDs on repeat. Compare actual
            # schema and every stored row; keep raw counters in private logs.
            result[table] = re.sub(r'\sAUTO_INCREMENT=\d+(?=\s|$)', '', ddl)
        return result


def check_original(before, after):
    append_only = set(CATALOGS) | {'sys_data_migration'}
    for table, rows in before.items():
        require(table in after and not (rows - after[table]), '原记录被修改或丢失：' + table)
        require(table in append_only or rows == after[table], '原业务表出现非预期新增记录：' + table)


def summary_rows(rows):
    return {table: {'rows': sum(values.values()), 'sha256': digest(json.dumps(sorted(values.items())).encode())}
            for table, values in sorted(rows.items())}


def check_final(db, original_codes, expected_tables):
    columns = db.columns(expected_tables)
    for table, names in ADDED_COLUMNS.items():
        require(set(names) <= set(columns[table]), '缺少新增字段：' + table)
    for table, indexes in ADDED_INDEXES.items():
        for name, fields in indexes.items():
            rows = db.query("SELECT column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='%s' AND index_name='%s' ORDER BY seq_in_index;" % (table, name))
            require(','.join(rows) == fields, '索引定义不符：' + name)
    for table, (field, additions) in CATALOGS.items():
        codes = db.query('SELECT %s FROM %s;' % (identifier(field), identifier(table)))
        require(collections.Counter(codes) == original_codes[table] + collections.Counter(additions),
                '新增菜单/权限目录不符：' + table)
    empty_tables = expected_tables - set(PLAN_DATA['baseline_tables']) - {'project_business_module'}
    for table in sorted(empty_tables):
        require(db.scalar('SELECT COUNT(*) FROM ' + identifier(table) + ';') == '0', '新增业务表不为空：' + table)
    defaults = {
        'sys_user': 'must_change_password <> 0 OR must_change_password IS NULL OR temporary_password_expires_at IS NOT NULL',
        'project_info': 'inbox_entry_visible <> 1 OR inbox_entry_visible IS NULL',
        'seal_application': 'stamped_result_required <> 0 OR stamped_result_required IS NULL',
        'site_meeting_visit_registration': "registration_source <> 'INVITATION' OR registration_source IS NULL",
        'site_visit_invitation': 'wechat_app_id IS NOT NULL OR visitor_identity_hash IS NOT NULL',
        'site_meeting_visit_audit_log': 'person_id IS NOT NULL OR checkin_qr_id IS NOT NULL',
    }
    for table, condition in defaults.items():
        require(db.scalar('SELECT COUNT(*) FROM %s WHERE %s;' % (identifier(table), condition)) == '0',
                '历史记录新增字段默认值不符：' + table)
    projects = db.query('SELECT id FROM project_info WHERE deleted=0;')
    expected = {pid + '\t' + module for pid in projects for module in ('SITE_ACCESS', 'DOCUMENT', 'INSPECTION', 'QUALITY', 'SAFETY_COMMITTEE')}
    modules = db.query('SELECT project_id,module_code FROM project_business_module;')
    require(len(modules) == len(expected) and set(modules) == expected, '项目模块初始集合不符')
    require(db.scalar('SELECT COUNT(*) FROM project_business_module WHERE enabled<>1 OR version<>1 OR activated_at IS NOT NULL OR updated_by IS NOT NULL;') == '0',
            '项目模块默认值不符')
    require(db.scalar("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='site_meeting_visit_registration' AND column_name IN ('wechat_app_id','visitor_identity_hash') AND is_nullable='YES';") == '2', '会议身份可空字段不符')
    require(db.scalar("SELECT datetime_precision FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='site_visitor_personal_profile' AND column_name='last_submitted_time';") == '6', '资料时间精度不符')
    return columns


def main():
    require(len(sys.argv) == 1, '此脚本不接受库名、账号或其他参数')
    require(os.getuid() == 0, '请在服务器 root 终端运行')
    os.umask(0o077)
    private_path(STATE, directory=True)
    private_path(STATE / 'restore-status.env')
    restore = dict(line.split('=', 1) for line in (STATE / 'restore-status.env').read_text().splitlines())
    require(restore == {'QA_DATABASE': DATABASE, 'BACKUP_DIR': BACKUP, 'RESTORED_TABLES': '98', 'RESTORE_COMPLETE': 'true'},
            '隔离恢复状态不符')
    payload = load_payload(RELEASE)
    output = STATE / 'migration-rehearsal'
    require(not output.exists() and not output.is_symlink(), '演练目录已存在；请反馈，不要重复运行')
    output.mkdir(mode=0o700)
    db = MySQL(STATE, output)
    db.isolation()
    baseline = set(PLAN_DATA['baseline_tables'])
    baseline_markers = set(PLAN_DATA['baseline_markers'])
    require(db.tables() == baseline and db.markers() == baseline_markers, '98 表/25 项标记精确基线不符')
    require(set((STATE / 'tables-before.txt').read_text().splitlines()) == baseline,
            '恢复时的表清单不符')
    require(set((STATE / 'markers-before.txt').read_text().splitlines()) == baseline_markers,
            '恢复时的标记清单不符')
    original_columns = db.columns(baseline)
    for table, names in ADDED_COLUMNS.items():
        require(table not in baseline or not (set(names) & set(original_columns[table])),
                '迁移前发现候选版本字段：' + table)
    original_codes = {}
    for table, (field, additions) in CATALOGS.items():
        original_codes[table] = collections.Counter(db.query('SELECT %s FROM %s;' % (identifier(field), identifier(table))))
        require(not (set(original_codes[table]) & additions), '迁移前发现候选版本目录：' + table)
    print('隔离、更新包及 98 表基线核验通过，正在保存原记录摘要。', flush=True)
    original = db.rows(original_columns)
    (output / 'before-columns.json').write_text(json.dumps(original_columns, indent=2))
    (output / 'before-data.json').write_text(json.dumps(summary_rows(original), indent=2))
    report = {'database': DATABASE, 'releaseId': RELEASE_ID, 'sourceManifestSha256': SOURCE_SHA,
              'scriptSha256': digest(Path(__file__).read_bytes()), 'migrations': PLAN_DATA['migrations'],
              'startedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'passes': []}
    first_rows = first_schema = None
    expected_tables = set(baseline)
    expected_markers = set(baseline_markers)
    for pass_number in (1, 2):
        pass_report = []
        for step, (item, sql) in enumerate(zip(PLAN_DATA['migrations'], payload), 1):
            label = 'pass%d-%02d-%s' % (pass_number, step, item['file'])
            result = db.raw(sql, label, warnings=True)
            require(result.returncode == 0 and not result.stderr, '迁移失败，日志：' + label)
            warnings = check_warnings(result.stdout, pass_number)
            expected_tables.update(item['new_tables'])
            expected_markers.add(item['marker'])
            require(db.tables() == expected_tables and db.markers() == expected_markers,
                    '迁移后表/标记集合不符：' + item['file'])
            pass_report.append({'file': item['file'], 'warnings': warnings, 'tables': len(expected_tables)})
            print('第 %d 轮 %02d/12 通过：%s（%d 表）' % (pass_number, step, item['file'], len(expected_tables)), flush=True)
        check_original(original, db.rows(original_columns))
        final_columns = check_final(db, original_codes, expected_tables)
        rows = db.rows(final_columns)
        schema = db.schema(expected_tables)
        if pass_number == 1:
            first_rows, first_schema = rows, schema
        else:
            require(rows == first_rows, '重复迁移改变了已存储记录')
            require(schema == first_schema, '重复迁移改变了表结构')
        (output / ('pass%d-data.json' % pass_number)).write_text(json.dumps(summary_rows(rows), indent=2))
        (output / ('pass%d-schema.json' % pass_number)).write_text(json.dumps(schema, indent=2))
        report['passes'].append(pass_report)
    report.update({'complete': True, 'tables': len(expected_tables), 'markers': len(expected_markers),
                   'originalRowsPreserved': True, 'repeatDataAndSchemaUnchanged': True,
                   'autoIncrementCountersExcluded': True, 'productionMigrated': False,
                   'completedAt': datetime.datetime.now(datetime.timezone.utc).isoformat()})
    (output / 'result.json').write_text(json.dumps(report, indent=2))
    print('隔离迁移双跑通过：114 表，37 项迁移标记。', flush=True)
    print('原 98 张表的旧字段记录保留；重复执行后记录与表结构一致。', flush=True)
    print('结果目录：' + str(output), flush=True)
    print('正式库未迁移；服务未停止或切换。', flush=True)


if __name__ == '__main__':
    try:
        main()
    except (Stop, OSError, ValueError, KeyError, configparser.Error) as error:
        # Do not print database output, credentials or exception representations.
        print('已停止：' + (str(error) if isinstance(error, Stop) else type(error).__name__) + '。请反馈，不要重跑或清理。', file=sys.stderr)
        sys.exit(1)
