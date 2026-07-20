-- 电箱二维码生命周期留痕。
-- 说明：本脚本为增量脚本，不包含 DROP TABLE，不会清空既有业务数据。

CREATE TABLE IF NOT EXISTS electric_box_qr_log (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '二维码操作日志ID',
    project_id         BIGINT NOT NULL COMMENT '项目ID',
    electric_box_id    BIGINT NOT NULL COMMENT '电箱ID',
    box_code           VARCHAR(64) NOT NULL COMMENT '操作时电箱编号',
    action_type        VARCHAR(30) NOT NULL COMMENT '操作类型: GENERATE/PRINT/REBIND/DISABLE/REMOVE',
    qr_type            VARCHAR(20) NOT NULL COMMENT '二维码类型: INTERNAL/PUBLIC',
    old_qr_code        VARCHAR(120) COMMENT '旧二维码编码或旧公开码',
    new_qr_code        VARCHAR(120) COMMENT '新二维码编码或新公开码',
    operator_user_id   BIGINT COMMENT '操作人ID',
    operator_username  VARCHAR(50) COMMENT '操作账号',
    reason             VARCHAR(300) COMMENT '操作原因',
    create_time        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_eb_qr_log_box (electric_box_id, create_time),
    KEY idx_eb_qr_log_project (project_id, create_time),
    KEY idx_eb_qr_log_old_code (old_qr_code),
    KEY idx_eb_qr_log_new_code (new_qr_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电箱二维码操作日志表';
