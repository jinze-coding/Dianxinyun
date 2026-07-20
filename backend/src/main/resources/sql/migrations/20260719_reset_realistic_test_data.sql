-- 2026-07-19 开发环境拟真测试数据重置脚本
--
-- 警告：本脚本会删除 dianxinyun 中现有业务数据，只保留表结构、角色、
-- 巡检权限模板、巡检模板及模板项。仅用于本地开发库，禁止在生产环境执行。
-- 工程资料文件、巡检记录和质量闭环记录由 scripts/seed-realistic-business-data.sh
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
) VALUES
(1, '1号楼主体结构作业区', '1号楼主体', '28600', '2026.03-2027.08', '主体结构施工', 'normal',
 '重大安全事故为零', '主体结构一次验收合格', '陈志远', '华东建设工程有限公司',
 '覆盖1号楼主体结构、钢筋加工、模板安装和塔吊作业面。',
 '2026-03-01', '2027-08-31', 121.507600, 31.233200, '上海市', '上海市', '浦东新区',
 '上海市浦东新区科创大道建设项目施工现场', 'BD09', 0),
(2, '地下室机电安装作业区', '地下室机电', '15400', '2026.06-2027.03', '机电安装', 'normal',
 '临时用电事故为零', '机电安装一次验收合格', '陈志远', '华东机电安装有限公司',
 '覆盖地下室配电房、设备机房、管线综合和施工电梯作业面。',
 '2026-06-01', '2027-03-31', 121.508100, 31.232700, '上海市', '上海市', '浦东新区',
 '上海市浦东新区科创大道建设项目地下室施工现场', 'BD09', 0),
(3, '场区临建及材料堆场', '临建堆场', '9200', '2026.02-2027.06', '临建使用', 'warning',
 '消防和临电事故为零', '材料分区堆放达标', '陈志远', '华东建设工程有限公司',
 '覆盖办公生活临建、钢材堆场、周转材料区和场区临时道路。',
 '2026-02-15', '2027-06-30', 121.506800, 31.232100, '上海市', '上海市', '浦东新区',
 '上海市浦东新区科创大道建设项目临建及材料场', 'BD09', 0);

-- 账号密码均为本地开发密码 admin123；手机号为合成测试号码。
INSERT INTO sys_user (
    id, username, password, password_login_enabled, real_name, phone, email,
    status, deleted
) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1,
 '系统管理员', '19900001000', 'admin@example.test', 1, 0),
(2, 'project_admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1,
 '陈志远', '19900001001', 'project.admin@example.test', 1, 0),
(3, 'inspector', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1,
 '周明远', '19900001002', 'inspector@example.test', 1, 0),
(4, 'quality_manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1,
 '李若岚', '19900001003', 'quality@example.test', 1, 0),
(5, 'document_manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1,
 '王静怡', '19900001004', 'document@example.test', 1, 0);

INSERT INTO sys_user_role (user_id, role_id)
SELECT 1, id FROM sys_role WHERE role_code = 'PLATFORM_ADMIN';
INSERT INTO sys_user_role (user_id, role_id)
SELECT 2, id FROM sys_role WHERE role_code = 'PROJECT_ADMIN';
INSERT INTO sys_user_role (user_id, role_id)
SELECT 3, id FROM sys_role WHERE role_code = 'USER';
INSERT INTO sys_user_role (user_id, role_id)
SELECT 4, id FROM sys_role WHERE role_code = 'SAFETY_ADMIN';
INSERT INTO sys_user_role (user_id, role_id)
SELECT 5, id FROM sys_role WHERE role_code = 'USER';

