-- 会议资料独立留档；不改变旧邀请、人员或文件。
SET @meeting_material_preflight = IF(EXISTS(SELECT 1 FROM sys_data_migration
 WHERE migration_key='20260910_VISITOR_REUSE_GUARD_MEETING_V1'),
 'SELECT 1','SELECT MEETING_MATERIALS_REQUIRES_VISITOR_REUSE_MIGRATION');
PREPARE stmt FROM @meeting_material_preflight; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS site_meeting_material (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 invitation_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 title VARCHAR(200) NOT NULL, category VARCHAR(30) NOT NULL DEFAULT 'OTHER',
 description VARCHAR(1000) NOT NULL DEFAULT '',
 current_version_id BIGINT NULL, published_version_id BIGINT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', version INT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 KEY idx_meeting_material_list (invitation_id,status,category,update_time),
 KEY idx_meeting_material_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS site_meeting_material_version (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 material_id BIGINT NOT NULL, invitation_id BIGINT NOT NULL, project_id BIGINT NOT NULL,
 version_no INT NOT NULL, file_id BIGINT NOT NULL,
 public_code CHAR(32) NOT NULL, upload_key CHAR(64) NOT NULL,
 uploader_id BIGINT NOT NULL, uploader_name VARCHAR(100) NOT NULL,
 change_note VARCHAR(500) NOT NULL DEFAULT '',
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_material_version (material_id,version_no),
 UNIQUE KEY uk_material_public_code (public_code),
 UNIQUE KEY uk_material_upload (upload_key),
 UNIQUE KEY uk_material_file (file_id),
 KEY idx_material_version_invitation (invitation_id), KEY idx_material_version_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS site_meeting_material_preview (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 version_id BIGINT NOT NULL, invitation_id BIGINT NOT NULL,
 kind VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
 file_id BIGINT NULL, failure_message VARCHAR(300) NULL,
 worker_id VARCHAR(64) NULL, lease_until DATETIME NULL, attempts INT NOT NULL DEFAULT 0,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_material_preview_version (version_id),
 KEY idx_material_preview_queue (status,lease_until,id),
 KEY idx_material_preview_invitation (invitation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_data_migration(migration_key) VALUES ('20260911_MEETING_MATERIALS_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key=incoming.migration_key;
