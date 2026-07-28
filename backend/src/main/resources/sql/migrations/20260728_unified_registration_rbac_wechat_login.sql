-- 统一注册、RBAC、微信绑定与 Web 扫码登录增量迁移。
-- 可重复执行；已有数据库只执行本脚本，禁止用 init.sql 代替。

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'credential_version'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN credential_version INT NOT NULL DEFAULT 1 COMMENT ''凭证版本，改密时递增''',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'password_reset_required'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN password_reset_required TINYINT NOT NULL DEFAULT 0 COMMENT ''是否必须由管理员重置密码''',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_user
SET password_reset_required = 1
WHERE password NOT REGEXP '^\\$2[aby]\\$[0-9]{2}\\$.{53}$'
   OR password = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH';

-- 注册流程把手机号作为账号占用键；已有重复手机号会让本迁移失败，需先人工核对。
SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'active_phone'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE sys_user ADD COLUMN active_phone VARCHAR(20) GENERATED ALWAYS AS (CASE WHEN deleted = 0 AND phone IS NOT NULL AND phone <> '''' THEN phone ELSE NULL END) STORED',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @user_phone_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND index_name = 'uk_sys_user_active_phone'
);
SET @user_phone_index_sql = IF(@user_phone_index_exists = 0,
    'ALTER TABLE sys_user ADD UNIQUE KEY uk_sys_user_active_phone (active_phone)',
    'SELECT 1');
PREPARE stmt FROM @user_phone_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_role'
      AND column_name = 'scope_type'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN scope_type VARCHAR(20) NOT NULL DEFAULT ''PLATFORM'' COMMENT ''PLATFORM/PROJECT''',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_role'
      AND column_name = 'builtin'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN builtin TINYINT NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_role'
      AND column_name = 'enabled'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN enabled TINYINT NOT NULL DEFAULT 1',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 授权种子只初始化一次。后续重复检查本迁移时不得恢复管理员主动撤销的授权或启用状态。
CREATE TABLE IF NOT EXISTS sys_data_migration (
    migration_key VARCHAR(120) PRIMARY KEY,
    applied_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据迁移种子执行标记';
SET @rbac_seed_required = (
    SELECT IF(COUNT(*) = 0, 1, 0)
    FROM sys_data_migration
    WHERE migration_key = '20260728_UNIFIED_REGISTRATION_RBAC_SEED_V1'
);

UPDATE sys_role
SET builtin = 1, enabled = 1
WHERE @rbac_seed_required = 1
  AND role_code IN ('PLATFORM_ADMIN', 'PROJECT_ADMIN', 'SAFETY_ADMIN', 'USER');

-- 项目职责与平台角色分开保存；role_code 保持旧项目职责编码，兼容 sys_user_project。
INSERT INTO sys_role(role_name, role_code, description, scope_type, builtin, enabled, deleted)
SELECT '项目管理员', 'PROJECT_ADMIN', '项目级管理员职责', 'PROJECT', 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_code = 'PROJECT_ADMIN' AND scope_type = 'PROJECT' AND deleted = 0
);
INSERT INTO sys_role(role_name, role_code, description, scope_type, builtin, enabled, deleted)
SELECT '巡检记录管理员', 'SAFETY_ADMIN', '项目级巡检记录管理职责', 'PROJECT', 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_code = 'SAFETY_ADMIN' AND scope_type = 'PROJECT' AND deleted = 0
);
INSERT INTO sys_role(role_name, role_code, description, scope_type, builtin, enabled, deleted)
SELECT '项目成员', 'USER', '项目级普通成员职责', 'PROJECT', 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role WHERE role_code = 'USER' AND scope_type = 'PROJECT' AND deleted = 0
);

