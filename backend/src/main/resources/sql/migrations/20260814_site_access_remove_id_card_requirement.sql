-- 外访登记停止采集身份证：仅放宽新记录列约束，不修改或删除历史密文。
-- 本脚本可重复执行；发布时必须先于新版后端执行。

SET @site_visit_person_id_not_nullable = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'site_visit_person'
      AND column_name IN ('id_card_encrypted', 'id_card_hash')
      AND is_nullable = 'NO'
);
SET @site_visit_person_id_sql = IF(
    @site_visit_person_id_not_nullable > 0,
    'ALTER TABLE site_visit_person MODIFY id_card_encrypted VARCHAR(512) NULL, MODIFY id_card_hash CHAR(64) NULL COMMENT ''历史身份证指纹；2026-08-14起新登记不再采集''',
    'SELECT 1'
);
PREPARE site_visit_person_id_stmt FROM @site_visit_person_id_sql;
EXECUTE site_visit_person_id_stmt;
DEALLOCATE PREPARE site_visit_person_id_stmt;

SET @site_visitor_profile_person_id_not_nullable = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'site_visitor_profile_person'
      AND column_name IN ('id_card_encrypted', 'id_card_hash')
      AND is_nullable = 'NO'
);
SET @site_visitor_profile_person_id_sql = IF(
    @site_visitor_profile_person_id_not_nullable > 0,
    'ALTER TABLE site_visitor_profile_person MODIFY id_card_encrypted VARCHAR(512) NULL, MODIFY id_card_hash CHAR(64) NULL COMMENT ''历史身份证指纹；2026-08-14起新登记不再采集''',
    'SELECT 1'
);
PREPARE site_visitor_profile_person_id_stmt FROM @site_visitor_profile_person_id_sql;
EXECUTE site_visitor_profile_person_id_stmt;
DEALLOCATE PREPARE site_visitor_profile_person_id_stmt;

INSERT IGNORE INTO sys_data_migration(migration_key)
VALUES ('20260814_SITE_ACCESS_REMOVE_ID_CARD_REQUIREMENT_V1');
