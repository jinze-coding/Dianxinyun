-- 用印申请一期工作流：每枚印章独立二维码、按用户 ANY_ONE 审批、抄送、站内通知及归档追溯。
-- 非破坏、可重复执行；执行前必须备份数据库。用印 scene 仅保存 SHA-256 摘要与 AES-GCM 密文。

CREATE TABLE IF NOT EXISTS seal_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    seal_code VARCHAR(40) NOT NULL,
    seal_name VARCHAR(100) NOT NULL,
    seal_type VARCHAR(30) NOT NULL DEFAULT 'PROJECT_SEAL',
    company_name VARCHAR(200) NOT NULL DEFAULT '上海建工智慧营造有限公司',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    scene_token_hash CHAR(64) NOT NULL,
    scene_token_encrypted VARCHAR(512) NOT NULL,
    qr_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
    qr_version INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_seal_definition_code (seal_code),
    UNIQUE KEY uk_seal_definition_name (project_id, seal_name, deleted),
    UNIQUE KEY uk_seal_definition_scene_hash (scene_token_hash),
    KEY idx_seal_definition_project_status (project_id, status, sort_order, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目实体印章与独立用印二维码';

CREATE TABLE IF NOT EXISTS seal_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_no VARCHAR(40) NULL COMMENT '提交时生成 YYSQ-yyyyMMdd-id8+',
    request_key VARCHAR(64) NOT NULL,
    source_application_id BIGINT,
    project_id BIGINT NOT NULL,
    seal_id BIGINT NOT NULL,
    seal_name VARCHAR(100) NOT NULL,
    company_name VARCHAR(200) NOT NULL,
    department_name VARCHAR(200) NOT NULL COMMENT '提交时项目名称快照',
    purpose VARCHAR(1000) NOT NULL,
    applicant_id BIGINT NOT NULL,
    applicant_name VARCHAR(50) NOT NULL,
    applicant_phone VARCHAR(20),
    application_date DATE NULL COMMENT '提交日快照',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/WITHDRAWN',
    approval_instance_id BIGINT,
    submit_time DATETIME,
    approver_id BIGINT,
    approver_name VARCHAR(50),
    approval_opinion VARCHAR(1000),
    approval_time DATETIME,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_seal_application_no (application_no),
    UNIQUE KEY uk_seal_application_request (applicant_id, request_key),
    KEY idx_seal_application_project_status (project_id, status, application_date, deleted),
    KEY idx_seal_application_applicant (applicant_id, create_time, deleted),
    KEY idx_seal_application_seal (seal_id, status, create_time),
    KEY idx_seal_application_source (source_application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用印申请及可信提交快照';

CREATE TABLE IF NOT EXISTS seal_application_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    document_name VARCHAR(200) NOT NULL,
    copies INT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_seal_item_application (application_id, sort_order),
    KEY idx_seal_item_project (project_id, application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用印文件与份数明细';

CREATE TABLE IF NOT EXISTS seal_application_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    item_id BIGINT,
    project_id BIGINT NOT NULL,
    file_resource_id BIGINT NOT NULL,
    file_role VARCHAR(30) NOT NULL COMMENT 'SOURCE/STAMPED_RESULT',
    uploader_id BIGINT NOT NULL,
    uploader_name VARCHAR(50) NOT NULL,
    archived_document_id BIGINT,
    archived_version_id BIGINT,
    archived_time DATETIME,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_seal_file_resource (file_resource_id),
    KEY idx_seal_file_application (application_id, file_role, deleted),
    KEY idx_seal_file_item (item_id, deleted),
    KEY idx_seal_file_archive (archived_document_id, archived_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用印源文件、盖章件及资料版本追溯';

CREATE TABLE IF NOT EXISTS seal_application_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    action_code VARCHAR(40) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(50) NOT NULL,
    opinion VARCHAR(1000),
    description VARCHAR(1000),
    ip_address VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_seal_log_application (application_id, create_time, id),
    KEY idx_seal_log_project (project_id, action_code, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用印申请不可变审计日志';

CREATE TABLE IF NOT EXISTS workflow_approval_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_code VARCHAR(50) NOT NULL,
    project_id BIGINT NOT NULL,
    seal_id BIGINT NOT NULL,
    approval_mode VARCHAR(20) NOT NULL DEFAULT 'ANY_ONE',
    enabled TINYINT NOT NULL DEFAULT 1,
    config_version INT NOT NULL DEFAULT 1,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_workflow_config_business_seal (business_code, project_id, seal_id),
    KEY idx_workflow_config_project (project_id, business_code, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='按项目与具体印章配置的用户审批';

CREATE TABLE IF NOT EXISTS workflow_approval_config_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    config_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    assignment_type VARCHAR(20) NOT NULL COMMENT 'APPROVER/DEFAULT_CC',
    sort_order INT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_workflow_config_user (config_id, assignment_type, user_id),
    KEY idx_workflow_config_user_project (project_id, user_id, assignment_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批配置直接选人与默认抄送';

CREATE TABLE IF NOT EXISTS workflow_approval_instance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_code VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    config_id BIGINT NOT NULL,
    config_version INT NOT NULL,
    approval_mode VARCHAR(20) NOT NULL DEFAULT 'ANY_ONE',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/WITHDRAWN',
    initiator_id BIGINT NOT NULL,
    initiator_name VARCHAR(50) NOT NULL,
    decision_user_id BIGINT,
    decision_user_name VARCHAR(50),
    decision_opinion VARCHAR(1000),
    decision_time DATETIME,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_workflow_instance_business (business_code, business_id),
    KEY idx_workflow_instance_project_status (project_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批配置提交时快照实例';

CREATE TABLE IF NOT EXISTS workflow_approval_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    instance_id BIGINT NOT NULL,
    business_code VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    assignee_user_id BIGINT NOT NULL,
    assignee_name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
    assignment_source VARCHAR(30) NOT NULL DEFAULT 'CONFIG' COMMENT 'CONFIG/ADMIN_REASSIGN',
    transferred_from_task_id BIGINT,
    decision_user_id BIGINT,
    decision_user_name VARCHAR(50),
    decision_opinion VARCHAR(1000),
    decision_time DATETIME,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_workflow_task_assignee (assignee_user_id, status, create_time),
    KEY idx_workflow_task_instance (instance_id, status, id),
    KEY idx_workflow_task_business (business_code, business_id, status),
    KEY idx_workflow_task_project (project_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户级审批待办与改派快照';

CREATE TABLE IF NOT EXISTS workflow_cc_recipient (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    business_code VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    user_name VARCHAR(50) NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    read_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_workflow_cc_business_user (business_code, business_id, user_id),
    KEY idx_workflow_cc_user (user_id, create_time),
    KEY idx_workflow_cc_project (project_id, business_code, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交前最终抄送快照';

CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    project_id BIGINT,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    event_code VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500),
    route_code VARCHAR(50) NOT NULL,
    route_params_json VARCHAR(1000) NOT NULL,
    is_read TINYINT NOT NULL DEFAULT 0,
    read_time DATETIME,
    dedup_key VARCHAR(160) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_notification_dedup (dedup_key),
    KEY idx_user_notification_inbox (user_id, is_read, create_time),
    KEY idx_user_notification_business (business_type, business_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知收件箱';

SET @seal_seed_required = (
    SELECT IF(COUNT(*) = 0, 1, 0) FROM sys_data_migration
    WHERE migration_key = '20260808_SEAL_APPLICATION_V1'
);

SET @document_menu_id = (SELECT id FROM sys_menu WHERE menu_code = 'WEB_DOCUMENT' AND deleted = 0 LIMIT 1);
SET @system_menu_id = (SELECT id FROM sys_menu WHERE menu_code = 'WEB_SYSTEM' AND deleted = 0 LIMIT 1);

INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
SELECT @document_menu_id, 'WEB', 'DOCUMENT_SEAL', '用印申请', 'TAB', 'SEAL_APPLICATION',
       'seal.view', 15, 1, 1, 1, 0
WHERE @seal_seed_required = 1 AND @document_menu_id IS NOT NULL
ON DUPLICATE KEY UPDATE menu_code = 'DOCUMENT_SEAL';

INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
SELECT @system_menu_id, 'WEB', 'SYSTEM_APPROVAL', '用印审批', 'MENU', 'SYSTEM_APPROVAL',
       'system.approval.view', 96, 1, 1, 1, 0
WHERE @seal_seed_required = 1 AND @system_menu_id IS NOT NULL
ON DUPLICATE KEY UPDATE menu_code = 'SYSTEM_APPROVAL';

-- 兼容已经执行过本迁移但仍使用旧菜单名称的环境；仅替换内置旧名称，不覆盖管理员自定义名称。
UPDATE sys_menu
SET menu_name = '用印审批'
WHERE menu_code = 'SYSTEM_APPROVAL' AND menu_name = '审批管理' AND builtin = 1 AND deleted = 0;

INSERT INTO sys_permission(permission_code, permission_name, module_code, description, enabled, builtin, deleted)
SELECT 'seal.view', '查看项目用印申请', 'WEB_DOCUMENT', '查看本项目全部用印申请', 1, 1, 0
WHERE @seal_seed_required = 1 ON DUPLICATE KEY UPDATE permission_code = 'seal.view';
INSERT INTO sys_permission(permission_code, permission_name, module_code, description, enabled, builtin, deleted)
SELECT 'seal.manage', '管理用印与归档', 'WEB_DOCUMENT', '补传盖章件并管理项目用印归档', 1, 1, 0
WHERE @seal_seed_required = 1 ON DUPLICATE KEY UPDATE permission_code = 'seal.manage';
INSERT INTO sys_permission(permission_code, permission_name, module_code, description, enabled, builtin, deleted)
SELECT 'seal.export', '导出用印台账', 'WEB_DOCUMENT', '按审批通过日期导出用印台账', 1, 1, 0
WHERE @seal_seed_required = 1 ON DUPLICATE KEY UPDATE permission_code = 'seal.export';
INSERT INTO sys_permission(permission_code, permission_name, module_code, description, enabled, builtin, deleted)
SELECT 'system.approval.view', '查看审批配置', 'SYSTEM_APPROVAL', '查看印章、用户审批与二维码配置', 1, 1, 0
WHERE @seal_seed_required = 1 ON DUPLICATE KEY UPDATE permission_code = 'system.approval.view';
INSERT INTO sys_permission(permission_code, permission_name, module_code, description, enabled, builtin, deleted)
SELECT 'system.approval.manage', '管理审批配置', 'SYSTEM_APPROVAL', '维护印章、直接选人审批、默认抄送、二维码与改派', 1, 1, 0
WHERE @seal_seed_required = 1 ON DUPLICATE KEY UPDATE permission_code = 'system.approval.manage';

SET @platform_admin_role_id = (
    SELECT id FROM sys_role WHERE role_code = 'PLATFORM_ADMIN' AND scope_type = 'PLATFORM'
      AND deleted = 0 ORDER BY id LIMIT 1
);
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT @platform_admin_role_id, id FROM sys_menu
WHERE @seal_seed_required = 1 AND @platform_admin_role_id IS NOT NULL
  AND menu_code IN ('DOCUMENT_SEAL', 'SYSTEM_APPROVAL') AND enabled = 1 AND deleted = 0;
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @platform_admin_role_id, id FROM sys_permission
WHERE @seal_seed_required = 1 AND @platform_admin_role_id IS NOT NULL
  AND permission_code IN ('seal.view','seal.manage','seal.export','system.approval.view','system.approval.manage')
  AND enabled = 1 AND deleted = 0;

INSERT IGNORE INTO sys_data_migration(migration_key)
SELECT '20260808_SEAL_APPLICATION_V1' WHERE @seal_seed_required = 1;

-- 回滚：先停用 DOCUMENT_SEAL/SYSTEM_APPROVAL 和五项权限；确认无在途审批、通知及归档追溯后再人工归档表数据。
-- 禁止直接 DROP 表或删除 file_resource/project_document，以免破坏用印审计与资料版本链。
