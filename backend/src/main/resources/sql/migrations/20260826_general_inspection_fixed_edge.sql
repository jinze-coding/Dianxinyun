-- 固定式临边巡检转换迁移。
-- 前置迁移：20260826_general_inspection.sql。
-- 本脚本保留既有 general_inspection_* 历史数据，只归档开放式配置并新增固定临边能力。

SET @has_old_general_inspection := (
    SELECT COUNT(*) FROM sys_data_migration
    WHERE migration_key = '20260826_GENERAL_INSPECTION_V1'
);
SET @require_old_general_inspection_sql := IF(
    @has_old_general_inspection = 1,
    'SELECT 1',
    'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''请先执行 20260826_general_inspection.sql'''
);
PREPARE require_old_general_inspection_stmt FROM @require_old_general_inspection_sql;
EXECUTE require_old_general_inspection_stmt;
DEALLOCATE PREPARE require_old_general_inspection_stmt;

SET @fixed_edge_already_applied := (
    SELECT COUNT(*) FROM sys_data_migration
    WHERE migration_key = '20260826_GENERAL_INSPECTION_FIXED_EDGE_V1'
);

-- 点位与任务增加固定类型快照；旧开放式点位/任务保持 NULL，不会进入临边接口。
SET @has_point_type_code := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_inspection_point'
      AND COLUMN_NAME = 'point_type_code'
);
SET @alter_point_type_code := IF(@has_point_type_code = 0,
    'ALTER TABLE general_inspection_point ADD COLUMN point_type_code VARCHAR(50) NULL COMMENT ''固定临边点位类型编码'' AFTER point_name',
    'SELECT 1');
PREPARE stmt FROM @alter_point_type_code; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_point_type_name := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_inspection_point'
      AND COLUMN_NAME = 'point_type_name'
);
SET @alter_point_type_name := IF(@has_point_type_name = 0,
    'ALTER TABLE general_inspection_point ADD COLUMN point_type_name VARCHAR(100) NULL COMMENT ''固定临边点位类型名称'' AFTER point_type_code',
    'SELECT 1');
PREPARE stmt FROM @alter_point_type_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_edge_active_since_time := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_inspection_point'
      AND COLUMN_NAME = 'edge_active_since_time'
);
SET @alter_edge_active_since_time := IF(@has_edge_active_since_time = 0,
    'ALTER TABLE general_inspection_point ADD COLUMN edge_active_since_time DATETIME NULL COMMENT ''本次启用临边巡检的起始时间'' AFTER point_type_name',
    'SELECT 1');
PREPARE stmt FROM @alter_edge_active_since_time; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_edge_generation_lower_bound_time := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_inspection_plan'
      AND COLUMN_NAME = 'edge_generation_lower_bound_time'
);
SET @alter_edge_generation_lower_bound_time := IF(@has_edge_generation_lower_bound_time = 0,
    'ALTER TABLE general_inspection_plan ADD COLUMN edge_generation_lower_bound_time DATETIME NULL COMMENT ''临边功能或计划最近启用的任务生成下界'' AFTER generated_through_time',
    'SELECT 1');
PREPARE stmt FROM @alter_edge_generation_lower_bound_time; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 兼容已创建但尚无独立启用下界的 EDGE 计划；创建时间是唯一不会误伤新点位后续任务的安全下界。
UPDATE general_inspection_plan
SET edge_generation_lower_bound_time = COALESCE(create_time, CURRENT_TIMESTAMP)
WHERE plan_code = 'EDGE_PROJECT_SCHEDULE'
  AND edge_generation_lower_bound_time IS NULL;

SET @has_task_point_type_code := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_inspection_task'
      AND COLUMN_NAME = 'point_type_code'
);
SET @alter_task_point_type_code := IF(@has_task_point_type_code = 0,
    'ALTER TABLE general_inspection_task ADD COLUMN point_type_code VARCHAR(50) NULL COMMENT ''固定临边点位类型编码快照'' AFTER point_name',
    'SELECT 1');
