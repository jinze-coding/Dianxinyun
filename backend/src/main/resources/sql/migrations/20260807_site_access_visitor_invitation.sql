-- 场内管理 V1：单次外访邀请、逐人登记、加密审计及正式 RBAC 目录。
-- 非破坏增量迁移；执行前必须备份数据库，禁止用 init.sql 代替。
-- 敏感字段只保存 AES-GCM 密文，密钥必须通过 VISITOR_DATA_ENCRYPTION_KEY 注入。

CREATE TABLE IF NOT EXISTS site_visit_invitation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    invite_no VARCHAR(40) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_encrypted VARCHAR(512) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUBMITTED/VOIDED，EXPIRED由离场时间派生',
    visit_start_time DATETIME NOT NULL,
    visit_end_time DATETIME NOT NULL,
    purpose VARCHAR(300) NOT NULL,
    visit_location VARCHAR(200) NOT NULL,
    host_user_id BIGINT NOT NULL,
    host_name VARCHAR(50) NOT NULL,
    host_phone_encrypted VARCHAR(512),
    internal_remark VARCHAR(500),
    visitor_company VARCHAR(200),
    contact_name VARCHAR(50),
    contact_phone_encrypted VARCHAR(512),
    visitor_count INT NOT NULL DEFAULT 0,
    travel_mode VARCHAR(20),
    vehicle_plate VARCHAR(20),
    visitor_remark VARCHAR(500),
    privacy_agreed_time DATETIME,
    submitted_time DATETIME,
    void_reason VARCHAR(300),
    voided_by_id BIGINT,
    voided_by_name VARCHAR(50),
    voided_time DATETIME,
    created_by_id BIGINT NOT NULL,
    created_by_name VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_visit_invite_no (invite_no),
    UNIQUE KEY uk_site_visit_token_hash (token_hash),
    KEY idx_site_visit_project_time (project_id, visit_start_time, deleted),
    KEY idx_site_visit_project_status (project_id, status, visit_end_time, deleted),
    KEY idx_site_visit_host (project_id, host_user_id, visit_start_time),
    KEY idx_site_visit_vehicle (project_id, vehicle_plate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场内管理单次外访邀请';

CREATE TABLE IF NOT EXISTS site_visit_person (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invitation_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    person_type VARCHAR(20) NOT NULL COMMENT 'CONTACT/COMPANION',
    person_name VARCHAR(50) NOT NULL,
    id_card_encrypted VARCHAR(512) NOT NULL,
    id_card_hash CHAR(64) NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_site_visit_person_invitation (invitation_id, deleted, sort_order),
    KEY idx_site_visit_person_project (project_id, id_card_hash, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外访邀请逐人实名明细';

CREATE TABLE IF NOT EXISTS site_visit_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invitation_id BIGINT,
    project_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL COMMENT 'CREATE/SUBMIT/UPDATE/VOID/EXPORT',
    operator_id BIGINT,
    operator_name VARCHAR(50) NOT NULL,
    before_snapshot_encrypted LONGTEXT,
    after_snapshot_encrypted LONGTEXT,
    comment VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_site_visit_audit_invitation (invitation_id, create_time),
    KEY idx_site_visit_audit_project (project_id, action_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场内管理外访加密审计';

SET @site_access_seed_required = (
    SELECT IF(COUNT(*) = 0, 1, 0)
    FROM sys_data_migration
    WHERE migration_key = '20260807_SITE_ACCESS_VISITOR_INVITATION_V1'
);

INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
SELECT NULL, 'WEB', 'WEB_SITE_ACCESS', '场内管理', 'MENU', 'SITE_ACCESS',
       'site_access.view', 5, 1, 1, 1, 0
WHERE @site_access_seed_required = 1
ON DUPLICATE KEY UPDATE menu_code = VALUES(menu_code);

SET @site_access_menu_id = (
    SELECT id FROM sys_menu WHERE menu_code = 'WEB_SITE_ACCESS' AND deleted = 0 LIMIT 1
);

INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
SELECT @site_access_menu_id, 'WEB', 'SITE_VISITOR', '外访管理', 'TAB', 'SITE_VISITOR',
       'site_access.view', 6, 1, 1, 1, 0
WHERE @site_access_seed_required = 1
ON DUPLICATE KEY UPDATE menu_code = VALUES(menu_code);

INSERT INTO sys_permission(permission_code, permission_name, module_code, description,
                           enabled, builtin, deleted)
SELECT 'site_access.view', '查看外访记录', 'WEB_SITE_ACCESS', '查看本项目完整外访信息', 1, 1, 0
WHERE @site_access_seed_required = 1
ON DUPLICATE KEY UPDATE permission_code = VALUES(permission_code);
INSERT INTO sys_permission(permission_code, permission_name, module_code, description,
                           enabled, builtin, deleted)
SELECT 'site_access.manage', '管理外访邀请', 'WEB_SITE_ACCESS', '创建、修改、作废邀请及生成小程序码', 1, 1, 0
WHERE @site_access_seed_required = 1
ON DUPLICATE KEY UPDATE permission_code = VALUES(permission_code);
INSERT INTO sys_permission(permission_code, permission_name, module_code, description,
                           enabled, builtin, deleted)
SELECT 'site_access.export', '导出外访人员', 'WEB_SITE_ACCESS', '导出完整手机号和身份证号', 1, 1, 0
WHERE @site_access_seed_required = 1
ON DUPLICATE KEY UPDATE permission_code = VALUES(permission_code);

SET @platform_admin_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'PLATFORM_ADMIN' AND scope_type = 'PLATFORM' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT IGNORE INTO sys_role_business_module(role_id, module_code)
SELECT @platform_admin_role_id, 'SITE_ACCESS'
WHERE @site_access_seed_required = 1 AND @platform_admin_role_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT @platform_admin_role_id, id
FROM sys_menu
WHERE @site_access_seed_required = 1
  AND @platform_admin_role_id IS NOT NULL
  AND menu_code IN ('WEB_SITE_ACCESS', 'SITE_VISITOR')
  AND enabled = 1 AND deleted = 0;

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @platform_admin_role_id, id
FROM sys_permission
WHERE @site_access_seed_required = 1
  AND @platform_admin_role_id IS NOT NULL
  AND permission_code IN ('site_access.view', 'site_access.manage', 'site_access.export')
  AND enabled = 1 AND deleted = 0;

INSERT IGNORE INTO sys_data_migration(migration_key)
SELECT '20260807_SITE_ACCESS_VISITOR_INVITATION_V1'
WHERE @site_access_seed_required = 1;

-- 回滚仅限应用已回退且确认没有需保留的外访数据后人工执行：
-- 1. 删除对应角色菜单、权限和模块关联；2. 软停用 WEB_SITE_ACCESS/SITE_VISITOR 及三项权限；
-- 3. 三张业务表含长期保存的身份证密文，禁止在未导出归档前直接 DROP。
