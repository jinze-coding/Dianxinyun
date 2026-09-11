-- Additive, rerunnable. Does not reset existing role grants or re-enable permissions.
CREATE TABLE IF NOT EXISTS seal_form_export_job (
  id BIGINT NOT NULL AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  requested_by_id BIGINT NOT NULL,
  requested_by_name VARCHAR(100) NOT NULL,
  request_key VARCHAR(64) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  selection_mode VARCHAR(20) NOT NULL,
  status VARCHAR(20) NOT NULL,
  application_count INT NOT NULL,
  processed_count INT NOT NULL DEFAULT 0,
  page_count INT NOT NULL DEFAULT 0,
  attempts INT NOT NULL DEFAULT 0,
  lease_owner VARCHAR(64) NULL,
  lease_until DATETIME NULL,
  file_resource_id BIGINT NULL,
  file_name VARCHAR(200) NULL,
  error_message VARCHAR(500) NULL,
  expires_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_seal_form_export_request (requested_by_id, request_key),
  KEY idx_seal_form_export_queue (status, lease_until, create_time),
  KEY idx_seal_form_export_owner (project_id, requested_by_id, create_time),
  KEY idx_seal_form_export_expiry (status, expires_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS seal_form_export_item (
  id BIGINT NOT NULL AUTO_INCREMENT,
  job_id BIGINT NOT NULL,
  project_id BIGINT NOT NULL,
  application_id BIGINT NOT NULL,
  item_order INT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_seal_form_export_application (job_id, application_id),
  UNIQUE KEY uk_seal_form_export_order (job_id, item_order),
  KEY idx_seal_form_export_project (project_id),
  KEY idx_seal_form_export_application (application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_permission(permission_code, permission_name, module_code, description, enabled, builtin, deleted)
SELECT 'seal.application.export', '导出用印申请单', 'WEB_DOCUMENT',
       '导出有权查看的已通过申请单，支持单份和合并PDF，不扩大查看范围', 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_data_migration WHERE migration_key = '20260911_SEAL_FORM_MERGE_EXPORT_V1')
ON DUPLICATE KEY UPDATE permission_code = 'seal.application.export';

INSERT IGNORE INTO sys_data_migration(migration_key) VALUES ('20260911_SEAL_FORM_MERGE_EXPORT_V1');