PREPARE stmt FROM @alter_task_point_type_code; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_task_point_type_name := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_inspection_task'
      AND COLUMN_NAME = 'point_type_name'
);
SET @alter_task_point_type_name := IF(@has_task_point_type_name = 0,
    'ALTER TABLE general_inspection_task ADD COLUMN point_type_name VARCHAR(100) NULL COMMENT ''固定临边点位类型名称快照'' AFTER point_type_code',
    'SELECT 1');
PREPARE stmt FROM @alter_task_point_type_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_task_building_name := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_inspection_task'
      AND COLUMN_NAME = 'building_name'
);
SET @alter_task_building_name := IF(@has_task_building_name = 0,
    'ALTER TABLE general_inspection_task ADD COLUMN building_name VARCHAR(100) NULL COMMENT ''楼栋快照'' AFTER point_type_name',
    'SELECT 1');
PREPARE stmt FROM @alter_task_building_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_task_floor_name := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_inspection_task'
      AND COLUMN_NAME = 'floor_name'
);
SET @alter_task_floor_name := IF(@has_task_floor_name = 0,
    'ALTER TABLE general_inspection_task ADD COLUMN floor_name VARCHAR(100) NULL COMMENT ''楼层快照'' AFTER building_name',
    'SELECT 1');
PREPARE stmt FROM @alter_task_floor_name; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_edge_point_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_inspection_point'
      AND INDEX_NAME = 'idx_general_edge_point'
);
SET @add_edge_point_index := IF(@has_edge_point_index = 0,
    'ALTER TABLE general_inspection_point ADD KEY idx_general_edge_point (project_id, point_type_code, status, deleted)',
    'SELECT 1');
PREPARE stmt FROM @add_edge_point_index; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_edge_task_index := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'general_inspection_task'
      AND INDEX_NAME = 'idx_general_edge_task'
);
SET @add_edge_task_index := IF(@has_edge_task_index = 0,
    'ALTER TABLE general_inspection_task ADD KEY idx_general_edge_task (project_id, point_type_code, occurrence_date, status)',
    'SELECT 1');
PREPARE stmt FROM @add_edge_task_index; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 开放式模板、项目自定义类别和多计划均只归档/停用，历史任务和日志不删除。
-- 旧开关表达的是开放式通用巡检试点，不能自动继承为固定临边试点；升级后一律由平台管理员重新开启。
UPDATE general_inspection_project_setting
SET enabled = 0,
    version = version + 1,
    update_time = CURRENT_TIMESTAMP
WHERE enabled <> 0 AND @fixed_edge_already_applied = 0;

UPDATE general_inspection_template
SET status = 'ARCHIVED', update_time = CURRENT_TIMESTAMP
WHERE scope_type <> 'SYSTEM' AND deleted = 0 AND status <> 'ARCHIVED'
  AND @fixed_edge_already_applied = 0;

UPDATE general_inspection_plan
SET status = 'ARCHIVED', update_time = CURRENT_TIMESTAMP
WHERE plan_code <> 'EDGE_PROJECT_SCHEDULE' AND deleted = 0 AND status <> 'ARCHIVED'
  AND @fixed_edge_already_applied = 0;

UPDATE general_inspection_point_category
SET enabled = 0, update_time = CURRENT_TIMESTAMP
WHERE enabled <> 0 AND @fixed_edge_already_applied = 0;

CREATE TEMPORARY TABLE tmp_edge_point_type (
    type_code VARCHAR(50) PRIMARY KEY,
    type_name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL
);
INSERT INTO tmp_edge_point_type(type_code, type_name, sort_order) VALUES
('FLOOR_BALCONY_EAVE_EDGE', '楼层、阳台及挑檐边', 1),
('STAIR_PLATFORM_FLIGHT_EDGE', '楼梯口、平台及梯段边', 2),
('ROOF_EDGE', '屋面边', 3),
('PIT_TRENCH_EDGE', '基坑、沟槽边', 4),
('OPENING_RESERVED_HOLE', '洞口、预留洞', 5),
('ELEVATOR_SHAFT', '电梯井口、井道', 6),
('HOIST_LANDING_PLATFORM', '升降机、物料提升机停层平台', 7),
('LOADING_UNLOADING_PLATFORM', '接料、卸料平台', 8);

INSERT INTO general_inspection_point_category
    (project_id, category_code, category_name, builtin, enabled, created_by_id)
