-- 场内管理 V1.3：共享会议邀请、微信身份隔离登记和逐组放行凭证。
-- 非破坏增量迁移；现有邀请全部保持 SINGLE，既有状态和人员快照不变。
-- 执行前必须备份数据库，并在隔离副本连续执行两次验证幂等性。

SET @add_site_visit_invite_type_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_visit_invitation' AND COLUMN_NAME = 'invite_type'),
    'SELECT 1',
    'ALTER TABLE site_visit_invitation ADD COLUMN invite_type VARCHAR(20) NOT NULL DEFAULT ''SINGLE'' COMMENT ''SINGLE/MEETING'' AFTER status'
);
PREPARE stmt FROM @add_site_visit_invite_type_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE site_visit_invitation SET invite_type = 'SINGLE'
WHERE invite_type IS NULL OR invite_type NOT IN ('SINGLE', 'MEETING');

SET @add_site_visit_invite_type_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_visit_invitation'
             AND INDEX_NAME = 'idx_site_visit_project_type_time'),
    'SELECT 1',
    'ALTER TABLE site_visit_invitation ADD INDEX idx_site_visit_project_type_time (project_id, invite_type, visit_start_time, deleted)'
);
PREPARE stmt FROM @add_site_visit_invite_type_index_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- IF NOT EXISTS 的幂等复跑会产生 1050 Note；只在这组受控建表期间关闭 Note，
-- Warning 与 Error 仍由 --show-warnings 严格上报。
SET @meeting_previous_sql_notes := @@sql_notes;
SET sql_notes = 0;

CREATE TABLE IF NOT EXISTS site_meeting_visit_registration (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    registration_no VARCHAR(40) NOT NULL,
    invitation_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    wechat_app_id VARCHAR(64) NOT NULL,
    visitor_identity_hash CHAR(64) NOT NULL COMMENT 'AppID与OpenID经会议登记用途隔离HMAC生成的身份指纹',
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED' COMMENT 'REGISTERED/VOIDED',
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
    void_reason VARCHAR(300),
    voided_by_id BIGINT,
    voided_by_name VARCHAR(50),
    voided_time DATETIME,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    active_identity_key VARCHAR(200) GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND status = 'REGISTERED'
             THEN CONCAT(invitation_id, ':', wechat_app_id, ':', visitor_identity_hash)
             ELSE NULL END
    ) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_meeting_visit_registration_no (registration_no),
    UNIQUE KEY uk_site_meeting_visit_active_identity (active_identity_key),
    KEY idx_site_meeting_visit_invitation_time (invitation_id, registered_time, deleted),
    KEY idx_site_meeting_visit_project_time (project_id, registered_time, deleted),
    KEY idx_site_meeting_visit_status (invitation_id, status, deleted),
    KEY idx_site_meeting_visit_vehicle (project_id, vehicle_plate, deleted),
    KEY idx_site_meeting_visit_profile (source_profile_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享会议邀请访客登记组';

CREATE TABLE IF NOT EXISTS site_meeting_visit_person (
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
    KEY idx_site_meeting_visit_person_registration (registration_id, deleted, sort_order),
    KEY idx_site_meeting_visit_person_project (project_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享会议邀请登记人员明细';

CREATE TABLE IF NOT EXISTS site_meeting_visit_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    registration_id BIGINT,
    invitation_id BIGINT COMMENT '单场操作关联邀请；跨会议批量导出时为空',
    project_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL COMMENT 'REGISTER/UPDATE/VOID/EXPORT',
    operator_id BIGINT,
    operator_name VARCHAR(50) NOT NULL,
    before_snapshot_encrypted LONGTEXT,
    after_snapshot_encrypted LONGTEXT,
    comment VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_site_meeting_visit_audit_registration (registration_id, create_time),
    KEY idx_site_meeting_visit_audit_invitation (invitation_id, create_time),
    KEY idx_site_meeting_visit_audit_project (project_id, action_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享会议邀请登记加密审计';

SET sql_notes = @meeting_previous_sql_notes;

INSERT INTO sys_data_migration(migration_key)
VALUES ('20260826_SITE_ACCESS_MEETING_INVITATION_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;

-- 回滚不得直接删除含敏感密文的登记表；如新版已产生数据，必须随数据库快照和应用版本整体恢复。
