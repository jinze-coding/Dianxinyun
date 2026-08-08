-- 退役系统管理中的“项目成员与权限”重复入口。
-- 项目与角色继续仅通过“注册审核”和“用户管理”分配。
-- 本脚本只清理菜单/权限关联并软删除目录项，不删除用户、项目、成员关系或项目角色。
-- 执行前必须完整备份；禁止用 init.sql 代替。

DELETE rm
FROM sys_role_menu rm
INNER JOIN sys_menu m ON m.id = rm.menu_id
LEFT JOIN sys_role r ON r.id = rm.role_id
WHERE m.menu_code = 'SYSTEM_PROJECT'
   OR (m.menu_code = 'WEB_SYSTEM' AND r.scope_type = 'PROJECT');

DELETE rp
FROM sys_role_permission rp
INNER JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.permission_code = 'project.member.manage';

UPDATE sys_menu
SET visible = 0,
    enabled = 0,
    deleted = 1,
    update_time = CURRENT_TIMESTAMP
WHERE menu_code = 'SYSTEM_PROJECT';

UPDATE sys_permission
SET enabled = 0,
    deleted = 1,
    update_time = CURRENT_TIMESTAMP
WHERE permission_code = 'project.member.manage';

INSERT IGNORE INTO sys_data_migration(migration_key)
VALUES ('20260807_RETIRE_PROJECT_MEMBER_MANAGEMENT_PAGE_V1');
