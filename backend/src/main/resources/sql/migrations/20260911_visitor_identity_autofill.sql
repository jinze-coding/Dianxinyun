-- 同一 AppID 下跨项目查询本人信息；不回填身份、不修改历史登记或权限。
-- 前置：20260902_site_access_meeting_checkin.sql、20260910_visitor_reuse_guard_meeting.sql。
SET @visitor_autofill_preflight = IF(
    EXISTS(SELECT 1 FROM sys_data_migration WHERE migration_key='20260910_VISITOR_REUSE_GUARD_MEETING_V1')
    AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
         AND TABLE_NAME IN ('site_visitor_personal_profile','site_visitor_personal_profile_audit','site_visit_invitation',
         'site_meeting_visit_registration','site_guard_visit_registration','site_visitor_profile'))=6
    AND EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()
               AND TABLE_NAME='site_visit_invitation' AND COLUMN_NAME='visitor_identity_hash'),
    'SELECT 1', 'SELECT VISITOR_AUTOFILL_REQUIRES_20260910_VISITOR_REUSE');
PREPARE stmt FROM @visitor_autofill_preflight; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 保留同秒内先后提交顺序；原秒级值原样扩展，不批量更新业务字段。
SET @visitor_autofill_sql = IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_visitor_personal_profile'
      AND COLUMN_NAME='last_submitted_time' AND DATETIME_PRECISION=6), 'SELECT 1',
    'ALTER TABLE site_visitor_personal_profile MODIFY last_submitted_time DATETIME(6) NOT NULL');
PREPARE stmt FROM @visitor_autofill_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @visitor_autofill_sql = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_visitor_personal_profile' AND INDEX_NAME='idx_personal_app_identity_latest'),
    'SELECT 1', 'ALTER TABLE site_visitor_personal_profile ADD INDEX idx_personal_app_identity_latest (wechat_app_id,owner_identity_hash,deleted,last_submitted_time,id)');
PREPARE stmt FROM @visitor_autofill_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @visitor_autofill_sql = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_visit_invitation' AND INDEX_NAME='idx_single_app_identity_latest'),
    'SELECT 1', 'ALTER TABLE site_visit_invitation ADD INDEX idx_single_app_identity_latest (wechat_app_id,visitor_identity_hash,deleted,invite_type,status,submitted_time,id)');
PREPARE stmt FROM @visitor_autofill_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @visitor_autofill_sql = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_meeting_visit_registration' AND INDEX_NAME='idx_meeting_app_identity_latest'),
    'SELECT 1', 'ALTER TABLE site_meeting_visit_registration ADD INDEX idx_meeting_app_identity_latest (wechat_app_id,visitor_identity_hash,deleted,status,registered_time,id)');
PREPARE stmt FROM @visitor_autofill_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @visitor_autofill_sql = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_guard_visit_registration' AND INDEX_NAME='idx_guard_app_identity_latest'),
    'SELECT 1', 'ALTER TABLE site_guard_visit_registration ADD INDEX idx_guard_app_identity_latest (wechat_app_id,visitor_identity_hash,deleted,status,registered_time,id)');
PREPARE stmt FROM @visitor_autofill_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @visitor_autofill_sql = IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='site_visitor_profile' AND INDEX_NAME='idx_named_app_identity_latest'),
    'SELECT 1', 'ALTER TABLE site_visitor_profile ADD INDEX idx_named_app_identity_latest (wechat_app_id,owner_openid_hash,deleted,status,last_used_time,id)');
PREPARE stmt FROM @visitor_autofill_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

INSERT INTO sys_data_migration(migration_key) VALUES ('20260911_VISITOR_IDENTITY_AUTOFILL_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key=incoming.migration_key;