INSERT INTO sys_user_project (
    user_id, project_id, project_role_code, inspection_permission_template_id, status
) VALUES
(1, 1, 'PROJECT_ADMIN', (SELECT id FROM inspection_permission_template WHERE template_code='PROJECT_ADMIN' LIMIT 1), 'ACTIVE'),
(1, 2, 'PROJECT_ADMIN', (SELECT id FROM inspection_permission_template WHERE template_code='PROJECT_ADMIN' LIMIT 1), 'ACTIVE'),
(1, 3, 'PROJECT_ADMIN', (SELECT id FROM inspection_permission_template WHERE template_code='PROJECT_ADMIN' LIMIT 1), 'ACTIVE'),
(2, 1, 'PROJECT_ADMIN', (SELECT id FROM inspection_permission_template WHERE template_code='PROJECT_ADMIN' LIMIT 1), 'ACTIVE'),
(2, 2, 'PROJECT_ADMIN', (SELECT id FROM inspection_permission_template WHERE template_code='PROJECT_ADMIN' LIMIT 1), 'ACTIVE'),
(2, 3, 'PROJECT_ADMIN', (SELECT id FROM inspection_permission_template WHERE template_code='PROJECT_ADMIN' LIMIT 1), 'ACTIVE'),
(3, 1, 'USER', (SELECT id FROM inspection_permission_template WHERE template_code='USER' LIMIT 1), 'ACTIVE'),
(3, 2, 'USER', (SELECT id FROM inspection_permission_template WHERE template_code='USER' LIMIT 1), 'ACTIVE'),
(4, 1, 'SAFETY_ADMIN', (SELECT id FROM inspection_permission_template WHERE template_code='SAFETY_ADMIN' LIMIT 1), 'ACTIVE'),
(4, 2, 'SAFETY_ADMIN', (SELECT id FROM inspection_permission_template WHERE template_code='SAFETY_ADMIN' LIMIT 1), 'ACTIVE'),
(5, 1, 'USER', (SELECT id FROM inspection_permission_template WHERE template_code='USER' LIMIT 1), 'ACTIVE'),
(5, 3, 'USER', (SELECT id FROM inspection_permission_template WHERE template_code='USER' LIMIT 1), 'ACTIVE');

INSERT INTO project_inspection_setting (
    project_id, daily_cutoff_time, pre_due_reminder_minutes, review_due_hours,
    rectification_days, enabled
) VALUES
(1, '18:00:00', 60, 24, 3, 1),
(2, '17:30:00', 60, 24, 3, 1),
(3, '17:00:00', 90, 24, 2, 1);

INSERT INTO electric_box (
    id, project_id, box_code, box_name, install_location,
    responsible_electrician_id, responsible_electrician_name,
    safety_manager_id, safety_manager_name, qr_code, qr_status, status,
    public_code, public_access_enabled, remark, deleted
) VALUES
(1, 1, 'EB-1F-AP-01', '1号楼东侧一级配电箱', '1号楼东侧钢筋加工区', 3, '周明远', 4, '李若岚', 'EBQR-1F-AP-01', 'BOUND', 'ACTIVE', 'PUB-1F-AP-01', 1, '负责钢筋加工区动力和照明', 0),
(2, 1, 'EB-1F-AP-02', '1号楼西侧二级配电箱', '1号楼西侧木工加工区', 3, '周明远', 4, '李若岚', 'EBQR-1F-AP-02', 'BOUND', 'ACTIVE', 'PUB-1F-AP-02', 1, '木工加工设备专用', 0),
(3, 1, 'EB-1F-B1-01', '地下室一层照明配电箱', '1号楼地下室一层东通道', 3, '周明远', 4, '李若岚', 'EBQR-1F-B1-01', 'BOUND', 'ACTIVE', 'PUB-1F-B1-01', 1, '地下室临时照明', 0),
(4, 1, 'EB-1F-TC-01', '1号塔吊专用配电箱', '1号楼北侧1号塔吊基础旁', 3, '周明远', 4, '李若岚', 'EBQR-1F-TC-01', 'BOUND', 'ACTIVE', 'PUB-1F-TC-01', 1, '塔吊动力专用配电箱', 0),
(5, 2, 'EB-MEP-B2-01', '地下二层机房配电箱', '地下二层制冷机房入口', 3, '周明远', 4, '李若岚', 'EBQR-MEP-B2-01', 'BOUND', 'ACTIVE', 'PUB-MEP-B2-01', 1, '机房安装临时用电', 0),
(6, 2, 'EB-MEP-EL-01', '施工电梯临时配电箱', '2号施工电梯首层入口', 3, '周明远', 4, '李若岚', 'EBQR-MEP-EL-01', 'BOUND', 'ACTIVE', 'PUB-MEP-EL-01', 1, '施工电梯动力专用', 0),
(7, 3, 'EB-YD-01', '材料堆场总配电箱', '钢材堆场东南角防护棚内', NULL, NULL, 4, '李若岚', 'EBQR-YD-01', 'BOUND', 'ACTIVE', 'PUB-YD-01', 1, '材料加工及夜间照明', 0);

