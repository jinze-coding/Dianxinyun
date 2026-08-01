-- 该兼容入口已禁用，防止在已有数据环境误执行旧版清表初始化。
-- 全新空库请阅读 backend/README.md，并使用：
--   scripts/init-empty-database.sh
-- 工具会要求精确确认，并拒绝任何已有表的目标数据库。

SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'init.sql is disabled; use scripts/init-empty-database.sh for an explicitly confirmed empty database';
