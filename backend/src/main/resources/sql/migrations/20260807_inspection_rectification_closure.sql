-- 巡检异常整改闭环角色、权限及菜单数据迁移。
-- 不新增业务表；复用 inspection_rectification 与既有整改日志表。
-- 长期数据库执行前必须先完整备份，禁止直接用 init.sql 覆盖。

SET @inspection_rectification_seed_required = (
    SELECT IF(COUNT(*) = 0, 1, 0)
    FROM sys_data_migration
    WHERE migration_key = '20260807_INSPECTION_RECTIFICATION_CLOSURE_V1'
);

-- 只允许将唯一的现有同名项目角色规范为稳定业务编码，避免误合并管理员自建角色。
SET @electrician_stable_count = (
    SELECT COUNT(*) FROM sys_role
    WHERE role_code = 'ELECTRICIAN' AND scope_type = 'PROJECT' AND deleted = 0
);
SET @electrician_named_count = (
    SELECT COUNT(*) FROM sys_role
    WHERE role_name = '电工' AND scope_type = 'PROJECT' AND deleted = 0
      AND role_code <> 'ELECTRICIAN'
);
SET @electrician_role_guard_sql = IF(
    @inspection_rectification_seed_required = 1
      AND (@electrician_stable_count > 1 OR @electrician_named_count > 1
           OR (@electrician_stable_count = 1 AND @electrician_named_count > 0)),
    'SELECT * FROM __migration_abort_ambiguous_electrician_role__',
    'SELECT 1'
);
PREPARE stmt FROM @electrician_role_guard_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @safety_stable_count = (
    SELECT COUNT(*) FROM sys_role
    WHERE role_code = 'SAFETY_OFFICER' AND scope_type = 'PROJECT' AND deleted = 0
);
SET @safety_named_count = (
    SELECT COUNT(*) FROM sys_role
    WHERE role_name = '安全员' AND scope_type = 'PROJECT' AND deleted = 0
      AND role_code <> 'SAFETY_OFFICER'
);
SET @safety_role_guard_sql = IF(
    @inspection_rectification_seed_required = 1
      AND (@safety_stable_count > 1 OR @safety_named_count > 1
           OR (@safety_stable_count = 1 AND @safety_named_count > 0)),
    'SELECT * FROM __migration_abort_ambiguous_safety_officer_role__',
    'SELECT 1'
);
PREPARE stmt FROM @safety_role_guard_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE sys_role
SET role_code = 'ELECTRICIAN', builtin = 1, enabled = 1,
    description = '负责处理分配给自己的巡检异常整改'
WHERE @inspection_rectification_seed_required = 1
  AND @electrician_stable_count = 0
  AND @electrician_named_count = 1
  AND role_name = '电工' AND scope_type = 'PROJECT' AND deleted = 0;

INSERT INTO sys_role(role_name, role_code, description, scope_type, project_manager_role,
                     builtin, enabled, deleted)
SELECT '电工', 'ELECTRICIAN', '负责处理分配给自己的巡检异常整改', 'PROJECT', 0, 1, 1, 0
WHERE @inspection_rectification_seed_required = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_role
      WHERE role_code = 'ELECTRICIAN' AND scope_type = 'PROJECT' AND deleted = 0
  );

UPDATE sys_role
SET builtin = 1, enabled = 1, description = '负责处理分配给自己的巡检异常整改'
WHERE @inspection_rectification_seed_required = 1
  AND role_code = 'ELECTRICIAN' AND scope_type = 'PROJECT' AND deleted = 0;

UPDATE sys_role
SET role_code = 'SAFETY_OFFICER', builtin = 1, enabled = 1,
    description = '负责巡检异常整改复查、退回和改派'
WHERE @inspection_rectification_seed_required = 1
  AND @safety_stable_count = 0
  AND @safety_named_count = 1
  AND role_name = '安全员' AND scope_type = 'PROJECT' AND deleted = 0;

INSERT INTO sys_role(role_name, role_code, description, scope_type, project_manager_role,
                     builtin, enabled, deleted)
SELECT '安全员', 'SAFETY_OFFICER', '负责巡检异常整改复查、退回和改派', 'PROJECT', 0, 1, 1, 0
WHERE @inspection_rectification_seed_required = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_role
      WHERE role_code = 'SAFETY_OFFICER' AND scope_type = 'PROJECT' AND deleted = 0
  );

UPDATE sys_role
SET builtin = 1, enabled = 1, description = '负责巡检异常整改复查、退回和改派'
WHERE @inspection_rectification_seed_required = 1
  AND role_code = 'SAFETY_OFFICER' AND scope_type = 'PROJECT' AND deleted = 0;

INSERT INTO sys_permission(permission_code, permission_name, module_code, description,
                           enabled, builtin, deleted)
