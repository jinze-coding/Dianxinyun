-- 质量周检：服务端共享草稿、整批问题提交与历史单问题兼容。
-- 非破坏增量迁移；执行前必须备份数据库，并确保迁移先于新版后端发布。

CREATE TABLE IF NOT EXISTS quality_weekly_inspection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '质量周检ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    inspection_no VARCHAR(40) COMMENT '正式周检编号，草稿为空',
    week_start DATE NOT NULL COMMENT '自然周周一',
    inspection_date DATE COMMENT '实际检查日期',
    conclusion VARCHAR(1000) COMMENT '周检结论',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED',
    submitted_issue_count INT NOT NULL DEFAULT 0 COMMENT '提交时问题数快照',
    created_by_id BIGINT NOT NULL COMMENT '草稿创建人',
    created_by_name VARCHAR(50) COMMENT '草稿创建人姓名快照',
    last_edited_by_id BIGINT NOT NULL COMMENT '最近编辑人',
    last_edited_by_name VARCHAR(50) COMMENT '最近编辑人姓名快照',
    submitted_by_id BIGINT COMMENT '提交人',
    submitted_by_name VARCHAR(50) COMMENT '提交人姓名快照',
    submitted_time DATETIME COMMENT '实际提交时间',
    version INT NOT NULL DEFAULT 0 COMMENT '共享草稿并发版本',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quality_weekly_project_week (project_id, week_start),
    UNIQUE KEY uk_quality_weekly_inspection_no (inspection_no),
    KEY idx_quality_weekly_project_status (project_id, status, week_start),
    KEY idx_quality_weekly_submitted_time (project_id, submitted_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量周检共享草稿与正式记录';

CREATE TABLE IF NOT EXISTS quality_weekly_inspection_draft_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '周检问题草稿ID',
    project_id BIGINT NOT NULL COMMENT '项目ID，服务端从父周检写入',
    inspection_id BIGINT NOT NULL COMMENT '质量周检ID',
    item_key VARCHAR(100) NOT NULL COMMENT '客户端稳定问题键',
    item_order INT NOT NULL COMMENT '问题顺序1-50',
    title VARCHAR(200) COMMENT '问题标题，草稿允许为空',
    location VARCHAR(200) COMMENT '问题位置',
    description VARCHAR(1000) COMMENT '问题描述',
    severity VARCHAR(20) COMMENT 'NORMAL/WARNING/DANGER',
    assignee_id BIGINT COMMENT '整改负责人',
    assignee_name VARCHAR(50) COMMENT '整改负责人姓名快照',
    deadline DATE COMMENT '闭环期限',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quality_weekly_item_key (inspection_id, item_key),
    UNIQUE KEY uk_quality_weekly_item_order (inspection_id, item_order),
    KEY idx_quality_weekly_item_project (project_id, inspection_id),
    KEY idx_quality_weekly_item_inspection (inspection_id, item_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量周检问题共享草稿';

SET @quality_weekly_issue_parent_column_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quality_issue'
          AND COLUMN_NAME = 'weekly_inspection_id'
    ),
    'SELECT 1',
    'ALTER TABLE quality_issue ADD COLUMN weekly_inspection_id BIGINT NULL COMMENT ''所属质量周检ID，历史独立问题为空'' AFTER project_id'
);
PREPARE quality_weekly_issue_parent_column_stmt FROM @quality_weekly_issue_parent_column_sql;
EXECUTE quality_weekly_issue_parent_column_stmt;
DEALLOCATE PREPARE quality_weekly_issue_parent_column_stmt;

SET @quality_weekly_issue_order_column_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quality_issue'
          AND COLUMN_NAME = 'inspection_item_order'
    ),
    'SELECT 1',
    'ALTER TABLE quality_issue ADD COLUMN inspection_item_order INT NULL COMMENT ''周检内问题顺序'' AFTER weekly_inspection_id'
);
PREPARE quality_weekly_issue_order_column_stmt FROM @quality_weekly_issue_order_column_sql;
EXECUTE quality_weekly_issue_order_column_stmt;
DEALLOCATE PREPARE quality_weekly_issue_order_column_stmt;

SET @quality_weekly_issue_parent_index_sql = IF(
    EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'quality_issue'
          AND INDEX_NAME = 'idx_quality_issue_weekly'
    ),
    'SELECT 1',
    'ALTER TABLE quality_issue ADD KEY idx_quality_issue_weekly (weekly_inspection_id, inspection_item_order, deleted)'
);
PREPARE quality_weekly_issue_parent_index_stmt FROM @quality_weekly_issue_parent_index_sql;
EXECUTE quality_weekly_issue_parent_index_stmt;
DEALLOCATE PREPARE quality_weekly_issue_parent_index_stmt;

-- 只调整当前仍启用的正式菜单显示名；不得插入、启用、显示或恢复任何历史菜单。
UPDATE sys_menu
SET menu_name = '质量周检'
WHERE menu_code = 'WEB_QUALITY' AND enabled = 1 AND deleted = 0;

UPDATE sys_menu
SET menu_name = '质量周检'
WHERE menu_code = 'MINI_QUALITY' AND enabled = 1 AND deleted = 0;

UPDATE sys_menu
SET menu_name = '周检记录'
WHERE menu_code = 'QUALITY_ISSUES' AND enabled = 1 AND deleted = 0;

INSERT IGNORE INTO sys_data_migration(migration_key)
VALUES ('20260826_QUALITY_WEEKLY_INSPECTION_V1');