CREATE TABLE IF NOT EXISTS registration_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(200),
    real_name VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100),
    application_reason VARCHAR(500),
    desired_project_ids JSON,
    desired_project_text VARCHAR(200),
    source_type VARCHAR(20) NOT NULL COMMENT 'WEB/MINI',
    phone_verification_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL_REVIEW',
    app_id VARCHAR(80),
    openid VARCHAR(128),
    unionid VARCHAR(128),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    status_token_hash CHAR(64) NOT NULL,
    created_user_id BIGINT,
    reviewer_id BIGINT,
    reviewer_name VARCHAR(50),
    review_comment VARCHAR(500),
    review_time DATETIME,
    pending_username VARCHAR(50) GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' THEN username ELSE NULL END) STORED,
    pending_phone VARCHAR(20) GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' THEN phone ELSE NULL END) STORED,
    pending_wechat_key VARCHAR(220) GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' AND app_id IS NOT NULL AND openid IS NOT NULL
              THEN CONCAT(app_id, ':', openid) ELSE NULL END) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_registration_status_token (status_token_hash),
    UNIQUE KEY uk_registration_pending_username (pending_username),
    UNIQUE KEY uk_registration_pending_phone (pending_phone),
    UNIQUE KEY uk_registration_pending_wechat (pending_wechat_key),
    KEY idx_registration_status_time (status, create_time),
    KEY idx_registration_wechat (app_id, openid, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一账号注册申请';

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'registration_application'
      AND column_name = 'desired_project_text'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE registration_application ADD COLUMN desired_project_text VARCHAR(200) AFTER desired_project_ids',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'registration_application'
      AND column_name = 'pending_wechat_key'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE registration_application ADD COLUMN pending_wechat_key VARCHAR(220) GENERATED ALWAYS AS (CASE WHEN status = ''PENDING'' AND app_id IS NOT NULL AND openid IS NOT NULL THEN CONCAT(app_id, '':'', openid) ELSE NULL END) STORED',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @registration_wechat_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'registration_application'
      AND index_name = 'uk_registration_pending_wechat'
);
SET @registration_wechat_index_sql = IF(@registration_wechat_index_exists = 0,
    'ALTER TABLE registration_application ADD UNIQUE KEY uk_registration_pending_wechat (pending_wechat_key)',
    'SELECT 1');