SELECT NULL, seed.type_code, seed.type_name, 1, 1, 0
FROM tmp_edge_point_type seed
WHERE @fixed_edge_already_applied = 0
  AND NOT EXISTS (
    SELECT 1 FROM general_inspection_point_category category
    WHERE category.project_id IS NULL
      AND CAST(category.category_code AS BINARY) = CAST(seed.type_code AS BINARY)
);

UPDATE general_inspection_point_category category
INNER JOIN tmp_edge_point_type seed
    ON CAST(seed.type_code AS BINARY) = CAST(category.category_code AS BINARY)
SET category.category_name = seed.type_name,
    category.builtin = 1,
    category.enabled = 1,
    category.update_time = CURRENT_TIMESTAMP
WHERE category.project_id IS NULL AND @fixed_edge_already_applied = 0;

-- 每类固定一套系统模板。SYSTEM 模板无业务编辑入口，版本1为只读快照。
INSERT INTO general_inspection_template
    (scope_type, project_id, source_template_id, template_code, template_name, category_name,
     status, overall_photo_min, overall_photo_max, overall_remark_required, remark,
     version, created_by_id, created_by_name, updated_by_id, updated_by_name, deleted)
SELECT 'SYSTEM', NULL, NULL, CONCAT('EDGE_', seed.type_code),
       CONCAT(seed.type_name, '临边巡检表'), seed.type_name,
       'PUBLISHED', 1, 9, 0,
       '系统固定检查表，仅用于日常巡检；不能替代项目专项施工方案和验收结论。',
       1, 0, 'SYSTEM', 0, 'SYSTEM', 0
FROM tmp_edge_point_type seed
WHERE @fixed_edge_already_applied = 0
  AND NOT EXISTS (
    SELECT 1 FROM general_inspection_template template
    WHERE template.scope_type = 'SYSTEM' AND template.project_id IS NULL
      AND CAST(template.template_code AS BINARY) = CAST(CONCAT('EDGE_', seed.type_code) AS BINARY)
      AND template.deleted = 0
);

UPDATE general_inspection_template template
INNER JOIN tmp_edge_point_type seed
    ON CAST(template.template_code AS BINARY) = CAST(CONCAT('EDGE_', seed.type_code) AS BINARY)
SET template.template_name = CONCAT(seed.type_name, '临边巡检表'),
    template.category_name = seed.type_name,
    template.status = 'PUBLISHED',
    template.overall_photo_min = 1,
    template.overall_photo_max = 9,
    template.overall_remark_required = 0,
    template.remark = '系统固定检查表，仅用于日常巡检；不能替代项目专项施工方案和验收结论。',
    template.deleted = 0,
    template.update_time = CURRENT_TIMESTAMP
WHERE template.scope_type = 'SYSTEM' AND template.project_id IS NULL
  AND @fixed_edge_already_applied = 0;

INSERT INTO general_inspection_template_version
    (template_id, version_no, effective_time, template_name, category_name,
     overall_photo_min, overall_photo_max, overall_remark_required, remark,
     published_by_id, published_by_name)
SELECT template.id, 1, '2026-08-26 00:00:00', template.template_name, template.category_name,
       1, 9, 0,
       '系统固定检查表，仅用于日常巡检；不能替代项目专项施工方案和验收结论。',
       0, 'SYSTEM'
FROM general_inspection_template template
INNER JOIN tmp_edge_point_type seed
    ON CAST(template.template_code AS BINARY) = CAST(CONCAT('EDGE_', seed.type_code) AS BINARY)
WHERE @fixed_edge_already_applied = 0
  AND template.scope_type = 'SYSTEM' AND template.project_id IS NULL AND template.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM general_inspection_template_version version
      WHERE version.template_id = template.id AND version.version_no = 1
  );

UPDATE general_inspection_template template
INNER JOIN general_inspection_template_version version
    ON version.template_id = template.id AND version.version_no = 1
INNER JOIN tmp_edge_point_type seed
    ON CAST(template.template_code AS BINARY) = CAST(CONCAT('EDGE_', seed.type_code) AS BINARY)
SET template.current_version_id = version.id,
    template.status = 'PUBLISHED',
    template.update_time = CURRENT_TIMESTAMP
