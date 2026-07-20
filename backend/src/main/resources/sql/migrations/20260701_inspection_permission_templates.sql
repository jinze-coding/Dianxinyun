-- 电箱巡检权限模板与项目用户授权。
-- 仅新增表和字段，不执行清库操作。

CREATE TABLE IF NOT EXISTS inspection_permission_template (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限模板ID',
    template_name   VARCHAR(80) NOT NULL COMMENT '模板名称',
    template_code   VARCHAR(80) NOT NULL COMMENT '模板编码',
    description     VARCHAR(255) COMMENT '说明',
    permission_codes TEXT NOT NULL COMMENT '权限码CSV',
    enabled         TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用: 1启用 0停用',
    builtin         TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置模板: 1是 0否',
    deleted         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_inspection_permission_template_code (template_code),
    KEY idx_inspection_permission_template_enabled (enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电箱巡检权限模板';

SET @has_inspection_permission_template_id := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user_project'
      AND COLUMN_NAME = 'inspection_permission_template_id'
);
SET @alter_sql := IF(@has_inspection_permission_template_id = 0,
    'ALTER TABLE sys_user_project ADD COLUMN inspection_permission_template_id BIGINT NULL COMMENT ''电箱巡检权限模板ID'' AFTER project_role_code',
    'SELECT 1'
);
PREPARE stmt FROM @alter_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_sys_user_project_template := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user_project'
      AND INDEX_NAME = 'idx_sys_user_project_permission_template'
);
SET @idx_sql := IF(@has_idx_sys_user_project_template = 0,
    'ALTER TABLE sys_user_project ADD KEY idx_sys_user_project_permission_template (inspection_permission_template_id)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO inspection_permission_template
    (template_name, template_code, description, permission_codes, enabled, builtin)
VALUES
    (
        '项目管理员',
        'PROJECT_ADMIN',
        '管理授权项目电箱、巡检、整改、汇总导出和项目用户授权',
        'BOX_VIEW,BOX_MANAGE,BOX_QR_MANAGE,BOX_PUBLIC_ACCESS,INSPECTION_DAILY_SUBMIT,INSPECTION_REVIEW,INSPECTION_RECORD_VIEW,RECTIFICATION_VIEW,RECTIFICATION_REVIEW,SUMMARY_VIEW,SUMMARY_EXPORT,PERMISSION_MANAGE',
        1,
        1
    ),
    (
        '安全管理员',
        'SAFETY_ADMIN',
        '管理电箱台账、二维码、安全复核、整改复查和巡检汇总',
        'BOX_VIEW,BOX_MANAGE,BOX_QR_MANAGE,BOX_PUBLIC_ACCESS,INSPECTION_REVIEW,INSPECTION_RECORD_VIEW,RECTIFICATION_VIEW,RECTIFICATION_REVIEW,SUMMARY_VIEW,SUMMARY_EXPORT',
        1,
        1
    ),
    (
        '项目成员/负责电工',
        'USER',
        '查看电箱、提交本人负责电箱日检和处理本人整改',
        'BOX_VIEW,INSPECTION_DAILY_SUBMIT,RECTIFICATION_VIEW',
        1,
        1
    )
ON DUPLICATE KEY UPDATE
    template_name = VALUES(template_name),
    description = VALUES(description),
    permission_codes = VALUES(permission_codes),
    enabled = 1,
    builtin = 1,
    deleted = 0,
    update_time = CURRENT_TIMESTAMP;

UPDATE sys_user_project sup
JOIN inspection_permission_template ipt
  ON ipt.template_code = CASE
      WHEN sup.project_role_code = 'PROJECT_ADMIN' THEN 'PROJECT_ADMIN'
      WHEN sup.project_role_code = 'SAFETY_ADMIN' THEN 'SAFETY_ADMIN'
      ELSE 'USER'
  END
SET sup.inspection_permission_template_id = ipt.id
WHERE sup.inspection_permission_template_id IS NULL;
