-- 电箱巡检一码两用、巡检范围、微信绑定与订阅消息增量结构。
-- 仅允许在已有库执行本增量脚本，禁止用 init.sql 替代。

USE dianxinyun;

CREATE TABLE IF NOT EXISTS electric_box_inspection_scope (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '巡检范围记录ID',
    project_id         BIGINT NOT NULL COMMENT '项目ID',
    electric_box_id    BIGINT NOT NULL COMMENT '电箱ID',
    included           TINYINT NOT NULL DEFAULT 1 COMMENT '是否纳入日检: 0否 1是',
    effective_date     DATE NOT NULL COMMENT '生效日期',
    end_date           DATE COMMENT '结束日期，空表示持续有效',
    reason             VARCHAR(300) COMMENT '变更原因',
    operator_id        BIGINT COMMENT '操作人ID',
    operator_name      VARCHAR(50) COMMENT '操作人姓名',
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_box_scope_box_date (electric_box_id, effective_date, end_date),
    KEY idx_box_scope_project_date (project_id, effective_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电箱日检范围历史';

CREATE TABLE IF NOT EXISTS project_inspection_setting (
    id                         BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '配置ID',
    project_id                 BIGINT NOT NULL COMMENT '项目ID',
    daily_cutoff_time          TIME NOT NULL DEFAULT '18:00:00' COMMENT '每日日检截止时间',
    pre_due_reminder_minutes   INT NOT NULL DEFAULT 60 COMMENT '截止前提醒分钟数',
    review_due_hours           INT NOT NULL DEFAULT 24 COMMENT '复核时限小时数',
    rectification_days         INT NOT NULL DEFAULT 3 COMMENT '整改自然日天数',
    enabled                    TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    create_time                DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_project_inspection_setting (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='项目电箱巡检设置';

CREATE TABLE IF NOT EXISTS sys_user_wechat_binding (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '绑定ID',
    user_id            BIGINT NOT NULL COMMENT '系统用户ID',
    app_id             VARCHAR(80) NOT NULL COMMENT '微信小程序AppID',
    openid             VARCHAR(128) NOT NULL COMMENT '微信OpenID',
    unionid            VARCHAR(128) COMMENT '微信UnionID',
    phone              VARCHAR(20) COMMENT '微信授权手机号',
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    bind_time          DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    last_login_time    DATETIME COMMENT '最近登录时间',
    deleted            TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记',
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_wechat_binding_openid (app_id, openid, deleted),
    KEY idx_wechat_binding_user (user_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户微信绑定';

CREATE TABLE IF NOT EXISTS wechat_access_application (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '申请ID',
    app_id             VARCHAR(80) NOT NULL COMMENT '微信小程序AppID',
    openid             VARCHAR(128) NOT NULL COMMENT '微信OpenID',
    phone              VARCHAR(20) COMMENT '微信授权手机号',
    real_name          VARCHAR(50) COMMENT '申请人姓名',
    project_id         BIGINT NOT NULL COMMENT '申请项目ID',
    source_type        VARCHAR(40) NOT NULL DEFAULT 'ELECTRIC_BOX' COMMENT '来源类型',
    source_id          BIGINT COMMENT '来源电箱ID',
    matched_user_id    BIGINT COMMENT '匹配的系统用户ID',
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
    reviewer_id        BIGINT COMMENT '审批人ID',
    reviewer_name      VARCHAR(50) COMMENT '审批人姓名',
    review_comment     VARCHAR(300) COMMENT '审批意见',
    review_time        DATETIME COMMENT '审批时间',
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_wechat_application_project (project_id, status, create_time),
    KEY idx_wechat_application_openid (app_id, openid, project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信注册和项目权限申请';

CREATE TABLE IF NOT EXISTS wechat_subscription_state (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订阅状态ID',
    user_id            BIGINT NOT NULL COMMENT '系统用户ID',
    app_id             VARCHAR(80) NOT NULL COMMENT '微信小程序AppID',
    openid             VARCHAR(128) NOT NULL COMMENT '微信OpenID',
    template_code      VARCHAR(64) NOT NULL COMMENT '业务模板编码',
    template_id        VARCHAR(128) COMMENT '微信模板ID',
    available_count    INT NOT NULL DEFAULT 0 COMMENT '可发送次数',
    status             VARCHAR(20) NOT NULL DEFAULT 'ACCEPT' COMMENT 'ACCEPT/REJECT/BAN',
    last_authorized_time DATETIME COMMENT '最近授权时间',
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_wechat_subscription (user_id, app_id, template_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信订阅消息授权状态';

CREATE TABLE IF NOT EXISTS wechat_message_log (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '消息日志ID',
    user_id            BIGINT COMMENT '接收系统用户ID',
    openid             VARCHAR(128) COMMENT '接收OpenID',
    template_code      VARCHAR(64) NOT NULL COMMENT '业务模板编码',
    business_type      VARCHAR(64) COMMENT '业务类型',
    business_id        BIGINT COMMENT '业务ID',
    status             VARCHAR(20) NOT NULL COMMENT 'PENDING/SENT/SKIPPED/FAILED',
    request_payload    TEXT COMMENT '脱敏后的发送内容',
    response_code      VARCHAR(40) COMMENT '微信响应码',
    response_message   VARCHAR(300) COMMENT '微信响应说明',
    retry_count        INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    sent_time          DATETIME COMMENT '发送时间',
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_wechat_message_business (business_type, business_id),
    KEY idx_wechat_message_user (user_id, status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微信订阅消息发送日志';
