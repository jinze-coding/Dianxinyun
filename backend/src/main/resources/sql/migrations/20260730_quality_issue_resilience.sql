-- 质量问题并发控制与创建幂等增量脚本。
-- 已有数据环境只执行本脚本；不得使用包含 DROP TABLE 的 init.sql 代替。

SET @quality_request_key_column_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quality_issue'
          AND COLUMN_NAME = 'request_key'
    ),
    'SELECT 1',
    'ALTER TABLE quality_issue ADD COLUMN request_key VARCHAR(100) NULL COMMENT ''客户端创建请求幂等键'' AFTER issue_no'
);
PREPARE quality_request_key_column_stmt FROM @quality_request_key_column_sql;
EXECUTE quality_request_key_column_stmt;
DEALLOCATE PREPARE quality_request_key_column_stmt;

SET @quality_version_column_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quality_issue'
          AND COLUMN_NAME = 'version'
    ),
    'SELECT 1',
    'ALTER TABLE quality_issue ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''并发控制版本号'' AFTER created_by_name'
);
PREPARE quality_version_column_stmt FROM @quality_version_column_sql;
EXECUTE quality_version_column_stmt;
DEALLOCATE PREPARE quality_version_column_stmt;

UPDATE quality_issue
SET version = 0
WHERE version IS NULL;

SET @quality_request_key_index_sql = IF(
    EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quality_issue'
          AND INDEX_NAME = 'uk_quality_issue_request'
    ),
    'SELECT 1',
    'ALTER TABLE quality_issue ADD UNIQUE KEY uk_quality_issue_request (project_id, created_by_id, request_key)'
);
PREPARE quality_request_key_index_stmt FROM @quality_request_key_index_sql;
EXECUTE quality_request_key_index_stmt;
DEALLOCATE PREPARE quality_request_key_index_stmt;
