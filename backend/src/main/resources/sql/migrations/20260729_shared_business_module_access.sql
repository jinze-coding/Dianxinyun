-- 跨端业务模块统一开关：资料、巡检、质量。
-- 仅在既有库上增量执行；禁止使用 init.sql。重复检查不得恢复管理员后来取消的模块授权。

CREATE TABLE IF NOT EXISTS sys_data_migration (
    migration_key VARCHAR(120) PRIMARY KEY,
    applied_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据迁移种子执行标记';

CREATE TABLE IF NOT EXISTS sys_role_business_module (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    module_code VARCHAR(32) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_role_business_module (role_id, module_code),
    KEY idx_sys_role_business_module_code (module_code, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色正式业务模块跨端开关';

SET @shared_business_module_seed_required = (
    SELECT IF(COUNT(*) = 0, 1, 0)
    FROM sys_data_migration
    WHERE migration_key = '20260729_SHARED_BUSINESS_MODULE_ACCESS_V1'
);

-- 由既有两端菜单关联和既有业务操作权限推导一次当前模块状态。
-- 后续角色保存只更新 sys_role_business_module，并同步对应的两端菜单，不会再次执行本种子。
INSERT IGNORE INTO sys_role_business_module(role_id, module_code)
SELECT seeded.role_id, seeded.module_code
FROM (
    SELECT rm.role_id,
           CASE
               WHEN m.menu_code IN ('WEB_DOCUMENT', 'MINI_DOCUMENT') THEN 'DOCUMENT'
               WHEN m.menu_code IN ('WEB_INSPECTION', 'MINI_INSPECTION') THEN 'INSPECTION'
               WHEN m.menu_code IN ('WEB_QUALITY', 'MINI_QUALITY') THEN 'QUALITY'
           END AS module_code
    FROM sys_role_menu rm
    INNER JOIN sys_menu m ON m.id = rm.menu_id
    WHERE m.menu_code IN ('WEB_DOCUMENT', 'MINI_DOCUMENT', 'WEB_INSPECTION',
                          'MINI_INSPECTION', 'WEB_QUALITY', 'MINI_QUALITY')
    UNION
    SELECT rp.role_id,
           CASE
               WHEN p.permission_code LIKE 'document.%' THEN 'DOCUMENT'
               WHEN p.permission_code LIKE 'quality.%' THEN 'QUALITY'
               WHEN p.permission_code LIKE 'inspection.%'
                    OR p.permission_code LIKE 'BOX\\_%'
                    OR p.permission_code LIKE 'INSPECTION\\_%'
                    OR p.permission_code LIKE 'SUMMARY\\_%'
                    OR p.permission_code LIKE 'RECTIFICATION\\_%' THEN 'INSPECTION'
           END AS module_code
    FROM sys_role_permission rp
    INNER JOIN sys_permission p ON p.id = rp.permission_id
    WHERE p.permission_code LIKE 'document.%'
       OR p.permission_code LIKE 'quality.%'
       OR p.permission_code LIKE 'inspection.%'
       OR p.permission_code LIKE 'BOX\\_%'
       OR p.permission_code LIKE 'INSPECTION\\_%'
       OR p.permission_code LIKE 'SUMMARY\\_%'
       OR p.permission_code LIKE 'RECTIFICATION\\_%'
) seeded
WHERE @shared_business_module_seed_required = 1
  AND seeded.module_code IS NOT NULL;

INSERT IGNORE INTO sys_data_migration(migration_key)
SELECT '20260729_SHARED_BUSINESS_MODULE_ACCESS_V1'
WHERE @shared_business_module_seed_required = 1;
