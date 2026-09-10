-- 场内管理 V1.4：共享会议邀请现场签到、现场补录与定位留痕。
-- 非破坏增量迁移；历史会议登记统一标记为 INVITATION，不生成历史签到记录。
-- 执行前必须备份数据库，并在隔离副本连续执行两次验证幂等性。

SET @add_meeting_registration_source_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_meeting_visit_registration'
             AND COLUMN_NAME = 'registration_source'),
    'SELECT 1',
    'ALTER TABLE site_meeting_visit_registration ADD COLUMN registration_source VARCHAR(20) NOT NULL DEFAULT ''INVITATION'' COMMENT ''INVITATION/WALK_IN'' AFTER status'
);
PREPARE stmt FROM @add_meeting_registration_source_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE site_meeting_visit_registration
SET registration_source = 'INVITATION'
WHERE registration_source IS NULL OR registration_source NOT IN ('INVITATION', 'WALK_IN');

-- 后台可为无微信设备的现场人员补录登记组；公共登记仍由服务层强制写入 AppID 与身份摘要。
SET @make_meeting_app_id_nullable_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_meeting_visit_registration'
             AND COLUMN_NAME = 'wechat_app_id' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE site_meeting_visit_registration MODIFY COLUMN wechat_app_id VARCHAR(64) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @make_meeting_app_id_nullable_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @make_meeting_identity_nullable_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_meeting_visit_registration'
             AND COLUMN_NAME = 'visitor_identity_hash' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE site_meeting_visit_registration MODIFY COLUMN visitor_identity_hash CHAR(64) NULL COMMENT ''AppID与OpenID经会议登记用途隔离HMAC生成的身份指纹''',
    'SELECT 1'
);
PREPARE stmt FROM @make_meeting_identity_nullable_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_meeting_audit_person_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_meeting_visit_audit_log'
             AND COLUMN_NAME = 'person_id'),
    'SELECT 1',
    'ALTER TABLE site_meeting_visit_audit_log ADD COLUMN person_id BIGINT NULL AFTER registration_id'
);
PREPARE stmt FROM @add_meeting_audit_person_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_meeting_audit_qr_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_meeting_visit_audit_log'
             AND COLUMN_NAME = 'checkin_qr_id'),
    'SELECT 1',
    'ALTER TABLE site_meeting_visit_audit_log ADD COLUMN checkin_qr_id BIGINT NULL AFTER person_id'
);
PREPARE stmt FROM @add_meeting_audit_qr_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_meeting_audit_person_index_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_meeting_visit_audit_log'
             AND INDEX_NAME = 'idx_site_meeting_visit_audit_person'),
    'SELECT 1',
    'ALTER TABLE site_meeting_visit_audit_log ADD INDEX idx_site_meeting_visit_audit_person (person_id, create_time)'
);
PREPARE stmt FROM @add_meeting_audit_person_index_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @meeting_checkin_previous_sql_notes := @@sql_notes;
SET sql_notes = 0;

CREATE TABLE IF NOT EXISTS site_meeting_checkin_qr (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invitation_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    scene_token_hash CHAR(64) NOT NULL,
    scene_token_encrypted VARCHAR(512) NOT NULL,
    qr_status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED/ROTATED',
    qr_version INT NOT NULL DEFAULT 1,
    checkin_start_time DATETIME NOT NULL,
    checkin_end_time DATETIME NOT NULL,
    location_radius_meters INT NOT NULL DEFAULT 300,
    created_by_id BIGINT NOT NULL,
    created_by_name VARCHAR(50) NOT NULL,
    updated_by_id BIGINT NOT NULL,
    updated_by_name VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    current_invitation_key BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND qr_status IN ('ENABLED', 'DISABLED') THEN invitation_id ELSE NULL END
    ) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_meeting_checkin_scene_hash (scene_token_hash),
    UNIQUE KEY uk_site_meeting_checkin_current_invitation (current_invitation_key),
    KEY idx_site_meeting_checkin_project (project_id, checkin_start_time, checkin_end_time, deleted),
    KEY idx_site_meeting_checkin_invitation_version (invitation_id, qr_version, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享会议现场签到码';

CREATE TABLE IF NOT EXISTS site_meeting_attendance (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    invitation_id BIGINT NOT NULL,
    registration_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    checkin_qr_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'CHECKED_IN' COMMENT 'CHECKED_IN/REVOKED',
    checkin_method VARCHAR(20) NOT NULL COMMENT 'VENUE_QR/STAFF_MANUAL',
    checkin_time DATETIME NOT NULL,
    wechat_app_id VARCHAR(64),
    checkin_identity_hash CHAR(64),
    location_result VARCHAR(30) NOT NULL COMMENT 'IN_RANGE/OUT_OF_RANGE/UNAVAILABLE/NO_REFERENCE/MANUAL',
    distance_meters INT,
    accuracy_meters INT,
    reference_project_version INT,
    revoked_by_id BIGINT,
    revoked_by_name VARCHAR(50),
    revoked_time DATETIME,
    revoke_reason VARCHAR(300),
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_meeting_attendance_person (person_id),
    KEY idx_site_meeting_attendance_invitation (invitation_id, status, checkin_time, deleted),
    KEY idx_site_meeting_attendance_registration (registration_id, status, deleted),
    KEY idx_site_meeting_attendance_project (project_id, checkin_time, deleted),
    KEY idx_site_meeting_attendance_qr (checkin_qr_id, checkin_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='共享会议逐人签到当前状态';

SET sql_notes = @meeting_checkin_previous_sql_notes;

INSERT INTO sys_data_migration(migration_key)
VALUES ('20260902_SITE_ACCESS_MEETING_CHECKIN_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;

-- 回滚不得直接删除签到记录；如新版已产生数据，必须随数据库快照和应用版本整体恢复。
