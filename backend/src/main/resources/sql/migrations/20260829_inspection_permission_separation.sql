-- 电箱巡检与临边巡检操作权限分离。
--
-- 本迁移新增临边含图报表专用权限 EDGE_INSPECTION_EXPORT，并把旧巡检通用权限的
-- 名称与说明收口为电箱口径。角色迁移只根据已有权限和菜单能力判断，不按角色编码、
-- 角色名称或岗位重置授权，也不会删除任何电箱细分权限。
-- 执行前必须按生产升级手册备份并预检；禁止用 init.sql 代替。

SET @inspection_permission_separation_required := (
    SELECT IF(COUNT(*) = 0, 1, 0)
    FROM sys_data_migration
    WHERE migration_key = '20260829_INSPECTION_PERMISSION_SEPARATION_V1'
);

START TRANSACTION;

-- 临边导出不再依赖电箱巡检的 inspection.export / SUMMARY_EXPORT。
INSERT INTO sys_permission(permission_code, permission_name, module_code, description,
                           enabled, builtin, deleted)
SELECT 'EDGE_INSPECTION_EXPORT', '导出临边巡检含图报表', 'WEB_INSPECTION',
       '创建、查看并下载临边巡检含图 Excel 导出任务', 1, 1, 0
WHERE @inspection_permission_separation_required = 1
  AND NOT EXISTS (
      SELECT 1 FROM sys_permission permission
      WHERE CAST(permission.permission_code AS BINARY) = CAST('EDGE_INSPECTION_EXPORT' AS BINARY)
  );

UPDATE sys_permission
SET permission_name = '导出临边巡检含图报表',
    module_code = 'WEB_INSPECTION',
    description = '创建、查看并下载临边巡检含图 Excel 导出任务',
    enabled = 1,
    builtin = 1,
    deleted = 0,
    update_time = CURRENT_TIMESTAMP
WHERE @inspection_permission_separation_required = 1
  AND CAST(permission_code AS BINARY) = CAST('EDGE_INSPECTION_EXPORT' AS BINARY);

-- 只更新权限字典文案，不改变现有角色的电箱授权关系；权限码与后端接口保持不变。
UPDATE sys_permission permission
INNER JOIN (
    SELECT 'inspection.view' code, '查看电箱巡检' name,
           '电箱台账、巡检记录、整改与汇总的通用查看前置权限' description UNION ALL
    SELECT 'inspection.manage', '管理电箱巡检',
           '管理电箱台账等电箱巡检配置的通用前置权限' UNION ALL
    SELECT 'inspection.submit', '提交电箱日检',
           '提交电箱每日巡检记录的通用前置权限' UNION ALL
    SELECT 'inspection.rectify', '提交电箱巡检整改',
           '提交明确分配给本人的电箱巡检整改' UNION ALL
    SELECT 'inspection.review', '复查电箱巡检整改',
           '复查、关闭或退回明确分配给本人的电箱巡检整改' UNION ALL
    SELECT 'inspection.export', '导出电箱巡检报表',
           '创建和下载电箱巡检报表的通用前置权限' UNION ALL
    SELECT 'INSPECTION_RECORD_VIEW', '查看电箱巡检记录',
           '查看项目电箱巡检记录明细' UNION ALL
    SELECT 'SUMMARY_VIEW', '查看电箱巡检汇总',
           '查看项目或单箱月度电箱巡检汇总' UNION ALL
    SELECT 'SUMMARY_EXPORT', '导出电箱巡检报表',
           '导出电箱月度巡检记录 Excel'
) seed ON CAST(seed.code AS BINARY) = CAST(permission.permission_code AS BINARY)
SET permission.permission_name = seed.name,
    permission.module_code = 'WEB_INSPECTION',
    permission.description = seed.description,
    permission.update_time = CURRENT_TIMESTAMP
WHERE @inspection_permission_separation_required = 1;

-- 菜单字典也按专区收口；只改名称，不改变菜单码、路由、状态或现有角色菜单关系。
UPDATE sys_menu
SET menu_name = CASE menu_code
        WHEN 'INSPECTION_RECORDS' THEN '电箱巡检记录'
        WHEN 'INSPECTION_RECTIFICATIONS' THEN '电箱整改闭环'
        ELSE menu_name
    END,
    update_time = CURRENT_TIMESTAMP
