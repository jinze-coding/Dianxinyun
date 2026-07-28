-- 2026-07-28 本地开发环境单项目演示数据重置脚本
--
-- 警告：本脚本会删除 dianxinyun 中全部现有业务数据和测试账号，只保留
-- 系统角色、巡检权限模板、巡检模板及模板项，然后重建一个最小完整演示项目。
-- 仅用于已备份的本地开发库，禁止在生产环境执行。
-- 工程资料、巡检记录和质量问题由 scripts/seed-realistic-business-data.sh
-- 在本脚本执行后通过真实接口生成，确保文件元数据与物理文件一致。

USE dianxinyun;

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM wechat_message_log;
DELETE FROM wechat_subscription_state;
DELETE FROM wechat_access_application;
DELETE FROM sys_user_wechat_binding;
DELETE FROM video_access_log;
DELETE FROM video_layout_config;
DELETE FROM sys_operation_log;
DELETE FROM quality_issue_log;
DELETE FROM quality_issue;
DELETE FROM project_document_version;
DELETE FROM project_document;
DELETE FROM document_folder;
DELETE FROM file_resource;
DELETE FROM inspection_rectification_review_log;
DELETE FROM inspection_rectification;
DELETE FROM inspection_review_log;
DELETE FROM inspection_record_item;
DELETE FROM inspection_record;
DELETE FROM electric_box_qr_log;
DELETE FROM electric_box_inspection_scope;
DELETE FROM electric_box;
DELETE FROM project_inspection_setting;
DELETE FROM safety_education_person;
DELETE FROM safety_education_batch;
DELETE FROM person_certificate;
DELETE FROM person_entry_exit_log;
DELETE FROM temporary_person;
DELETE FROM device_status_record;
DELETE FROM device_info;
DELETE FROM camera_resource;
DELETE FROM external_system_config;
DELETE FROM sys_user_project;
DELETE FROM sys_user_role;
DELETE FROM sys_user;
DELETE FROM project_info;

ALTER TABLE wechat_message_log AUTO_INCREMENT = 1;
ALTER TABLE wechat_subscription_state AUTO_INCREMENT = 1;
ALTER TABLE wechat_access_application AUTO_INCREMENT = 1;
ALTER TABLE sys_user_wechat_binding AUTO_INCREMENT = 1;
ALTER TABLE video_access_log AUTO_INCREMENT = 1;
ALTER TABLE video_layout_config AUTO_INCREMENT = 1;
ALTER TABLE sys_operation_log AUTO_INCREMENT = 1;
ALTER TABLE quality_issue_log AUTO_INCREMENT = 1;
ALTER TABLE quality_issue AUTO_INCREMENT = 1;
ALTER TABLE project_document_version AUTO_INCREMENT = 1;
ALTER TABLE project_document AUTO_INCREMENT = 1;
ALTER TABLE document_folder AUTO_INCREMENT = 1;
ALTER TABLE file_resource AUTO_INCREMENT = 1;
ALTER TABLE inspection_rectification_review_log AUTO_INCREMENT = 1;
ALTER TABLE inspection_rectification AUTO_INCREMENT = 1;
ALTER TABLE inspection_review_log AUTO_INCREMENT = 1;
ALTER TABLE inspection_record_item AUTO_INCREMENT = 1;
ALTER TABLE inspection_record AUTO_INCREMENT = 1;
ALTER TABLE electric_box_qr_log AUTO_INCREMENT = 1;
ALTER TABLE electric_box_inspection_scope AUTO_INCREMENT = 1;
ALTER TABLE electric_box AUTO_INCREMENT = 1;
ALTER TABLE project_inspection_setting AUTO_INCREMENT = 1;
ALTER TABLE safety_education_person AUTO_INCREMENT = 1;
ALTER TABLE safety_education_batch AUTO_INCREMENT = 1;
ALTER TABLE person_certificate AUTO_INCREMENT = 1;
ALTER TABLE person_entry_exit_log AUTO_INCREMENT = 1;
ALTER TABLE temporary_person AUTO_INCREMENT = 1;
ALTER TABLE device_status_record AUTO_INCREMENT = 1;
ALTER TABLE device_info AUTO_INCREMENT = 1;
ALTER TABLE camera_resource AUTO_INCREMENT = 1;
ALTER TABLE external_system_config AUTO_INCREMENT = 1;
ALTER TABLE sys_user_project AUTO_INCREMENT = 1;
ALTER TABLE sys_user_role AUTO_INCREMENT = 1;
ALTER TABLE sys_user AUTO_INCREMENT = 1;
ALTER TABLE project_info AUTO_INCREMENT = 1;