PREPARE stmt FROM @registration_wechat_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 已处理申请绝不能长期保留凭证摘要；也修复早期版本因 ORM 忽略 null 而残留的摘要。
UPDATE registration_application
SET password_hash = NULL
WHERE status IN ('APPROVED', 'REJECTED', 'CANCELLED')
  AND password_hash IS NOT NULL;

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT,
    client_type VARCHAR(20) NOT NULL DEFAULT 'COMMON',
    menu_code VARCHAR(80) NOT NULL,
    menu_name VARCHAR(80) NOT NULL,
    resource_type VARCHAR(20) NOT NULL DEFAULT 'MENU',
    route_path VARCHAR(200),
    permission_code VARCHAR(100),
    sort_order INT NOT NULL DEFAULT 0,
    visible TINYINT NOT NULL DEFAULT 1,
    enabled TINYINT NOT NULL DEFAULT 1,
    builtin TINYINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_menu_code (menu_code),
    KEY idx_sys_menu_parent (parent_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨端菜单与功能资源';

CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    module_code VARCHAR(80) NOT NULL,
    description VARCHAR(300),
    enabled TINYINT NOT NULL DEFAULT 1,
    builtin TINYINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_permission_code (permission_code),
    KEY idx_sys_permission_module (module_code, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口与操作权限';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_role_menu (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联';

CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色操作权限关联';

INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
VALUES
(NULL, 'WEB', 'WEB_DOCUMENT', '资料管理', 'MENU', 'DOCUMENT_MANAGEMENT', 'document.view', 10, 1, 1, 1, 0),
(NULL, 'WEB', 'WEB_INSPECTION', '巡检管理', 'MENU', 'ELECTRIC_INSPECTION', 'inspection.view', 20, 1, 1, 1, 0),
(NULL, 'WEB', 'WEB_QUALITY', '质量管理', 'MENU', 'QUALITY_MANAGEMENT', 'quality.view', 30, 1, 1, 1, 0),
(NULL, 'WEB', 'WEB_SYSTEM', '系统管理', 'DIRECTORY', 'SYSTEM_MANAGEMENT', 'system.user.view', 90, 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE menu_code = VALUES(menu_code);

INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
VALUES
(NULL, 'MINI_PROGRAM', 'MINI_DOCUMENT', '资料管理', 'MENU', '/pages/documents/index', 'document.view', 10, 1, 1, 1, 0),
(NULL, 'MINI_PROGRAM', 'MINI_INSPECTION', '巡检管理', 'MENU', '/pages/inspection/index', 'inspection.view', 20, 1, 1, 1, 0),
(NULL, 'MINI_PROGRAM', 'MINI_QUALITY', '质量管理', 'MENU', '/pages/quality/index', 'quality.view', 30, 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE menu_code = VALUES(menu_code);

SET @system_menu_id = (SELECT id FROM sys_menu WHERE menu_code = 'WEB_SYSTEM' LIMIT 1);
INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
VALUES
(@system_menu_id, 'WEB', 'SYSTEM_REGISTRATION', '注册审核', 'MENU', 'SYSTEM_REGISTRATION', 'system.registration.review', 91, 1, 1, 1, 0),
(@system_menu_id, 'WEB', 'SYSTEM_USER', '用户管理', 'MENU', 'SYSTEM_USER', 'system.user.view', 92, 1, 1, 1, 0),
(@system_menu_id, 'WEB', 'SYSTEM_ROLE', '角色与权限', 'MENU', 'SYSTEM_ROLE', 'system.role.manage', 93, 1, 1, 1, 0),
(@system_menu_id, 'WEB', 'SYSTEM_MENU', '菜单与功能', 'MENU', 'SYSTEM_MENU', 'system.menu.manage', 94, 1, 1, 1, 0),
(@system_menu_id, 'WEB', 'SYSTEM_PROJECT', '项目授权', 'MENU', 'SYSTEM_PROJECT', 'system.project.manage', 95, 1, 1, 1, 0),
(@system_menu_id, 'WEB', 'SYSTEM_WECHAT', '微信绑定', 'MENU', 'SYSTEM_WECHAT', 'system.wechat.manage', 96, 1, 1, 1, 0),
(@system_menu_id, 'WEB', 'SYSTEM_AUDIT', '操作日志', 'MENU', 'SYSTEM_AUDIT', 'system.audit.view', 97, 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE menu_code = VALUES(menu_code);

INSERT INTO sys_permission(permission_code, permission_name, module_code, description, enabled, builtin, deleted)
VALUES
('system.registration.review', '审核注册申请', 'SYSTEM_REGISTRATION', NULL, 1, 1, 0),
('system.user.view', '查看用户', 'SYSTEM_USER', NULL, 1, 1, 0),
('system.user.manage', '分配用户角色', 'SYSTEM_USER', NULL, 1, 1, 0),
('system.user.status', '启停用户', 'SYSTEM_USER', NULL, 1, 1, 0),
('system.user.reset_password', '重置用户密码', 'SYSTEM_USER', NULL, 1, 1, 0),
('system.role.manage', '管理角色权限', 'SYSTEM_ROLE', NULL, 1, 1, 0),
('system.menu.manage', '管理菜单功能', 'SYSTEM_MENU', NULL, 1, 1, 0),
('system.project.manage', '管理项目授权', 'SYSTEM_PROJECT', NULL, 1, 1, 0),
('system.wechat.manage', '管理微信绑定', 'SYSTEM_WECHAT', NULL, 1, 1, 0),
('system.audit.view', '查看操作日志', 'SYSTEM_AUDIT', NULL, 1, 1, 0),
('document.view', '查看资料', 'WEB_DOCUMENT', NULL, 1, 1, 0),
('document.upload', '上传资料', 'WEB_DOCUMENT', NULL, 1, 1, 0),
('document.manage', '管理资料', 'WEB_DOCUMENT', NULL, 1, 1, 0),
('inspection.view', '查看巡检', 'WEB_INSPECTION', NULL, 1, 1, 0),
('inspection.submit', '提交巡检', 'WEB_INSPECTION', NULL, 1, 1, 0),
('inspection.manage', '管理巡检', 'WEB_INSPECTION', NULL, 1, 1, 0),
('inspection.export', '导出巡检', 'WEB_INSPECTION', NULL, 1, 1, 0),
('quality.view', '查看质量问题', 'WEB_QUALITY', NULL, 1, 1, 0),
('quality.manage', '发起和管理质量问题', 'WEB_QUALITY', NULL, 1, 1, 0),
('quality.rectify', '提交质量整改', 'WEB_QUALITY', NULL, 1, 1, 0),
('quality.review', '复查质量问题', 'WEB_QUALITY', NULL, 1, 1, 0)
ON DUPLICATE KEY UPDATE permission_code = VALUES(permission_code);

SET @platform_admin_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'PLATFORM_ADMIN' AND scope_type = 'PLATFORM' AND deleted = 0
    ORDER BY id LIMIT 1
);
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT @platform_admin_role_id, id FROM sys_menu
WHERE @rbac_seed_required = 1 AND enabled = 1 AND deleted = 0;
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @platform_admin_role_id, id FROM sys_permission
WHERE @rbac_seed_required = 1 AND enabled = 1 AND deleted = 0;

-- 普通平台用户默认只获得三个正式业务入口及查看权限；写操作继续由管理员分配。
SET @platform_user_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'USER' AND scope_type = 'PLATFORM' AND deleted = 0
    ORDER BY id LIMIT 1
);
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT @platform_user_role_id, id
FROM sys_menu
WHERE @rbac_seed_required = 1
  AND menu_code IN ('WEB_DOCUMENT', 'WEB_INSPECTION', 'WEB_QUALITY',
                    'MINI_DOCUMENT', 'MINI_INSPECTION', 'MINI_QUALITY')
  AND enabled = 1 AND deleted = 0;
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @platform_user_role_id, id
FROM sys_permission
WHERE @rbac_seed_required = 1
  AND permission_code IN ('document.view', 'inspection.view', 'quality.view')
  AND enabled = 1 AND deleted = 0;

-- 项目角色通过有效 sys_user_project 关系生效；接口仍继续校验具体项目范围。
SET @project_admin_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'PROJECT_ADMIN' AND scope_type = 'PROJECT' AND deleted = 0
    ORDER BY id LIMIT 1
);
SET @safety_admin_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'SAFETY_ADMIN' AND scope_type = 'PROJECT' AND deleted = 0
    ORDER BY id LIMIT 1
);
SET @project_user_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'USER' AND scope_type = 'PROJECT' AND deleted = 0
    ORDER BY id LIMIT 1
);

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT @project_admin_role_id, id
FROM sys_menu
WHERE @rbac_seed_required = 1
  AND menu_code IN ('WEB_DOCUMENT', 'WEB_INSPECTION', 'WEB_QUALITY',
                    'MINI_DOCUMENT', 'MINI_INSPECTION', 'MINI_QUALITY',
                    'WEB_SYSTEM', 'SYSTEM_PROJECT')
  AND enabled = 1 AND deleted = 0;
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @project_admin_role_id, id
FROM sys_permission
WHERE @rbac_seed_required = 1
  AND permission_code IN (
    'document.view', 'document.upload', 'document.manage',
    'inspection.view', 'inspection.submit', 'inspection.manage', 'inspection.export',
    'quality.view', 'quality.manage', 'quality.rectify', 'quality.review',
    'system.project.manage'
)
  AND enabled = 1 AND deleted = 0;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT @safety_admin_role_id, id
