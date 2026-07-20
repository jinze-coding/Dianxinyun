-- =============================================
-- 巡检项目级职责与外部公开扫码开关
-- 说明：开发库增量脚本，不包含 DROP TABLE。
-- =============================================

USE dianxinyun;

-- sys_user_project 增加项目内职责，支持同一用户在不同项目拥有不同巡检权限。
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sys_user_project'
       AND COLUMN_NAME = 'project_role_code') = 0,
    'ALTER TABLE sys_user_project ADD COLUMN project_role_code VARCHAR(40) NOT NULL DEFAULT ''USER'' COMMENT ''项目内职责: PROJECT_ADMIN/SAFETY_ADMIN/USER'' AFTER project_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sys_user_project'
       AND COLUMN_NAME = 'update_time') = 0,
    'ALTER TABLE sys_user_project ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT ''更新时间'' AFTER create_time',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 兼容历史授权：全局平台管理员补齐所有项目授权，便于后台项目级职责页直接展示和维护。
INSERT INTO sys_user_project (user_id, project_id, project_role_code)
SELECT u.id, p.id, 'PROJECT_ADMIN'
FROM sys_user u
JOIN sys_user_role sur ON sur.user_id = u.id
JOIN sys_role sr ON sr.id = sur.role_id
JOIN project_info p ON p.deleted = 0
LEFT JOIN sys_user_project sup
  ON sup.user_id = u.id
 AND sup.project_id = p.id
WHERE sr.role_code = 'PLATFORM_ADMIN'
  AND sr.deleted = 0
  AND u.deleted = 0
  AND sup.id IS NULL;

UPDATE sys_user_project sup
SET sup.project_role_code = 'PROJECT_ADMIN'
WHERE EXISTS (
    SELECT 1
    FROM sys_user_role sur
    JOIN sys_role sr ON sr.id = sur.role_id
    WHERE sur.user_id = sup.user_id
      AND sr.role_code = 'PLATFORM_ADMIN'
      AND sr.deleted = 0
);

-- 兼容历史授权：已有全局角色的用户在已授权项目内同步为项目级职责。
UPDATE sys_user_project sup
SET sup.project_role_code = 'SAFETY_ADMIN'
WHERE EXISTS (
    SELECT 1
    FROM sys_user_role sur
    JOIN sys_role sr ON sr.id = sur.role_id
    WHERE sur.user_id = sup.user_id
      AND sr.role_code = 'SAFETY_ADMIN'
      AND sr.deleted = 0
)
  AND sup.project_role_code = 'USER';

UPDATE sys_user_project sup
SET sup.project_role_code = 'PROJECT_ADMIN'
WHERE EXISTS (
    SELECT 1
    FROM sys_user_role sur
    JOIN sys_role sr ON sr.id = sur.role_id
    WHERE sur.user_id = sup.user_id
      AND sr.role_code = 'PROJECT_ADMIN'
      AND sr.deleted = 0
)
  AND sup.project_role_code IN ('USER', 'SAFETY_ADMIN');

-- 增加唯一授权约束前清理重复授权，保留最早一条。
DELETE duplicate_row
FROM sys_user_project duplicate_row
JOIN sys_user_project keep_row
  ON keep_row.user_id = duplicate_row.user_id
 AND keep_row.project_id = duplicate_row.project_id
 AND keep_row.id < duplicate_row.id;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'sys_user_project'
       AND INDEX_NAME = 'uk_sys_user_project_user_project') = 0,
    'ALTER TABLE sys_user_project ADD UNIQUE KEY uk_sys_user_project_user_project (user_id, project_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- electric_box 增加外部公开扫码启停开关。
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'electric_box'
       AND COLUMN_NAME = 'public_access_enabled') = 0,
    'ALTER TABLE electric_box ADD COLUMN public_access_enabled TINYINT NOT NULL DEFAULT 1 COMMENT ''公开只读扫码是否启用: 0禁用 1启用'' AFTER public_code',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE electric_box
SET public_access_enabled = 1
WHERE public_access_enabled IS NULL;
