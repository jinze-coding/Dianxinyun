-- Web 人员/质量/安全与小程序数据一体化增量脚本。
-- 只包含非破坏性变更，可用于已有 dianxinyun 数据库。

USE dianxinyun;

CREATE TABLE IF NOT EXISTS person_entry_exit_log (
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

CREATE TABLE IF NOT EXISTS person_certificate (
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

UPDATE temporary_person
SET status = CASE
    WHEN UPPER(TRIM(status)) IN ('EDUCATED', '已教育') THEN 'EDUCATED'
    WHEN UPPER(TRIM(status)) IN ('LEFT', '已离场') THEN 'LEFT'
    ELSE 'WAIT_EDUCATION'
END
WHERE deleted = 0;

UPDATE safety_education_batch
SET status = CASE
    WHEN UPPER(TRIM(status)) IN ('COMPLETED', '已完成') THEN 'COMPLETED'
    WHEN UPPER(TRIM(status)) IN ('IN_PROGRESS', '进行中') THEN 'IN_PROGRESS'
    ELSE 'NOT_STARTED'
END
WHERE deleted = 0;

UPDATE safety_education_person
SET status = CASE
    WHEN UPPER(TRIM(status)) IN ('COMPLETED', 'FINISHED', '已完成') THEN 'COMPLETED'
    ELSE 'IN_PROGRESS'
END;

INSERT INTO person_entry_exit_log
    (project_id, person_id, action_type, occurred_at, operator_id, operator_name, remark)
SELECT p.project_id,
       p.id,
       'ENTRY',
       COALESCE(p.entry_time, p.create_time, NOW()),
       1,
       '系统迁移',
       '根据历史人员台账补录首次进场'
FROM temporary_person p
WHERE p.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM person_entry_exit_log l WHERE l.person_id = p.id
  );

INSERT INTO person_entry_exit_log
    (project_id, person_id, action_type, occurred_at, operator_id, operator_name, remark)
SELECT p.project_id,
       p.id,
       'EXIT',
       COALESCE(p.update_time, p.entry_time, p.create_time, NOW()),
       1,
       '系统迁移',
       '根据历史离场状态补录'
FROM temporary_person p
WHERE p.deleted = 0
  AND p.status = 'LEFT'
  AND NOT EXISTS (
      SELECT 1 FROM person_entry_exit_log l
      WHERE l.person_id = p.id AND l.action_type = 'EXIT'
  );

SET @has_idx_file_business := (
    SELECT COUNT(1) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'file_resource'
      AND INDEX_NAME = 'idx_file_business'
);
SET @sql := IF(@has_idx_file_business = 0,
    'ALTER TABLE file_resource ADD KEY idx_file_business (project_id, business_type, business_id, deleted)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
