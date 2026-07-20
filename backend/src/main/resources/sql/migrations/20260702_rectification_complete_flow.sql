USE dianxinyun;

SET @schema_name = DATABASE();

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN completed_time DATETIME NULL COMMENT ''整改提交时间'' AFTER rectification_photo_file_ids',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'inspection_rectification'
    AND column_name = 'completed_time'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN review_time DATETIME NULL COMMENT ''复查时间'' AFTER reviewer_name',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'inspection_rectification'
    AND column_name = 'review_time'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN review_comment VARCHAR(1000) NULL COMMENT ''复查意见或退回原因'' AFTER review_time',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'inspection_rectification'
    AND column_name = 'review_comment'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN reject_count INT NOT NULL DEFAULT 0 COMMENT ''复查退回次数'' AFTER review_comment',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'inspection_rectification'
    AND column_name = 'reject_count'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE inspection_rectification ADD COLUMN recheck_deadline DATE NULL COMMENT ''退回后再次提交复查截止日期'' AFTER reject_count',
    'SELECT 1'
  )
  FROM information_schema.columns
  WHERE table_schema = @schema_name
    AND table_name = 'inspection_rectification'
    AND column_name = 'recheck_deadline'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS inspection_rectification_review_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  rectification_id BIGINT NOT NULL COMMENT '整改任务ID',
  project_id BIGINT NOT NULL COMMENT '项目ID',
  electric_box_id BIGINT NULL COMMENT '电箱ID',
  inspection_record_id BIGINT NULL COMMENT '检查记录ID',
  action_type VARCHAR(32) NOT NULL COMMENT '动作：COMPLETE/CLOSE/REJECT/ASSIGN/REMIND/ESCALATE',
  from_status VARCHAR(32) NULL COMMENT '变更前状态',
  to_status VARCHAR(32) NULL COMMENT '变更后状态',
  operator_id BIGINT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64) NULL COMMENT '操作人姓名',
  comment VARCHAR(1000) NULL COMMENT '整改说明、复查意见或退回原因',
  photo_file_ids VARCHAR(500) NULL COMMENT '整改照片文件ID，逗号分隔',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  KEY idx_rectification_log_task (rectification_id, deleted, create_time),
  KEY idx_rectification_log_project (project_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查整改复查留痕';

UPDATE inspection_rectification
SET completed_time = update_time
WHERE completed_time IS NULL
  AND status IN ('COMPLETED', 'CLOSED');

UPDATE inspection_rectification
SET review_time = close_time
WHERE review_time IS NULL
  AND close_time IS NOT NULL;

UPDATE inspection_rectification
SET reject_count = 0
WHERE reject_count IS NULL;
