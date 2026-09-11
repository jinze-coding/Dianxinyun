-- Source attachments are optional. Only the assigned approver chooses stamped-copy requirements.
-- Existing applications retain the previous optional stamped-copy behavior.
SET @seal_attachments_column_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'seal_application'
             AND column_name = 'stamped_result_required'),
    'SELECT 1',
    'ALTER TABLE seal_application ADD COLUMN stamped_result_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''Approval requires a stamped copy'' AFTER approval_opinion'
);
PREPARE seal_attachments_stmt FROM @seal_attachments_column_sql;
EXECUTE seal_attachments_stmt;
DEALLOCATE PREPARE seal_attachments_stmt;

INSERT INTO sys_data_migration(migration_key) VALUES ('20260911_SEAL_OPTIONAL_ATTACHMENTS_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;
