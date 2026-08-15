-- 场内管理 V1.2：门卫室长期访客登记码、免审批登记及24小时放行页。
-- 非破坏增量迁移；不修改现有单次邀请、人员或常用资料数据。
-- 执行前必须备份数据库，并确保数据库迁移先于新版后端发布。

CREATE TABLE IF NOT EXISTS site_guard_visit_qr (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    scene_token_hash CHAR(64) NOT NULL,
    scene_token_encrypted VARCHAR(512) NOT NULL,
    qr_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED/ROTATED',
    qr_version INT NOT NULL DEFAULT 1,
    current_project_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND qr_status IN ('ENABLED', 'DISABLED') THEN project_id ELSE NULL END
    ) STORED,
    created_by_id BIGINT NOT NULL,
    created_by_name VARCHAR(50) NOT NULL,
    updated_by_id BIGINT NOT NULL,
    updated_by_name VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_guard_visit_qr_scene (scene_token_hash),
    UNIQUE KEY uk_site_guard_visit_qr_current_project (current_project_id),
    KEY idx_site_guard_visit_qr_project (project_id, qr_version, deleted),
    KEY idx_site_guard_visit_qr_status (project_id, qr_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目门卫室长期访客登记码';

CREATE TABLE IF NOT EXISTS site_guard_visit_registration (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    registration_no VARCHAR(40) NOT NULL,
    project_id BIGINT NOT NULL,
    guard_qr_id BIGINT NOT NULL,
    wechat_app_id VARCHAR(64) NOT NULL,
    visitor_identity_hash CHAR(64) NOT NULL COMMENT 'AppID与OpenID经用途隔离HMAC生成的身份指纹',
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/VOIDED，EXPIRED由valid_until派生',
    visitor_company VARCHAR(200) NOT NULL,
    contact_name VARCHAR(50) NOT NULL,
    contact_phone_encrypted VARCHAR(512) NOT NULL,
    visitor_count INT NOT NULL DEFAULT 1,
    travel_mode VARCHAR(20) NOT NULL,
    vehicle_plate VARCHAR(20),
    visitor_remark VARCHAR(500),
    source_profile_id BIGINT,
    privacy_agreed_time DATETIME NOT NULL,
    registered_time DATETIME NOT NULL,
    valid_until DATETIME NOT NULL,
    void_reason VARCHAR(300),
    voided_by_id BIGINT,
    voided_by_name VARCHAR(50),
    voided_time DATETIME,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_guard_visit_registration_no (registration_no),
    KEY idx_site_guard_visit_registration_project_time (project_id, registered_time, deleted),
    KEY idx_site_guard_visit_registration_status (project_id, status, valid_until, deleted),
    KEY idx_site_guard_visit_registration_identity (project_id, wechat_app_id, visitor_identity_hash, status, valid_until, deleted),
    KEY idx_site_guard_visit_registration_qr (guard_qr_id, registered_time, deleted),
    KEY idx_site_guard_visit_registration_vehicle (project_id, vehicle_plate, deleted),
    KEY idx_site_guard_visit_registration_profile (source_profile_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门卫室免审批访客登记';

CREATE TABLE IF NOT EXISTS site_guard_visit_person (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    person_type VARCHAR(20) NOT NULL COMMENT 'CONTACT/COMPANION',
    person_company VARCHAR(200),
    person_name VARCHAR(50),
    phone_encrypted VARCHAR(512),
    sort_order INT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_site_guard_visit_person_registration (registration_id, deleted, sort_order),
    KEY idx_site_guard_visit_person_project (project_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门卫室访客登记人员明细';

CREATE TABLE IF NOT EXISTS site_guard_visit_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    registration_id BIGINT,
    project_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL COMMENT 'REGISTER/UPDATE/VOID/EXPORT',
    operator_id BIGINT,
    operator_name VARCHAR(50) NOT NULL,
    before_snapshot_encrypted LONGTEXT,
    after_snapshot_encrypted LONGTEXT,
    comment VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_site_guard_visit_audit_registration (registration_id, create_time),
    KEY idx_site_guard_visit_audit_project (project_id, action_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门卫室访客登记加密审计';

INSERT IGNORE INTO sys_data_migration(migration_key)
VALUES ('20260814_SITE_ACCESS_GUARD_VISITOR_REGISTRATION_V1');

-- 轮换仅将旧码标记为 ROTATED 并创建新码，以便旧 scene 明确返回 HTTP 410。
-- 回滚仅限应用已回退且确认无须保留门卫登记数据后人工执行；含敏感密文的数据表禁止直接清空。