WHERE @inspection_permission_separation_required = 1
  AND menu_code IN ('INSPECTION_RECORDS', 'INSPECTION_RECTIFICATIONS');

SET @edge_view_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('EDGE_INSPECTION_VIEW' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
SET @edge_export_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('EDGE_INSPECTION_EXPORT' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
SET @inspection_export_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('inspection.export' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
SET @summary_export_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('SUMMARY_EXPORT' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);

-- 等价迁移旧临边导出组合。必须在清理旧技术依赖前执行，保证原有临边导出能力不丢失。
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT DISTINCT role.id, @edge_export_permission_id
FROM sys_role role
INNER JOIN sys_role_permission edge_view_role_permission
    ON edge_view_role_permission.role_id = role.id
   AND edge_view_role_permission.permission_id = @edge_view_permission_id
INNER JOIN sys_role_permission summary_export_role_permission
    ON summary_export_role_permission.role_id = role.id
   AND summary_export_role_permission.permission_id = @summary_export_permission_id
INNER JOIN sys_role_permission inspection_export_role_permission
    ON inspection_export_role_permission.role_id = role.id
   AND inspection_export_role_permission.permission_id = @inspection_export_permission_id
WHERE @inspection_permission_separation_required = 1
  AND role.deleted = 0
  AND @edge_export_permission_id IS NOT NULL
ON DUPLICATE KEY UPDATE role_id = sys_role_permission.role_id;

-- 平台管理员必须显式拥有新增的全部启用操作权限；这里只补新增项。
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT role.id, @edge_export_permission_id
FROM sys_role role
WHERE @inspection_permission_separation_required = 1
  AND role.role_code = 'PLATFORM_ADMIN'
  AND role.scope_type = 'PLATFORM'
  AND role.deleted = 0
  AND @edge_export_permission_id IS NOT NULL
ON DUPLICATE KEY UPDATE role_id = sys_role_permission.role_id;

-- 任何已持正式临边权限的未删除角色都必须具备 INSPECTION 模块及两端根菜单、临边子页面。
-- 不以 EDGE_INSPECTION_VIEW 为唯一前提，避免其他临边权限因历史配置失去入口。
INSERT INTO sys_role_business_module(role_id, module_code)
SELECT DISTINCT role.id, 'INSPECTION'
FROM sys_role role
INNER JOIN sys_role_permission role_permission ON role_permission.role_id = role.id
INNER JOIN sys_permission permission ON permission.id = role_permission.permission_id
WHERE @inspection_permission_separation_required = 1
  AND role.deleted = 0
  AND permission.enabled = 1
  AND permission.deleted = 0
  AND permission.permission_code IN (
      'EDGE_INSPECTION_VIEW', 'EDGE_INSPECTION_MANAGE', 'EDGE_INSPECTION_SUBMIT',
      'EDGE_INSPECTION_RECTIFY', 'EDGE_INSPECTION_REVIEW', 'EDGE_INSPECTION_EXPORT'
  )
ON DUPLICATE KEY UPDATE role_id = sys_role_business_module.role_id;

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT DISTINCT role.id, menu.id
FROM sys_role role
INNER JOIN sys_role_permission role_permission ON role_permission.role_id = role.id
INNER JOIN sys_permission permission ON permission.id = role_permission.permission_id
INNER JOIN sys_menu menu
    ON menu.menu_code IN ('WEB_INSPECTION', 'MINI_INSPECTION', 'INSPECTION_EDGE')
   AND menu.enabled = 1
   AND menu.deleted = 0
WHERE @inspection_permission_separation_required = 1
  AND role.deleted = 0
  AND permission.enabled = 1
  AND permission.deleted = 0
  AND permission.permission_code IN (
      'EDGE_INSPECTION_VIEW', 'EDGE_INSPECTION_MANAGE', 'EDGE_INSPECTION_SUBMIT',
      'EDGE_INSPECTION_RECTIFY', 'EDGE_INSPECTION_REVIEW', 'EDGE_INSPECTION_EXPORT'
  )
ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id;

-- 以下清理只处理已经持有任一正式临边权限的普通角色。平台管理员依法拥有全部正式能力，
-- 不参与旧依赖清理；电箱细分权限本身从不删除。通用技术权限仅在没有对应电箱能力或页面时
-- 移除，避免角色授权页出现孤立权限。
SET @box_manage_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('BOX_MANAGE' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
DELETE target_role_permission
FROM sys_role_permission target_role_permission
INNER JOIN sys_permission target_permission
    ON target_permission.id = target_role_permission.permission_id
   AND CAST(target_permission.permission_code AS BINARY) = CAST('inspection.manage' AS BINARY)
INNER JOIN sys_role role
    ON role.id = target_role_permission.role_id AND role.deleted = 0
   AND NOT (role.role_code = 'PLATFORM_ADMIN' AND role.scope_type = 'PLATFORM')
INNER JOIN sys_role_permission edge_role_permission ON edge_role_permission.role_id = role.id
INNER JOIN sys_permission edge_permission
    ON edge_permission.id = edge_role_permission.permission_id
   AND edge_permission.enabled = 1 AND edge_permission.deleted = 0
   AND edge_permission.permission_code IN (
       'EDGE_INSPECTION_VIEW', 'EDGE_INSPECTION_MANAGE', 'EDGE_INSPECTION_SUBMIT',
       'EDGE_INSPECTION_RECTIFY', 'EDGE_INSPECTION_REVIEW', 'EDGE_INSPECTION_EXPORT'
   )
LEFT JOIN sys_role_permission box_manage_role_permission
    ON box_manage_role_permission.role_id = role.id
   AND box_manage_role_permission.permission_id = @box_manage_permission_id
WHERE @inspection_permission_separation_required = 1
  AND box_manage_role_permission.id IS NULL;

SET @daily_submit_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('INSPECTION_DAILY_SUBMIT' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
DELETE target_role_permission
FROM sys_role_permission target_role_permission
INNER JOIN sys_permission target_permission
    ON target_permission.id = target_role_permission.permission_id
   AND CAST(target_permission.permission_code AS BINARY) = CAST('inspection.submit' AS BINARY)
INNER JOIN sys_role role
    ON role.id = target_role_permission.role_id AND role.deleted = 0
   AND NOT (role.role_code = 'PLATFORM_ADMIN' AND role.scope_type = 'PLATFORM')
INNER JOIN sys_role_permission edge_role_permission ON edge_role_permission.role_id = role.id
INNER JOIN sys_permission edge_permission
    ON edge_permission.id = edge_role_permission.permission_id
   AND edge_permission.enabled = 1 AND edge_permission.deleted = 0
   AND edge_permission.permission_code IN (
       'EDGE_INSPECTION_VIEW', 'EDGE_INSPECTION_MANAGE', 'EDGE_INSPECTION_SUBMIT',
       'EDGE_INSPECTION_RECTIFY', 'EDGE_INSPECTION_REVIEW', 'EDGE_INSPECTION_EXPORT'
   )
LEFT JOIN sys_role_permission daily_submit_role_permission
    ON daily_submit_role_permission.role_id = role.id
   AND daily_submit_role_permission.permission_id = @daily_submit_permission_id
WHERE @inspection_permission_separation_required = 1
  AND daily_submit_role_permission.id IS NULL;

-- 巡检汇总属于电箱“巡检记录”页面；没有该页面的临边角色不保留 SUMMARY_*。
DELETE target_role_permission
FROM sys_role_permission target_role_permission
INNER JOIN sys_permission target_permission
    ON target_permission.id = target_role_permission.permission_id
   AND target_permission.permission_code IN ('SUMMARY_VIEW', 'SUMMARY_EXPORT')
INNER JOIN sys_role role
    ON role.id = target_role_permission.role_id AND role.deleted = 0
   AND NOT (role.role_code = 'PLATFORM_ADMIN' AND role.scope_type = 'PLATFORM')
INNER JOIN sys_role_permission edge_role_permission ON edge_role_permission.role_id = role.id
INNER JOIN sys_permission edge_permission
    ON edge_permission.id = edge_role_permission.permission_id
   AND edge_permission.enabled = 1 AND edge_permission.deleted = 0
   AND edge_permission.permission_code IN (
       'EDGE_INSPECTION_VIEW', 'EDGE_INSPECTION_MANAGE', 'EDGE_INSPECTION_SUBMIT',
       'EDGE_INSPECTION_RECTIFY', 'EDGE_INSPECTION_REVIEW', 'EDGE_INSPECTION_EXPORT'
   )
LEFT JOIN sys_menu records_menu
    ON CAST(records_menu.menu_code AS BINARY) = CAST('INSPECTION_RECORDS' AS BINARY)
   AND records_menu.enabled = 1 AND records_menu.deleted = 0
LEFT JOIN sys_role_menu records_role_menu
    ON records_role_menu.role_id = role.id AND records_role_menu.menu_id = records_menu.id
WHERE @inspection_permission_separation_required = 1
  AND records_role_menu.id IS NULL;

-- SUMMARY_EXPORT 清理完成后，再判断电箱报表通用前置权限是否仍有真实依赖。
DELETE target_role_permission
FROM sys_role_permission target_role_permission
INNER JOIN sys_permission target_permission
    ON target_permission.id = target_role_permission.permission_id
   AND CAST(target_permission.permission_code AS BINARY) = CAST('inspection.export' AS BINARY)
INNER JOIN sys_role role
    ON role.id = target_role_permission.role_id AND role.deleted = 0
   AND NOT (role.role_code = 'PLATFORM_ADMIN' AND role.scope_type = 'PLATFORM')
INNER JOIN sys_role_permission edge_role_permission ON edge_role_permission.role_id = role.id
INNER JOIN sys_permission edge_permission
    ON edge_permission.id = edge_role_permission.permission_id
   AND edge_permission.enabled = 1 AND edge_permission.deleted = 0
   AND edge_permission.permission_code IN (
       'EDGE_INSPECTION_VIEW', 'EDGE_INSPECTION_MANAGE', 'EDGE_INSPECTION_SUBMIT',
       'EDGE_INSPECTION_RECTIFY', 'EDGE_INSPECTION_REVIEW', 'EDGE_INSPECTION_EXPORT'
   )
LEFT JOIN sys_role_permission summary_export_role_permission
    ON summary_export_role_permission.role_id = role.id
   AND summary_export_role_permission.permission_id = @summary_export_permission_id
WHERE @inspection_permission_separation_required = 1
  AND summary_export_role_permission.id IS NULL;

-- 电箱整改技术权限只有在角色仍持“电箱整改闭环”页面时保留。
DELETE target_role_permission
FROM sys_role_permission target_role_permission
INNER JOIN sys_permission target_permission
    ON target_permission.id = target_role_permission.permission_id
   AND target_permission.permission_code IN ('inspection.rectify', 'inspection.review')
INNER JOIN sys_role role
    ON role.id = target_role_permission.role_id AND role.deleted = 0
   AND NOT (role.role_code = 'PLATFORM_ADMIN' AND role.scope_type = 'PLATFORM')
INNER JOIN sys_role_permission edge_role_permission ON edge_role_permission.role_id = role.id
INNER JOIN sys_permission edge_permission
    ON edge_permission.id = edge_role_permission.permission_id
   AND edge_permission.enabled = 1 AND edge_permission.deleted = 0
   AND edge_permission.permission_code IN (
       'EDGE_INSPECTION_VIEW', 'EDGE_INSPECTION_MANAGE', 'EDGE_INSPECTION_SUBMIT',
       'EDGE_INSPECTION_RECTIFY', 'EDGE_INSPECTION_REVIEW', 'EDGE_INSPECTION_EXPORT'
   )
LEFT JOIN sys_menu rectifications_menu
    ON CAST(rectifications_menu.menu_code AS BINARY) = CAST('INSPECTION_RECTIFICATIONS' AS BINARY)
   AND rectifications_menu.enabled = 1 AND rectifications_menu.deleted = 0
LEFT JOIN sys_role_menu rectifications_role_menu
    ON rectifications_role_menu.role_id = role.id
   AND rectifications_role_menu.menu_id = rectifications_menu.id
WHERE @inspection_permission_separation_required = 1
  AND rectifications_role_menu.id IS NULL;

-- inspection.view 是电箱页面的通用查看前置权限。只有不存在下列任何电箱能力时才清理。
SET @box_view_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('BOX_VIEW' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
SET @box_qr_manage_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('BOX_QR_MANAGE' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
SET @box_public_access_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('BOX_PUBLIC_ACCESS' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
SET @record_view_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('INSPECTION_RECORD_VIEW' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
SET @summary_view_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('SUMMARY_VIEW' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
SET @rectify_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('inspection.rectify' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);
SET @review_permission_id := (
    SELECT id FROM sys_permission
    WHERE CAST(permission_code AS BINARY) = CAST('inspection.review' AS BINARY)
      AND enabled = 1 AND deleted = 0 LIMIT 1
);

DELETE target_role_permission
FROM sys_role_permission target_role_permission
INNER JOIN sys_permission target_permission
    ON target_permission.id = target_role_permission.permission_id
   AND CAST(target_permission.permission_code AS BINARY) = CAST('inspection.view' AS BINARY)
INNER JOIN sys_role role
    ON role.id = target_role_permission.role_id AND role.deleted = 0
   AND NOT (role.role_code = 'PLATFORM_ADMIN' AND role.scope_type = 'PLATFORM')
INNER JOIN sys_role_permission edge_role_permission ON edge_role_permission.role_id = role.id
INNER JOIN sys_permission edge_permission
    ON edge_permission.id = edge_role_permission.permission_id
   AND edge_permission.enabled = 1 AND edge_permission.deleted = 0
   AND edge_permission.permission_code IN (
       'EDGE_INSPECTION_VIEW', 'EDGE_INSPECTION_MANAGE', 'EDGE_INSPECTION_SUBMIT',
       'EDGE_INSPECTION_RECTIFY', 'EDGE_INSPECTION_REVIEW', 'EDGE_INSPECTION_EXPORT'
   )
LEFT JOIN sys_role_permission box_view_role_permission
    ON box_view_role_permission.role_id = role.id
   AND box_view_role_permission.permission_id = @box_view_permission_id
LEFT JOIN sys_role_permission box_manage_role_permission
    ON box_manage_role_permission.role_id = role.id
   AND box_manage_role_permission.permission_id = @box_manage_permission_id
LEFT JOIN sys_role_permission box_qr_manage_role_permission
    ON box_qr_manage_role_permission.role_id = role.id
   AND box_qr_manage_role_permission.permission_id = @box_qr_manage_permission_id
LEFT JOIN sys_role_permission box_public_access_role_permission
    ON box_public_access_role_permission.role_id = role.id
   AND box_public_access_role_permission.permission_id = @box_public_access_permission_id
LEFT JOIN sys_role_permission record_view_role_permission
    ON record_view_role_permission.role_id = role.id
   AND record_view_role_permission.permission_id = @record_view_permission_id
LEFT JOIN sys_role_permission summary_view_role_permission
    ON summary_view_role_permission.role_id = role.id
   AND summary_view_role_permission.permission_id = @summary_view_permission_id
LEFT JOIN sys_role_permission summary_export_role_permission
    ON summary_export_role_permission.role_id = role.id
   AND summary_export_role_permission.permission_id = @summary_export_permission_id
LEFT JOIN sys_role_permission rectify_role_permission
    ON rectify_role_permission.role_id = role.id
   AND rectify_role_permission.permission_id = @rectify_permission_id
LEFT JOIN sys_role_permission review_role_permission
    ON review_role_permission.role_id = role.id
   AND review_role_permission.permission_id = @review_permission_id
WHERE @inspection_permission_separation_required = 1
  AND box_view_role_permission.id IS NULL
  AND box_manage_role_permission.id IS NULL
  AND box_qr_manage_role_permission.id IS NULL
  AND box_public_access_role_permission.id IS NULL
  AND record_view_role_permission.id IS NULL
  AND summary_view_role_permission.id IS NULL
  AND summary_export_role_permission.id IS NULL
  AND rectify_role_permission.id IS NULL
  AND review_role_permission.id IS NULL;

INSERT INTO sys_data_migration(migration_key)
SELECT '20260829_INSPECTION_PERMISSION_SEPARATION_V1'
WHERE @inspection_permission_separation_required = 1
ON DUPLICATE KEY UPDATE migration_key = sys_data_migration.migration_key;

COMMIT;

-- 回滚边界：新增权限、等价迁移和保守清理均属于角色授权数据变更，禁止统一 DELETE 回滚。
-- 如需回滚，必须使用执行前的 sys_permission / sys_role_permission / sys_role_menu /
-- sys_role_business_module 授权快照恢复，并删除本迁移标记；不得按岗位模板重新覆盖角色。
