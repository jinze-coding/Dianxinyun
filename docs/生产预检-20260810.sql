-- 2026-08-10 生产升级只读预检。
-- 只允许在已确认的目标业务库中执行；本文件不包含 INSERT/UPDATE/DELETE/DDL。

SELECT DATABASE() AS database_name, VERSION() AS mysql_version, NOW() AS checked_at;

SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';

SELECT migration_key
FROM sys_data_migration
WHERE migration_key IN (
    '20260728_UNIFIED_REGISTRATION_RBAC_SEED_V1',
    '20260729_PROJECT_MULTI_ROLE_MEMBER_MANAGEMENT_V1',
    '20260729_SHARED_BUSINESS_MODULE_ACCESS_V1',
    '20260803_ROLE_MENU_PERMISSION_HIERARCHY_V1',
    '20260807_INSPECTION_RECTIFICATION_CLOSURE_V1',
    '20260807_RETIRE_PROJECT_MEMBER_MANAGEMENT_PAGE_V1',
    '20260807_SITE_ACCESS_VISITOR_INVITATION_V1',
    '20260808_SEAL_APPLICATION_V1',
    '20260810_SITE_ACCESS_REUSABLE_VISITOR_PROFILE_V1',
    '20260810_SITE_ACCESS_VISITOR_IDENTITY_HMAC_V1',
    '20260810_INSPECTION_ELECTRICIAN_SUBMIT_PERMISSION_V1'
)
ORDER BY migration_key;

SELECT child.menu_code,
       child.menu_name,
       child.resource_type,
       child.permission_code,
       parent.menu_code AS parent_menu_code,
       child.enabled,
       child.deleted
FROM sys_menu child
LEFT JOIN sys_menu parent ON parent.id = child.parent_id
WHERE child.menu_code IN (
    'DOCUMENT_LIBRARY', 'DOCUMENT_RECYCLE', 'DOCUMENT_SEAL',
    'INSPECTION_LEDGER', 'INSPECTION_RECORDS', 'INSPECTION_RECTIFICATIONS',
    'QUALITY_ISSUES', 'QUALITY_DOCUMENTS',
    'WEB_SITE_ACCESS', 'SITE_VISITOR', 'SYSTEM_APPROVAL'
)
ORDER BY child.menu_code;

SELECT permission_code, module_code, enabled, deleted
FROM sys_permission
WHERE permission_code IN (
    'document.view', 'document.manage',
    'inspection.view', 'inspection.submit', 'INSPECTION_DAILY_SUBMIT',
    'inspection.rectify', 'inspection.review',
    'quality.view',
    'site_access.view', 'site_access.manage', 'site_access.export',
    'seal.view', 'seal.manage', 'seal.export',
    'system.approval.view', 'system.approval.manage'
)
ORDER BY permission_code;

SELECT r.role_code,
       COUNT(DISTINCT CASE
           WHEN p.permission_code IN ('inspection.submit', 'INSPECTION_DAILY_SUBMIT')
           THEN p.permission_code
       END) AS electrician_submit_permission_count,
       GROUP_CONCAT(DISTINCT p.permission_code ORDER BY p.permission_code) AS permission_codes
FROM sys_role r
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
LEFT JOIN sys_permission p ON p.id = rp.permission_id AND p.enabled = 1 AND p.deleted = 0
WHERE r.role_code = 'ELECTRICIAN'
  AND r.scope_type = 'PROJECT'
  AND r.enabled = 1 AND r.deleted = 0
GROUP BY r.id, r.role_code;

SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'project_info'
  AND column_name IN ('profile_version', 'fixed_ip_address', 'contract_amount', 'building_area')
ORDER BY column_name;

SELECT index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'file_resource'
  AND index_name = 'idx_file_project_profile'
GROUP BY index_name;

SELECT table_name, column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'site_visit_invitation' AND column_name = 'source_profile_id')
    OR (table_name IN ('site_visit_person', 'site_visitor_profile_person')
        AND column_name IN ('id_card_encrypted', 'id_card_hash'))
  )
ORDER BY table_name, ordinal_position;

SELECT table_name, index_name, GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_order
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND (
    (table_name = 'site_visit_invitation' AND index_name = 'idx_site_visit_source_profile')
    OR table_name IN ('site_visitor_profile', 'site_visitor_profile_person', 'site_visitor_profile_audit_log')
  )
GROUP BY table_name, index_name
ORDER BY table_name, index_name;