INSERT INTO project_info (
    id, project_name, short_name, area, period, phase, project_status,
    safety_goal, quality_goal, manager, contractor, description,
    start_date, end_date, longitude, latitude, province, city, district,
    address, coordinate_type, deleted
) VALUES (
    1, '智慧工地综合演示项目', '综合演示项目', '12000', '2026.07-2027.12',
    '主体结构施工', 'normal', '重大安全事故为零', '一次验收合格',
    '系统管理员', '演示施工单位',
    '用于 Web 与小程序共同联调的唯一演示项目，所有信息均为虚构演示内容。',
    '2026-07-01', '2027-12-31', 121.507600, 31.233200,
    '上海市', '上海市', '浦东新区', '上海市浦东新区智慧工地演示现场',
    'BD09', 0
);

-- 仅预留平台管理员主体，不提供可登录的默认密码。
-- 后续必须执行 20260728 增量迁移，并通过 ADMIN_RESET_* 显式设置密码。
INSERT INTO sys_user (
    id, username, password, password_login_enabled, real_name, phone, email,
    status, deleted
) VALUES (
    1, 'admin', '!EXPLICIT_RESET_REQUIRED!',
    0, '系统管理员', '19900001000', 'admin@example.test', 1, 0
);

INSERT INTO sys_user_role (user_id, role_id)
SELECT 1, id FROM sys_role WHERE role_code = 'PLATFORM_ADMIN';

INSERT INTO sys_user_project (
    user_id, project_id, project_role_code, inspection_permission_template_id, status
) VALUES (
    1, 1, 'PROJECT_ADMIN',
    (SELECT id FROM inspection_permission_template WHERE template_code = 'PROJECT_ADMIN' LIMIT 1),
    'ACTIVE'
);

INSERT INTO project_inspection_setting (
    project_id, daily_cutoff_time, pre_due_reminder_minutes,
    review_due_hours, rectification_days, enabled
) VALUES (1, '18:00:00', 60, 24, 3, 1);

INSERT INTO electric_box (
    id, project_id, box_code, box_name, install_location,
    responsible_electrician_id, responsible_electrician_name,
    safety_manager_id, safety_manager_name, qr_code, qr_status, status,
    public_code, public_access_enabled, remark, deleted
) VALUES (
    1, 1, 'DEMO-EB-001', '演示一级配电箱', '主体楼一层东侧演示区',
    1, '系统管理员', 1, '系统管理员', 'DEMO-EBQR-001', 'BOUND', 'ACTIVE',
    'DEMO-PUBLIC-001', 1, 'Web 与小程序扫码巡检演示电箱', 0
);

INSERT INTO electric_box_inspection_scope (
    project_id, electric_box_id, included, effective_date, end_date,
    reason, operator_id, operator_name
) VALUES (
    1, 1, 1, DATE_SUB(CURDATE(), INTERVAL 30 DAY), NULL,
    '演示电箱纳入日检', 1, '系统管理员'
);

INSERT INTO camera_resource (
    project_id, camera_name, camera_code, area, camera_type,
    rtsp_url, online_status, deleted
) VALUES (
    1, '演示现场摄像头', 'DEMO-CAM-001', '主体楼东入口',
    '枪机', NULL, 1, 0
);

INSERT INTO device_info (
    project_id, device_name, device_code, device_type, status,
    height, max_load, last_report, remark, deleted
) VALUES (
    1, '演示塔式起重机', 'DEMO-TC-001', 'tower_crane', 'running',
    '60m', '8t', NOW(), '演示设备，不对应真实现场设备', 0
);

INSERT INTO temporary_person (
    id, project_id, name, gender, idcard, phone, unit, role,
    entry_time, status, remark, deleted
) VALUES (
    1, 1, '演示人员', '男', '310101199001010011', '19910002001',
    '演示施工班组', '电工', NOW(), 'EDUCATED', '虚构演示人员', 0
);

INSERT INTO person_entry_exit_log (
    project_id, person_id, action_type, occurred_at,
    operator_id, operator_name, remark
) VALUES (
    1, 1, 'ENTRY', NOW(), 1, '系统管理员', '演示人员入场'
);

INSERT INTO safety_education_batch (
    id, project_id, batch_name, edu_type, training_time, training_place,
    trainer, status, remark, course_hours, exam_type, deleted
) VALUES (
    1, 1, '演示人员三级安全教育', '三级安全教育', NOW(),
    '项目会议室', '系统管理员', 'COMPLETED',
    '单项目演示数据', 2, '现场问答', 0
);

INSERT INTO safety_education_person (
    batch_id, person_id, status, finish_time
) VALUES (1, 1, 'FINISHED', NOW());

SET FOREIGN_KEY_CHECKS = 1;
