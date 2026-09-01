-- 质量周检、电箱日检与固定临边巡检到期未提交站内提醒。
-- 非破坏增量迁移；现有项目提醒规则一律保持关闭，临边开关存放于版本化计划 JSON。

-- IF NOT EXISTS 的幂等复跑会产生 1050 Note；只在受控建表期间关闭 Note，
-- Warning 与 Error 仍由 --show-warnings 严格上报。
SET @inspection_reminder_previous_sql_notes := @@sql_notes;
SET sql_notes = 0;

CREATE TABLE IF NOT EXISTS quality_weekly_reminder_setting (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '质量周检提醒设置ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用站内提醒',
    day_of_week TINYINT NOT NULL DEFAULT 7 COMMENT '提醒星期，1周一至7周日',
    trigger_time TIME NOT NULL DEFAULT '18:00:00' COMMENT '提醒时间',
    responsible_user_id BIGINT COMMENT '唯一主责任人用户ID',
    responsible_user_name VARCHAR(50) COMMENT '主责任人姓名快照',
    effective_time DATETIME COMMENT '本次启用规则生效时间，仅后续周期有效',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_by_id BIGINT COMMENT '创建人ID',
    created_by_name VARCHAR(50) COMMENT '创建人姓名快照',
    updated_by_id BIGINT COMMENT '最近操作人ID',
    updated_by_name VARCHAR(50) COMMENT '最近操作人姓名快照',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quality_weekly_reminder_project (project_id),
    KEY idx_quality_weekly_reminder_schedule (enabled, day_of_week, trigger_time, project_id),
    KEY idx_quality_weekly_reminder_owner (responsible_user_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目质量周检未提交站内提醒设置';

SET sql_notes = @inspection_reminder_previous_sql_notes;

SET @inspection_submission_reminder_enabled_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'project_inspection_setting'
          AND COLUMN_NAME = 'submission_reminder_enabled'
    ),
    'SELECT 1',
    'ALTER TABLE project_inspection_setting ADD COLUMN submission_reminder_enabled TINYINT NOT NULL DEFAULT 0 COMMENT ''到期未提交站内提醒开关'' AFTER enabled'
);
PREPARE inspection_submission_reminder_enabled_stmt FROM @inspection_submission_reminder_enabled_sql;
EXECUTE inspection_submission_reminder_enabled_stmt;
DEALLOCATE PREPARE inspection_submission_reminder_enabled_stmt;

SET @inspection_reminder_effective_time_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'project_inspection_setting'
          AND COLUMN_NAME = 'reminder_effective_time'
    ),
    'SELECT 1',
    'ALTER TABLE project_inspection_setting ADD COLUMN reminder_effective_time DATETIME NULL COMMENT ''本次启用规则生效时间，仅后续箱日有效'' AFTER submission_reminder_enabled'
);
PREPARE inspection_reminder_effective_time_stmt FROM @inspection_reminder_effective_time_sql;
EXECUTE inspection_reminder_effective_time_stmt;
DEALLOCATE PREPARE inspection_reminder_effective_time_stmt;

SET @inspection_setting_version_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'project_inspection_setting'
          AND COLUMN_NAME = 'version'
    ),
    'SELECT 1',
    'ALTER TABLE project_inspection_setting ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT ''乐观锁版本'' AFTER reminder_effective_time'
);
PREPARE inspection_setting_version_stmt FROM @inspection_setting_version_sql;
EXECUTE inspection_setting_version_stmt;
DEALLOCATE PREPARE inspection_setting_version_stmt;

SET @inspection_submission_reminder_index_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'project_inspection_setting'
          AND INDEX_NAME = 'idx_project_inspection_reminder'
    ),
    'SELECT 1',
    'ALTER TABLE project_inspection_setting ADD KEY idx_project_inspection_reminder (submission_reminder_enabled, daily_cutoff_time, project_id)'
);
PREPARE inspection_submission_reminder_index_stmt FROM @inspection_submission_reminder_index_sql;
EXECUTE inspection_submission_reminder_index_stmt;
DEALLOCATE PREPARE inspection_submission_reminder_index_stmt;

INSERT INTO sys_data_migration(migration_key)
VALUES ('20260829_INSPECTION_SUBMISSION_REMINDER_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;