FROM sys_menu
WHERE @rbac_seed_required = 1
  AND menu_code IN ('WEB_DOCUMENT', 'WEB_INSPECTION', 'WEB_QUALITY',
                    'MINI_DOCUMENT', 'MINI_INSPECTION', 'MINI_QUALITY')
  AND enabled = 1 AND deleted = 0;
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @safety_admin_role_id, id
FROM sys_permission
WHERE @rbac_seed_required = 1
  AND permission_code IN (
    'document.view', 'document.upload',
    'inspection.view', 'inspection.submit', 'inspection.manage', 'inspection.export',
    'quality.view', 'quality.manage', 'quality.rectify', 'quality.review'
)
  AND enabled = 1 AND deleted = 0;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT @project_user_role_id, id
FROM sys_menu
WHERE @rbac_seed_required = 1
  AND menu_code IN ('WEB_DOCUMENT', 'WEB_INSPECTION', 'WEB_QUALITY',
                    'MINI_DOCUMENT', 'MINI_INSPECTION', 'MINI_QUALITY')
  AND enabled = 1 AND deleted = 0;
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @project_user_role_id, id
FROM sys_permission
WHERE @rbac_seed_required = 1
  AND permission_code IN (
    'document.view', 'document.upload',
    'inspection.view', 'inspection.submit',
    'quality.view', 'quality.manage', 'quality.rectify'
)
  AND enabled = 1 AND deleted = 0;

