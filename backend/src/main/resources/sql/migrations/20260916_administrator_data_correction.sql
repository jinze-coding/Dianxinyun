-- Additive correction journal. Existing business records and permissions are preserved.
CREATE TABLE IF NOT EXISTS sys_data_correction_log (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 project_id BIGINT NOT NULL,
 target_type VARCHAR(50) NOT NULL,
 target_id BIGINT NOT NULL,
 target_label VARCHAR(200) NOT NULL,
 operator_id BIGINT NOT NULL,
 operator_name VARCHAR(100) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 request_key VARCHAR(64) NOT NULL,
 preview_token VARCHAR(64) NOT NULL,
 before_snapshot_encrypted LONGTEXT NOT NULL,
 after_snapshot_encrypted LONGTEXT NOT NULL,
 create_time DATETIME(6) NOT NULL,
 UNIQUE KEY uk_correction_request(operator_id,request_key),
 UNIQUE KEY uk_correction_preview(preview_token),
 KEY idx_correction_target(target_type,target_id,id),
 KEY idx_correction_project(project_id,create_time,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_data_correction_attachment (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 correction_id BIGINT NULL,
 project_id BIGINT NOT NULL,
 target_type VARCHAR(50) NOT NULL,
 target_id BIGINT NOT NULL,
 slot_key VARCHAR(50) NOT NULL,
 file_id BIGINT NOT NULL,
 original_file_id BIGINT NULL,
 phase VARCHAR(16) NOT NULL COMMENT 'PENDING, BEFORE, AFTER',
 operator_id BIGINT NOT NULL,
 upload_key VARCHAR(64) NULL,
 file_name VARCHAR(255) NOT NULL,
 file_sha256 VARCHAR(64) NULL,
 expires_at DATETIME(6) NULL,
 create_time DATETIME(6) NOT NULL,
 UNIQUE KEY uk_correction_upload(upload_key),
 KEY idx_correction_file(file_id,correction_id),
 KEY idx_correction_file_target(target_type,target_id,original_file_id,file_sha256),
 KEY idx_correction_files(correction_id,phase),
 KEY idx_correction_staging(operator_id,phase,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission(permission_code,permission_name,module_code,description,enabled,builtin,deleted)
SELECT 'system.data.correct','管理员数据纠错','SYSTEM','仅平台管理员可纠正业务内容并查看修改前后历史',1,1,0
WHERE NOT EXISTS(SELECT 1 FROM sys_data_migration WHERE migration_key='20260916_ADMINISTRATOR_DATA_CORRECTION')
ON DUPLICATE KEY UPDATE permission_code=VALUES(permission_code);
INSERT INTO sys_menu(parent_id,client_type,menu_code,menu_name,resource_type,route_path,permission_code,sort_order,visible,enabled,builtin,deleted)
SELECT (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_code='WEB_SYSTEM' AND deleted=0 LIMIT 1) p),
 'WEB','SYSTEM_DATA_CORRECTION','数据纠错','TAB','SYSTEM_DATA_CORRECTION','system.data.correct',85,1,1,1,0
WHERE NOT EXISTS(SELECT 1 FROM sys_data_migration WHERE migration_key='20260916_ADMINISTRATOR_DATA_CORRECTION')
ON DUPLICATE KEY UPDATE menu_code=VALUES(menu_code);
INSERT IGNORE INTO sys_data_migration(migration_key) VALUES('20260916_ADMINISTRATOR_DATA_CORRECTION');
