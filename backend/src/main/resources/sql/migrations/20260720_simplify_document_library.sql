-- 工程资料库口径收敛：只保留一级资料目录，资料类型字段降级为历史兼容字段。
-- 非破坏迁移：不删除目录、资料、版本或文件记录。

-- 同一作业区域内存在同名嵌套目录时，先生成可追溯的唯一名称，再拉平目录。
UPDATE document_folder AS folder
INNER JOIN (
    SELECT project_id, folder_name
    FROM document_folder
    WHERE deleted = 0
    GROUP BY project_id, folder_name
    HAVING COUNT(*) > 1
) AS duplicated
    ON duplicated.project_id = folder.project_id
   AND duplicated.folder_name = folder.folder_name
SET folder.folder_name = CONCAT('历史目录-', folder.id, '-', LEFT(folder.folder_name, 70))
WHERE folder.deleted = 0
  AND folder.parent_id <> 0;

UPDATE document_folder
SET parent_id = 0,
    update_time = CURRENT_TIMESTAMP
WHERE parent_id <> 0;

ALTER TABLE document_folder
    MODIFY COLUMN parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '历史兼容字段，一级目录固定为0';

SET @has_root_only_check := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'document_folder'
      AND CONSTRAINT_NAME = 'chk_document_folder_root_only'
);
SET @sql := IF(
    @has_root_only_check = 0,
    'ALTER TABLE document_folder ADD CONSTRAINT chk_document_folder_root_only CHECK (parent_id = 0)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE project_document
    MODIFY COLUMN category VARCHAR(40) NOT NULL DEFAULT 'PROJECT_DATA'
    COMMENT '历史兼容字段，正式界面不再维护';
