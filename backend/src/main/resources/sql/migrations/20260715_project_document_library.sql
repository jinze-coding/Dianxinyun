-- 工程资料库增量脚本。
-- 仅包含非破坏性结构变更和三条无物理文件演示资料的精确逻辑删除。
-- 禁止以 init.sql 替代本脚本在已有环境执行。

USE dianxinyun;

SET @has_column := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'storage_provider'
);
SET @sql := IF(@has_column = 0,
    'ALTER TABLE file_resource ADD COLUMN storage_provider VARCHAR(20) NULL COMMENT ''存储提供方: local/minio'' AFTER file_path',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'storage_key'
);
SET @sql := IF(@has_column = 0,
    'ALTER TABLE file_resource ADD COLUMN storage_key VARCHAR(500) NULL COMMENT ''存储对象键'' AFTER storage_provider',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'original_file_name'
);
SET @sql := IF(@has_column = 0,
    'ALTER TABLE file_resource ADD COLUMN original_file_name VARCHAR(255) NULL COMMENT ''原始文件名'' AFTER storage_key',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'mime_type'
);
SET @sql := IF(@has_column = 0,
    'ALTER TABLE file_resource ADD COLUMN mime_type VARCHAR(150) NULL COMMENT ''MIME 类型'' AFTER original_file_name',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'file_extension'
);
SET @sql := IF(@has_column = 0,
    'ALTER TABLE file_resource ADD COLUMN file_extension VARCHAR(20) NULL COMMENT ''文件扩展名'' AFTER mime_type',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_column := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'file_resource' AND COLUMN_NAME = 'sha256'
);
SET @sql := IF(@has_column = 0,
    'ALTER TABLE file_resource ADD COLUMN sha256 CHAR(64) NULL COMMENT ''文件 SHA-256'' AFTER file_extension',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS document_folder (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '目录ID',
    project_id      BIGINT NOT NULL COMMENT '作业区域ID',
    parent_id       BIGINT NOT NULL DEFAULT 0 COMMENT '上级目录ID，0为根目录',
    folder_name     VARCHAR(100) NOT NULL COMMENT '目录名称',
    sort_no         INT NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by      BIGINT NOT NULL COMMENT '创建人ID',
    deleted         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    active_folder_name VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN folder_name ELSE NULL END) STORED COMMENT '未删除目录唯一键',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_document_folder_active_name (project_id, parent_id, active_folder_name),
    KEY idx_document_folder_project (project_id, deleted, parent_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程资料目录';

CREATE TABLE IF NOT EXISTS project_document (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '资料ID',
    project_id          BIGINT NOT NULL COMMENT '作业区域ID',
    folder_id           BIGINT NOT NULL DEFAULT 0 COMMENT '目录ID，0为根目录',
    document_no         VARCHAR(100) COMMENT '资料编号',
    title               VARCHAR(200) NOT NULL COMMENT '资料名称',
    category            VARCHAR(40) NOT NULL DEFAULT 'OTHER' COMMENT '资料分类',
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ARCHIVED',
    current_version_id  BIGINT COMMENT '当前版本ID',
    created_by          BIGINT NOT NULL COMMENT '上传人ID',
    created_by_name     VARCHAR(100) COMMENT '上传人姓名快照',
    remark              VARCHAR(1000) COMMENT '备注',
    deleted             TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除/回收站',
    active_title        VARCHAR(200) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN title ELSE NULL END) STORED COMMENT '未删除资料名称唯一键',
    active_document_no  VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN document_no ELSE NULL END) STORED COMMENT '未删除资料编号唯一键',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_project_document_active_title (project_id, folder_id, active_title),
    UNIQUE KEY uk_project_document_active_no (project_id, active_document_no),
    KEY idx_project_document_project (project_id, deleted, status, update_time),
    KEY idx_project_document_folder (project_id, folder_id, deleted, update_time),
    KEY idx_project_document_no (project_id, document_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程资料主表';

CREATE TABLE IF NOT EXISTS project_document_version (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '版本ID',
    document_id         BIGINT NOT NULL COMMENT '资料ID',
    version_no          INT NOT NULL COMMENT '版本号，从1递增',
    file_resource_id    BIGINT NOT NULL COMMENT '通用文件资源ID',
    change_note         VARCHAR(500) COMMENT '版本说明',
    created_by          BIGINT NOT NULL COMMENT '上传人ID',
    created_by_name     VARCHAR(100) COMMENT '上传人姓名快照',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_project_document_version (document_id, version_no),
    KEY idx_document_version_file (file_resource_id),
    KEY idx_document_version_time (document_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程资料版本';

-- 兼容本脚本早期已执行版本：唯一键仅约束未删除记录，回收站允许保留同名历史。
SET @has_index := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'document_folder' AND INDEX_NAME = 'uk_document_folder_name');
SET @sql := IF(@has_index > 0, 'ALTER TABLE document_folder DROP INDEX uk_document_folder_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_column := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'document_folder' AND COLUMN_NAME = 'active_folder_name');
SET @sql := IF(@has_column = 0, 'ALTER TABLE document_folder ADD COLUMN active_folder_name VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN folder_name ELSE NULL END) STORED', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_index := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'document_folder' AND INDEX_NAME = 'uk_document_folder_active_name');
SET @sql := IF(@has_index = 0, 'ALTER TABLE document_folder ADD UNIQUE KEY uk_document_folder_active_name (project_id, parent_id, active_folder_name)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_index := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document' AND INDEX_NAME = 'uk_project_document_title');
SET @sql := IF(@has_index > 0, 'ALTER TABLE project_document DROP INDEX uk_project_document_title', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_column := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document' AND COLUMN_NAME = 'active_title');
SET @sql := IF(@has_column = 0, 'ALTER TABLE project_document ADD COLUMN active_title VARCHAR(200) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN title ELSE NULL END) STORED', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_column := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document' AND COLUMN_NAME = 'active_document_no');
SET @sql := IF(@has_column = 0, 'ALTER TABLE project_document ADD COLUMN active_document_no VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN document_no ELSE NULL END) STORED', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_index := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document' AND INDEX_NAME = 'uk_project_document_active_title');
SET @sql := IF(@has_index = 0, 'ALTER TABLE project_document ADD UNIQUE KEY uk_project_document_active_title (project_id, folder_id, active_title)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @has_index := (SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document' AND INDEX_NAME = 'uk_project_document_active_no');
SET @sql := IF(@has_index = 0, 'ALTER TABLE project_document ADD UNIQUE KEY uk_project_document_active_no (project_id, active_document_no)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE file_resource
SET deleted = 1,
    update_time = CURRENT_TIMESTAMP
WHERE project_id = 1
  AND uploader_id IS NULL
  AND deleted = 0
  AND (
      (file_name = '安全培训资料.pdf' AND file_path = '/uploads/training/安全培训资料.pdf')
      OR (file_name = '施工许可证.jpg' AND file_path = '/uploads/document/施工许可证.jpg')
      OR (file_name = '人员签字表.pdf' AND file_path = '/uploads/signature/人员签字表.pdf')
  );
