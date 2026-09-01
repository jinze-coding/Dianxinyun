-- 固定式临边巡检异步含图 Excel 导出。
-- 非破坏增量迁移；执行前必须备份数据库，并确保迁移先于新版后端发布。

SET @edge_export_type_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'general_inspection_export_job' AND COLUMN_NAME = 'export_type'),
    'SELECT 1',
    'ALTER TABLE general_inspection_export_job ADD COLUMN export_type VARCHAR(20) NOT NULL DEFAULT ''LEGACY'' COMMENT ''LEGACY/EDGE'' AFTER end_date'
);
PREPARE edge_export_type_stmt FROM @edge_export_type_sql;
EXECUTE edge_export_type_stmt;
DEALLOCATE PREPARE edge_export_type_stmt;

SET @edge_export_point_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'general_inspection_export_job' AND COLUMN_NAME = 'point_count'),
    'SELECT 1',
    'ALTER TABLE general_inspection_export_job ADD COLUMN point_count INT NOT NULL DEFAULT 0 AFTER progress'
);
PREPARE edge_export_point_count_stmt FROM @edge_export_point_count_sql;
EXECUTE edge_export_point_count_stmt;
DEALLOCATE PREPARE edge_export_point_count_stmt;

SET @edge_export_task_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'general_inspection_export_job' AND COLUMN_NAME = 'task_count'),
    'SELECT 1',
    'ALTER TABLE general_inspection_export_job ADD COLUMN task_count INT NOT NULL DEFAULT 0 AFTER point_count'
);
PREPARE edge_export_task_count_stmt FROM @edge_export_task_count_sql;
EXECUTE edge_export_task_count_stmt;
DEALLOCATE PREPARE edge_export_task_count_stmt;

SET @edge_export_photo_count_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'general_inspection_export_job' AND COLUMN_NAME = 'photo_count'),
    'SELECT 1',
    'ALTER TABLE general_inspection_export_job ADD COLUMN photo_count INT NOT NULL DEFAULT 0 AFTER task_count'
);
PREPARE edge_export_photo_count_stmt FROM @edge_export_photo_count_sql;
EXECUTE edge_export_photo_count_stmt;
DEALLOCATE PREPARE edge_export_photo_count_stmt;

SET @edge_export_photo_bytes_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'general_inspection_export_job' AND COLUMN_NAME = 'photo_bytes'),
    'SELECT 1',
    'ALTER TABLE general_inspection_export_job ADD COLUMN photo_bytes BIGINT NOT NULL DEFAULT 0 AFTER photo_count'
);
PREPARE edge_export_photo_bytes_stmt FROM @edge_export_photo_bytes_sql;
EXECUTE edge_export_photo_bytes_stmt;
DEALLOCATE PREPARE edge_export_photo_bytes_stmt;

SET @edge_export_dispatch_index_sql = IF(
    EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
            AND TABLE_NAME = 'general_inspection_export_job' AND INDEX_NAME = 'idx_general_export_dispatch'),
    'SELECT 1',
    'ALTER TABLE general_inspection_export_job ADD KEY idx_general_export_dispatch (export_type, status, create_time, id)'
);
PREPARE edge_export_dispatch_index_stmt FROM @edge_export_dispatch_index_sql;
EXECUTE edge_export_dispatch_index_stmt;
DEALLOCATE PREPARE edge_export_dispatch_index_stmt;

-- IF NOT EXISTS 的幂等复跑会产生 1050 Note；只在受控建表期间关闭 Note，
-- Warning 与 Error 仍由 --show-warnings 严格上报。
SET @edge_export_previous_sql_notes := @@sql_notes;
SET sql_notes = 0;

CREATE TABLE IF NOT EXISTS general_inspection_export_job_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '任务快照关联ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    job_id BIGINT NOT NULL COMMENT '导出任务ID',
    task_id BIGINT NOT NULL COMMENT '临边巡检任务ID',
    item_order INT NOT NULL COMMENT '导出顺序',
    occurrence_date DATE NOT NULL COMMENT '任务业务日期快照',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_general_export_job_task (job_id, task_id),
    UNIQUE KEY uk_general_export_job_order (job_id, item_order),
    KEY idx_general_export_task_project (project_id, job_id),
    KEY idx_general_export_task_business (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='临边巡检导出任务不可变关联快照';

SET sql_notes = @edge_export_previous_sql_notes;

INSERT INTO sys_data_migration(migration_key)
VALUES ('20260828_EDGE_INSPECTION_EXPORT_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;
