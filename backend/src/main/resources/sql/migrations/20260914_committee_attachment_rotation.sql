-- Persist media orientation without changing originals, records or role grants.
SET @committee_rotation_sql = IF((SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='safety_committee_attachment' AND column_name='rotation_degrees')=0,
 'ALTER TABLE safety_committee_attachment ADD COLUMN rotation_degrees INT NOT NULL DEFAULT 0', 'SELECT 1');
PREPARE committee_rotation_stmt FROM @committee_rotation_sql;
EXECUTE committee_rotation_stmt;
DEALLOCATE PREPARE committee_rotation_stmt;
SET @committee_rotation_sql = IF((SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='safety_committee_attachment' AND column_name='rotation_version')=0,
 'ALTER TABLE safety_committee_attachment ADD COLUMN rotation_version INT NOT NULL DEFAULT 1', 'SELECT 1');
PREPARE committee_rotation_stmt FROM @committee_rotation_sql;
EXECUTE committee_rotation_stmt;
DEALLOCATE PREPARE committee_rotation_stmt;
INSERT IGNORE INTO sys_data_migration(migration_key) VALUES ('20260914_COMMITTEE_ATTACHMENT_ROTATION');
