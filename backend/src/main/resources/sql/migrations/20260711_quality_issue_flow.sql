-- 质量问题检查、整改、复查闭环增量脚本。
-- 目标环境执行一次即可；不要使用 init.sql 替代本脚本。

CREATE TABLE IF NOT EXISTS quality_issue (
    id                              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '质量问题ID',
    project_id                      BIGINT NOT NULL COMMENT '施工区域/项目ID',
    issue_no                        VARCHAR(40) NOT NULL COMMENT '问题编号',
    request_key                     VARCHAR(100) COMMENT '客户端创建请求幂等键',
    title                           VARCHAR(200) NOT NULL COMMENT '问题标题',
    location                        VARCHAR(200) COMMENT '问题位置',
    description                     VARCHAR(1000) COMMENT '问题描述',
    severity                        VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '严重程度: NORMAL/WARNING/DANGER',
    status                          VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RECHECK/CLOSED/VOIDED',
    assignee_id                     BIGINT COMMENT '整改负责人用户ID',
    assignee_name                   VARCHAR(50) COMMENT '整改负责人姓名快照',
    deadline                        DATE COMMENT '整改期限',
    rectification_description       VARCHAR(1000) COMMENT '整改说明',
    rectification_photo_file_ids    VARCHAR(1000) COMMENT '整改照片文件ID，逗号分隔',
    rectified_time                  DATETIME COMMENT '提交整改时间',
    reviewer_id                     BIGINT COMMENT '复查人用户ID',
    reviewer_name                   VARCHAR(50) COMMENT '复查人姓名快照',
    review_comment                  VARCHAR(1000) COMMENT '复查意见',
    review_time                     DATETIME COMMENT '复查时间',
    created_by_id                   BIGINT NOT NULL COMMENT '发起人用户ID',
    created_by_name                 VARCHAR(50) COMMENT '发起人姓名快照',
    version                         INT NOT NULL DEFAULT 0 COMMENT '并发控制版本号',
    deleted                         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    create_time                     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time                     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_quality_issue_no (issue_no),
    UNIQUE KEY uk_quality_issue_request (project_id, created_by_id, request_key),
    KEY idx_quality_issue_project_status (project_id, status, deleted),
    KEY idx_quality_issue_assignee (assignee_id, status, deleted),
    KEY idx_quality_issue_deadline (project_id, deadline, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量问题闭环';

CREATE TABLE IF NOT EXISTS quality_issue_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '日志ID',
    issue_id        BIGINT NOT NULL COMMENT '质量问题ID',
    project_id      BIGINT NOT NULL COMMENT '施工区域/项目ID',
    action_type     VARCHAR(30) NOT NULL COMMENT '动作: CREATE/RECTIFY/REVIEW_PASS/REVIEW_REJECT/ASSIGN/VOID',
    from_status     VARCHAR(20) COMMENT '原状态',
    to_status       VARCHAR(20) COMMENT '新状态',
    operator_id     BIGINT NOT NULL COMMENT '操作人用户ID',
    operator_name   VARCHAR(50) COMMENT '操作人姓名快照',
    comment         VARCHAR(1000) COMMENT '操作说明',
    photo_file_ids  VARCHAR(1000) COMMENT '操作照片文件ID，逗号分隔',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_quality_issue_log_issue (issue_id, create_time),
    KEY idx_quality_issue_log_project (project_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质量问题操作留痕';
