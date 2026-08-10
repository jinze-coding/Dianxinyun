-- 场内管理公开外访安全加固：退役历史未加钥身份证 SHA-256 摘要。
-- 必须在部署写入 HMAC 指纹的新应用版本前执行；不解密、不导出任何身份证明文。
-- 当前 id_card_hash 仅作后续门岗/闸机扩展预留，旧摘要没有业务查询依赖。

SET @site_access_identity_hardening_required = (
    SELECT IF(COUNT(*) = 0, 1, 0)
    FROM sys_data_migration
    WHERE migration_key = '20260810_SITE_ACCESS_VISITOR_IDENTITY_HMAC_V1'
);

-- 用与身份证无关的逐行退役值覆盖旧 SHA-256，阻断数据库副本上的离线枚举。
-- 新版本应用写入的值改为带 site-access:id-card:v1 用途前缀的 HMAC-SHA256。
UPDATE site_visit_person
SET id_card_hash = SHA2(CONCAT('retired:site_visit_person:', id), 256)
WHERE @site_access_identity_hardening_required = 1;

UPDATE site_visitor_profile_person
SET id_card_hash = SHA2(CONCAT('retired:site_visitor_profile_person:', id), 256)
WHERE @site_access_identity_hardening_required = 1;

INSERT IGNORE INTO sys_data_migration(migration_key)
SELECT '20260810_SITE_ACCESS_VISITOR_IDENTITY_HMAC_V1'
WHERE @site_access_identity_hardening_required = 1;

-- 该迁移只覆盖可离线枚举的历史摘要，不修改 AES-GCM 密文、邀请快照或常用资料状态。
