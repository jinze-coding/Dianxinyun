-- Additive and repeatable. Does not create accounts or change existing credentials/authorizations.
SET @import_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='must_change_password'), 'SELECT 1', 'ALTER TABLE sys_user ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0');
PREPARE import_stmt FROM @import_ddl; EXECUTE import_stmt; DEALLOCATE PREPARE import_stmt;
SET @import_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sys_user' AND column_name='temporary_password_expires_at'), 'SELECT 1', 'ALTER TABLE sys_user ADD COLUMN temporary_password_expires_at DATETIME(6) NULL');
PREPARE import_stmt FROM @import_ddl; EXECUTE import_stmt; DEALLOCATE PREPARE import_stmt;

CREATE TABLE IF NOT EXISTS system_user_import_batch (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 created_by BIGINT NOT NULL, created_by_name VARCHAR(100) NOT NULL,
 status VARCHAR(20) NOT NULL, person_count INT NOT NULL DEFAULT 0,
 new_count INT NOT NULL DEFAULT 0, skipped_count INT NOT NULL DEFAULT 0,
 error_count INT NOT NULL DEFAULT 0, prepared_count INT NOT NULL DEFAULT 0,
 message VARCHAR(500) NULL, request_key VARCHAR(80) NULL,
 lease_token VARCHAR(40) NULL, lease_until DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL, completed_at DATETIME(6) NULL,
 KEY idx_user_import_jobs(status,lease_until,id), KEY idx_user_import_owner(created_by,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_user_import_item (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, batch_id BIGINT NOT NULL,
 excel_row INT NOT NULL, real_name VARCHAR(50) NULL, phone VARCHAR(20) NULL,
 project_id BIGINT NULL, role_id BIGINT NULL,
 project_label VARCHAR(300) NULL, role_label VARCHAR(150) NULL,
 status VARCHAR(20) NOT NULL, message VARCHAR(500) NULL, user_id BIGINT NULL,
 credential_version INT NULL, credential_owner_id BIGINT NULL,
 credential_cipher VARCHAR(300) NULL COMMENT 'Short-lived AES-GCM handout, never a user-chosen password',
 download_until DATETIME(6) NULL, password_expires_at DATETIME(6) NULL,
 UNIQUE KEY uk_user_import_row(batch_id,excel_row), KEY idx_user_import_user(user_id,id),
 KEY idx_user_import_credential_expiry(download_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO sys_data_migration(migration_key) VALUES('20260914_USER_BATCH_IMPORT');
