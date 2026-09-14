-- Additive migration. No ordinary role grants; do not re-enable withdrawn catalog entries.
CREATE TABLE IF NOT EXISTS safety_committee_record (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL, inspector_id BIGINT NOT NULL, inspector_name VARCHAR(100) NOT NULL,
 inspected_at DATETIME(6) NOT NULL, category VARCHAR(50) NOT NULL, conclusion VARCHAR(2000) NOT NULL DEFAULT '',
 version INT NOT NULL DEFAULT 1, request_key VARCHAR(64) NOT NULL, request_hash CHAR(64) NOT NULL,
 create_time DATETIME(6) NOT NULL, update_time DATETIME(6) NOT NULL,
 UNIQUE KEY uk_committee_request(project_id,inspector_id,request_key),
 KEY idx_committee_project_date(project_id,inspected_at,id),
 KEY idx_committee_category(project_id,category,inspected_at,id), KEY idx_committee_inspector(inspector_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS safety_committee_attachment (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 project_id BIGINT NOT NULL, record_id BIGINT NULL, target_record_id BIGINT NULL,
 draft_key VARCHAR(64) NOT NULL, uploader_id BIGINT NOT NULL, file_id BIGINT NOT NULL,
 upload_key VARCHAR(64) NOT NULL, status VARCHAR(20) NOT NULL, sort_order INT NOT NULL DEFAULT 0,
 preview_kind VARCHAR(20) NOT NULL, preview_status VARCHAR(20) NOT NULL, preview_file_id BIGINT NULL,
 attempts INT NOT NULL DEFAULT 0, worker_id VARCHAR(64) NULL, lease_until DATETIME NULL,
 failure_message VARCHAR(500) NULL, expires_at DATETIME NOT NULL,
 create_time DATETIME(6) NOT NULL, update_time DATETIME(6) NOT NULL,
 UNIQUE KEY uk_committee_upload(upload_key), UNIQUE KEY uk_committee_file(file_id),
 KEY idx_committee_record(record_id,status,sort_order), KEY idx_committee_attachment_project(project_id),
 KEY idx_committee_preview(preview_status,lease_until,id), KEY idx_committee_staging(status,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS safety_committee_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, project_id BIGINT NOT NULL, record_id BIGINT NOT NULL,
 operator_id BIGINT NOT NULL, operator_name VARCHAR(100) NOT NULL, action VARCHAR(30) NOT NULL,
 before_json LONGTEXT NULL, after_json LONGTEXT NULL, create_time DATETIME(6) NOT NULL,
 KEY idx_committee_log_record(record_id,id), KEY idx_committee_log_project(project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SET @committee_seed = NOT EXISTS (SELECT 1 FROM sys_data_migration WHERE migration_key='20260914_SAFETY_COMMITTEE_V1');
INSERT INTO sys_menu(parent_id,client_type,menu_code,menu_name,resource_type,route_path,permission_code,sort_order,visible,enabled,builtin,deleted)
SELECT NULL,'WEB','WEB_SAFETY_COMMITTEE','安委会巡检','MENU','SAFETY_COMMITTEE','safety_committee.view',45,1,1,1,0
WHERE @committee_seed=1 ON DUPLICATE KEY UPDATE menu_code=VALUES(menu_code);
INSERT INTO sys_menu(parent_id,client_type,menu_code,menu_name,resource_type,route_path,permission_code,sort_order,visible,enabled,builtin,deleted)
SELECT NULL,'MINI_PROGRAM','MINI_SAFETY_COMMITTEE','安委会巡检','MENU','/pages/safety-committee/index','safety_committee.view',45,1,1,1,0
WHERE @committee_seed=1 ON DUPLICATE KEY UPDATE menu_code=VALUES(menu_code);
INSERT INTO sys_menu(parent_id,client_type,menu_code,menu_name,resource_type,route_path,permission_code,sort_order,visible,enabled,builtin,deleted)
SELECT (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_code='WEB_SAFETY_COMMITTEE' AND deleted=0 LIMIT 1) committee_parent),'WEB','SAFETY_COMMITTEE_RECORDS','巡检记录','TAB','SAFETY_COMMITTEE_RECORDS','safety_committee.view',1,1,1,1,0
WHERE @committee_seed=1 ON DUPLICATE KEY UPDATE menu_code=VALUES(menu_code);
INSERT INTO sys_permission(permission_code,permission_name,module_code,description,enabled,builtin,deleted)
SELECT 'safety_committee.view','查看项目巡检记录','WEB_SAFETY_COMMITTEE','安委会巡检独立权限，不继承其他巡检模块',1,1,0
WHERE @committee_seed=1 ON DUPLICATE KEY UPDATE permission_code=VALUES(permission_code);
INSERT INTO sys_permission(permission_code,permission_name,module_code,description,enabled,builtin,deleted)
SELECT 'safety_committee.submit','上报安委会巡检','WEB_SAFETY_COMMITTEE','安委会巡检独立权限，不继承其他巡检模块',1,1,0
WHERE @committee_seed=1 ON DUPLICATE KEY UPDATE permission_code=VALUES(permission_code);
INSERT INTO sys_permission(permission_code,permission_name,module_code,description,enabled,builtin,deleted)
SELECT 'safety_committee.edit_own','修改本人巡检记录','WEB_SAFETY_COMMITTEE','安委会巡检独立权限，不继承其他巡检模块',1,1,0
WHERE @committee_seed=1 ON DUPLICATE KEY UPDATE permission_code=VALUES(permission_code);
INSERT IGNORE INTO sys_data_migration(migration_key) VALUES ('20260914_SAFETY_COMMITTEE_V1');
