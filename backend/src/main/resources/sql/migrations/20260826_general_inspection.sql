-- 通用巡检与临边巡检：独立于现有电箱巡检的数据域。
-- 非破坏增量迁移；长期数据库执行前必须备份数据库与上传目录，并先在隔离副本双跑。

CREATE TABLE IF NOT EXISTS general_inspection_project_setting (
    project_id BIGINT PRIMARY KEY,
    enabled TINYINT NOT NULL DEFAULT 0 COMMENT '项目级试点开关，默认关闭',
    version INT NOT NULL DEFAULT 0,
    updated_by_id BIGINT,
    updated_by_name VARCHAR(50),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检项目开关';

CREATE TABLE IF NOT EXISTS general_inspection_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scope_type VARCHAR(20) NOT NULL COMMENT 'COMPANY/PROJECT',
    project_id BIGINT NULL,
    source_template_id BIGINT NULL,
    template_code VARCHAR(50) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    category_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    current_version_id BIGINT NULL,
    overall_photo_min INT NOT NULL DEFAULT 0,
    overall_photo_max INT NOT NULL DEFAULT 9,
    overall_remark_required TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(1000),
    version INT NOT NULL DEFAULT 0,
    created_by_id BIGINT NOT NULL,
    created_by_name VARCHAR(50),
    updated_by_id BIGINT NOT NULL,
    updated_by_name VARCHAR(50),
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_general_template_code (scope_type, project_id, template_code, deleted),
    KEY idx_general_template_scope (scope_type, project_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检模板主记录';

CREATE TABLE IF NOT EXISTS general_inspection_template_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    effective_time DATETIME NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    category_name VARCHAR(100),
    overall_photo_min INT NOT NULL DEFAULT 0,
    overall_photo_max INT NOT NULL DEFAULT 9,
    overall_remark_required TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(1000),
    published_by_id BIGINT NOT NULL,
    published_by_name VARCHAR(50),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_general_template_version (template_id, version_no),
    KEY idx_general_template_effective (template_id, effective_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检不可变模板版本';

CREATE TABLE IF NOT EXISTS general_inspection_template_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    template_version_id BIGINT NULL COMMENT 'NULL 为当前草稿项，非空为发布快照',
    item_key VARCHAR(50) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    guidance VARCHAR(1000),
    standard_reference VARCHAR(500),
    allow_na TINYINT NOT NULL DEFAULT 0,
    normal_photo_min INT NOT NULL DEFAULT 0,
    abnormal_photo_min INT NOT NULL DEFAULT 0,
    photo_max INT NOT NULL DEFAULT 9,
    normal_description_required TINYINT NOT NULL DEFAULT 0,
    abnormal_description_required TINYINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_general_template_draft_items (template_id, template_version_id, sort_order),
    UNIQUE KEY uk_general_template_version_item (template_version_id, item_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检模板检查项及发布快照';

CREATE TABLE IF NOT EXISTS general_inspection_point_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NULL COMMENT 'NULL 为系统预置类别',
    category_code VARCHAR(50) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    builtin TINYINT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_by_id BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_general_point_category (project_id, category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检点位类别';

CREATE TABLE IF NOT EXISTS general_inspection_point (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    point_code VARCHAR(50) NOT NULL,
    point_name VARCHAR(100) NOT NULL,
    category_id BIGINT,
    category_name VARCHAR(100),
    area_name VARCHAR(100),
    building_name VARCHAR(100),
    floor_name VARCHAR(100),
    location_desc VARCHAR(300),
    risk_note VARCHAR(1000),
    reference_photo_file_ids VARCHAR(2000),
    qr_enabled TINYINT NOT NULL DEFAULT 0,
    public_code VARCHAR(80),
    qr_version INT NOT NULL DEFAULT 0,
    public_access_enabled TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/ARCHIVED',
    version INT NOT NULL DEFAULT 0,
    created_by_id BIGINT NOT NULL,
    created_by_name VARCHAR(50),
    updated_by_id BIGINT NOT NULL,
    updated_by_name VARCHAR(50),
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_general_point_code (project_id, point_code, deleted),
    UNIQUE KEY uk_general_point_public_code (public_code),
    KEY idx_general_point_project (project_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检点位';

CREATE TABLE IF NOT EXISTS general_inspection_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    plan_code VARCHAR(50) NOT NULL,
    plan_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/PAUSED/ENDED/ARCHIVED',
    draft_config_json JSON NOT NULL,
    current_version_id BIGINT NULL,
    generated_through_time DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    created_by_id BIGINT NOT NULL,
    created_by_name VARCHAR(50),
    updated_by_id BIGINT NOT NULL,
    updated_by_name VARCHAR(50),
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_general_plan_code (project_id, plan_code, deleted),
    KEY idx_general_plan_project (project_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检计划主记录';

CREATE TABLE IF NOT EXISTS general_inspection_plan_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    effective_time DATETIME NOT NULL,
    config_json JSON NOT NULL,
    published_by_id BIGINT NOT NULL,
    published_by_name VARCHAR(50),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_general_plan_version (plan_id, version_no),
    KEY idx_general_plan_effective (plan_id, effective_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检不可变计划版本';

CREATE TABLE IF NOT EXISTS general_inspection_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    plan_version_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    template_version_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    revision_no INT NOT NULL DEFAULT 0,
    replaces_task_id BIGINT,
    plan_name VARCHAR(100) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    point_code VARCHAR(50) NOT NULL,
    point_name VARCHAR(100) NOT NULL,
    location_desc VARCHAR(300),
    slot_code VARCHAR(50) NOT NULL,
    slot_name VARCHAR(100) NOT NULL,
    occurrence_date DATE NOT NULL,
    available_time DATETIME NOT NULL,
    start_time DATETIME NOT NULL,
    due_time DATETIME NOT NULL,
    assignee_id BIGINT NULL COMMENT '责任人失效后置空，由管理者明确改派',
    assignee_name VARCHAR(50),
    backup_assignee_ids VARCHAR(1000),
    default_rectifier_id BIGINT,
    default_rectifier_name VARCHAR(50),
    default_rectification_days INT NOT NULL DEFAULT 3,
    reviewer_id BIGINT,
    reviewer_name VARCHAR(50),
    backup_reviewer_ids VARCHAR(1000),
    qr_required TINYINT NOT NULL DEFAULT 0,
    qr_version INT NOT NULL DEFAULT 0,
    scan_verified_by BIGINT,
    scan_verified_time DATETIME,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    submitted_by_id BIGINT,
    submitted_by_name VARCHAR(50),
    submitted_time DATETIME,
    on_time TINYINT,
    overall_photo_min INT NOT NULL DEFAULT 0,
    overall_photo_max INT NOT NULL DEFAULT 9,
    overall_remark_required TINYINT NOT NULL DEFAULT 0,
    overall_photo_file_ids VARCHAR(2000),
    remark VARCHAR(1000),
    public_remark VARCHAR(500),
    abnormal_count INT NOT NULL DEFAULT 0,
    cancel_reason VARCHAR(500),
    cancelled_by_id BIGINT,
    cancelled_by_name VARCHAR(50),
    cancelled_time DATETIME,
    correction_note VARCHAR(1000),
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_general_task_occurrence (plan_id, point_id, slot_code, occurrence_date, revision_no),
    KEY idx_general_task_project_due (project_id, due_time, status),
    KEY idx_general_task_assignee (assignee_id, status, due_time),
    KEY idx_general_task_public_month (point_id, occurrence_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检计划任务及正式记录';

CREATE TABLE IF NOT EXISTS general_inspection_task_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    template_item_id BIGINT,
    item_key VARCHAR(50) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    guidance VARCHAR(1000),
    standard_reference VARCHAR(500),
    allow_na TINYINT NOT NULL DEFAULT 0,
    normal_photo_min INT NOT NULL DEFAULT 0,
    abnormal_photo_min INT NOT NULL DEFAULT 0,
    photo_max INT NOT NULL DEFAULT 9,
    normal_description_required TINYINT NOT NULL DEFAULT 0,
    abnormal_description_required TINYINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL,
    result VARCHAR(20),
    description VARCHAR(1000),
    photo_file_ids VARCHAR(2000),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_general_task_item (task_id, item_key),
    KEY idx_general_task_item_order (task_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检任务检查项快照与结果';

CREATE TABLE IF NOT EXISTS general_inspection_rectification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    task_item_id BIGINT NOT NULL,
    point_id BIGINT NOT NULL,
    point_name VARCHAR(100) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    problem_desc VARCHAR(1000),
    requirement VARCHAR(1000),
    assignee_id BIGINT,
    assignee_name VARCHAR(50),
    deadline DATE,
    reviewer_id BIGINT,
    reviewer_name VARCHAR(50),
    status VARCHAR(20) NOT NULL COMMENT 'UNASSIGNED/PENDING/COMPLETED/REJECTED/CLOSED/VOIDED',
    feedback VARCHAR(1000),
    rectification_photo_file_ids VARCHAR(2000),
    completed_time DATETIME,
    review_comment VARCHAR(1000),
    review_time DATETIME,
    reject_count INT NOT NULL DEFAULT 0,
    close_time DATETIME,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_general_rectification_item (task_item_id, status),
    KEY idx_general_rectification_assignee (assignee_id, status, deadline),
    KEY idx_general_rectification_reviewer (reviewer_id, status, deadline),
    KEY idx_general_rectification_project (project_id, status, deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检逐项整改';

CREATE TABLE IF NOT EXISTS general_inspection_action_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    business_id BIGINT NOT NULL,
    action_type VARCHAR(30) NOT NULL,
    operator_id BIGINT,
    operator_name VARCHAR(50),
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    comment VARCHAR(1000),
    before_json JSON,
    after_json JSON,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_general_action_business (business_type, business_id, create_time),
    KEY idx_general_action_project (project_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检配置、任务、纠错和整改留痕';

CREATE TABLE IF NOT EXISTS general_inspection_export_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    requested_by_id BIGINT NOT NULL,
    requested_by_name VARCHAR(50),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED/EXPIRED',
    progress INT NOT NULL DEFAULT 0,
    file_resource_id BIGINT,
    error_message VARCHAR(1000),
    expires_time DATETIME,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_general_export_requester (requested_by_id, status, create_time),
    KEY idx_general_export_expiry (status, expires_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检异步含图Excel导出';

CREATE TABLE IF NOT EXISTS general_inspection_event_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_key VARCHAR(180) NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    project_id BIGINT NOT NULL,
    business_type VARCHAR(40) NOT NULL,
    business_id BIGINT NOT NULL,
    payload_json VARCHAR(4000),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    occurred_time DATETIME NOT NULL,
    published_time DATETIME,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_general_event_key (event_key),
    KEY idx_general_event_dispatch (status, retry_count, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用巡检外部提醒事件发件箱';

INSERT INTO general_inspection_point_category
    (project_id, category_code, category_name, builtin, enabled)
SELECT NULL, seed.code, seed.name, 1, 1
FROM (
    SELECT 'FLOOR_EDGE' code, '楼层边' name UNION ALL
    SELECT 'STAIR_EDGE', '楼梯边' UNION ALL
    SELECT 'ROOF_EDGE', '屋面边' UNION ALL
    SELECT 'PIT_TRENCH', '基坑沟槽' UNION ALL
    SELECT 'OPENING', '洞口' UNION ALL
    SELECT 'RECEIVING_PLATFORM', '接料平台'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM general_inspection_point_category c
    WHERE c.project_id IS NULL AND c.category_code = seed.code
);

INSERT INTO sys_permission(permission_code, permission_name, module_code, description,
                           enabled, builtin, deleted)
SELECT 'CUSTOM_INSPECTION_SUBMIT', '提交通用巡检', 'WEB_INSPECTION',
       '执行分配给自己的临边及其他项目自定义巡检任务', 1, 1, 0
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'CUSTOM_INSPECTION_SUBMIT'
);

SET @general_inspection_web_parent = (
    SELECT id FROM sys_menu WHERE menu_code = 'WEB_INSPECTION' AND deleted = 0 LIMIT 1
);
INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
SELECT @general_inspection_web_parent, 'WEB', 'INSPECTION_CONFIG', '巡检配置', 'TAB',
       'INSPECTION_CONFIG', 'inspection.manage', 22, 1, 1, 1, 0
WHERE @general_inspection_web_parent IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE menu_code = 'INSPECTION_CONFIG' AND deleted = 0);

SET @platform_admin_role_id = (
    SELECT id FROM sys_role
    WHERE role_code = 'PLATFORM_ADMIN' AND deleted = 0 ORDER BY id LIMIT 1
);
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT @platform_admin_role_id, id FROM sys_permission
WHERE @platform_admin_role_id IS NOT NULL
  AND permission_code = 'CUSTOM_INSPECTION_SUBMIT' AND enabled = 1 AND deleted = 0;
INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT @platform_admin_role_id, id FROM sys_menu
WHERE @platform_admin_role_id IS NOT NULL
  AND menu_code = 'INSPECTION_CONFIG' AND enabled = 1 AND deleted = 0;

-- 公司级临边参考模板只提供可复制的检查结构；项目发布前必须结合施工方案审核。
INSERT INTO general_inspection_template
    (scope_type, project_id, template_code, template_name, category_name, status,
     overall_photo_min, overall_photo_max, overall_remark_required, remark,
     version, created_by_id, created_by_name, updated_by_id, updated_by_name)
SELECT 'COMPANY', NULL, 'EDGE_REFERENCE', '临边通用参考模板', '临边巡检', 'PUBLISHED',
       1, 9, 0,
       '参考 JGJ 80-2016 与 GB 55034-2022；必须结合项目施工方案审核并复制为项目模板后使用。',
       1, 0, 'SYSTEM', 0, 'SYSTEM'
WHERE NOT EXISTS (
    SELECT 1 FROM general_inspection_template
    WHERE scope_type = 'COMPANY' AND project_id IS NULL
      AND template_code = 'EDGE_REFERENCE' AND deleted = 0
);

SET @edge_template_id = (
    SELECT id FROM general_inspection_template
    WHERE scope_type = 'COMPANY' AND project_id IS NULL
      AND template_code = 'EDGE_REFERENCE' AND deleted = 0 LIMIT 1
);
INSERT INTO general_inspection_template_version
    (template_id, version_no, effective_time, template_name, category_name,
     overall_photo_min, overall_photo_max, overall_remark_required, remark,
     published_by_id, published_by_name)
SELECT @edge_template_id, 1, CURRENT_TIMESTAMP, '临边通用参考模板', '临边巡检',
       1, 9, 0,
       '参考模板不替代安全技术方案，项目复制后应由安全负责人审核。', 0, 'SYSTEM'
WHERE @edge_template_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM general_inspection_template_version
      WHERE template_id = @edge_template_id AND version_no = 1
  );
SET @edge_version_id = (
    SELECT id FROM general_inspection_template_version
    WHERE template_id = @edge_template_id AND version_no = 1 LIMIT 1
);
UPDATE general_inspection_template
SET current_version_id = @edge_version_id
WHERE id = @edge_template_id AND current_version_id IS NULL;

INSERT INTO general_inspection_template_item
    (template_id, template_version_id, item_key, item_name, guidance, standard_reference,
     allow_na, normal_photo_min, abnormal_photo_min, photo_max,
     normal_description_required, abnormal_description_required, sort_order)
SELECT @edge_template_id, @edge_version_id, seed.item_key, seed.item_name, seed.guidance,
       seed.standard_reference, seed.allow_na, 0, 1, 9, 0, 1, seed.sort_order
FROM (
    SELECT 'PROTECTION_CONTINUITY' item_key, '防护设施连续完整' item_name,
           '检查临空侧防护是否连续覆盖、无缺口。' guidance,
           'JGJ 80-2016 4.1' standard_reference, 0 allow_na, 1 sort_order UNION ALL
    SELECT 'RAILING_STABILITY', '栏杆构件与固定牢固',
           '检查横杆、立杆、连接和固定是否松动、变形或损坏。',
           'JGJ 80-2016 4.3', 0, 2 UNION ALL
    SELECT 'ENCLOSURE_TOEBOARD', '封闭设施与挡脚板完整',
           '检查安全网、工具式栏板及挡脚板是否完整严密。',
           'JGJ 80-2016 4.1、4.3', 1, 3 UNION ALL
    SELECT 'SPECIAL_PROTECTION', '点位专项防护匹配',
           '按楼梯边、坑槽、洞口或接料平台类型核对专项防护。',
           'JGJ 80-2016 4.1、4.2', 1, 4 UNION ALL
    SELECT 'WARNING_SIGN', '警示标识有效',
           '检查安全警示标识及需要时的夜间警示是否清晰有效。',
           'JGJ 80-2016 3.0.4', 1, 5 UNION ALL
    SELECT 'FALLING_OBJECT_RISK', '周边通道与坠物风险受控',
           '检查通道、材料和工具是否阻碍通行或存在坠落风险。',
           'JGJ 80-2016 3.0.6', 0, 6 UNION ALL
    SELECT 'TEMPORARY_CHANGE', '临时拆改措施可靠并恢复',
           '如发生临时拆改，检查替代措施和作业后恢复情况。',
           'JGJ 80-2016 3.0.9', 1, 7 UNION ALL
    SELECT 'WEATHER_RECHECK', '恶劣天气或扰动后已复查',
           '雨雪、大风或施工扰动后检查设施是否复查并修复。',
           'JGJ 80-2016 3.0.8', 1, 8 UNION ALL
    SELECT 'PPE_AND_WATCH', '个人防护与现场监护落实',
           '防护不足或需在设施外作业时，检查个人防护和监护措施。',
           'JGJ 80-2016 3.0.5', 1, 9
) seed
WHERE @edge_template_id IS NOT NULL AND @edge_version_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM general_inspection_template_item i
      WHERE i.template_version_id = @edge_version_id AND i.item_key = seed.item_key
  );

INSERT IGNORE INTO sys_data_migration(migration_key)
VALUES ('20260826_GENERAL_INSPECTION_V1');
