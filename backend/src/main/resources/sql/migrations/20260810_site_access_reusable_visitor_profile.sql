-- 场内管理 V1.1：外访人员微信静默身份、同项目常用资料复用。
-- 非破坏增量迁移；历史邀请继续保留独立快照，常用资料停用不回写历史记录。

CREATE TABLE IF NOT EXISTS site_visitor_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    profile_code VARCHAR(40) NOT NULL,
    project_id BIGINT NOT NULL,
    wechat_app_id VARCHAR(64) NOT NULL,
    owner_openid_encrypted VARCHAR(512) NOT NULL,
    owner_openid_hash CHAR(64) NOT NULL,
    profile_name VARCHAR(100) NOT NULL,
    visitor_company VARCHAR(200) NOT NULL,
    contact_name VARCHAR(50) NOT NULL,
    contact_phone_encrypted VARCHAR(512) NOT NULL,
    visitor_count INT NOT NULL DEFAULT 1,
    travel_mode VARCHAR(20) NOT NULL,
    vehicle_plate VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    privacy_agreed_time DATETIME NOT NULL,
    last_used_time DATETIME,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_visitor_profile_code (profile_code),
    KEY idx_site_visitor_profile_owner (project_id, wechat_app_id, owner_openid_hash, status, deleted),
    KEY idx_site_visitor_profile_project (project_id, status, last_used_time, deleted),
    KEY idx_site_visitor_profile_vehicle (project_id, vehicle_plate, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外访人员同项目常用资料';

CREATE TABLE IF NOT EXISTS site_visitor_profile_person (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    person_type VARCHAR(20) NOT NULL COMMENT 'CONTACT/COMPANION',
    person_name VARCHAR(50) NOT NULL,
    id_card_encrypted VARCHAR(512) NOT NULL,
    id_card_hash CHAR(64) NOT NULL COMMENT '带用途隔离的HMAC-SHA256指纹',
    sort_order INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_site_visitor_profile_person (profile_id, deleted, sort_order),
    KEY idx_site_visitor_profile_person_project (project_id, id_card_hash, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常用资料逐人实名明细';

CREATE TABLE IF NOT EXISTS site_visitor_profile_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL COMMENT 'CREATE/USE/UPDATE/DISABLE/DELETE',
    operator_id BIGINT,
    operator_name VARCHAR(50) NOT NULL,
    before_snapshot_encrypted LONGTEXT,
    after_snapshot_encrypted LONGTEXT,
    comment VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_site_visitor_profile_audit_profile (profile_id, create_time),
    KEY idx_site_visitor_profile_audit_project (project_id, action_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常用资料加密审计';

SET @site_visit_source_profile_column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'site_visit_invitation'
      AND column_name = 'source_profile_id'
);
SET @site_visit_source_profile_sql = IF(
    @site_visit_source_profile_column_exists = 0,
    'ALTER TABLE site_visit_invitation ADD COLUMN source_profile_id BIGINT NULL AFTER visitor_remark',
    'SELECT 1'
);
PREPARE site_visit_source_profile_stmt FROM @site_visit_source_profile_sql;
EXECUTE site_visit_source_profile_stmt;
DEALLOCATE PREPARE site_visit_source_profile_stmt;

SET @site_visit_source_profile_index_exists = (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'site_visit_invitation'
      AND index_name = 'idx_site_visit_source_profile'
);
SET @site_visit_source_profile_index_sql = IF(
    @site_visit_source_profile_index_exists = 0,
    'ALTER TABLE site_visit_invitation ADD KEY idx_site_visit_source_profile (source_profile_id)',
    'SELECT 1'
);
PREPARE site_visit_source_profile_index_stmt FROM @site_visit_source_profile_index_sql;
EXECUTE site_visit_source_profile_index_stmt;
DEALLOCATE PREPARE site_visit_source_profile_index_stmt;

INSERT IGNORE INTO sys_data_migration(migration_key)
VALUES ('20260810_SITE_ACCESS_REUSABLE_VISITOR_PROFILE_V1');

-- 回滚仅限应用已回退且确认常用资料不再需要后人工执行；三张资料表含敏感字段密文，禁止直接清空。
