-- =============================================
-- 电信云平台项目现场综合管理系统
-- 数据库初始化脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS site_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE site_platform;

-- =============================================
-- 1. 项目信息表
-- =============================================
DROP TABLE IF EXISTS project_info;
CREATE TABLE project_info (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '项目ID',
    project_name    VARCHAR(200) NOT NULL COMMENT '项目名称',
    short_name      VARCHAR(50) COMMENT '项目简称',
    area            VARCHAR(50) COMMENT '建筑面积(㎡)',
    period          VARCHAR(100) COMMENT '工期',
    phase           VARCHAR(50) COMMENT '当前阶段',
    project_status  VARCHAR(20) DEFAULT 'normal' COMMENT '项目状态: normal/warning/stopped',
    safety_goal     VARCHAR(200) COMMENT '安全目标',
    quality_goal    VARCHAR(200) COMMENT '质量目标',
    manager         VARCHAR(50) COMMENT '项目经理',
    contractor      VARCHAR(200) COMMENT '施工单位',
    description     TEXT COMMENT '项目描述',
    start_date      DATE COMMENT '开工日期',
    end_date        DATE COMMENT '预计截止日期',
    longitude       DECIMAL(10,6) COMMENT '经度',
    latitude        DECIMAL(10,6) COMMENT '纬度',
    address         VARCHAR(500) COMMENT '详细地址',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目信息表';

-- =============================================
-- 2. 用户表
-- =============================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username        VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password        VARCHAR(200) NOT NULL COMMENT '密码',
    real_name       VARCHAR(50) COMMENT '真实姓名',
    phone           VARCHAR(20) COMMENT '手机号',
    email           VARCHAR(100) COMMENT '邮箱',
    status          TINYINT DEFAULT 1 COMMENT '状态: 0禁用 1启用',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =============================================
-- 3. 角色表
-- =============================================
DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    role_name       VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code       VARCHAR(50) NOT NULL COMMENT '角色编码',
    description     VARCHAR(200) COMMENT '描述',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- =============================================
-- 4. 用户角色关联表
-- =============================================
DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    role_id         BIGINT NOT NULL COMMENT '角色ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- =============================================
-- 5. 用户项目权限表
-- =============================================
DROP TABLE IF EXISTS sys_user_project;
CREATE TABLE sys_user_project (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户项目权限表';

-- =============================================
-- 6. 外部系统配置表
-- =============================================
DROP TABLE IF EXISTS external_system_config;
CREATE TABLE external_system_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    system_name     VARCHAR(100) NOT NULL COMMENT '系统名称',
    system_type     VARCHAR(50) COMMENT '系统类型: personnel/safety/device',
    access_url      VARCHAR(500) COMMENT '访问地址',
    status          VARCHAR(20) DEFAULT 'normal' COMMENT '状态: normal/abnormal',
    description     VARCHAR(200) COMMENT '描述',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部系统配置表';

-- =============================================
-- 7. 临时人员表
-- =============================================
DROP TABLE IF EXISTS temporary_person;
CREATE TABLE temporary_person (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '人员ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    name            VARCHAR(50) NOT NULL COMMENT '姓名',
    gender          VARCHAR(10) COMMENT '性别',
    idcard          VARCHAR(20) COMMENT '身份证号',
    phone           VARCHAR(20) COMMENT '手机号',
    unit            VARCHAR(100) COMMENT '所属单位',
    role            VARCHAR(50) COMMENT '工种',
    entry_time      DATETIME COMMENT '入场时间',
    status          VARCHAR(20) DEFAULT 'WAIT_EDUCATION' COMMENT '状态: WAIT_EDUCATION/EDUCATED/LEFT',
    remark          VARCHAR(500) COMMENT '备注',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='临时人员表';

-- =============================================
-- 8. 安全教育批次表
-- =============================================
DROP TABLE IF EXISTS safety_education_batch;
CREATE TABLE safety_education_batch (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '批次ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    batch_name      VARCHAR(200) NOT NULL COMMENT '批次名称',
    edu_type        VARCHAR(50) DEFAULT '三级安全教育' COMMENT '教育类型',
    training_time   DATETIME COMMENT '培训时间',
    training_place  VARCHAR(100) COMMENT '培训地点',
    trainer         VARCHAR(50) COMMENT '培训讲师',
    status          VARCHAR(20) DEFAULT 'NOT_STARTED' COMMENT '状态: NOT_STARTED/IN_PROGRESS/COMPLETED',
    remark          VARCHAR(500) COMMENT '备注',
    course_hours    INT COMMENT '培训课时',
    exam_type       VARCHAR(100) COMMENT '考核方式',
    training_material VARCHAR(200) COMMENT '培训课件',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全教育批次表';

-- =============================================
-- 9. 安全教育人员关联表
-- =============================================
DROP TABLE IF EXISTS safety_education_person;
CREATE TABLE safety_education_person (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id        BIGINT NOT NULL COMMENT '批次ID',
    person_id       BIGINT NOT NULL COMMENT '人员ID',
    status          VARCHAR(20) DEFAULT 'WAITING' COMMENT '状态: WAITING/FINISHED',
    finish_time     DATETIME COMMENT '完成时间',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全教育人员关联表';

-- =============================================
-- 10. 文件资料表
-- =============================================
DROP TABLE IF EXISTS file_resource;
CREATE TABLE file_resource (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '文件ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    file_name       VARCHAR(200) NOT NULL COMMENT '文件名',
    file_type       VARCHAR(50) COMMENT '文件类型: training/document/signature/certificate/other',
    file_path       VARCHAR(500) NOT NULL COMMENT '文件存储路径',
    file_size       BIGINT COMMENT '文件大小(字节)',
    business_type   VARCHAR(50) COMMENT '业务类型: safety_education/person/training',
    business_id     BIGINT COMMENT '关联业务ID',
    uploader_id     BIGINT COMMENT '上传人ID',
    status          VARCHAR(20) DEFAULT 'UPLOADED' COMMENT '状态: UPLOADED/PENDING_CONFIRM/ARCHIVED',
    remark          VARCHAR(500) COMMENT '备注',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件资料表';

-- =============================================
-- 11. 摄像头资源表
-- =============================================
DROP TABLE IF EXISTS camera_resource;
CREATE TABLE camera_resource (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '摄像头ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    camera_name     VARCHAR(100) NOT NULL COMMENT '摄像头名称',
    camera_code     VARCHAR(100) COMMENT '摄像头编号',
    area            VARCHAR(50) COMMENT '所属区域',
    camera_type     VARCHAR(50) COMMENT '摄像头类型',
    rtsp_url        VARCHAR(500) COMMENT 'RTSP地址',
    online_status   TINYINT DEFAULT 1 COMMENT '在线状态: 0离线 1在线',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='摄像头资源表';

-- =============================================
-- 12. 视频窗口布局配置表
-- =============================================
DROP TABLE IF EXISTS video_layout_config;
CREATE TABLE video_layout_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    layout_type     VARCHAR(20) DEFAULT 'quad' COMMENT '布局类型: single/quad/eight/sixteen',
    layout_data     TEXT COMMENT '布局数据(JSON格式)',
    is_default      TINYINT DEFAULT 0 COMMENT '是否默认布局',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频窗口布局配置表';

-- =============================================
-- 13. 设备信息表
-- =============================================
DROP TABLE IF EXISTS device_info;
CREATE TABLE device_info (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '设备ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    device_name     VARCHAR(100) NOT NULL COMMENT '设备名称',
    device_code     VARCHAR(100) COMMENT '设备编号',
    device_type     VARCHAR(50) NOT NULL COMMENT '设备类型: tower_crane/elevator/monitor/other',
    status          VARCHAR(20) DEFAULT 'running' COMMENT '状态: running/stopped/abnormal',
    height          VARCHAR(50) COMMENT '高度(塔吊)',
    max_load        VARCHAR(50) COMMENT '最大载重',
    last_report     DATETIME COMMENT '最近上报时间',
    remark          VARCHAR(500) COMMENT '备注',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备信息表';

-- =============================================
-- 14. 设备状态记录表
-- =============================================
DROP TABLE IF EXISTS device_status_record;
CREATE TABLE device_status_record (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id       BIGINT NOT NULL COMMENT '设备ID',
    status          VARCHAR(20) NOT NULL COMMENT '状态',
    load_value      VARCHAR(50) COMMENT '载重值',
    height_value    VARCHAR(50) COMMENT '高度值',
    wind_speed      VARCHAR(50) COMMENT '风速',
    record_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备状态记录表';

-- =============================================
-- 15. 视频访问日志表
-- =============================================
DROP TABLE IF EXISTS video_access_log;
CREATE TABLE video_access_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    camera_id       BIGINT NOT NULL COMMENT '摄像头ID',
    access_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
    ip_address      VARCHAR(50) COMMENT 'IP地址'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频访问日志表';

-- =============================================
-- 16. 操作日志表
-- =============================================
DROP TABLE IF EXISTS sys_operation_log;
CREATE TABLE sys_operation_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT COMMENT '用户ID',
    username        VARCHAR(50) COMMENT '用户名',
    operation_type  VARCHAR(50) NOT NULL COMMENT '操作类型',
    operation_desc  VARCHAR(500) COMMENT '操作描述',
    business_type   VARCHAR(50) COMMENT '业务类型',
    business_id     BIGINT COMMENT '业务ID',
    ip_address      VARCHAR(50) COMMENT 'IP地址',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- =============================================
-- 初始化测试数据
-- =============================================

-- 插入测试项目
INSERT INTO project_info (project_name, short_name, area, period, phase, project_status, safety_goal, quality_goal, manager, contractor, start_date, end_date, longitude, latitude, address) VALUES
('电信云数据中心A区', 'A区', '50000', '2025.06-2026.12', '施工中', 'normal', '零事故', '优良', '张三', '中建一局', '2025-06-01', '2026-12-31', 116.397428, 39.909204, '北京市东城区天安门广场东侧'),
('电信云数据中心B区', 'B区', '35000', '2025.09-2027.03', '收尾中', 'warning', '零事故', '优良', '李四', '中建二局', '2025-09-01', '2027-03-31', 116.461523, 39.905285, '北京市朝阳区国贸中心B座'),
('上海浦东云枢纽', '浦东枢纽', '62000', '2025.03-2027.06', '施工中', 'normal', '零事故', '鲁班奖', '王建国', '中建八局', '2025-03-15', '2027-06-30', 121.543743, 31.233568, '上海市浦东新区陆家嘴环路1000号'),
('广州天河数据中心', '天河DC', '48000', '2025.07-2027.09', '基础施工', 'normal', '零事故', '优良', '陈志强', '中建四局', '2025-07-01', '2027-09-30', 113.331520, 23.126272, '广州市天河区珠江新城华夏路8号'),
('成都高新云计算中心', '高新云', '38000', '2025.04-2026.12', '主体施工', 'normal', '零事故', '天府杯', '赵明', '中建三局', '2025-04-20', '2026-12-31', 104.061540, 30.570890, '成都市高新区天府大道中段688号'),
('武汉光谷数据中心', '光谷DC', '42000', '2025.08-2027.04', '装修阶段', 'warning', '零事故', '优良', '刘伟', '中建二局', '2025-08-10', '2027-04-30', 114.421140, 30.506650, '武汉市洪山区光谷大道77号'),
('深圳南山5G基站园区', '南山5G', '28000', '2025.11-2026.08', '设备安装', 'normal', '零事故', '优良', '黄丽华', '华为建设', '2025-11-01', '2026-08-31', 113.950720, 22.534570, '深圳市南山区科技园科苑路15号'),
('杭州西湖数据枢纽', '西湖枢纽', '55000', '2025.05-2027.02', '主体施工', 'normal', '零事故', '钱江杯', '周伟明', '中建一局', '2025-05-15', '2027-02-28', 120.149290, 30.278470, '杭州市西湖区文三路508号');

-- 插入测试用户 (密码: 123456, BCrypt加密)
INSERT INTO sys_user (username, password, real_name, phone, email, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '系统管理员', '13800138000', 'admin@site.com', 1),
('manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '项目经理', '13800138001', 'manager@site.com', 1);

-- 插入角色
INSERT INTO sys_role (role_name, role_code, description) VALUES
('平台管理员', 'PLATFORM_ADMIN', '平台管理员，拥有所有权限'),
('项目管理员', 'PROJECT_ADMIN', '项目管理员，管理指定项目'),
('安全管理员', 'SAFETY_ADMIN', '安全管理员，负责安全管理'),
('普通用户', 'USER', '普通用户，仅查看权限');

-- 关联用户角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1), (2, 2);

-- 关联用户项目权限
INSERT INTO sys_user_project (user_id, project_id) VALUES (1, 1), (1, 2), (2, 1);

-- 插入摄像头模拟数据
INSERT INTO camera_resource (project_id, camera_name, camera_code, area, camera_type, online_status) VALUES
(1, '东门摄像头', 'CAM-001', '东门', '海康', 1),
(1, '西门摄像头', 'CAM-002', '西门', '海康', 1),
(1, '塔吊摄像头1', 'CAM-003', '塔吊区', '海康', 1),
(1, '施工区摄像头', 'CAM-004', '施工区', '海康', 0),
(2, '北门摄像头', 'CAM-005', '北门', '海康', 1),
(2, '材料区摄像头', 'CAM-006', '材料区', '海康', 1);

-- 插入设备模拟数据
INSERT INTO device_info (project_id, device_name, device_code, device_type, status, height, max_load, last_report) VALUES
(1, '1号塔吊', 'TC-001', 'tower_crane', 'running', '120m', '8T', NOW()),
(1, '2号塔吊', 'TC-002', 'tower_crane', 'running', '100m', '6T', NOW()),
(1, '施工电梯', 'EL-001', 'elevator', 'running', '80m', '2T', NOW()),
(1, '监控摄像头组', 'MON-001', 'monitor', 'running', NULL, NULL, NOW()),
(2, '3号塔吊', 'TC-003', 'tower_crane', 'stopped', '90m', '6T', NOW());

-- 插入临时人员模拟数据
INSERT INTO temporary_person (project_id, name, gender, idcard, phone, unit, role, entry_time, status) VALUES
(1, '王五', '男', '110101199001011234', '13900139000', '钢筋班组', '钢筋工', NOW(), 'EDUCATED'),
(1, '赵六', '男', '110101199502022345', '13900139001', '木工班组', '木工', NOW(), 'WAIT_EDUCATION'),
(1, '孙七', '女', '110101199803033456', '13900139002', '混凝土班组', '混凝土工', NOW(), 'EDUCATED'),
(2, '周八', '男', '110101200104044567', '13900139003', '钢结构班组', '焊工', NOW(), 'LEFT');

-- 插入安全教育培训批次
INSERT INTO safety_education_batch (project_id, batch_name, edu_type, training_time, training_place, trainer, status) VALUES
(1, '2026年第一期安全培训', '三级安全教育', '2026-04-01 09:00:00', '项目部会议室', '张三', 'COMPLETED'),
(1, '2026年第二期安全培训', '三级安全教育', '2026-04-15 09:00:00', '项目部会议室', '李四', 'IN_PROGRESS');

-- 插入文件资料模拟数据
INSERT INTO file_resource (project_id, file_name, file_type, file_path, file_size, business_type, status) VALUES
(1, '安全培训资料.pdf', 'training', '/uploads/training/安全培训资料.pdf', 1024000, 'safety_education', 'ARCHIVED'),
(1, '施工许可证.jpg', 'document', '/uploads/document/施工许可证.jpg', 512000, NULL, 'UPLOADED'),
(1, '人员签字表.pdf', 'signature', '/uploads/signature/人员签字表.pdf', 256000, 'person', 'PENDING_CONFIRM');

-- 插入外部系统配置
INSERT INTO external_system_config (project_id, system_name, system_type, access_url, status) VALUES
(1, '原人员管理系统', 'personnel', 'http://192.168.1.100:8080', 'normal'),
(1, '塔吊监控系统', 'device', 'http://192.168.1.101:8080', 'normal');
