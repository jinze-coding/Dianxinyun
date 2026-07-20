-- =============================================
-- 电信云平台项目现场综合管理系统
-- 数据库初始化脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS dianxinyun DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE dianxinyun;

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
    province        VARCHAR(64) COMMENT '省',
    city            VARCHAR(64) COMMENT '市',
    district        VARCHAR(64) COMMENT '区县',
    address         VARCHAR(500) COMMENT '详细地址',
    coordinate_type VARCHAR(32) DEFAULT 'BD09' COMMENT '坐标系类型',
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
    password_login_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许账号密码登录: 1是 0否',
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
-- 5. 电箱巡检权限模板表
-- =============================================
DROP TABLE IF EXISTS inspection_permission_template;
CREATE TABLE inspection_permission_template (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '权限模板ID',
    template_name   VARCHAR(80) NOT NULL COMMENT '模板名称',
    template_code   VARCHAR(80) NOT NULL COMMENT '模板编码',
    description     VARCHAR(255) COMMENT '说明',
    permission_codes TEXT NOT NULL COMMENT '权限码CSV',
    enabled         TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用: 1启用 0停用',
    builtin         TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置模板: 1是 0否',
    deleted         TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_inspection_permission_template_code (template_code),
    KEY idx_inspection_permission_template_enabled (enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电箱巡检权限模板';

-- =============================================
-- 6. 用户项目权限表
-- =============================================
DROP TABLE IF EXISTS sys_user_project;
CREATE TABLE sys_user_project (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    project_role_code VARCHAR(40) NOT NULL DEFAULT 'USER' COMMENT '项目内职责: PROJECT_ADMIN/SAFETY_ADMIN/USER',
    inspection_permission_template_id BIGINT NULL COMMENT '电箱巡检权限模板ID',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '项目访问状态: ACTIVE/DISABLED',
    status_reason   VARCHAR(300) COMMENT '项目授权启停原因',
    status_changed_by BIGINT COMMENT '最近启停操作人',
    status_changed_time DATETIME COMMENT '最近启停时间',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_sys_user_project_user_project (user_id, project_id),
    KEY idx_sys_user_project_permission_template (inspection_permission_template_id),
    KEY idx_sys_user_project_status (project_id, status, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户项目权限表';

-- 小程序微信身份绑定
DROP TABLE IF EXISTS sys_user_wechat_binding;
CREATE TABLE sys_user_wechat_binding (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id            BIGINT NOT NULL,
    app_id             VARCHAR(80) NOT NULL,
    openid             VARCHAR(128) NOT NULL,
    unionid            VARCHAR(128),
    phone              VARCHAR(20),
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED/UNBOUND',
    deleted            TINYINT NOT NULL DEFAULT 0,
    active_user_id     BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' AND deleted = 0 THEN user_id ELSE NULL END) STORED,
    bind_time          DATETIME DEFAULT CURRENT_TIMESTAMP,
    last_login_time    DATETIME,
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wechat_binding_openid (app_id, openid, deleted),
    UNIQUE KEY uk_wechat_binding_active_user (app_id, active_user_id),
    KEY idx_wechat_binding_user (user_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户微信绑定';

DROP TABLE IF EXISTS wechat_access_application;
CREATE TABLE wechat_access_application (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id             VARCHAR(80) NOT NULL,
    openid             VARCHAR(128) NOT NULL,
    phone              VARCHAR(20),
    real_name          VARCHAR(50),
    project_id         BIGINT NOT NULL,
    source_type        VARCHAR(40) NOT NULL DEFAULT 'ELECTRIC_BOX',
    source_id          BIGINT,
    matched_user_id    BIGINT,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_id        BIGINT,
    reviewer_name      VARCHAR(50),
    review_comment     VARCHAR(300),
    review_time        DATETIME,
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_wechat_application_project (project_id, status, create_time),
    KEY idx_wechat_application_openid (app_id, openid, project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信注册和项目权限申请';

-- =============================================
-- 7. 外部系统配置表
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
-- 9A. 人员进退场流水
-- =============================================
DROP TABLE IF EXISTS person_entry_exit_log;
CREATE TABLE person_entry_exit_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '进退场流水ID',
    project_id      BIGINT NOT NULL COMMENT '项目ID',
    person_id       BIGINT NOT NULL COMMENT '人员ID',
    action_type     VARCHAR(20) NOT NULL COMMENT '动作: ENTRY/EXIT',
    occurred_at     DATETIME NOT NULL COMMENT '业务发生时间',
    operator_id     BIGINT NOT NULL COMMENT '操作人ID',
    operator_name   VARCHAR(50) COMMENT '操作人姓名快照',
    remark          VARCHAR(500) COMMENT '备注',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_person_movement_person (person_id, occurred_at),
    KEY idx_person_movement_project (project_id, occurred_at),
    KEY idx_person_movement_action (project_id, action_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员进退场流水';

-- =============================================
-- 9B. 人员特种作业及资格证件
-- =============================================
DROP TABLE IF EXISTS person_certificate;
CREATE TABLE person_certificate (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '证件ID',
    project_id          BIGINT NOT NULL COMMENT '项目ID',
    person_id           BIGINT NOT NULL COMMENT '人员ID',
    certificate_type    VARCHAR(80) NOT NULL COMMENT '证件类型',
    certificate_no      VARCHAR(100) NOT NULL COMMENT '证件编号',
    issue_date          DATE COMMENT '发证日期',
    expiry_date         DATE COMMENT '到期日期',
    file_id             BIGINT COMMENT '证件附件ID',
    remark              VARCHAR(500) COMMENT '备注',
    deleted             TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_person_certificate_person (person_id, deleted, expiry_date),
    KEY idx_person_certificate_project (project_id, deleted, expiry_date),
    KEY idx_person_certificate_no (project_id, certificate_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员特种作业及资格证件';

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
    storage_provider VARCHAR(20) COMMENT '存储提供方: local/minio',
    storage_key     VARCHAR(500) COMMENT '存储对象键',
    original_file_name VARCHAR(255) COMMENT '原始文件名',
    mime_type       VARCHAR(150) COMMENT 'MIME 类型',
    file_extension  VARCHAR(20) COMMENT '文件扩展名',
    sha256          CHAR(64) COMMENT '文件 SHA-256',
    file_size       BIGINT COMMENT '文件大小(字节)',
    business_type   VARCHAR(50) COMMENT '业务类型: safety_education/person/training',
    business_id     BIGINT COMMENT '关联业务ID',
    uploader_id     BIGINT COMMENT '上传人ID',
    status          VARCHAR(20) DEFAULT 'UPLOADED' COMMENT '状态: UPLOADED/PENDING_CONFIRM/ARCHIVED',
    remark          VARCHAR(500) COMMENT '备注',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_file_business (project_id, business_type, business_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件资料表';

-- =============================================
-- 10A. 工程资料库目录、资料与版本
-- =============================================
DROP TABLE IF EXISTS project_document_version;
DROP TABLE IF EXISTS project_document;
DROP TABLE IF EXISTS document_folder;
CREATE TABLE document_folder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '历史兼容字段，一级目录固定为0',
    folder_name VARCHAR(100) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    active_folder_name VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN folder_name ELSE NULL END) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_document_folder_active_name (project_id, parent_id, active_folder_name),
    KEY idx_document_folder_project (project_id, deleted, parent_id, sort_no),
    CONSTRAINT chk_document_folder_root_only CHECK (parent_id = 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程资料目录';

CREATE TABLE project_document (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    folder_id BIGINT NOT NULL DEFAULT 0,
    document_no VARCHAR(100),
    title VARCHAR(200) NOT NULL,
    category VARCHAR(40) NOT NULL DEFAULT 'PROJECT_DATA' COMMENT '历史兼容字段，正式界面不再维护',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    current_version_id BIGINT,
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100),
    remark VARCHAR(1000),
    deleted TINYINT NOT NULL DEFAULT 0,
    active_title VARCHAR(200) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN title ELSE NULL END) STORED,
    active_document_no VARCHAR(100) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN document_no ELSE NULL END) STORED,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_document_active_title (project_id, folder_id, active_title),
    UNIQUE KEY uk_project_document_active_no (project_id, active_document_no),
    KEY idx_project_document_project (project_id, deleted, status, update_time),
    KEY idx_project_document_folder (project_id, folder_id, deleted, update_time),
    KEY idx_project_document_no (project_id, document_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程资料主表';

CREATE TABLE project_document_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    file_resource_id BIGINT NOT NULL,
    change_note VARCHAR(500),
    created_by BIGINT NOT NULL,
    created_by_name VARCHAR(100),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_project_document_version (document_id, version_no),
    KEY idx_document_version_file (file_resource_id),
    KEY idx_document_version_time (document_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工程资料版本';

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
-- 17. 电箱台账
-- =============================================
DROP TABLE IF EXISTS electric_box;
CREATE TABLE electric_box (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '电箱ID',
    project_id BIGINT NOT NULL COMMENT '项目ID',
    box_code VARCHAR(64) NOT NULL COMMENT '电箱编号',
    box_name VARCHAR(100) COMMENT '电箱名称',
    install_location VARCHAR(200) NOT NULL COMMENT '安装位置',
    responsible_electrician_id BIGINT COMMENT '负责电工ID',
    responsible_electrician_name VARCHAR(50) COMMENT '负责电工姓名',
    safety_manager_id BIGINT COMMENT '安全负责人ID',
    safety_manager_name VARCHAR(50) COMMENT '安全负责人姓名',
    qr_code VARCHAR(100) COMMENT '内部二维码编码',
    qr_status VARCHAR(20) DEFAULT 'BOUND' COMMENT 'BOUND/DISABLED/REPLACED',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/REMOVED',
    public_code VARCHAR(100) NOT NULL COMMENT '公开只读扫码码',
    public_access_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '公开访问是否启用',
    remark VARCHAR(500) COMMENT '备注',
    deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_electric_box_public_code (public_code),
    UNIQUE KEY uk_electric_box_project_code (project_id, box_code, deleted),
    UNIQUE KEY uk_electric_box_project_qr (project_id, qr_code, deleted),
    KEY idx_electric_box_project (project_id),
    KEY idx_electric_box_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电箱台账表';

-- =============================================
-- 18. 电箱二维码生命周期留痕
-- =============================================
DROP TABLE IF EXISTS electric_box_qr_log;
CREATE TABLE electric_box_qr_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    electric_box_id BIGINT NOT NULL,
    box_code VARCHAR(64) NOT NULL,
    action_type VARCHAR(30) NOT NULL COMMENT 'GENERATE/PRINT/REBIND/DISABLE/REMOVE',
    qr_type VARCHAR(20) NOT NULL COMMENT 'INTERNAL/PUBLIC',
    old_qr_code VARCHAR(120),
    new_qr_code VARCHAR(120),
    operator_user_id BIGINT,
    operator_username VARCHAR(50),
    reason VARCHAR(300),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_eb_qr_log_box (electric_box_id, create_time),
    KEY idx_eb_qr_log_project (project_id, create_time),
    KEY idx_eb_qr_log_old_code (old_qr_code),
    KEY idx_eb_qr_log_new_code (new_qr_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电箱二维码操作日志表';

-- =============================================
-- 19. 现场检查模板及模板项
-- =============================================
DROP TABLE IF EXISTS inspection_template;
CREATE TABLE inspection_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    frequency VARCHAR(20) DEFAULT 'DAILY',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    remark VARCHAR(500),
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_inspection_template_code (template_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查模板表';

DROP TABLE IF EXISTS inspection_template_item;
CREATE TABLE inspection_template_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    template_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    input_type VARCHAR(30) DEFAULT 'NORMAL_ABNORMAL',
    required TINYINT DEFAULT 1,
    sort_order INT DEFAULT 0,
    abnormal_requirement VARCHAR(300),
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_template_item_code (template_code, item_code, deleted),
    KEY idx_template_item_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查模板项表';

-- =============================================
-- 20. 现场检查记录及复核留痕
-- =============================================
DROP TABLE IF EXISTS inspection_record;
CREATE TABLE inspection_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    electric_box_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    source VARCHAR(40) NOT NULL COMMENT 'ELECTRICIAN_DAILY/SAFETY_SPOT_CHECK',
    problem_category VARCHAR(50),
    check_date DATE NOT NULL,
    inspector_id BIGINT NOT NULL,
    inspector_name VARCHAR(50),
    status VARCHAR(40) DEFAULT 'REVIEW_PENDING',
    review_status VARCHAR(40) DEFAULT 'PENDING',
    reviewer_id BIGINT,
    reviewer_name VARCHAR(50),
    review_time DATETIME,
    review_due_time DATETIME,
    assigned_reviewer_id BIGINT,
    assigned_reviewer_name VARCHAR(50),
    review_comment VARCHAR(1000),
    review_overdue TINYINT NOT NULL DEFAULT 0,
    outer_photo_file_ids VARCHAR(500),
    inner_photo_file_ids VARCHAR(500),
    abnormal_count INT DEFAULT 0,
    remark VARCHAR(1000),
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_inspection_record_project_month (project_id, check_date),
    KEY idx_inspection_record_box_date (electric_box_id, check_date),
    KEY idx_inspection_record_status (status),
    KEY idx_inspection_record_review_assignment (project_id, status, assigned_reviewer_id, review_due_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查记录主表';

DROP TABLE IF EXISTS inspection_review_log;
CREATE TABLE inspection_review_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    electric_box_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    from_reviewer_id BIGINT,
    from_reviewer_name VARCHAR(50),
    to_reviewer_id BIGINT,
    to_reviewer_name VARCHAR(50),
    operator_id BIGINT,
    operator_name VARCHAR(50),
    comment VARCHAR(1000),
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_review_log_record (record_id, create_time),
    KEY idx_review_log_project (project_id, create_time),
    KEY idx_review_log_action (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查记录复核留痕表';

DROP TABLE IF EXISTS inspection_record_item;
CREATE TABLE inspection_record_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id BIGINT NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    result VARCHAR(30) NOT NULL,
    description VARCHAR(500),
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_record_item_record (record_id),
    KEY idx_record_item_result (result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查项结果明细表';

-- =============================================
-- 21. 检查整改闭环及复查留痕
-- =============================================
DROP TABLE IF EXISTS inspection_rectification;
CREATE TABLE inspection_rectification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    electric_box_id BIGINT NOT NULL,
    inspection_record_id BIGINT,
    record_item_id BIGINT,
    box_code VARCHAR(64),
    problem_desc VARCHAR(1000) NOT NULL,
    problem_category VARCHAR(50),
    requirement VARCHAR(1000),
    assignee_id BIGINT,
    assignee_name VARCHAR(50),
    deadline DATE,
    status VARCHAR(30) DEFAULT 'PENDING',
    feedback VARCHAR(1000),
    rectification_photo_file_ids VARCHAR(500),
    completed_time DATETIME,
    reviewer_id BIGINT,
    reviewer_name VARCHAR(50),
    review_time DATETIME,
    review_comment VARCHAR(1000),
    reject_count INT NOT NULL DEFAULT 0,
    recheck_deadline DATE,
    escalation_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    escalation_time DATETIME,
    escalation_note VARCHAR(1000),
    close_time DATETIME,
    deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_rectification_project_status (project_id, status),
    KEY idx_rectification_assignee (assignee_id, status),
    KEY idx_rectification_record (inspection_record_id),
    KEY idx_rectification_category (project_id, problem_category, status),
    KEY idx_rectification_escalation (project_id, status, deadline, escalation_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='整改闭环任务表';

DROP TABLE IF EXISTS inspection_rectification_review_log;
CREATE TABLE inspection_rectification_review_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rectification_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    electric_box_id BIGINT NOT NULL,
    inspection_record_id BIGINT,
    action_type VARCHAR(40) NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    operator_id BIGINT,
    operator_name VARCHAR(50),
    comment VARCHAR(1000),
    photo_file_ids VARCHAR(500),
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_rectification_log_task (rectification_id, create_time),
    KEY idx_rectification_log_project (project_id, create_time),
    KEY idx_rectification_log_action (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查整改闭环留痕表';

-- =============================================
-- 22. 质量问题闭环表
-- =============================================
DROP TABLE IF EXISTS quality_issue;
CREATE TABLE quality_issue (
    id                              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '质量问题ID',
    project_id                      BIGINT NOT NULL COMMENT '施工区域/项目ID',
    issue_no                        VARCHAR(40) NOT NULL COMMENT '问题编号',
    title                           VARCHAR(200) NOT NULL COMMENT '问题标题',
    location                        VARCHAR(200) COMMENT '问题位置',
    description                     VARCHAR(1000) COMMENT '问题描述',
    severity                        VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '严重程度',
    status                          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RECHECK/CLOSED',
    assignee_id                     BIGINT COMMENT '整改负责人用户ID',
    assignee_name                   VARCHAR(50) COMMENT '整改负责人姓名快照',
    deadline                        DATE COMMENT '整改期限',
    rectification_description       VARCHAR(1000) COMMENT '整改说明',
    rectification_photo_file_ids    VARCHAR(1000) COMMENT '整改照片文件ID',
    rectified_time                  DATETIME COMMENT '提交整改时间',
    reviewer_id                     BIGINT COMMENT '复查人用户ID',
    reviewer_name                   VARCHAR(50) COMMENT '复查人姓名快照',
    review_comment                  VARCHAR(1000) COMMENT '复查意见',
    review_time                     DATETIME COMMENT '复查时间',
    created_by_id                   BIGINT NOT NULL COMMENT '发起人用户ID',
    created_by_name                 VARCHAR(50) COMMENT '发起人姓名快照',
    deleted                         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time                     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_quality_issue_no (issue_no),
    KEY idx_quality_issue_project_status (project_id, status, deleted),
    KEY idx_quality_issue_assignee (assignee_id, status, deleted),
    KEY idx_quality_issue_deadline (project_id, deadline, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量问题闭环';

-- =============================================
-- 23. 质量问题操作留痕表
-- =============================================
DROP TABLE IF EXISTS quality_issue_log;
CREATE TABLE quality_issue_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    issue_id        BIGINT NOT NULL COMMENT '质量问题ID',
    project_id      BIGINT NOT NULL COMMENT '施工区域/项目ID',
    action_type     VARCHAR(30) NOT NULL COMMENT '操作类型',
    from_status     VARCHAR(20) COMMENT '原状态',
    to_status       VARCHAR(20) COMMENT '新状态',
    operator_id     BIGINT NOT NULL COMMENT '操作人用户ID',
    operator_name   VARCHAR(50) COMMENT '操作人姓名快照',
    comment         VARCHAR(1000) COMMENT '操作说明',
    photo_file_ids  VARCHAR(1000) COMMENT '操作照片文件ID',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_quality_issue_log_issue (issue_id, create_time),
    KEY idx_quality_issue_log_project (project_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量问题操作留痕';

-- =============================================
-- 初始化拟真基础测试数据
--
-- 这里只写入作业区域、测试账号和基础台账。工程资料、巡检记录、质量问题
-- 及其真实物理文件请在后端启动后执行 scripts/seed-realistic-business-data.sh。
-- =============================================

-- 作业区域均为同一建设项目下的现场管理范围。
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

-- 账号密码均为本地开发密码 admin123；手机号和邮箱为合成测试信息。
INSERT INTO sys_user (
    id, username, password, password_login_enabled, real_name, phone, email,
    status, deleted
) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, '系统管理员', '19900001000', 'admin@example.test', 1, 0),
(2, 'project_admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, '陈志远', '19900001001', 'project.admin@example.test', 1, 0),
(3, 'inspector', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, '周明远', '19900001002', 'inspector@example.test', 1, 0),
(4, 'quality_manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, '李若岚', '19900001003', 'quality@example.test', 1, 0),
(5, 'document_manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 1, '王静怡', '19900001004', 'document@example.test', 1, 0);

-- 插入角色
INSERT INTO sys_role (role_name, role_code, description) VALUES
('平台管理员', 'PLATFORM_ADMIN', '平台管理员，拥有所有权限'),
('项目管理员', 'PROJECT_ADMIN', '项目管理员，管理指定项目'),
('安全管理员', 'SAFETY_ADMIN', '安全管理员，负责安全管理'),
('普通用户', 'USER', '普通用户，仅查看权限');

-- 关联用户角色
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

-- 插入电箱巡检权限模板
INSERT INTO inspection_permission_template (template_name, template_code, description, permission_codes, enabled, builtin) VALUES
('项目管理员', 'PROJECT_ADMIN', '管理电箱台账、二维码、巡检记录、月表导出和项目用户授权', 'BOX_VIEW,BOX_MANAGE,BOX_QR_MANAGE,BOX_PUBLIC_ACCESS,INSPECTION_DAILY_SUBMIT,INSPECTION_RECORD_VIEW,SUMMARY_VIEW,SUMMARY_EXPORT,PERMISSION_MANAGE', 1, 1),
('巡检记录管理员', 'SAFETY_ADMIN', '查看项目电箱、巡检记录和月表导出，不包含用户授权', 'BOX_VIEW,BOX_MANAGE,BOX_QR_MANAGE,BOX_PUBLIC_ACCESS,INSPECTION_RECORD_VIEW,SUMMARY_VIEW,SUMMARY_EXPORT', 1, 1),
('巡检员', 'USER', '查看项目电箱并提交日常巡检', 'BOX_VIEW,INSPECTION_DAILY_SUBMIT', 1, 1);

-- 关联用户作业区域权限
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

-- 摄像头资源只保存测试台账，不填写可播放的生产 RTSP 地址。
INSERT INTO camera_resource (
    project_id, camera_name, camera_code, area, camera_type, rtsp_url,
    online_status, deleted
) VALUES
(1, '1号楼东入口摄像头', 'CAM-1F-EAST-01', '1号楼东入口', '枪机', NULL, 1, 0),
(1, '1号塔吊全景摄像头', 'CAM-1F-TC-01', '1号塔吊', '球机', NULL, 1, 0),
(1, '地下室通道摄像头', 'CAM-1F-B1-01', '地下室一层', '枪机', NULL, 0, 0),
(2, '地下二层机房摄像头', 'CAM-MEP-B2-01', '制冷机房', '半球', NULL, 1, 0),
(3, '钢材堆场摄像头', 'CAM-YD-01', '钢材堆场', '球机', NULL, 1, 0);

-- 设备台账
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

-- 初始电箱台账与首个日检模板
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

INSERT INTO inspection_template (template_code, template_name, frequency, status, remark) VALUES
('ELECTRIC_BOX_DAILY', '电箱检查记录表', 'DAILY', 'ACTIVE', '小程序首个现场检查模板');
INSERT INTO inspection_template_item (template_id, template_code, item_code, item_name, sort_order, abnormal_requirement) VALUES
(1, 'ELECTRIC_BOX_DAILY', 'APPEARANCE', '内外观', 1, '恢复箱门闭合并清理周边杂物'),
(1, 'ELECTRIC_BOX_DAILY', 'LEAKAGE_PROTECTOR', '漏电保护器', 2, '检查漏保动作状态并更换异常部件'),
(1, 'ELECTRIC_BOX_DAILY', 'FUSE', '熔断/开关', 3, '恢复规范熔断和开关配置'),
(1, 'ELECTRIC_BOX_DAILY', 'PROTECTIVE_ZERO', '保护接零', 4, '补齐保护接零并确认连接牢固'),
(1, 'ELECTRIC_BOX_DAILY', 'SOCKET_220', '220V插座', 5, '排查220V插座和临时用电线路'),
(1, 'ELECTRIC_BOX_DAILY', 'SOCKET_380', '380V插座', 6, '排查380V插座和临时用电线路');
