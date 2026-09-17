-- Administrator chooses one password per batch; existing accounts are unchanged.
SET @import_password_sql = IF((SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='system_user_import_batch' AND column_name='temporary_password_cipher')=0,
 'ALTER TABLE system_user_import_batch ADD COLUMN temporary_password_cipher TEXT NULL COMMENT ''待执行批次密码密文，终态清理''', 'SELECT 1');
PREPARE import_password_stmt FROM @import_password_sql;
EXECUTE import_password_stmt;
DEALLOCATE PREPARE import_password_stmt;
SET @import_password_sql = IF((SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='system_user_import_batch' AND column_name='confirmation_password_hash')=0,
 'ALTER TABLE system_user_import_batch ADD COLUMN confirmation_password_hash VARCHAR(100) NULL COMMENT ''确认幂等密码BCrypt摘要''', 'SELECT 1');
PREPARE import_password_stmt FROM @import_password_sql;
EXECUTE import_password_stmt;
DEALLOCATE PREPARE import_password_stmt;
INSERT IGNORE INTO sys_data_migration(migration_key) VALUES ('20260915_USER_IMPORT_ADMIN_PASSWORD');