WHERE template.scope_type = 'SYSTEM' AND template.project_id IS NULL AND template.deleted = 0
  AND @fixed_edge_already_applied = 0;

CREATE TEMPORARY TABLE tmp_edge_template_item (
    type_code VARCHAR(50) NOT NULL,
    item_key VARCHAR(50) NOT NULL,
    item_name VARCHAR(200) NOT NULL,
    guidance VARCHAR(1000) NOT NULL,
    standard_reference VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL,
    PRIMARY KEY(type_code, item_key)
);
INSERT INTO tmp_edge_template_item VALUES
('FLOOR_BALCONY_EAVE_EDGE','PROTECTION_CONTINUITY','临空侧防护连续完整','检查楼层、阳台及挑檐临空侧防护是否连续覆盖、无缺口。','JGJ 80-2016；GB 55034-2022',1),
('FLOOR_BALCONY_EAVE_EDGE','RAILING_CONFIGURATION','栏杆构造符合要求','检查栏杆高度、横杆、立杆设置是否符合项目施工方案及规范要求。','JGJ 80-2016 4.3',2),
('FLOOR_BALCONY_EAVE_EDGE','ENCLOSURE_TOEBOARD','封闭及挡脚措施完整','检查安全网或栏板封闭、挡脚板是否连续严密。','JGJ 80-2016 4.3',3),
('FLOOR_BALCONY_EAVE_EDGE','FIXING_STABILITY','防护固定连接牢靠','检查防护构件及其与主体结构连接是否松动、变形或损坏。','JGJ 80-2016 4.3',4),
('FLOOR_BALCONY_EAVE_EDGE','MATERIAL_FALL_RISK','无拆改、堆料及坠物风险','检查是否存在私拆防护、临边堆料及工具材料坠落风险。','JGJ 80-2016；重大事故隐患判定标准（2024版）',5),

('STAIR_PLATFORM_FLIGHT_EDGE','ALL_EDGES_PROTECTED','楼梯及平台临空边防护完整','检查楼梯口、平台和梯段各临空边是否全部设置防护。','JGJ 80-2016 4.1、4.3',1),
('STAIR_PLATFORM_FLIGHT_EDGE','RAILING_STABILITY','栏杆构造和固定可靠','检查栏杆构造、连接和固定是否牢固，无松动变形。','JGJ 80-2016 4.3',2),
('STAIR_PLATFORM_FLIGHT_EDGE','ENCLOSURE_TOEBOARD','封闭及挡脚措施完整','检查封闭设施和挡脚板是否连续、无破损。','JGJ 80-2016 4.3',3),
('STAIR_PLATFORM_FLIGHT_EDGE','PASSAGE_CONDITION','通道安全畅通','检查通道是否存在障碍、积水、油污和打滑风险。','GB 55034-2022',4),
('STAIR_PLATFORM_FLIGHT_EDGE','WARNING_LIGHTING','警示及照明有效','检查安全警示和夜间、暗处照明是否清晰有效。','JGJ 80-2016 3.0.4',5),

('ROOF_EDGE','PERIMETER_PROTECTION','屋面周边防护完整','检查屋面周边临边防护是否连续、完整、牢固。','JGJ 80-2016 4.1、5.2',1),
('ROOF_EDGE','ROOF_TYPE_FALL_PROTECTION','防坠措施与屋面类型匹配','检查坡屋面、轻质屋面是否采用与屋面类型匹配的防坠措施。','JGJ 80-2016；JGJ 311-2013',2),
('ROOF_EDGE','WORK_PASSAGE_PLATFORM','作业通道和平台可靠','检查屋面作业通道、操作平台铺设及防护是否可靠。','JGJ 80-2016 5.2',3),
('ROOF_EDGE','MATERIAL_SLIP_FALL','材料无超载滑移坠落风险','检查材料堆放是否超载，是否采取防滑移、防坠落措施。','GB 55034-2022',4),
('ROOF_EDGE','ADVERSE_WEATHER_CONTROL','恶劣天气作业受控','检查大风、雨雪等恶劣天气后是否复查，是否存在违规作业。','JGJ 80-2016 3.0.8',5),