INSERT IGNORE INTO sys_data_migration(migration_key)
VALUES ('20260728_UNIFIED_REGISTRATION_RBAC_SEED_V1');

-- “已有用户申请项目访问”同一微信、同一项目只能存在一条待审批记录。
-- 若历史并发已产生重复申请，保留最新一条待审批，其余转为已取消后再建立唯一键。
UPDATE wechat_access_application older
JOIN wechat_access_application newer
  ON newer.app_id = older.app_id
 AND newer.openid = older.openid
 AND newer.project_id = older.project_id
 AND newer.status = 'PENDING'
 AND newer.id > older.id
SET older.status = 'CANCELLED',
    older.review_comment = COALESCE(older.review_comment, '迁移清理重复待审批项目访问申请'),
    older.update_time = CURRENT_TIMESTAMP
WHERE older.status = 'PENDING';

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'wechat_access_application'
      AND column_name = 'pending_access_key'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE wechat_access_application ADD COLUMN pending_access_key VARCHAR(240) GENERATED ALWAYS AS (CASE WHEN status = ''PENDING'' THEN CONCAT(app_id, '':'', openid, '':'', project_id) ELSE NULL END) STORED',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @pending_access_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'wechat_access_application'
      AND index_name = 'uk_wechat_access_pending'
);
SET @pending_access_index_sql = IF(@pending_access_index_exists = 0,
    'ALTER TABLE wechat_access_application ADD UNIQUE KEY uk_wechat_access_pending (pending_access_key)',
    'SELECT 1');
PREPARE stmt FROM @pending_access_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 同一 AppID 下有效 OpenID、UnionID 和系统用户均保持一对一。
SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user_wechat_binding'
      AND column_name = 'active_unionid'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE sys_user_wechat_binding ADD COLUMN active_unionid VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN status = ''ACTIVE'' AND deleted = 0 AND unionid IS NOT NULL AND unionid <> '''' THEN unionid ELSE NULL END) STORED',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user_wechat_binding'
      AND index_name = 'uk_wechat_binding_active_unionid'
);
SET @index_sql = IF(@index_exists = 0,
    'ALTER TABLE sys_user_wechat_binding ADD UNIQUE KEY uk_wechat_binding_active_unionid (app_id, active_unionid)',
    'SELECT 1');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @role_scope_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_role'
      AND index_name = 'uk_sys_role_code_scope'
);
SET @role_scope_index_sql = IF(@role_scope_index_exists = 0,
    'ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_code_scope (role_code, scope_type, deleted)',
    'SELECT 1');
PREPARE stmt FROM @role_scope_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @user_role_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user_role'
      AND index_name = 'uk_sys_user_role'
);
SET @user_role_index_sql = IF(@user_role_index_exists = 0,
    'ALTER TABLE sys_user_role ADD UNIQUE KEY uk_sys_user_role (user_id, role_id)',
    'SELECT 1');
PREPARE stmt FROM @user_role_index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
