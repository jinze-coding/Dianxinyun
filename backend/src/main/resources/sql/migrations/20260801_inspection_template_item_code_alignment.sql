-- 电箱日检模板插座检查项编码与当前后端、客户端和接口文档对齐。
-- 只修改两条模板种子数据，不修改表结构；执行前必须备份目标数据库。
-- 如同一模板同时存在旧编码与目标编码，脚本拒绝继续，需先人工核对重复项。

DELIMITER $$

DROP PROCEDURE IF EXISTS align_inspection_template_item_codes$$
CREATE PROCEDURE align_inspection_template_item_codes()
BEGIN
    IF EXISTS (
        SELECT 1
        FROM inspection_template_item legacy
        INNER JOIN inspection_template_item canonical
                ON canonical.template_code = legacy.template_code
               AND canonical.deleted = 0
               AND canonical.item_code = 'SOCKET_220V'
        WHERE legacy.template_code = 'ELECTRIC_BOX_DAILY'
          AND legacy.deleted = 0
          AND legacy.item_code = 'SOCKET_220'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ELECTRIC_BOX_DAILY 同时存在 SOCKET_220 与 SOCKET_220V，请先人工核对';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM inspection_template_item legacy
        INNER JOIN inspection_template_item canonical
                ON canonical.template_code = legacy.template_code
               AND canonical.deleted = 0
               AND canonical.item_code = 'SOCKET_380V'
        WHERE legacy.template_code = 'ELECTRIC_BOX_DAILY'
          AND legacy.deleted = 0
          AND legacy.item_code = 'SOCKET_380'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'ELECTRIC_BOX_DAILY 同时存在 SOCKET_380 与 SOCKET_380V，请先人工核对';
    END IF;

    UPDATE inspection_template_item
    SET item_code = 'SOCKET_220V',
        update_time = CURRENT_TIMESTAMP
    WHERE template_code = 'ELECTRIC_BOX_DAILY'
      AND item_code = 'SOCKET_220'
      AND deleted = 0;

    UPDATE inspection_template_item
    SET item_code = 'SOCKET_380V',
        update_time = CURRENT_TIMESTAMP
    WHERE template_code = 'ELECTRIC_BOX_DAILY'
      AND item_code = 'SOCKET_380'
      AND deleted = 0;

    UPDATE inspection_template_item
    SET item_name = '熔断',
        update_time = CURRENT_TIMESTAMP
    WHERE template_code = 'ELECTRIC_BOX_DAILY'
      AND item_code = 'FUSE'
      AND deleted = 0
      AND item_name <> '熔断';
END$$

CALL align_inspection_template_item_codes()$$
DROP PROCEDURE align_inspection_template_item_codes$$

DELIMITER ;
