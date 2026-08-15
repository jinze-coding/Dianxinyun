-- 同行人员可选单位、姓名和手机号：扩展邀请快照与常用资料逐人明细。
-- 本脚本不修改历史人员内容；手机号仅保存 AES-GCM 密文。
-- 可重复执行；发布时必须先于读取新列的新版后端执行。

SET @site_visit_person_company_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'site_visit_person'
      AND column_name = 'person_company'
);
SET @site_visit_person_company_sql = IF(
    @site_visit_person_company_exists = 0,
    'ALTER TABLE site_visit_person ADD COLUMN person_company VARCHAR(200) NULL AFTER person_type',
    'SELECT 1'
);
PREPARE site_visit_person_company_stmt FROM @site_visit_person_company_sql;
EXECUTE site_visit_person_company_stmt;
DEALLOCATE PREPARE site_visit_person_company_stmt;

SET @site_visit_person_phone_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'site_visit_person'
      AND column_name = 'phone_encrypted'
);
SET @site_visit_person_phone_sql = IF(
    @site_visit_person_phone_exists = 0,
    'ALTER TABLE site_visit_person ADD COLUMN phone_encrypted VARCHAR(512) NULL AFTER person_name',
    'SELECT 1'
);
PREPARE site_visit_person_phone_stmt FROM @site_visit_person_phone_sql;
EXECUTE site_visit_person_phone_stmt;
DEALLOCATE PREPARE site_visit_person_phone_stmt;

SET @site_visit_person_name_not_nullable = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'site_visit_person'
      AND column_name = 'person_name'
      AND is_nullable = 'NO'
);
SET @site_visit_person_name_sql = IF(
    @site_visit_person_name_not_nullable > 0,
    'ALTER TABLE site_visit_person MODIFY person_name VARCHAR(50) NULL',
    'SELECT 1'
);
PREPARE site_visit_person_name_stmt FROM @site_visit_person_name_sql;
EXECUTE site_visit_person_name_stmt;
DEALLOCATE PREPARE site_visit_person_name_stmt;

SET @site_profile_person_company_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'site_visitor_profile_person'
      AND column_name = 'person_company'
);
SET @site_profile_person_company_sql = IF(
    @site_profile_person_company_exists = 0,
    'ALTER TABLE site_visitor_profile_person ADD COLUMN person_company VARCHAR(200) NULL AFTER person_type',
    'SELECT 1'
);
PREPARE site_profile_person_company_stmt FROM @site_profile_person_company_sql;
EXECUTE site_profile_person_company_stmt;
DEALLOCATE PREPARE site_profile_person_company_stmt;

SET @site_profile_person_phone_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'site_visitor_profile_person'
      AND column_name = 'phone_encrypted'
);
SET @site_profile_person_phone_sql = IF(
    @site_profile_person_phone_exists = 0,
    'ALTER TABLE site_visitor_profile_person ADD COLUMN phone_encrypted VARCHAR(512) NULL AFTER person_name',
    'SELECT 1'
);
PREPARE site_profile_person_phone_stmt FROM @site_profile_person_phone_sql;
EXECUTE site_profile_person_phone_stmt;
DEALLOCATE PREPARE site_profile_person_phone_stmt;

SET @site_profile_person_name_not_nullable = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'site_visitor_profile_person'
      AND column_name = 'person_name'
      AND is_nullable = 'NO'
);
SET @site_profile_person_name_sql = IF(
    @site_profile_person_name_not_nullable > 0,
    'ALTER TABLE site_visitor_profile_person MODIFY person_name VARCHAR(50) NULL',
    'SELECT 1'
);
PREPARE site_profile_person_name_stmt FROM @site_profile_person_name_sql;
EXECUTE site_profile_person_name_stmt;
DEALLOCATE PREPARE site_profile_person_name_stmt;

INSERT IGNORE INTO sys_data_migration(migration_key)
VALUES ('20260814_SITE_ACCESS_COMPANION_OPTIONAL_CONTACT_V1');
