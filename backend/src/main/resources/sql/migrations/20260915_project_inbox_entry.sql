-- Display preference only: keep existing tasks, notifications and business modules.
SET @project_inbox_entry_sql = IF((SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='project_info' AND column_name='inbox_entry_visible')=0,
 'ALTER TABLE project_info ADD COLUMN inbox_entry_visible TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''当前项目显示待办消息入口，不改变业务流程''', 'SELECT 1');
PREPARE project_inbox_entry_stmt FROM @project_inbox_entry_sql;
EXECUTE project_inbox_entry_stmt;
DEALLOCATE PREPARE project_inbox_entry_stmt;
INSERT IGNORE INTO sys_data_migration(migration_key) VALUES ('20260915_PROJECT_INBOX_ENTRY');
