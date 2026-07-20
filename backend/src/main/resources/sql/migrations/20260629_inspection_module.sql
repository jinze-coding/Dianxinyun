-- =============================================
-- 现场移动端小程序：电箱台账、日检、复核、整改闭环
-- 说明：开发库增量脚本，不包含 DROP TABLE。
-- =============================================

USE dianxinyun;

CREATE TABLE IF NOT EXISTS electric_box (
    id                          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '电箱ID',
    project_id                  BIGINT NOT NULL COMMENT '项目ID',
    box_code                    VARCHAR(64) NOT NULL COMMENT '电箱编号',
    box_name                    VARCHAR(100) COMMENT '电箱名称',
    install_location            VARCHAR(200) NOT NULL COMMENT '安装位置',
    responsible_electrician_id  BIGINT COMMENT '负责电工ID',
    responsible_electrician_name VARCHAR(50) COMMENT '负责电工姓名',
    safety_manager_id           BIGINT COMMENT '安全负责人ID',
    safety_manager_name         VARCHAR(50) COMMENT '安全负责人姓名',
    qr_code                     VARCHAR(100) COMMENT '内部二维码编码',
    qr_status                   VARCHAR(20) DEFAULT 'BOUND' COMMENT '二维码状态: BOUND/DISABLED/REPLACED',
    status                      VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
    public_code                 VARCHAR(100) NOT NULL COMMENT '公开只读扫码码',
    public_access_enabled       TINYINT NOT NULL DEFAULT 1 COMMENT '公开只读扫码是否启用: 0禁用 1启用',
    remark                      VARCHAR(500) COMMENT '备注',
    deleted                     TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time                 DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                 DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_electric_box_project_code (project_id, box_code, deleted),
    UNIQUE KEY uk_electric_box_project_qr (project_id, qr_code, deleted),
    UNIQUE KEY uk_electric_box_public_code (public_code),
    KEY idx_electric_box_project (project_id),
    KEY idx_electric_box_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电箱台账表';

CREATE TABLE IF NOT EXISTS inspection_template (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模板ID',
    template_code   VARCHAR(64) NOT NULL COMMENT '模板编码',
    template_name   VARCHAR(100) NOT NULL COMMENT '模板名称',
    frequency       VARCHAR(20) DEFAULT 'DAILY' COMMENT '频次: DAILY/MONTHLY',
    status          VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
    remark          VARCHAR(500) COMMENT '备注',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_inspection_template_code (template_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查模板表';

CREATE TABLE IF NOT EXISTS inspection_template_item (
    id                      BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '模板项ID',
    template_id             BIGINT NOT NULL COMMENT '模板ID',
    template_code           VARCHAR(64) NOT NULL COMMENT '模板编码',
    item_code               VARCHAR(64) NOT NULL COMMENT '检查项编码',
    item_name               VARCHAR(100) NOT NULL COMMENT '检查项名称',
    input_type              VARCHAR(30) DEFAULT 'NORMAL_ABNORMAL' COMMENT '录入类型',
    required                TINYINT DEFAULT 1 COMMENT '是否必填',
    sort_order              INT DEFAULT 0 COMMENT '排序',
    abnormal_requirement    VARCHAR(300) COMMENT '异常处理要求',
    deleted                 TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time             DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time             DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_template_item_code (template_code, item_code, deleted),
    KEY idx_template_item_template (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查模板项表';

CREATE TABLE IF NOT EXISTS inspection_record (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '检查记录ID',
    project_id          BIGINT NOT NULL COMMENT '项目ID',
    electric_box_id     BIGINT NOT NULL COMMENT '电箱ID',
    template_code       VARCHAR(64) NOT NULL COMMENT '模板编码',
    source              VARCHAR(40) NOT NULL COMMENT '来源: ELECTRICIAN_DAILY/SAFETY_SPOT_CHECK',
    check_date          DATE NOT NULL COMMENT '检查日期',
    inspector_id        BIGINT NOT NULL COMMENT '检查人ID',
    inspector_name      VARCHAR(50) COMMENT '检查人姓名',
    status              VARCHAR(40) DEFAULT 'REVIEW_PENDING' COMMENT '记录状态',
    review_status       VARCHAR(40) DEFAULT 'PENDING' COMMENT '复核状态',
    reviewer_id         BIGINT COMMENT '复核人ID',
    reviewer_name       VARCHAR(50) COMMENT '复核人姓名',
    review_time         DATETIME COMMENT '复核时间',
    outer_photo_file_ids VARCHAR(500) COMMENT '外观照片文件ID，逗号分隔',
    inner_photo_file_ids VARCHAR(500) COMMENT '内部照片文件ID，逗号分隔',
    abnormal_count      INT DEFAULT 0 COMMENT '异常项数量',
    remark              VARCHAR(1000) COMMENT '备注',
    deleted             TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_inspection_record_project_month (project_id, check_date),
    KEY idx_inspection_record_box_date (electric_box_id, check_date),
    KEY idx_inspection_record_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查记录主表';

CREATE TABLE IF NOT EXISTS inspection_record_item (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '检查项记录ID',
    record_id       BIGINT NOT NULL COMMENT '检查记录ID',
    item_code       VARCHAR(64) NOT NULL COMMENT '检查项编码',
    item_name       VARCHAR(100) NOT NULL COMMENT '检查项名称',
    result          VARCHAR(30) NOT NULL COMMENT '结果: NORMAL/ABNORMAL',
    description     VARCHAR(500) COMMENT '说明',
    deleted         TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_record_item_record (record_id),
    KEY idx_record_item_result (result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='检查项结果明细表';

CREATE TABLE IF NOT EXISTS inspection_rectification (
    id                          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '整改任务ID',
    project_id                  BIGINT NOT NULL COMMENT '项目ID',
    electric_box_id             BIGINT NOT NULL COMMENT '电箱ID',
    inspection_record_id        BIGINT COMMENT '来源检查记录ID',
    record_item_id              BIGINT COMMENT '来源检查项ID',
    box_code                    VARCHAR(64) COMMENT '电箱编号快照',
    problem_desc                VARCHAR(1000) NOT NULL COMMENT '问题描述',
    requirement                 VARCHAR(1000) COMMENT '整改要求',
    assignee_id                 BIGINT COMMENT '整改责任人ID',
    assignee_name               VARCHAR(50) COMMENT '整改责任人姓名',
    deadline                    DATE COMMENT '整改期限',
    status                      VARCHAR(30) DEFAULT 'PENDING' COMMENT '状态: PENDING/COMPLETED/CLOSED/REJECTED',
    feedback                    VARCHAR(1000) COMMENT '整改反馈',
    rectification_photo_file_ids VARCHAR(500) COMMENT '整改照片文件ID，逗号分隔',
    reviewer_id                 BIGINT COMMENT '复查人ID',
    reviewer_name               VARCHAR(50) COMMENT '复查人姓名',
    close_time                  DATETIME COMMENT '关闭时间',
    deleted                     TINYINT DEFAULT 0 COMMENT '删除标记',
    create_time                 DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                 DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_rectification_project_status (project_id, status),
    KEY idx_rectification_assignee (assignee_id, status),
    KEY idx_rectification_record (inspection_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='整改闭环任务表';

INSERT INTO inspection_template (template_code, template_name, frequency, status, remark)
SELECT 'ELECTRIC_BOX_DAILY', '电箱每日巡检', 'DAILY', 'ACTIVE', '来源于上海建工电箱检查记录表'
WHERE NOT EXISTS (
    SELECT 1 FROM inspection_template WHERE template_code = 'ELECTRIC_BOX_DAILY' AND deleted = 0
);

INSERT INTO inspection_template_item (template_id, template_code, item_code, item_name, input_type, required, sort_order, abnormal_requirement)
SELECT t.id, 'ELECTRIC_BOX_DAILY', x.item_code, x.item_name, 'NORMAL_ABNORMAL', 1, x.sort_order, x.abnormal_requirement
FROM inspection_template t
JOIN (
    SELECT 'APPEARANCE' item_code, '内外观' item_name, 10 sort_order, '上传问题照片并说明外观或内部异常' abnormal_requirement
    UNION ALL SELECT 'LEAKAGE_PROTECTOR', '漏电保护器', 20, '确认漏保动作状态并提交整改'
    UNION ALL SELECT 'FUSE', '熔断', 30, '检查熔断配置并提交整改'
    UNION ALL SELECT 'PROTECTIVE_ZERO', '保护接零', 40, '检查保护接零连接并提交整改'
    UNION ALL SELECT 'SOCKET_220V', '220V插座', 50, '检查220V插座并提交整改'
    UNION ALL SELECT 'SOCKET_380V', '380V插座', 60, '检查380V插座并提交整改'
) x
WHERE t.template_code = 'ELECTRIC_BOX_DAILY'
  AND NOT EXISTS (
      SELECT 1
      FROM inspection_template_item i
      WHERE i.template_code = 'ELECTRIC_BOX_DAILY'
        AND i.item_code = x.item_code
        AND i.deleted = 0
  );

INSERT INTO electric_box (
    project_id, box_code, box_name, install_location, responsible_electrician_id,
    responsible_electrician_name, safety_manager_id, safety_manager_name, qr_code,
    qr_status, status, public_code, remark
)
SELECT 1, 'EB-A1-001', 'A区1号二级电箱', 'A区东侧钢筋加工棚', 2,
       '项目经理', 2, '项目经理', 'EBQR-A1-001',
       'BOUND', 'ACTIVE', 'PUB-A1-001', '开发样例电箱'
WHERE EXISTS (SELECT 1 FROM project_info WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM electric_box WHERE project_id = 1 AND box_code = 'EB-A1-001' AND deleted = 0);

INSERT INTO electric_box (
    project_id, box_code, box_name, install_location, responsible_electrician_id,
    responsible_electrician_name, safety_manager_id, safety_manager_name, qr_code,
    qr_status, status, public_code, remark
)
SELECT 1, 'EB-A1-002', 'A区2号二级电箱', 'A区北侧材料堆场', 2,
       '项目经理', 2, '项目经理', 'EBQR-A1-002',
       'BOUND', 'ACTIVE', 'PUB-A1-002', '开发样例电箱'
WHERE EXISTS (SELECT 1 FROM project_info WHERE id = 1)
  AND NOT EXISTS (SELECT 1 FROM electric_box WHERE project_id = 1 AND box_code = 'EB-A1-002' AND deleted = 0);
