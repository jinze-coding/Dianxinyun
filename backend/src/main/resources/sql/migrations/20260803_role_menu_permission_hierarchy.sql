-- 角色菜单细化到当前 Web 可见页签。
-- 非破坏迁移：只新增内置 sys_menu 记录并一次性回填 sys_role_menu，不修改表结构。
-- 执行前必须完整备份；禁止用 init.sql 代替。
-- 前置：已执行 20260728_unified_registration_rbac_wechat_login.sql，
-- 因此 sys_data_migration 已存在；本脚本不新增表或字段。

SET @role_tab_seed_required = (
    SELECT IF(COUNT(*) = 0, 1, 0)
    FROM sys_data_migration
    WHERE migration_key = '20260803_ROLE_MENU_PERMISSION_HIERARCHY_V1'
);

SET @document_menu_id = (SELECT id FROM sys_menu WHERE menu_code = 'WEB_DOCUMENT' AND deleted = 0 LIMIT 1);
SET @inspection_menu_id = (SELECT id FROM sys_menu WHERE menu_code = 'WEB_INSPECTION' AND deleted = 0 LIMIT 1);
SET @quality_menu_id = (SELECT id FROM sys_menu WHERE menu_code = 'WEB_QUALITY' AND deleted = 0 LIMIT 1);

SET @missing_parent_menu_count = ((@document_menu_id IS NULL) + (@inspection_menu_id IS NULL) + (@quality_menu_id IS NULL));
SET @parent_menu_guard_sql = IF(@missing_parent_menu_count > 0,
    'SELECT * FROM __migration_abort_missing_business_parent_menu__',
    'SELECT 1');
PREPARE stmt FROM @parent_menu_guard_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
VALUES
(@document_menu_id, 'WEB', 'DOCUMENT_LIBRARY', '资料库', 'TAB', 'DOCUMENT_LIBRARY', 'document.view', 11, 1, 1, 1, 0),
(@document_menu_id, 'WEB', 'DOCUMENT_RECYCLE', '回收站', 'TAB', 'DOCUMENT_RECYCLE', 'document.manage', 12, 1, 1, 1, 0),
(@inspection_menu_id, 'WEB', 'INSPECTION_LEDGER', '电箱台账', 'TAB', 'INSPECTION_LEDGER', 'inspection.view', 21, 1, 1, 1, 0),
(@inspection_menu_id, 'WEB', 'INSPECTION_RECORDS', '巡检记录', 'TAB', 'INSPECTION_RECORDS', 'inspection.view', 22, 1, 1, 1, 0),
(@quality_menu_id, 'WEB', 'QUALITY_ISSUES', '质量问题', 'TAB', 'QUALITY_ISSUES', 'quality.view', 31, 1, 1, 1, 0),
(@quality_menu_id, 'WEB', 'QUALITY_DOCUMENTS', '质量资料', 'TAB', 'QUALITY_DOCUMENTS', 'quality.view', 32, 1, 1, 1, 0)
ON DUPLICATE KEY UPDATE menu_code = VALUES(menu_code);

-- 资料库此前随资料模块显示；回收站此前仅 document.manage 用户可见。
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT rbm.role_id, m.id
FROM sys_role_business_module rbm
INNER JOIN sys_role r ON r.id = rbm.role_id AND r.deleted = 0
INNER JOIN sys_menu m ON m.menu_code = 'DOCUMENT_LIBRARY' AND m.deleted = 0
WHERE @role_tab_seed_required = 1 AND rbm.module_code = 'DOCUMENT';

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT DISTINCT rbm.role_id, m.id
FROM sys_role_business_module rbm
INNER JOIN sys_role r ON r.id = rbm.role_id AND r.deleted = 0
INNER JOIN sys_role_permission rp ON rp.role_id = rbm.role_id
INNER JOIN sys_permission p ON p.id = rp.permission_id
INNER JOIN sys_menu m ON m.menu_code = 'DOCUMENT_RECYCLE' AND m.deleted = 0
WHERE @role_tab_seed_required = 1
  AND rbm.module_code = 'DOCUMENT'
  AND p.permission_code = 'document.manage' AND p.enabled = 1 AND p.deleted = 0;

-- 巡检和质量的两个页签升级前均随父模块显示，首次迁移全部保留，之后由管理员自行调整。
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT rbm.role_id, m.id
FROM sys_role_business_module rbm
INNER JOIN sys_role r ON r.id = rbm.role_id AND r.deleted = 0
INNER JOIN sys_menu m ON m.menu_code IN ('INSPECTION_LEDGER', 'INSPECTION_RECORDS') AND m.deleted = 0
WHERE @role_tab_seed_required = 1 AND rbm.module_code = 'INSPECTION';

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT rbm.role_id, m.id
FROM sys_role_business_module rbm
INNER JOIN sys_role r ON r.id = rbm.role_id AND r.deleted = 0
INNER JOIN sys_menu m ON m.menu_code IN ('QUALITY_ISSUES', 'QUALITY_DOCUMENTS') AND m.deleted = 0
WHERE @role_tab_seed_required = 1 AND rbm.module_code = 'QUALITY';

INSERT IGNORE INTO sys_data_migration(migration_key)
SELECT '20260803_ROLE_MENU_PERMISSION_HIERARCHY_V1'
WHERE @role_tab_seed_required = 1;

-- 回滚说明（仅在应用代码回滚后执行）：
-- DELETE rm FROM sys_role_menu rm INNER JOIN sys_menu m ON m.id = rm.menu_id
-- WHERE m.menu_code IN ('DOCUMENT_LIBRARY','DOCUMENT_RECYCLE','INSPECTION_LEDGER',
--                       'INSPECTION_RECORDS','QUALITY_ISSUES','QUALITY_DOCUMENTS');
-- DELETE FROM sys_menu WHERE menu_code IN ('DOCUMENT_LIBRARY','DOCUMENT_RECYCLE','INSPECTION_LEDGER',
--                                          'INSPECTION_RECORDS','QUALITY_ISSUES','QUALITY_DOCUMENTS');
-- DELETE FROM sys_data_migration WHERE migration_key = '20260803_ROLE_MENU_PERMISSION_HIERARCHY_V1';
