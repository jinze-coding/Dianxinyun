-- 小程序注册用户、微信绑定和项目巡检授权管理增量结构。
-- 仅在已有 dianxinyun 数据库执行；禁止用 init.sql 替代本迁移。

USE dianxinyun;

SET @has_password_login_enabled := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'password_login_enabled'
);
SET @sql := IF(@has_password_login_enabled = 0,
    'ALTER TABLE sys_user ADD COLUMN password_login_enabled TINYINT NOT NULL DEFAULT 1 COMMENT ''是否允许账号密码登录: 1是 0否'' AFTER password', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE inspection_permission_template
SET description = '管理电箱台账、二维码、巡检记录、月表导出和项目用户授权',
    permission_codes = 'BOX_VIEW,BOX_MANAGE,BOX_QR_MANAGE,BOX_PUBLIC_ACCESS,INSPECTION_DAILY_SUBMIT,INSPECTION_RECORD_VIEW,SUMMARY_VIEW,SUMMARY_EXPORT,PERMISSION_MANAGE',
    update_time = CURRENT_TIMESTAMP
WHERE template_code = 'PROJECT_ADMIN' AND builtin = 1 AND deleted = 0;

UPDATE inspection_permission_template
SET template_name = '巡检记录管理员',
    description = '查看项目电箱、巡检记录和月表导出，不包含用户授权',
    permission_codes = 'BOX_VIEW,BOX_MANAGE,BOX_QR_MANAGE,BOX_PUBLIC_ACCESS,INSPECTION_RECORD_VIEW,SUMMARY_VIEW,SUMMARY_EXPORT',
    update_time = CURRENT_TIMESTAMP
WHERE template_code = 'SAFETY_ADMIN' AND builtin = 1 AND deleted = 0;

UPDATE inspection_permission_template
SET template_name = '巡检员',
    description = '查看项目电箱并提交日常巡检',
    permission_codes = 'BOX_VIEW,INSPECTION_DAILY_SUBMIT',
    update_time = CURRENT_TIMESTAMP
WHERE template_code = 'USER' AND builtin = 1 AND deleted = 0;

SET @has_project_access_status := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_project' AND COLUMN_NAME = 'status'
);
SET @sql := IF(@has_project_access_status = 0,
    'ALTER TABLE sys_user_project ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE'' COMMENT ''项目访问状态: ACTIVE/DISABLED'' AFTER inspection_permission_template_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_project_status_reason := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_project' AND COLUMN_NAME = 'status_reason'
);
SET @sql := IF(@has_project_status_reason = 0,
    'ALTER TABLE sys_user_project ADD COLUMN status_reason VARCHAR(300) NULL COMMENT ''项目授权启停原因'' AFTER status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_project_status_changed_by := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_project' AND COLUMN_NAME = 'status_changed_by'
);
SET @sql := IF(@has_project_status_changed_by = 0,
    'ALTER TABLE sys_user_project ADD COLUMN status_changed_by BIGINT NULL COMMENT ''最近启停操作人'' AFTER status_reason', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_project_status_changed_time := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_project' AND COLUMN_NAME = 'status_changed_time'
);
SET @sql := IF(@has_project_status_changed_time = 0,
    'ALTER TABLE sys_user_project ADD COLUMN status_changed_time DATETIME NULL COMMENT ''最近启停时间'' AFTER status_changed_by', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE sys_user_project SET status = 'ACTIVE' WHERE status IS NULL OR status = '';

UPDATE sys_user_wechat_binding target
JOIN (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, app_id
               ORDER BY COALESCE(last_login_time, bind_time, create_time) DESC, id DESC
           ) AS row_no
    FROM sys_user_wechat_binding
    WHERE deleted = 0 AND status = 'ACTIVE'
) ranked ON ranked.id = target.id
SET target.status = 'DISABLED', target.update_time = CURRENT_TIMESTAMP
WHERE ranked.row_no > 1;

SET @has_active_user_id := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_wechat_binding' AND COLUMN_NAME = 'active_user_id'
);
SET @sql := IF(@has_active_user_id = 0,
    'ALTER TABLE sys_user_wechat_binding ADD COLUMN active_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = ''ACTIVE'' AND deleted = 0 THEN user_id ELSE NULL END) STORED COMMENT ''同AppID有效绑定唯一键'' AFTER status', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_unique_active_binding := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_wechat_binding' AND INDEX_NAME = 'uk_wechat_binding_active_user'
);
SET @sql := IF(@has_unique_active_binding = 0,
    'ALTER TABLE sys_user_wechat_binding ADD UNIQUE KEY uk_wechat_binding_active_user (app_id, active_user_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_project_status_idx := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_project' AND INDEX_NAME = 'idx_sys_user_project_status'
);
SET @sql := IF(@has_project_status_idx = 0,
    'ALTER TABLE sys_user_project ADD KEY idx_sys_user_project_status (project_id, status, user_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
