-- 正式角色授权目录一致性修复。
--
-- 本迁移只根据角色已经拥有的启用操作权限和业务模块，补齐业务模块、跨端根菜单、
-- Web 父菜单及权限唯一归属的业务子页面；不创建表、菜单、权限，也不授予任何操作权限。
-- 跨多个页面复用的通用权限只补业务模块与根菜单，不推断具体子页面。
-- 执行前必须按生产升级手册备份并预检；禁止用 init.sql 代替。

START TRANSACTION;

-- 旧“项目成员与权限”入口已经退出正式产品。兼容遗漏 20260807 迁移标记的环境，
-- 仅停用旧目录与权限并清理关联，不恢复任何旧授权范围。
DELETE rm
FROM sys_role_menu rm
INNER JOIN sys_menu menu ON menu.id = rm.menu_id
LEFT JOIN sys_role role ON role.id = rm.role_id
WHERE CAST(menu.menu_code AS BINARY) = CAST('SYSTEM_PROJECT' AS BINARY)
   OR (CAST(menu.menu_code AS BINARY) = CAST('WEB_SYSTEM' AS BINARY)
       AND role.scope_type = 'PROJECT');

DELETE rp
FROM sys_role_permission rp
INNER JOIN sys_permission permission ON permission.id = rp.permission_id
WHERE CAST(permission.permission_code AS BINARY) = CAST('project.member.manage' AS BINARY);

UPDATE sys_menu
SET visible = 0,
    enabled = 0,
    deleted = 1,
    update_time = CURRENT_TIMESTAMP
WHERE CAST(menu_code AS BINARY) = CAST('SYSTEM_PROJECT' AS BINARY);

UPDATE sys_permission
SET enabled = 0,
    deleted = 1,
    update_time = CURRENT_TIMESTAMP
WHERE CAST(permission_code AS BINARY) = CAST('project.member.manage' AS BINARY);

