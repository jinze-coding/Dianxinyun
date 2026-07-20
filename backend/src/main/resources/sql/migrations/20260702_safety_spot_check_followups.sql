-- 安全抽查后续流程增强：问题分类、整改要求模板落库、整改改派和逾期升级提醒。
-- 仅增量变更，不包含 DROP TABLE，不要用 init.sql 替代。

USE dianxinyun;

SET @has_record_problem_category := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_record'
      AND COLUMN_NAME = 'problem_category'
);
SET @sql := IF(@has_record_problem_category = 0,
    'ALTER TABLE inspection_record ADD COLUMN problem_category VARCHAR(50) NULL COMMENT ''安全抽查问题分类'' AFTER source',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_rectification_problem_category := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND COLUMN_NAME = 'problem_category'
);
SET @sql := IF(@has_rectification_problem_category = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN problem_category VARCHAR(50) NULL COMMENT ''整改问题分类'' AFTER problem_desc',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_completed_time := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND COLUMN_NAME = 'completed_time'
);
SET @sql := IF(@has_completed_time = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN completed_time DATETIME NULL COMMENT ''整改完成提交时间'' AFTER rectification_photo_file_ids',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_review_time := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND COLUMN_NAME = 'review_time'
);
SET @sql := IF(@has_review_time = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN review_time DATETIME NULL COMMENT ''整改复查时间'' AFTER reviewer_name',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_review_comment := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND COLUMN_NAME = 'review_comment'
);
SET @sql := IF(@has_review_comment = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN review_comment VARCHAR(1000) NULL COMMENT ''整改复查意见'' AFTER review_time',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_reject_count := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND COLUMN_NAME = 'reject_count'
);
SET @sql := IF(@has_reject_count = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN reject_count INT NOT NULL DEFAULT 0 COMMENT ''复查退回次数'' AFTER review_comment',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_recheck_deadline := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND COLUMN_NAME = 'recheck_deadline'
);
SET @sql := IF(@has_recheck_deadline = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN recheck_deadline DATE NULL COMMENT ''复查退回后的再次整改期限'' AFTER reject_count',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_escalation_status := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND COLUMN_NAME = 'escalation_status'
);
SET @sql := IF(@has_escalation_status = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN escalation_status VARCHAR(20) NOT NULL DEFAULT ''NONE'' COMMENT ''升级提醒状态: NONE/REMINDED/ESCALATED'' AFTER recheck_deadline',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_escalation_time := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND COLUMN_NAME = 'escalation_time'
);
SET @sql := IF(@has_escalation_time = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN escalation_time DATETIME NULL COMMENT ''最近一次升级提醒时间'' AFTER escalation_status',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_escalation_note := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND COLUMN_NAME = 'escalation_note'
);
SET @sql := IF(@has_escalation_note = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN escalation_note VARCHAR(1000) NULL COMMENT ''最近一次升级提醒说明'' AFTER escalation_time',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_rectification_category := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND INDEX_NAME = 'idx_rectification_category'
);
SET @sql := IF(@has_idx_rectification_category = 0,
    'ALTER TABLE inspection_rectification ADD KEY idx_rectification_category (project_id, problem_category, status)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_rectification_escalation := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_rectification'
      AND INDEX_NAME = 'idx_rectification_escalation'
);
SET @sql := IF(@has_idx_rectification_escalation = 0,
    'ALTER TABLE inspection_rectification ADD KEY idx_rectification_escalation (project_id, status, deadline, escalation_status)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS inspection_rectification_review_log (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '整改日志ID',
    rectification_id      BIGINT NOT NULL COMMENT '整改任务ID',
    project_id            BIGINT NOT NULL COMMENT '项目ID',
    electric_box_id       BIGINT NOT NULL COMMENT '电箱ID',
    inspection_record_id  BIGINT NULL COMMENT '检查记录ID',
    action_type           VARCHAR(40) NOT NULL COMMENT '动作: COMPLETE/CLOSE/REJECT/ASSIGN/ESCALATE',
    from_status           VARCHAR(30) NULL COMMENT '原整改状态',
    to_status             VARCHAR(30) NULL COMMENT '新整改状态',
    operator_id           BIGINT NULL COMMENT '操作人ID',
    operator_name         VARCHAR(50) NULL COMMENT '操作人姓名',
    comment               VARCHAR(1000) NULL COMMENT '操作说明',
    photo_file_ids        VARCHAR(500) NULL COMMENT '关联照片ID',
    deleted               TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_rectification_log_task (rectification_id, create_time),
    KEY idx_rectification_log_project (project_id, create_time),
    KEY idx_rectification_log_action (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查整改闭环留痕表';

UPDATE inspection_rectification
SET reject_count = COALESCE(reject_count, 0),
    escalation_status = COALESCE(escalation_status, 'NONE')
WHERE deleted = 0;