('PIT_TRENCH_EDGE','PERIMETER_GUARD','基坑沟槽周边防护完整','检查基坑、沟槽周边栏杆、封闭和警戒是否连续完整。','JGJ 80-2016；JGJ 311-2013',1),
('PIT_TRENCH_EDGE','ACCESS_ANTI_SLIP','上下通道及防滑可靠','检查上下通道、梯道、扶手和防滑措施是否安全可靠。','JGJ 311-2013',2),
('PIT_TRENCH_EDGE','SUPPORT_SLOPE_CONDITION','支护边坡无异常征兆','检查支护、边坡有无裂缝、变形、漏水、流土等明显异常。','JGJ 311-2013；重大事故隐患判定标准（2024版）',3),
('PIT_TRENCH_EDGE','SURCHARGE_EQUIPMENT_DISTANCE','周边堆载和机械位置受控','检查堆载和机械设备与坑边距离是否符合施工方案。','JGJ 311-2013',4),
('PIT_TRENCH_EDGE','DRAIN_WARNING_LIGHTING','排水警戒照明有效','检查排水设施、警戒区域和夜间警示照明是否有效。','JGJ 311-2013',5),

('OPENING_RESERVED_HOLE','MATCHED_PROTECTION_METHOD','洞口防护方式匹配','检查防护方式是否与洞口尺寸、方向和使用状态匹配。','JGJ 80-2016 4.2',1),
('OPENING_RESERVED_HOLE','COVER_LOAD_FIXING','盖板承载固定可靠','检查盖板承载能力、固定和防移位措施是否可靠。','JGJ 80-2016 4.2',2),
('OPENING_RESERVED_HOLE','LARGE_VERTICAL_OPENING','大洞口竖向洞口防护完整','检查栏杆、挡脚板和安全网等组合防护是否完整。','JGJ 80-2016 4.2',3),
('OPENING_RESERVED_HOLE','NO_PROTECTION_GAPS','防护无缺口破损','检查洞口周边防护是否存在缺口、松动、破损。','JGJ 80-2016 4.2',4),
('OPENING_RESERVED_HOLE','WARNING_NO_REMOVAL','警示清晰且无私拆','检查警示标识是否清晰，是否存在擅自拆除、挪动防护。','JGJ 80-2016 3.0.4、3.0.9',5),

('ELEVATOR_SHAFT','SHAFT_DOOR_DIMENSION','井口防护门构造符合要求','检查防护门尺寸、底部间隙及挡脚措施是否符合要求。','JGJ 80-2016 4.2',1),
('ELEVATOR_SHAFT','SHAFT_DOOR_CLOSED','防护门固定并保持关闭','检查防护门固定、防外开和闭锁状态是否有效。','JGJ 80-2016 4.2',2),
('ELEVATOR_SHAFT','HORIZONTAL_SAFETY_NET','井道水平安全网完整','检查井道水平安全网设置、固定及完整性。','JGJ 80-2016 4.2',3),
('ELEVATOR_SHAFT','UPPER_ISOLATION','施工层上部隔离有效','检查井道内施工层上部隔离防护是否有效。','JGJ 80-2016；GB 55034-2022',4),
('ELEVATOR_SHAFT','FALLING_OBJECT_ACCESS','无坠物及无关人员进入风险','检查井道杂物、坠物风险和无关人员进入控制。','GB 55034-2022',5),

('HOIST_LANDING_PLATFORM','PLATFORM_SIDE_PROTECTION','平台两侧防护完整','检查停层平台两侧栏杆、挡脚和封闭是否完整。','JGJ 80-2016 5.2',1),
('HOIST_LANDING_PLATFORM','LANDING_DOOR','楼层防护门有效','检查楼层防护门、防外开和关闭措施是否有效。','JGJ 80-2016；GB 55034-2022',2),
('HOIST_LANDING_PLATFORM','CONNECTION_DECK','平台连接铺板可靠','检查平台与结构连接、支撑和铺板是否牢固可靠。','JGJ 80-2016 5.2',3),
('HOIST_LANDING_PLATFORM','PASSAGE_ISOLATION','通道及隔离设施完整','检查进出通道、防护棚及隔离设施是否完整。','JGJ 80-2016 5.2',4),
('HOIST_LANDING_PLATFORM','LOAD_AND_HOUSEKEEPING','限载清晰且无超载堆积','检查限载标识、材料堆积和超载情况。','GB 55034-2022；重大事故隐患判定标准（2024版）',5),

