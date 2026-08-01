-- 微信快捷注册与首次密码设置。
-- 仅增量执行；严禁以 init.sql 重建已有数据库。可重复检查，不覆盖既有申请记录。

SET @column_exists = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'registration_application'
      AND column_name = 'registration_mode'
);
SET @column_sql = IF(@column_exists = 0,
    'ALTER TABLE registration_application ADD COLUMN registration_mode VARCHAR(30) NOT NULL DEFAULT ''STANDARD'' COMMENT ''STANDARD/WECHAT_QUICK'' AFTER source_type',
    'SELECT 1');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史申请均为原有的标准注册，不能因重复执行改变管理员已经完成的审核结果。
UPDATE registration_application
SET registration_mode = 'STANDARD'
WHERE registration_mode IS NULL OR registration_mode = '';
