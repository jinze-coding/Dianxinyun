-- 项目级多角色与项目经理成员管理。
-- 仅在已有库上执行；可重复检查，禁止用 init.sql 代替。

CREATE TABLE IF NOT EXISTS sys_data_migration (
    migration_key VARCHAR(120) PRIMARY KEY,
    applied_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据迁移种子执行标记';

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_role' AND column_name = 'project_manager_role'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE sys_role ADD COLUMN project_manager_role TINYINT NOT NULL DEFAULT 0 COMMENT ''是否受保护的项目经理角色'' AFTER scope_type',
    'SELECT 1');
PREPARE stmt FROM @column_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sys_user_project_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_by BIGINT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_project_role (user_id, project_id, role_id),
    KEY idx_sys_user_project_role_project_user (project_id, user_id),
    KEY idx_sys_user_project_role_role (role_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目成员多角色关联';

-- 旧成员职责必须能映射到一个启用中的项目角色；无法映射时在写入授权前中止，
-- 由实施人员先补齐角色而不是静默降低成员权限。
SET @unmapped_legacy_project_role_count = (
    SELECT COUNT(*)
    FROM sys_user_project sup
    LEFT JOIN sys_role r
      ON r.role_code = sup.project_role_code
     AND r.scope_type = 'PROJECT'
     AND r.deleted = 0
    WHERE sup.project_role_code IS NOT NULL
      AND sup.project_role_code <> ''
      AND r.id IS NULL
);
SET @unmapped_legacy_project_role_guard = IF(@unmapped_legacy_project_role_count > 0,
    'SELECT * FROM __migration_abort_unmapped_project_role_code__',
    'SELECT 1');
PREPARE stmt FROM @unmapped_legacy_project_role_guard; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @project_role_seed_required = (
    SELECT IF(COUNT(*) = 0, 1, 0)
    FROM sys_data_migration
    WHERE migration_key = '20260729_PROJECT_MULTI_ROLE_MEMBER_MANAGEMENT_V1'
);

-- 项目管理员沿用既有编码，正式名称调整为项目经理；其授予和撤销只允许平台管理员。
UPDATE sys_role
SET role_name = '项目经理', project_manager_role = 1
WHERE @project_role_seed_required = 1
  AND role_code = 'PROJECT_ADMIN' AND scope_type = 'PROJECT' AND deleted = 0;

UPDATE sys_role
SET project_manager_role = 0
WHERE @project_role_seed_required = 1
  AND NOT (role_code = 'PROJECT_ADMIN' AND scope_type = 'PROJECT')
  AND project_manager_role <> 0;

-- 仅保留平台管理员的全局角色关系；普通账号的业务权限改由项目角色产生。
DELETE ur
FROM sys_user_role ur
INNER JOIN sys_role r ON r.id = ur.role_id
WHERE @project_role_seed_required = 1
  AND NOT (r.role_code = 'PLATFORM_ADMIN' AND r.scope_type = 'PLATFORM');

-- 项目成员管理进入统一权限目录。巡检细分码沿用既有业务常量，不再从模板读取。
INSERT INTO sys_permission(permission_code, permission_name, module_code, description, enabled, builtin, deleted)
SELECT seed.permission_code, seed.permission_name, seed.module_code, seed.description, 1, 1, 0
FROM (
    SELECT 'project.member.manage' permission_code, '管理项目成员' permission_name, 'SYSTEM_PROJECT' module_code,
           '加入、移出成员，调整项目角色和项目访问状态' description
    UNION ALL SELECT 'BOX_VIEW', '查看电箱台账', 'WEB_INSPECTION', '查看电箱台账、详情和二维码信息'
    UNION ALL SELECT 'BOX_MANAGE', '管理电箱台账', 'WEB_INSPECTION', '新增、编辑、停用、拆除和导入电箱'
    UNION ALL SELECT 'BOX_QR_MANAGE', '二维码与贴纸管理', 'WEB_INSPECTION', '生成、补打、换绑二维码和查看二维码日志'
    UNION ALL SELECT 'BOX_PUBLIC_ACCESS', '电箱外部访问启停', 'WEB_INSPECTION', '启用或停用单个电箱外部公开只读访问'
    UNION ALL SELECT 'INSPECTION_DAILY_SUBMIT', '提交电箱日检', 'WEB_INSPECTION', '提交当前项目日常巡检记录'
    UNION ALL SELECT 'INSPECTION_RECORD_VIEW', '查看巡检记录', 'WEB_INSPECTION', '查看项目巡检记录明细'
    UNION ALL SELECT 'SUMMARY_VIEW', '查看巡检汇总', 'WEB_INSPECTION', '查看项目或单箱月度巡检汇总'
    UNION ALL SELECT 'SUMMARY_EXPORT', '导出巡检汇总', 'WEB_INSPECTION', '导出月度巡检记录 Excel'
) seed
WHERE @project_role_seed_required = 1
ON DUPLICATE KEY UPDATE permission_code = VALUES(permission_code);

UPDATE sys_menu
SET menu_name = '项目成员与权限', permission_code = 'project.member.manage'
WHERE @project_role_seed_required = 1 AND menu_code = 'SYSTEM_PROJECT';

-- 将旧单职责关系转换为新的角色关联，保留旧字段作为历史兼容信息。
INSERT IGNORE INTO sys_user_project_role(user_id, project_id, role_id, create_time)
SELECT sup.user_id, sup.project_id, r.id, COALESCE(sup.create_time, CURRENT_TIMESTAMP)
FROM sys_user_project sup
INNER JOIN sys_role r
  ON r.role_code = sup.project_role_code
 AND r.scope_type = 'PROJECT'
 AND r.deleted = 0
WHERE @project_role_seed_required = 1;

-- 内置模板的细分巡检权限并入对应项目角色。
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
INNER JOIN inspection_permission_template t
  ON t.template_code = r.role_code AND t.builtin = 1 AND t.deleted = 0
INNER JOIN sys_permission p
  ON FIND_IN_SET(p.permission_code, t.permission_codes) > 0
WHERE @project_role_seed_required = 1
  AND r.scope_type = 'PROJECT'
  AND r.role_code IN ('PROJECT_ADMIN', 'SAFETY_ADMIN', 'USER')
  AND p.permission_code <> 'PERMISSION_MANAGE';

-- 旧模板中的成员授权能力只迁给受保护的项目经理角色。
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
INNER JOIN inspection_permission_template t
  ON t.template_code = r.role_code AND t.builtin = 1 AND t.deleted = 0
INNER JOIN sys_permission p ON p.permission_code = 'project.member.manage'
WHERE @project_role_seed_required = 1
  AND r.scope_type = 'PROJECT'
  AND r.project_manager_role = 1
  AND FIND_IN_SET('PERMISSION_MANAGE', t.permission_codes) > 0;

-- 自定义旧模板转为可追溯的迁移角色，避免历史成员权限丢失。
INSERT INTO sys_role(role_name, role_code, description, scope_type, project_manager_role, builtin, enabled, deleted)
SELECT CONCAT('[迁移] ', t.template_name), CONCAT('MIGRATED_IPT_', t.id),
       CONCAT('由历史巡检权限模板 ', t.template_code, ' 迁移'), 'PROJECT', 0, 0, t.enabled, 0
FROM inspection_permission_template t
WHERE @project_role_seed_required = 1
  AND t.builtin = 0 AND t.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role r
      WHERE r.role_code = CONCAT('MIGRATED_IPT_', t.id) AND r.scope_type = 'PROJECT' AND r.deleted = 0
  );

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
INNER JOIN inspection_permission_template t ON r.role_code = CONCAT('MIGRATED_IPT_', t.id)
INNER JOIN sys_permission p ON FIND_IN_SET(p.permission_code, t.permission_codes) > 0
WHERE @project_role_seed_required = 1
  AND t.builtin = 0 AND t.deleted = 0
  AND p.permission_code <> 'PERMISSION_MANAGE';

