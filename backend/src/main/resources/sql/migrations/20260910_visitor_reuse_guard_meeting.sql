-- 本人快捷资料、单次预约微信归属与门卫多会议预约关联。
-- 在会议签到迁移之后执行；历史单次预约身份保持空，不按手机号回填。

-- 必须先完成会议签到迁移；在任何结构写入前验证依赖，缺少依赖时 PREPARE 直接失败。
SET @visitor_reuse_preflight = IF(
    EXISTS(SELECT 1 FROM sys_data_migration WHERE migration_key='20260902_SITE_ACCESS_MEETING_CHECKIN_V1')
    AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_meeting_attendance')
    AND EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_meeting_checkin_qr')
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_meeting_visit_registration' AND COLUMN_NAME='registration_source'),
    'SELECT 1', 'SELECT VISITOR_REUSE_REQUIRES_20260902_MEETING_CHECKIN_MIGRATION');
PREPARE stmt FROM @visitor_reuse_preflight; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @visitor_reuse_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_visit_invitation' AND COLUMN_NAME='wechat_app_id'),
    'SELECT 1', 'ALTER TABLE site_visit_invitation ADD COLUMN wechat_app_id VARCHAR(64) NULL');
PREPARE stmt FROM @visitor_reuse_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @visitor_reuse_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_visit_invitation' AND COLUMN_NAME='visitor_identity_hash'),
    'SELECT 1', 'ALTER TABLE site_visit_invitation ADD COLUMN visitor_identity_hash CHAR(64) NULL');
PREPARE stmt FROM @visitor_reuse_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @visitor_reuse_sql = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_visit_invitation' AND INDEX_NAME='idx_site_visit_owner_window'),
    'SELECT 1', 'ALTER TABLE site_visit_invitation ADD INDEX idx_site_visit_owner_window (project_id,wechat_app_id,visitor_identity_hash,status,visit_start_time,visit_end_time,deleted)');
PREPARE stmt FROM @visitor_reuse_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS site_visitor_personal_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    wechat_app_id VARCHAR(64) NOT NULL,
    owner_identity_hash CHAR(64) NOT NULL,
    remember_enabled TINYINT NOT NULL DEFAULT 0,
    visitor_company VARCHAR(200),
    contact_name VARCHAR(50),
    contact_phone_encrypted VARCHAR(512),
    travel_mode VARCHAR(20),
    vehicle_plate VARCHAR(20),
    privacy_agreed_time DATETIME,
    last_submitted_time DATETIME NOT NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_site_personal_profile_owner (project_id,wechat_app_id,owner_identity_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访客同项目本人快捷资料及记住偏好';

CREATE TABLE IF NOT EXISTS site_visitor_personal_profile_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    profile_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    before_encrypted LONGTEXT,
    after_encrypted LONGTEXT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_site_personal_audit_profile (profile_id,create_time),
    KEY idx_site_personal_audit_project (project_id,create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本人快捷资料加密审计';

CREATE TABLE IF NOT EXISTS site_guard_meeting_registration (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    guard_registration_id BIGINT NOT NULL,
    invitation_id BIGINT NOT NULL,
    meeting_registration_id BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_guard_meeting_registration (guard_registration_id,meeting_registration_id),
    KEY idx_guard_meeting_invitation (invitation_id,meeting_registration_id),
    KEY idx_guard_meeting_project (project_id,guard_registration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门卫登记关联多场会议预约';

INSERT INTO sys_data_migration(migration_key)
VALUES ('20260910_VISITOR_REUSE_GUARD_MEETING_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;
