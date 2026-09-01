-- 质量问题按日筛选与异步含图 Excel 导出。
-- 非破坏增量迁移；执行前必须备份数据库，并确保迁移先于新版后端发布。

SET @quality_issue_record_date_column_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quality_issue'
          AND COLUMN_NAME = 'record_date'
    ),
    'SELECT 1',
    'ALTER TABLE quality_issue ADD COLUMN record_date DATE NULL COMMENT ''质量问题业务日期'' AFTER inspection_item_order'
);
PREPARE quality_issue_record_date_column_stmt FROM @quality_issue_record_date_column_sql;
EXECUTE quality_issue_record_date_column_stmt;
DEALLOCATE PREPARE quality_issue_record_date_column_stmt;

UPDATE quality_issue qi
LEFT JOIN quality_weekly_inspection qwi ON qwi.id = qi.weekly_inspection_id
SET qi.record_date = COALESCE(qwi.inspection_date, DATE(qi.create_time))
WHERE qi.record_date IS NULL;

SET @quality_issue_record_date_not_null_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quality_issue'
          AND COLUMN_NAME = 'record_date'
          AND IS_NULLABLE = 'NO'
    ),
    'SELECT 1',
    'ALTER TABLE quality_issue MODIFY COLUMN record_date DATE NOT NULL COMMENT ''质量问题业务日期'''
);
PREPARE quality_issue_record_date_not_null_stmt FROM @quality_issue_record_date_not_null_sql;
EXECUTE quality_issue_record_date_not_null_stmt;
DEALLOCATE PREPARE quality_issue_record_date_not_null_stmt;

SET @quality_issue_record_date_index_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quality_issue'
          AND INDEX_NAME = 'idx_quality_issue_project_record_date'
    ),
    'SELECT 1',
    'ALTER TABLE quality_issue ADD KEY idx_quality_issue_project_record_date (project_id, record_date, status, deleted)'
);
PREPARE quality_issue_record_date_index_stmt FROM @quality_issue_record_date_index_sql;
EXECUTE quality_issue_record_date_index_stmt;
DEALLOCATE PREPARE quality_issue_record_date_index_stmt;

-- IF NOT EXISTS 的幂等复跑会产生 1050 Note；只在这组受控建表期间关闭 Note，
-- Warning 与 Error 仍由 --show-warnings 严格上报。
SET @quality_export_previous_sql_notes := @@sql_notes;
SET sql_notes = 0;

CREATE TABLE IF NOT EXISTS quality_issue_export_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '导出任务ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    requested_by_id BIGINT NOT NULL COMMENT '申请人ID',
    requested_by_name VARCHAR(50) COMMENT '申请人姓名快照',
    start_date DATE NOT NULL COMMENT '问题业务日期起点',
    end_date DATE NOT NULL COMMENT '问题业务日期终点',
    issue_source VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/WEEKLY/HISTORICAL',
    issue_status VARCHAR(20) NOT NULL DEFAULT 'ALL' COMMENT 'ALL/PENDING/OVERDUE/RECHECK/CLOSED/VOIDED',
    keyword VARCHAR(100) COMMENT '问题关键词',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED/EXPIRED',
    progress INT NOT NULL DEFAULT 0 COMMENT '生成进度0-100',
    issue_count INT NOT NULL DEFAULT 0 COMMENT '任务问题数快照',
    photo_count INT NOT NULL DEFAULT 0 COMMENT '任务照片数快照',
    photo_bytes BIGINT NOT NULL DEFAULT 0 COMMENT '任务照片原始字节数快照',
    file_resource_id BIGINT COMMENT '生成的Excel文件ID',
    error_message VARCHAR(1000) COMMENT '失败原因',
    expires_time DATETIME COMMENT '文件过期时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_quality_export_project (project_id, create_time),
    KEY idx_quality_export_requester (requested_by_id, status, create_time),
    KEY idx_quality_export_dispatch (status, create_time),
    KEY idx_quality_export_expiry (status, expires_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量问题异步含图Excel导出任务';

CREATE TABLE IF NOT EXISTS quality_issue_export_job_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务问题快照ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    job_id BIGINT NOT NULL COMMENT '导出任务ID',
    issue_id BIGINT NOT NULL COMMENT '质量问题ID',
    item_order INT NOT NULL COMMENT '导出顺序',
    record_date DATE NOT NULL COMMENT '问题业务日期快照',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quality_export_job_issue (job_id, issue_id),
    UNIQUE KEY uk_quality_export_job_order (job_id, item_order),
    KEY idx_quality_export_item_project (project_id, job_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量问题导出任务问题快照';

SET sql_notes = @quality_export_previous_sql_notes;

INSERT INTO sys_data_migration(migration_key)
VALUES ('20260828_QUALITY_ISSUE_DAILY_EXPORT_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;