('LOADING_UNLOADING_PLATFORM','ANCHOR_SUPPORT_CONNECTION','搁置拉结支撑连接可靠','检查平台搁置、拉结和支撑点是否按方案连接主体结构。','JGJ 80-2016 6.4；GB 55034-2022',1),
('LOADING_UNLOADING_PLATFORM','ROPE_ROD_SUPPORT','钢丝绳拉杆支撑完好','检查钢丝绳、拉杆和支撑有无缺失、松动、损伤。','JGJ 80-2016 6.4',2),
('LOADING_UNLOADING_PLATFORM','DECK_SIDE_PROTECTION','铺板及侧边防护完整','检查平台铺板、栏杆、挡脚和侧边封闭是否完整。','JGJ 80-2016 6.4',3),
('LOADING_UNLOADING_PLATFORM','LOAD_LIMIT','限载标识清晰且无超载','检查限载标识、实际荷载和材料堆放是否符合方案。','JGJ 80-2016 6.4；GB 55034-2022',4),
('LOADING_UNLOADING_PLATFORM','DROP_ZONE_ISOLATION','下方坠落区域有效隔离','检查平台下方坠落影响区域是否设置隔离和警戒。','JGJ 80-2016 3.0.6',5);

INSERT INTO general_inspection_template_item
    (template_id, template_version_id, item_key, item_name, guidance, standard_reference,
     allow_na, normal_photo_min, abnormal_photo_min, photo_max,
     normal_description_required, abnormal_description_required, sort_order)
SELECT template.id, version.id, seed.item_key, seed.item_name, seed.guidance,
       seed.standard_reference, 0, 0, 1, 9, 0, 1, seed.sort_order
FROM tmp_edge_template_item seed
INNER JOIN general_inspection_template template
    ON CAST(template.template_code AS BINARY) = CAST(CONCAT('EDGE_', seed.type_code) AS BINARY)
   AND template.scope_type = 'SYSTEM' AND template.project_id IS NULL AND template.deleted = 0
INNER JOIN general_inspection_template_version version
    ON version.template_id = template.id AND version.version_no = 1
WHERE @fixed_edge_already_applied = 0
  AND NOT EXISTS (
    SELECT 1 FROM general_inspection_template_item item
    WHERE item.template_version_id = version.id
      AND CAST(item.item_key AS BINARY) = CAST(seed.item_key AS BINARY)
);

-- 旧开放入口和权限停用；新菜单与五项操作权限独立授权。
UPDATE sys_menu
SET visible = 0, enabled = 0, deleted = 0, update_time = CURRENT_TIMESTAMP
WHERE menu_code = 'INSPECTION_CONFIG';

UPDATE sys_permission
SET enabled = 0, deleted = 1
WHERE permission_code = 'CUSTOM_INSPECTION_SUBMIT';

INSERT INTO sys_permission(permission_code, permission_name, module_code, description,
                           enabled, builtin, deleted)
SELECT seed.code, seed.name, 'WEB_INSPECTION', seed.description, 1, 1, 0
FROM (
    SELECT 'EDGE_INSPECTION_VIEW' code, '查看临边巡检' name, '查看临边点位、任务、记录、整改单和统计' description UNION ALL
    SELECT 'EDGE_INSPECTION_MANAGE', '管理临边巡检', '维护临边点位和周期设置，取消、改派任务与整改单' UNION ALL
    SELECT 'EDGE_INSPECTION_SUBMIT', '执行临边巡检', '执行明确分配给自己的固定式临边巡检任务' UNION ALL
    SELECT 'EDGE_INSPECTION_RECTIFY', '临边巡检整改', '整单提交明确分配给自己的临边整改反馈' UNION ALL
    SELECT 'EDGE_INSPECTION_REVIEW', '临边巡检复查', '整单关闭或退回明确分配给自己的临边整改单'
) seed
WHERE @fixed_edge_already_applied = 0
  AND NOT EXISTS (
    SELECT 1 FROM sys_permission permission
    WHERE CAST(permission.permission_code AS BINARY) = CAST(seed.code AS BINARY)
);