INSERT INTO sys_data_migration(migration_key)
VALUES ('20260807_RETIRE_PROJECT_MEMBER_MANAGEMENT_PAGE_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;

-- 清理仍指向不存在、停用或已删除目录/权限的关系。visible=0 可能只是合法的暂时隐藏，
-- 不能单独作为删除授权关系的条件。这里只删除无效关系，
-- 不触碰仍启用的角色操作权限。
DELETE rm
FROM sys_role_menu rm
LEFT JOIN sys_menu menu ON menu.id = rm.menu_id
WHERE menu.id IS NULL
   OR menu.enabled <> 1
   OR menu.deleted <> 0;

DELETE rp
FROM sys_role_permission rp
LEFT JOIN sys_permission permission ON permission.id = rp.permission_id
WHERE permission.id IS NULL
   OR permission.enabled <> 1
   OR permission.deleted <> 0;

-- 已有启用操作权限首先决定正式业务模块。permission.module_code 是现行权限目录的
-- 权威模块归属；停用或删除的历史权限不会参与推导。
INSERT INTO sys_role_business_module(role_id, module_code)
SELECT DISTINCT role.id,
       CASE permission.module_code
           WHEN 'WEB_SITE_ACCESS' THEN 'SITE_ACCESS'
           WHEN 'WEB_DOCUMENT' THEN 'DOCUMENT'
           WHEN 'WEB_INSPECTION' THEN 'INSPECTION'
           WHEN 'WEB_QUALITY' THEN 'QUALITY'
       END AS module_code
FROM sys_role role
INNER JOIN sys_role_permission rp ON rp.role_id = role.id
INNER JOIN sys_permission permission ON permission.id = rp.permission_id
WHERE role.enabled = 1
  AND role.deleted = 0
  AND permission.enabled = 1
  AND permission.deleted = 0
  AND permission.module_code IN ('WEB_SITE_ACCESS', 'WEB_DOCUMENT', 'WEB_INSPECTION', 'WEB_QUALITY')
ON DUPLICATE KEY UPDATE role_id = sys_role_business_module.role_id;

-- 一个业务模块是 Web/小程序共用开关。根据现有模块关系补齐全部正式跨端根菜单；
-- SITE_ACCESS 当前只有 Web 内部管理根菜单，访客小程序页仍是免登录公开页。
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT DISTINCT module.role_id, menu.id
FROM sys_role_business_module module
INNER JOIN sys_role role ON role.id = module.role_id AND role.enabled = 1 AND role.deleted = 0
INNER JOIN (
    SELECT 'SITE_ACCESS' module_code, 'WEB_SITE_ACCESS' menu_code UNION ALL
    SELECT 'DOCUMENT', 'WEB_DOCUMENT' UNION ALL
    SELECT 'DOCUMENT', 'MINI_DOCUMENT' UNION ALL
    SELECT 'INSPECTION', 'WEB_INSPECTION' UNION ALL
    SELECT 'INSPECTION', 'MINI_INSPECTION' UNION ALL
    SELECT 'QUALITY', 'WEB_QUALITY' UNION ALL
    SELECT 'QUALITY', 'MINI_QUALITY'
) root_map ON CAST(root_map.module_code AS BINARY) = CAST(module.module_code AS BINARY)
INNER JOIN sys_menu menu
    ON CAST(menu.menu_code AS BINARY) = CAST(root_map.menu_code AS BINARY)
   AND menu.visible = 1 AND menu.enabled = 1 AND menu.deleted = 0
ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id;

-- 唯一页面权限映射开始。
-- 只有能够唯一确定页面的权限才补子页面。document.view/manage、inspection.view/manage/
-- submit/export、SUMMARY_*、quality.view/manage 等跨页权限不会出现在此映射中。
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT DISTINCT role.id, menu.id
FROM sys_role role
INNER JOIN sys_role_permission rp ON rp.role_id = role.id
INNER JOIN sys_permission permission ON permission.id = rp.permission_id
INNER JOIN (
    SELECT 'site_access.view' permission_code, 'SITE_VISITOR' menu_code UNION ALL
    SELECT 'site_access.manage', 'SITE_VISITOR' UNION ALL
    SELECT 'site_access.export', 'SITE_VISITOR' UNION ALL
    SELECT 'document.upload', 'DOCUMENT_LIBRARY' UNION ALL
    SELECT 'document.receive', 'DOCUMENT_CIRCULATION' UNION ALL
    SELECT 'document.issue', 'DOCUMENT_CIRCULATION' UNION ALL
    SELECT 'document.circulation.view', 'DOCUMENT_CIRCULATION' UNION ALL
    SELECT 'document.circulation.export', 'DOCUMENT_CIRCULATION' UNION ALL
    SELECT 'seal.view', 'DOCUMENT_SEAL' UNION ALL
    SELECT 'seal.manage', 'DOCUMENT_SEAL' UNION ALL
    SELECT 'seal.export', 'DOCUMENT_SEAL' UNION ALL
    SELECT 'BOX_VIEW', 'INSPECTION_LEDGER' UNION ALL
    SELECT 'BOX_MANAGE', 'INSPECTION_LEDGER' UNION ALL
    SELECT 'BOX_QR_MANAGE', 'INSPECTION_LEDGER' UNION ALL
    SELECT 'BOX_PUBLIC_ACCESS', 'INSPECTION_LEDGER' UNION ALL
    SELECT 'INSPECTION_DAILY_SUBMIT', 'INSPECTION_RECORDS' UNION ALL
    SELECT 'INSPECTION_RECORD_VIEW', 'INSPECTION_RECORDS' UNION ALL
    SELECT 'inspection.rectify', 'INSPECTION_RECTIFICATIONS' UNION ALL
    SELECT 'inspection.review', 'INSPECTION_RECTIFICATIONS' UNION ALL
    SELECT 'EDGE_INSPECTION_VIEW', 'INSPECTION_EDGE' UNION ALL
    SELECT 'EDGE_INSPECTION_MANAGE', 'INSPECTION_EDGE' UNION ALL
    SELECT 'EDGE_INSPECTION_SUBMIT', 'INSPECTION_EDGE' UNION ALL
    SELECT 'EDGE_INSPECTION_RECTIFY', 'INSPECTION_EDGE' UNION ALL
    SELECT 'EDGE_INSPECTION_REVIEW', 'INSPECTION_EDGE' UNION ALL
    SELECT 'quality.rectify', 'QUALITY_ISSUES' UNION ALL
    SELECT 'quality.review', 'QUALITY_ISSUES'
) page_map
    ON CAST(page_map.permission_code AS BINARY) = CAST(permission.permission_code AS BINARY)
INNER JOIN sys_menu menu
    ON CAST(menu.menu_code AS BINARY) = CAST(page_map.menu_code AS BINARY)
   AND menu.visible = 1 AND menu.enabled = 1 AND menu.deleted = 0
WHERE role.enabled = 1
  AND role.deleted = 0
  AND permission.enabled = 1
  AND permission.deleted = 0
ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id;
-- 唯一页面权限映射结束。

-- 平台系统权限不属于四个项目业务模块，但仍须补齐 WEB_SYSTEM 父菜单和唯一系统子页。
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT DISTINCT role.id, parent_menu.id
FROM sys_role role
INNER JOIN sys_role_permission rp ON rp.role_id = role.id
INNER JOIN sys_permission permission ON permission.id = rp.permission_id
INNER JOIN sys_menu parent_menu
    ON CAST(parent_menu.menu_code AS BINARY) = CAST('WEB_SYSTEM' AS BINARY)
   AND parent_menu.visible = 1 AND parent_menu.enabled = 1 AND parent_menu.deleted = 0
WHERE role.enabled = 1
  AND role.deleted = 0
  AND role.scope_type = 'PLATFORM'
  AND permission.enabled = 1
  AND permission.deleted = 0
  AND permission.module_code IN (
      'SYSTEM_REGISTRATION', 'SYSTEM_USER', 'SYSTEM_ROLE', 'SYSTEM_MENU',
      'SYSTEM_WECHAT', 'SYSTEM_APPROVAL', 'SYSTEM_AUDIT'
  )
ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id;

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT DISTINCT role.id, menu.id
FROM sys_role role
INNER JOIN sys_role_permission rp ON rp.role_id = role.id
INNER JOIN sys_permission permission ON permission.id = rp.permission_id
INNER JOIN (
    SELECT 'system.registration.review' permission_code, 'SYSTEM_REGISTRATION' menu_code UNION ALL
    SELECT 'system.user.view', 'SYSTEM_USER' UNION ALL
    SELECT 'system.user.manage', 'SYSTEM_USER' UNION ALL
    SELECT 'system.user.reset_password', 'SYSTEM_USER' UNION ALL
    SELECT 'system.user.status', 'SYSTEM_USER' UNION ALL
    SELECT 'system.role.manage', 'SYSTEM_ROLE' UNION ALL
    SELECT 'system.menu.manage', 'SYSTEM_MENU' UNION ALL
    SELECT 'system.wechat.manage', 'SYSTEM_WECHAT' UNION ALL
    SELECT 'system.approval.view', 'SYSTEM_APPROVAL' UNION ALL
    SELECT 'system.approval.manage', 'SYSTEM_APPROVAL' UNION ALL
    SELECT 'system.audit.view', 'SYSTEM_AUDIT'
) system_page_map
    ON CAST(system_page_map.permission_code AS BINARY) = CAST(permission.permission_code AS BINARY)
INNER JOIN sys_menu menu
    ON CAST(menu.menu_code AS BINARY) = CAST(system_page_map.menu_code AS BINARY)
   AND menu.visible = 1 AND menu.enabled = 1 AND menu.deleted = 0
WHERE role.enabled = 1
  AND role.deleted = 0
  AND role.scope_type = 'PLATFORM'
  AND permission.enabled = 1
  AND permission.deleted = 0
ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id;

INSERT INTO sys_data_migration(migration_key)
VALUES ('20260829_ROLE_AUTHORIZATION_CONSISTENCY_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;

COMMIT;

-- 回滚边界：本迁移补的是由现有操作权限/业务模块确定的菜单关系，不能通过统一 DELETE 盲目回滚，
-- 否则会误删迁移前已存在的合法关联。需要回滚时只删除新迁移标记，并按执行前角色授权快照恢复；
-- SYSTEM_PROJECT/project.member.manage 属于已正式退役范围，不得随本迁移回滚而重新启用。
