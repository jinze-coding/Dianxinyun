-- 统一项目角色库启用后，移除不再被任何正式接口使用的旧项目授权权限码。
-- 可重复执行；不删除历史模板或成员旧字段。

DELETE rp
FROM sys_role_permission rp
INNER JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.permission_code = 'system.project.manage';

UPDATE sys_permission
SET enabled = 0, deleted = 1
WHERE permission_code = 'system.project.manage'
  AND deleted = 0;
