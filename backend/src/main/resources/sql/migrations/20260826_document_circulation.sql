-- 图纸与技术文件收发、版本追溯、二维码签收。
-- 非破坏性增量：历史资料保留 GENERAL，不补建收发批次。
-- 执行前必须备份数据库和上传目录，并在隔离副本连续执行两次。

SET @create_sys_data_migration_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.TABLES
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_data_migration'
             AND TABLE_TYPE = 'BASE TABLE'),
    'DO 0',
    'CREATE TABLE sys_data_migration (
        migration_key VARCHAR(120) PRIMARY KEY,
        applied_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=''数据迁移种子执行标记'''
);
PREPARE stmt FROM @create_sys_data_migration_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_document_type_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document'
             AND COLUMN_NAME = 'document_type'),
    'SELECT 1',
    'ALTER TABLE project_document ADD COLUMN document_type VARCHAR(30) NOT NULL DEFAULT ''GENERAL'' COMMENT ''GENERAL/DRAWING/TECHNICAL_DOCUMENT'' AFTER category'
);
PREPARE stmt FROM @add_document_type_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_external_revision_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document_version'
             AND COLUMN_NAME = 'external_revision'),
    'SELECT 1',
    'ALTER TABLE project_document_version ADD COLUMN external_revision VARCHAR(100) NULL COMMENT ''外部版次'' AFTER change_note'
);
PREPARE stmt FROM @add_external_revision_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_version_status_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document_version'
             AND COLUMN_NAME = 'version_status'),
    'SELECT 1',
    'ALTER TABLE project_document_version ADD COLUMN version_status VARCHAR(20) NOT NULL DEFAULT ''CURRENT'' COMMENT ''CURRENT/SUPERSEDED/WITHDRAWN'' AFTER external_revision'
);
PREPARE stmt FROM @add_version_status_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_superseded_by_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document_version'
             AND COLUMN_NAME = 'superseded_by_version_id'),
    'SELECT 1',
    'ALTER TABLE project_document_version ADD COLUMN superseded_by_version_id BIGINT NULL COMMENT ''替代本版本的新版本ID'' AFTER version_status'
);
PREPARE stmt FROM @add_superseded_by_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_published_by_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document_version'
             AND COLUMN_NAME = 'published_by'),
    'SELECT 1',
    'ALTER TABLE project_document_version ADD COLUMN published_by BIGINT NULL COMMENT ''发布人ID'' AFTER superseded_by_version_id'
);
PREPARE stmt FROM @add_published_by_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_published_by_name_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document_version'
             AND COLUMN_NAME = 'published_by_name'),
    'SELECT 1',
    'ALTER TABLE project_document_version ADD COLUMN published_by_name VARCHAR(100) NULL COMMENT ''发布人姓名快照'' AFTER published_by'
);
PREPARE stmt FROM @add_published_by_name_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_published_time_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document_version'
             AND COLUMN_NAME = 'published_time'),
    'SELECT 1',
    'ALTER TABLE project_document_version ADD COLUMN published_time DATETIME NULL COMMENT ''正式发布时间'' AFTER published_by_name'
);
PREPARE stmt FROM @add_published_time_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @add_withdrawn_reason_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'project_document_version'
             AND COLUMN_NAME = 'withdrawn_reason'),
    'SELECT 1',
    'ALTER TABLE project_document_version ADD COLUMN withdrawn_reason VARCHAR(500) NULL COMMENT ''撤回原因'' AFTER published_time'
);
PREPARE stmt FROM @add_withdrawn_reason_sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE project_document SET document_type = 'GENERAL'
WHERE document_type IS NULL OR document_type NOT IN ('GENERAL', 'DRAWING', 'TECHNICAL_DOCUMENT');

UPDATE project_document_version version_row
INNER JOIN project_document document_row ON document_row.id = version_row.document_id
SET version_row.version_status = CASE
    WHEN version_row.id = document_row.current_version_id THEN 'CURRENT'
    ELSE 'SUPERSEDED'
END
WHERE version_row.version_status IS NULL
   OR version_row.version_status NOT IN ('CURRENT', 'SUPERSEDED', 'WITHDRAWN')
   OR (version_row.id <> document_row.current_version_id AND version_row.version_status = 'CURRENT');

CREATE TABLE IF NOT EXISTS document_incoming_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    incoming_no VARCHAR(40) NOT NULL,
    source_organization VARCHAR(200) NOT NULL,
    sender_name VARCHAR(100),
    source_reference_no VARCHAR(100),
    receive_method VARCHAR(30),
    received_at DATETIME NOT NULL,
    receiver_id BIGINT NOT NULL,
    receiver_name VARCHAR(100) NOT NULL,
    remark VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/VOIDED',
    published_by BIGINT,
    published_by_name VARCHAR(100),
    published_time DATETIME,
    voided_by BIGINT,
    voided_by_name VARCHAR(100),
    voided_time DATETIME,
    void_reason VARCHAR(500),
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_incoming_no (incoming_no),
    KEY idx_document_incoming_project (project_id, status, received_at),
    KEY idx_document_incoming_receiver (receiver_id, status, update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图纸技术文件收文批次';

CREATE TABLE IF NOT EXISTS document_incoming_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    file_resource_id BIGINT NOT NULL,
    folder_id BIGINT NOT NULL DEFAULT 0,
    item_order INT NOT NULL,
    title VARCHAR(200) NOT NULL,
    document_no VARCHAR(100),
    document_type VARCHAR(30) NOT NULL COMMENT 'DRAWING/TECHNICAL_DOCUMENT',
    external_revision VARCHAR(100),
    match_mode VARCHAR(20) NOT NULL COMMENT 'NEW_DOCUMENT/NEW_VERSION',
    target_document_id BIGINT,
    duplicate_revision_reason VARCHAR(500),
    change_note VARCHAR(500),
    published_document_id BIGINT,
    published_version_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PUBLISHED/WITHDRAWN',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_incoming_item_order (batch_id, item_order),
    UNIQUE KEY uk_document_incoming_file (file_resource_id),
    KEY idx_document_incoming_item_batch (batch_id, status, item_order),
    KEY idx_document_incoming_item_target (project_id, target_document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收文批次文件核对项';

CREATE TABLE IF NOT EXISTS document_distribution_batch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    incoming_batch_id BIGINT,
    distribution_no VARCHAR(40) NOT NULL,
    deadline DATETIME NOT NULL,
    notification_template VARCHAR(500) NOT NULL COMMENT '发布时生成并保存的站内通知内容快照',
    message_note VARCHAR(500),
    electronic_signature_required TINYINT NOT NULL DEFAULT 0,
    paper_signature_required TINYINT NOT NULL DEFAULT 1,
    qr_scene_digest CHAR(64) NOT NULL,
    qr_scene_ciphertext VARCHAR(500) NOT NULL,
    qr_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/VOIDED',
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED/COMPLETED/DISPUTED/VOIDED',
    published_by BIGINT NOT NULL,
    published_by_name VARCHAR(100) NOT NULL,
    published_time DATETIME NOT NULL,
    overdue_notification_time DATETIME,
    voided_by BIGINT,
    voided_by_name VARCHAR(100),
    voided_time DATETIME,
    void_reason VARCHAR(500),
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_distribution_no (distribution_no),
    UNIQUE KEY uk_document_distribution_scene (qr_scene_digest),
    KEY idx_document_distribution_project (project_id, status, deadline),
    KEY idx_document_distribution_incoming (incoming_batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图纸技术文件发放批次';

CREATE TABLE IF NOT EXISTS document_distribution_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    item_order INT NOT NULL,
    document_id BIGINT NOT NULL,
    version_id BIGINT NOT NULL,
    document_no_snapshot VARCHAR(100),
    title_snapshot VARCHAR(200) NOT NULL,
    document_type_snapshot VARCHAR(30) NOT NULL,
    system_version_no INT NOT NULL,
    external_revision_snapshot VARCHAR(100),
    file_name_snapshot VARCHAR(255) NOT NULL,
    sha256_snapshot CHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_distribution_item_order (batch_id, item_order),
    UNIQUE KEY uk_document_distribution_item_version (batch_id, version_id),
    KEY idx_document_distribution_item_document (document_id, version_id),
    KEY idx_document_distribution_item_project (project_id, batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发放批次精确版本快照';

CREATE TABLE IF NOT EXISTS document_distribution_recipient (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    username_snapshot VARCHAR(100) NOT NULL,
    real_name_snapshot VARCHAR(100) NOT NULL,
    phone_snapshot VARCHAR(30),
    role_names_snapshot VARCHAR(500),
    member_status_snapshot VARCHAR(20) NOT NULL,
    channel VARCHAR(20) NOT NULL COMMENT 'ELECTRONIC/PAPER/BOTH',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/DISPUTED',
    mandatory TINYINT NOT NULL DEFAULT 0,
    notified_time DATETIME,
    reminder_sent_time DATETIME,
    confirmed_time DATETIME,
    signature_file_id BIGINT,
    dispute_note VARCHAR(500),
    dispute_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_distribution_recipient (batch_id, user_id),
    KEY idx_document_recipient_todo (user_id, status, project_id, update_time),
    KEY idx_document_recipient_batch (batch_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发放批次接收人及签收状态';

CREATE TABLE IF NOT EXISTS document_distribution_recipient_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    distribution_item_id BIGINT NOT NULL,
    paper_copy_count INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_recipient_item (recipient_id, distribution_item_id),
    KEY idx_document_recipient_item_batch (batch_id, recipient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接收人逐文件纸质份数';

CREATE TABLE IF NOT EXISTS document_circulation_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    incoming_batch_id BIGINT,
    distribution_batch_id BIGINT,
    recipient_id BIGINT,
    document_id BIGINT,
    version_id BIGINT,
    user_id BIGINT,
    user_name VARCHAR(100),
    event_type VARCHAR(50) NOT NULL,
    channel VARCHAR(20),
    event_result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    event_summary VARCHAR(500),
    event_data_json TEXT,
    client_ip VARCHAR(64),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_document_event_project (project_id, event_type, create_time),
    KEY idx_document_event_distribution (distribution_batch_id, recipient_id, create_time),
    KEY idx_document_event_version (document_id, version_id, event_type, create_time),
    KEY idx_document_event_user_version (user_id, version_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图纸资料收发结构化追溯事件';

SET @document_menu_id = (
    SELECT id FROM sys_menu
    WHERE menu_code = 'WEB_DOCUMENT' AND enabled = 1 AND deleted = 0
    ORDER BY id LIMIT 1
);
INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
SELECT @document_menu_id, 'WEB', 'DOCUMENT_CIRCULATION', '图纸收发', 'TAB',
       'DOCUMENT_CIRCULATION', 'document.circulation.view', 12, 1, 1, 1, 0
WHERE @document_menu_id IS NOT NULL
ON DUPLICATE KEY UPDATE menu_code = 'DOCUMENT_CIRCULATION';

INSERT INTO sys_permission(permission_code, permission_name, module_code, description,
                           enabled, builtin, deleted)
VALUES
('document.receive', '登记和发布图纸收文', 'WEB_DOCUMENT', '收文草稿、分片上传、版本匹配与原子发布', 1, 1, 0),
('document.issue', '发放图纸和管理签收', 'WEB_DOCUMENT', '现行版本发放、接收人、二维码和作废', 1, 1, 0),
('document.circulation.view', '查看图纸收发台账', 'WEB_DOCUMENT', '查看项目收文、发放、签收和追溯记录', 1, 1, 0),
('document.circulation.export', '导出图纸收发台账', 'WEB_DOCUMENT', '导出五个工作表的综合台账', 1, 1, 0) AS incoming
ON DUPLICATE KEY UPDATE permission_code = incoming.permission_code;

SET @platform_admin_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'PLATFORM_ADMIN' AND scope_type = 'PLATFORM' AND deleted = 0
    ORDER BY id LIMIT 1
);
INSERT INTO sys_role_menu(role_id, menu_id)
SELECT @platform_admin_role_id, id FROM sys_menu
WHERE @platform_admin_role_id IS NOT NULL
  AND menu_code = 'DOCUMENT_CIRCULATION' AND enabled = 1 AND deleted = 0
ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id;
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT @platform_admin_role_id, id FROM sys_permission
WHERE @platform_admin_role_id IS NOT NULL
  AND permission_code IN ('document.receive', 'document.issue',
                          'document.circulation.view', 'document.circulation.export')
  AND enabled = 1 AND deleted = 0
ON DUPLICATE KEY UPDATE role_id = sys_role_permission.role_id;

INSERT INTO sys_data_migration(migration_key)
VALUES ('20260826_DOCUMENT_CIRCULATION_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;