SELECT seed.permission_code, seed.permission_name, seed.module_code, seed.description, 1, 1, 0
FROM (
    SELECT 'inspection.rectify' permission_code, '提交巡检整改' permission_name,
           'WEB_INSPECTION' module_code, '查看并提交分配给自己的巡检异常整改' description
    UNION ALL
    SELECT 'inspection.review', '复查巡检整改', 'WEB_INSPECTION',
           '查看项目整改并执行复查、退回和改派'
) seed
WHERE @inspection_rectification_seed_required = 1
ON DUPLICATE KEY UPDATE
permission_name = VALUES(permission_name), module_code = VALUES(module_code),
description = VALUES(description), enabled = 1, builtin = 1, deleted = 0;

SET @inspection_menu_id = (
    SELECT id FROM sys_menu WHERE menu_code = 'WEB_INSPECTION' AND deleted = 0 LIMIT 1
);
SET @inspection_menu_guard_sql = IF(
    @inspection_rectification_seed_required = 1 AND @inspection_menu_id IS NULL,
    'SELECT * FROM __migration_abort_missing_web_inspection_menu__',
    'SELECT 1'
);
PREPARE stmt FROM @inspection_menu_guard_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
SELECT @inspection_menu_id, 'WEB', 'INSPECTION_RECTIFICATIONS', '整改闭环', 'TAB',
       'INSPECTION_RECTIFICATIONS', 'inspection.view', 23, 1, 1, 1, 0
WHERE @inspection_rectification_seed_required = 1
ON DUPLICATE KEY UPDATE
parent_id = VALUES(parent_id), menu_name = VALUES(menu_name), route_path = VALUES(route_path),
permission_code = VALUES(permission_code), sort_order = VALUES(sort_order),
visible = 1, enabled = 1, builtin = 1, deleted = 0;

SET @electrician_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'ELECTRICIAN' AND scope_type = 'PROJECT' AND deleted = 0 LIMIT 1
);
SET @safety_officer_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'SAFETY_OFFICER' AND scope_type = 'PROJECT' AND deleted = 0 LIMIT 1
);
SET @platform_admin_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'PLATFORM_ADMIN' AND scope_type = 'PLATFORM' AND deleted = 0 LIMIT 1
);

INSERT IGNORE INTO sys_role_business_module(role_id, module_code)
SELECT roles.role_id, 'INSPECTION'
FROM (
    SELECT @electrician_role_id AS role_id
    UNION ALL SELECT @safety_officer_role_id
) roles
WHERE @inspection_rectification_seed_required = 1 AND roles.role_id IS NOT NULL;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT roles.role_id, menus.id
FROM (
    SELECT @electrician_role_id AS role_id
    UNION ALL SELECT @safety_officer_role_id
) roles
INNER JOIN sys_menu menus
  ON menus.menu_code IN ('WEB_INSPECTION', 'MINI_INSPECTION', 'INSPECTION_RECTIFICATIONS')
 AND menus.deleted = 0
WHERE @inspection_rectification_seed_required = 1 AND roles.role_id IS NOT NULL;

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @electrician_role_id, p.id
FROM sys_permission p
WHERE @inspection_rectification_seed_required = 1
  AND @electrician_role_id IS NOT NULL
  AND p.permission_code IN ('inspection.view', 'inspection.rectify')
  AND p.enabled = 1 AND p.deleted = 0;

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @safety_officer_role_id, p.id
FROM sys_permission p
WHERE @inspection_rectification_seed_required = 1
  AND @safety_officer_role_id IS NOT NULL
  AND p.permission_code IN ('inspection.view', 'inspection.review', 'INSPECTION_RECORD_VIEW')
  AND p.enabled = 1 AND p.deleted = 0;

-- 平台管理员保留应急管理权限和整改页入口。
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT @platform_admin_role_id, id
FROM sys_menu
WHERE @inspection_rectification_seed_required = 1
  AND @platform_admin_role_id IS NOT NULL
  AND menu_code = 'INSPECTION_RECTIFICATIONS' AND deleted = 0;

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @platform_admin_role_id, id
FROM sys_permission
WHERE @inspection_rectification_seed_required = 1
  AND @platform_admin_role_id IS NOT NULL
  AND permission_code IN ('inspection.rectify', 'inspection.review')
  AND enabled = 1 AND deleted = 0;

INSERT IGNORE INTO sys_data_migration(migration_key)
SELECT '20260807_INSPECTION_RECTIFICATION_CLOSURE_V1'
WHERE @inspection_rectification_seed_required = 1;

-- 回滚仅删除本迁移新增授权和菜单，不自动恢复随机角色编码；执行回滚前需确认无业务数据引用。
