-- Project availability never changes shared role grants. Re-runs preserve saved choices.
CREATE TABLE IF NOT EXISTS project_business_module (
 project_id BIGINT NOT NULL,
 module_code VARCHAR(32) NOT NULL,
 enabled TINYINT NOT NULL DEFAULT 1,
 version BIGINT NOT NULL DEFAULT 1,
 activated_at DATETIME(6) NULL COMMENT 'Lower bound only after deliberate re-enable',
 updated_by BIGINT NULL,
 update_time DATETIME(6) NOT NULL,
 PRIMARY KEY(project_id,module_code), KEY idx_project_module_enabled(module_code,enabled,project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO project_business_module(project_id,module_code,enabled,version,update_time)
SELECT p.id,m.code,1,1,NOW(6) FROM project_info p CROSS JOIN (
 SELECT 'SITE_ACCESS' code UNION ALL SELECT 'DOCUMENT' UNION ALL SELECT 'INSPECTION'
 UNION ALL SELECT 'QUALITY' UNION ALL SELECT 'SAFETY_COMMITTEE'
) m WHERE p.deleted=0;
INSERT INTO sys_menu(parent_id,client_type,menu_code,menu_name,resource_type,route_path,sort_order,visible,enabled,builtin,deleted)
SELECT (SELECT id FROM (SELECT id FROM sys_menu WHERE menu_code='WEB_SYSTEM' AND deleted=0 LIMIT 1) parent_menu),
 'WEB','SYSTEM_PROJECT_MODULE','项目模块','TAB','SYSTEM_PROJECT_MODULE',45,1,1,1,0
WHERE NOT EXISTS(SELECT 1 FROM sys_data_migration WHERE migration_key='20260914_PROJECT_BUSINESS_MODULES')
ON DUPLICATE KEY UPDATE menu_code=VALUES(menu_code);
INSERT IGNORE INTO sys_data_migration(migration_key) VALUES('20260914_PROJECT_BUSINESS_MODULES');