INSERT IGNORE INTO sys_user_project_role(user_id, project_id, role_id, create_time)
SELECT sup.user_id, sup.project_id, r.id, COALESCE(sup.create_time, CURRENT_TIMESTAMP)
FROM sys_user_project sup
INNER JOIN inspection_permission_template t ON t.id = sup.inspection_permission_template_id AND t.builtin = 0
INNER JOIN sys_role r ON r.role_code = CONCAT('MIGRATED_IPT_', t.id) AND r.scope_type = 'PROJECT' AND r.deleted = 0
WHERE @project_role_seed_required = 1;

-- 非项目经理不得保留项目成员管理权限或系统管理菜单。
DELETE rp
FROM sys_role_permission rp
INNER JOIN sys_role r ON r.id = rp.role_id
INNER JOIN sys_permission p ON p.id = rp.permission_id
WHERE @project_role_seed_required = 1
  AND r.scope_type = 'PROJECT'
  AND r.project_manager_role = 0
  AND p.permission_code IN ('project.member.manage', 'system.project.manage');

DELETE rm
FROM sys_role_menu rm
INNER JOIN sys_role r ON r.id = rm.role_id
INNER JOIN sys_menu m ON m.id = rm.menu_id
WHERE @project_role_seed_required = 1
  AND r.scope_type = 'PROJECT'
  AND r.project_manager_role = 0
  AND m.menu_code IN ('WEB_SYSTEM', 'SYSTEM_PROJECT');

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT r.id, m.id
FROM sys_role r
INNER JOIN sys_menu m ON m.menu_code IN ('WEB_SYSTEM', 'SYSTEM_PROJECT')
WHERE @project_role_seed_required = 1
  AND r.scope_type = 'PROJECT' AND r.project_manager_role = 1
  AND m.enabled = 1 AND m.deleted = 0;

INSERT IGNORE INTO sys_data_migration(migration_key)
SELECT '20260729_PROJECT_MULTI_ROLE_MEMBER_MANAGEMENT_V1'
WHERE @project_role_seed_required = 1;
