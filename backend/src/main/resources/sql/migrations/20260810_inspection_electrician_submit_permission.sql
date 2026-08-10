-- 修复内置项目电工角色能整改但不能发起电箱日检的权限缺口。
-- 不修改表结构、用户或业务记录；脚本可重复执行并会修复被误删的两条角色授权。

SET @electrician_role_count = (
    SELECT COUNT(*) FROM sys_role
    WHERE role_code = 'ELECTRICIAN' AND scope_type = 'PROJECT' AND deleted = 0
);
SET @electrician_role_guard_sql = IF(
    @electrician_role_count <> 1,
    'SELECT * FROM __migration_abort_missing_or_ambiguous_electrician_role__',
    'SELECT 1'
);
PREPARE stmt FROM @electrician_role_guard_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO sys_permission(permission_code, permission_name, module_code, description,
                           enabled, builtin, deleted)
SELECT seed.permission_code, seed.permission_name, 'WEB_INSPECTION', seed.description, 1, 1, 0
FROM (
    SELECT 'inspection.submit' permission_code, '提交巡检' permission_name,
           '通过受保护接口提交电箱日检' description
    UNION ALL
    SELECT 'INSPECTION_DAILY_SUBMIT', '提交电箱日检',
           '电箱日检页面和业务范围权限' description
) seed
ON DUPLICATE KEY UPDATE
permission_name = VALUES(permission_name), module_code = VALUES(module_code),
description = VALUES(description), enabled = 1, builtin = 1, deleted = 0;

SET @electrician_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'ELECTRICIAN' AND scope_type = 'PROJECT' AND deleted = 0
    LIMIT 1
);

INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @electrician_role_id, id
FROM sys_permission
WHERE permission_code IN ('inspection.submit', 'INSPECTION_DAILY_SUBMIT')
  AND enabled = 1 AND deleted = 0;

UPDATE sys_role
SET builtin = 1,
    enabled = 1,
    description = '负责电箱日检及分配给自己的巡检异常整改'
WHERE id = @electrician_role_id;

INSERT IGNORE INTO sys_data_migration(migration_key)
VALUES ('20260810_INSPECTION_ELECTRICIAN_SUBMIT_PERMISSION_V1');

-- 回滚（仅在确认电工不再承担日检后执行）：
-- DELETE rp FROM sys_role_permission rp
-- JOIN sys_role r ON r.id = rp.role_id
-- JOIN sys_permission p ON p.id = rp.permission_id
-- WHERE r.role_code = 'ELECTRICIAN' AND r.scope_type = 'PROJECT'
--   AND p.permission_code IN ('inspection.submit', 'INSPECTION_DAILY_SUBMIT');
-- DELETE FROM sys_data_migration
-- WHERE migration_key = '20260810_INSPECTION_ELECTRICIAN_SUBMIT_PERMISSION_V1';
