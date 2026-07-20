-- 安全员复核时限、分配与留痕。
-- 仅增量变更，不包含 DROP TABLE，不要用 init.sql 替代。

USE dianxinyun;

SET @has_review_due_time := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_record'
      AND COLUMN_NAME = 'review_due_time'
);
SET @sql := IF(@has_review_due_time = 0,
    'ALTER TABLE inspection_record ADD COLUMN review_due_time DATETIME NULL COMMENT ''复核截止时间'' AFTER review_time',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_assigned_reviewer_id := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_record'
      AND COLUMN_NAME = 'assigned_reviewer_id'
);
SET @sql := IF(@has_assigned_reviewer_id = 0,
    'ALTER TABLE inspection_record ADD COLUMN assigned_reviewer_id BIGINT NULL COMMENT ''分配复核人ID'' AFTER review_due_time',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_assigned_reviewer_name := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_record'
      AND COLUMN_NAME = 'assigned_reviewer_name'
);
SET @sql := IF(@has_assigned_reviewer_name = 0,
    'ALTER TABLE inspection_record ADD COLUMN assigned_reviewer_name VARCHAR(50) NULL COMMENT ''分配复核人姓名'' AFTER assigned_reviewer_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_review_comment := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_record'
      AND COLUMN_NAME = 'review_comment'
);
SET @sql := IF(@has_review_comment = 0,
    'ALTER TABLE inspection_record ADD COLUMN review_comment VARCHAR(1000) NULL COMMENT ''最近一次复核意见'' AFTER assigned_reviewer_name',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_review_overdue := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_record'
      AND COLUMN_NAME = 'review_overdue'
);
SET @sql := IF(@has_review_overdue = 0,
    'ALTER TABLE inspection_record ADD COLUMN review_overdue TINYINT NOT NULL DEFAULT 0 COMMENT ''复核是否逾期: 0否 1是'' AFTER review_comment',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_idx_review_assignment := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'inspection_record'
      AND INDEX_NAME = 'idx_inspection_record_review_assignment'
);
SET @sql := IF(@has_idx_review_assignment = 0,
    'ALTER TABLE inspection_record ADD KEY idx_inspection_record_review_assignment (project_id, status, assigned_reviewer_id, review_due_time)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS inspection_review_log (
    id                    BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '复核日志ID',
    record_id             BIGINT NOT NULL COMMENT '检查记录ID',
    project_id            BIGINT NOT NULL COMMENT '项目ID',
    electric_box_id       BIGINT NOT NULL COMMENT '电箱ID',
    action_type           VARCHAR(40) NOT NULL COMMENT '动作: ASSIGN/REASSIGN/UNASSIGN/PASS/REJECT/RECTIFY/OVERDUE',
    from_reviewer_id      BIGINT NULL COMMENT '原复核人ID',
    from_reviewer_name    VARCHAR(50) NULL COMMENT '原复核人姓名',
    to_reviewer_id        BIGINT NULL COMMENT '新复核人ID',
    to_reviewer_name      VARCHAR(50) NULL COMMENT '新复核人姓名',
    operator_id           BIGINT NULL COMMENT '操作人ID',
    operator_name         VARCHAR(50) NULL COMMENT '操作人姓名',
    comment               VARCHAR(1000) NULL COMMENT '复核/分配意见',
    deleted               TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_review_log_record (record_id, create_time),
    KEY idx_review_log_project (project_id, create_time),
    KEY idx_review_log_action (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查记录复核留痕表';

UPDATE inspection_record r
LEFT JOIN electric_box eb ON eb.id = r.electric_box_id AND eb.deleted = 0
SET r.review_due_time = COALESCE(r.review_due_time, DATE_ADD(COALESCE(r.create_time, NOW()), INTERVAL 24 HOUR)),
    r.assigned_reviewer_id = COALESCE(r.assigned_reviewer_id, eb.safety_manager_id),
    r.assigned_reviewer_name = COALESCE(r.assigned_reviewer_name, eb.safety_manager_name),
    r.review_overdue = CASE
        WHEN r.status = 'REVIEW_PENDING'
         AND COALESCE(r.review_due_time, DATE_ADD(COALESCE(r.create_time, NOW()), INTERVAL 24 HOUR)) < NOW()
        THEN 1
        ELSE COALESCE(r.review_overdue, 0)
    END
WHERE r.deleted = 0;