INSERT INTO electric_box_inspection_scope (
    project_id, electric_box_id, included, effective_date, end_date,
    reason, operator_id, operator_name
) VALUES
(1, 1, 1, '2026-07-01', NULL, '投入使用并纳入日检', 2, '陈志远'),
(1, 2, 1, '2026-07-01', NULL, '投入使用并纳入日检', 2, '陈志远'),
(1, 3, 1, '2026-07-10', NULL, '地下室作业面启用', 2, '陈志远'),
(1, 4, 1, '2026-07-01', NULL, '塔吊安装验收后纳入日检', 2, '陈志远'),
(2, 5, 1, '2026-07-12', NULL, '机房安装作业开始', 2, '陈志远'),
(2, 6, 1, '2026-07-12', NULL, '施工电梯投入使用', 2, '陈志远'),
(3, 7, 1, '2026-07-01', NULL, '材料堆场投入使用', 2, '陈志远');

INSERT INTO camera_resource (
    project_id, camera_name, camera_code, area, camera_type, rtsp_url,
    online_status, deleted
) VALUES
(1, '1号楼东入口摄像头', 'CAM-1F-EAST-01', '1号楼东入口', '枪机', NULL, 1, 0),
(1, '1号塔吊全景摄像头', 'CAM-1F-TC-01', '1号塔吊', '球机', NULL, 1, 0),
(1, '地下室通道摄像头', 'CAM-1F-B1-01', '地下室一层', '枪机', NULL, 0, 0),
(2, '地下二层机房摄像头', 'CAM-MEP-B2-01', '制冷机房', '半球', NULL, 1, 0),
(3, '钢材堆场摄像头', 'CAM-YD-01', '钢材堆场', '球机', NULL, 1, 0);

INSERT INTO device_info (
    project_id, device_name, device_code, device_type, status, height,
    max_load, last_report, remark, deleted
) VALUES
(1, '1号塔式起重机', 'TC-01', 'tower_crane', 'running', '65m', '8t', NOW(), '已完成月度维保', 0),
(1, '1号施工电梯', 'EL-01', 'elevator', 'running', '58m', '2t', NOW(), '人货两用施工升降机', 0),
(1, '扬尘在线监测仪', 'ENV-01', 'monitor', 'running', NULL, NULL, NOW(), '监测PM2.5、PM10和噪声', 0),
(2, '地下室临时排水泵组', 'PUMP-B2-01', 'other', 'running', NULL, NULL, NOW(), '两用一备', 0),
(3, '材料堆场喷淋控制器', 'SPRAY-YD-01', 'other', 'abnormal', NULL, NULL, DATE_SUB(NOW(), INTERVAL 2 HOUR), '2号喷头压力偏低，待检修', 0);

-- 合成测试人员，不对应真实身份证或手机号。
INSERT INTO temporary_person (
    project_id, name, gender, idcard, phone, unit, role, entry_time,
    status, remark, deleted
) VALUES
(1, '张建国', '男', '310101199001010011', '19910002001', '华东建设劳务一队', '钢筋工', '2026-07-01 07:30:00', 'EDUCATED', '已完成三级教育', 0),
(1, '刘海峰', '男', '310101199002020022', '19910002002', '华东建设劳务一队', '木工', '2026-07-02 07:35:00', 'EDUCATED', '已完成三级教育', 0),
(1, '赵晓梅', '女', '310101199003030033', '19910002003', '华东建设劳务一队', '资料员', '2026-07-03 08:00:00', 'EDUCATED', '已完成三级教育', 0),
(2, '孙启明', '男', '310101199004040044', '19910002004', '华东机电安装班组', '电工', '2026-07-12 07:40:00', 'EDUCATED', '特种作业证件已核验', 0),
(2, '郭文杰', '男', '310101199005050055', '19910002005', '华东机电安装班组', '管道工', '2026-07-18 08:10:00', 'WAIT_EDUCATION', '待完成项目级教育', 0),
(3, '何志鹏', '男', '310101199006060066', '19910002006', '场区综合班组', '材料员', '2026-06-28 08:00:00', 'EDUCATED', '负责材料进出场登记', 0);

SET FOREIGN_KEY_CHECKS = 1;