UPDATE sys_permission permission
INNER JOIN (
    SELECT 'EDGE_INSPECTION_VIEW' code, '查看临边巡检' name, '查看临边点位、任务、记录、整改单和统计' description UNION ALL
    SELECT 'EDGE_INSPECTION_MANAGE', '管理临边巡检', '维护临边点位和周期设置，取消、改派任务与整改单' UNION ALL
    SELECT 'EDGE_INSPECTION_SUBMIT', '执行临边巡检', '执行明确分配给自己的固定式临边巡检任务' UNION ALL
    SELECT 'EDGE_INSPECTION_RECTIFY', '临边巡检整改', '整单提交明确分配给自己的临边整改反馈' UNION ALL
    SELECT 'EDGE_INSPECTION_REVIEW', '临边巡检复查', '整单关闭或退回明确分配给自己的临边整改单'
) seed ON CAST(seed.code AS BINARY) = CAST(permission.permission_code AS BINARY)
SET permission.permission_name = seed.name,
    permission.module_code = 'WEB_INSPECTION',
    permission.description = seed.description,
    permission.enabled = 1,
    permission.builtin = 1,
    permission.deleted = 0
WHERE @fixed_edge_already_applied = 0;

SET @inspection_menu_id := (
    SELECT id FROM sys_menu WHERE menu_code = 'WEB_INSPECTION' AND deleted = 0 LIMIT 1
);
INSERT INTO sys_menu(parent_id, client_type, menu_code, menu_name, resource_type, route_path,
                     permission_code, sort_order, visible, enabled, builtin, deleted)
SELECT @inspection_menu_id, 'WEB', 'INSPECTION_EDGE', '临边巡检', 'TAB',
       'INSPECTION_EDGE', 'EDGE_INSPECTION_VIEW', 22, 1, 1, 1, 0
WHERE @fixed_edge_already_applied = 0
  AND @inspection_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu
      WHERE CAST(menu_code AS BINARY) = CAST('INSPECTION_EDGE' AS BINARY)
  );

UPDATE sys_menu
SET parent_id = @inspection_menu_id,
    menu_name = '临边巡检', resource_type = 'TAB', route_path = 'INSPECTION_EDGE',
    permission_code = 'EDGE_INSPECTION_VIEW', sort_order = 22,
    visible = 1, enabled = 1, builtin = 1, deleted = 0,
    update_time = CURRENT_TIMESTAMP
WHERE menu_code = 'INSPECTION_EDGE' AND @fixed_edge_already_applied = 0;

SET @platform_admin_role_id := (
    SELECT id FROM sys_role WHERE role_code = 'PLATFORM_ADMIN' AND deleted = 0 ORDER BY id LIMIT 1
);
INSERT INTO sys_role_permission(role_id, permission_id)
SELECT @platform_admin_role_id, permission.id
FROM sys_permission permission
WHERE @fixed_edge_already_applied = 0
  AND @platform_admin_role_id IS NOT NULL
  AND permission.permission_code IN (
      'EDGE_INSPECTION_VIEW','EDGE_INSPECTION_MANAGE','EDGE_INSPECTION_SUBMIT',
      'EDGE_INSPECTION_RECTIFY','EDGE_INSPECTION_REVIEW'
  ) AND permission.enabled = 1 AND permission.deleted = 0
ON DUPLICATE KEY UPDATE role_id = sys_role_permission.role_id;

INSERT INTO sys_role_menu(role_id, menu_id)
SELECT @platform_admin_role_id, menu.id
FROM sys_menu menu
WHERE @fixed_edge_already_applied = 0
  AND @platform_admin_role_id IS NOT NULL
  AND menu.menu_code = 'INSPECTION_EDGE' AND menu.enabled = 1 AND menu.deleted = 0
ON DUPLICATE KEY UPDATE role_id = sys_role_menu.role_id;

DROP TEMPORARY TABLE IF EXISTS tmp_edge_template_item;
DROP TEMPORARY TABLE IF EXISTS tmp_edge_point_type;

INSERT INTO sys_data_migration(migration_key)
VALUES ('20260826_GENERAL_INSPECTION_FIXED_EDGE_V1') AS incoming
ON DUPLICATE KEY UPDATE migration_key = incoming.migration_key;
