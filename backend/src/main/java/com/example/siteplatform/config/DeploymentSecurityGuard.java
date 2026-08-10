package com.example.siteplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * Fail-closed checks for settings that are intentionally relaxed only during
 * local development. No secret value is written to logs or exception messages.
 */
@Component
public class DeploymentSecurityGuard {
    static final String SITE_ACCESS_IDENTITY_HMAC_MIGRATION =
            "20260810_SITE_ACCESS_VISITOR_IDENTITY_HMAC_V1";
    static final String INSPECTION_ELECTRICIAN_SUBMIT_MIGRATION =
            "20260810_INSPECTION_ELECTRICIAN_SUBMIT_PERMISSION_V1";

    public DeploymentSecurityGuard(
            @Value("${spring.datasource.url:}") String dataSourceUrl,
            @Value("${spring.datasource.username:}") String dataSourceUsername,
            @Value("${spring.datasource.password:}") String dataSourcePassword,
            @Value("${spring.data.redis.password:}") String redisPassword,
            @Value("${knife4j.enable:false}") boolean knife4jEnabled,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUiEnabled,
            @Value("${server.forward-headers-strategy:NONE}") String forwardHeadersStrategy,
            Environment environment,
            JdbcTemplate jdbcTemplate) {
        if (isProduction(environment)) {
            validateProductionDatabase(dataSourceUrl, dataSourceUsername, dataSourcePassword);
            if (!StringUtils.hasText(redisPassword)) {
                throw new IllegalStateException("生产环境必须配置 REDIS_PASSWORD");
            }
            if (knife4jEnabled || apiDocsEnabled || swaggerUiEnabled) {
                throw new IllegalStateException("生产环境禁止启用 Knife4j、Swagger UI 或 OpenAPI JSON");
            }
            if (!"NATIVE".equalsIgnoreCase(forwardHeadersStrategy)) {
                throw new IllegalStateException(
                        "生产环境必须配置 FORWARD_HEADERS_STRATEGY=NATIVE，确保反向代理后的限流和审计使用真实客户端IP");
            }
            validateRequiredMigrations(jdbcTemplate);
        }
    }

    private boolean isProduction(Environment environment) {
        boolean developmentProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile)
                        || "local".equalsIgnoreCase(profile)
                        || "test".equalsIgnoreCase(profile));
        boolean productionProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));
        return productionProfile || !developmentProfile;
    }

    private void validateProductionDatabase(
            String dataSourceUrl, String dataSourceUsername, String dataSourcePassword) {
        if (!StringUtils.hasText(dataSourceUrl)
                || !StringUtils.hasText(dataSourceUsername)
                || !StringUtils.hasText(dataSourcePassword)) {
            throw new IllegalStateException("生产环境必须配置 DB_URL、DB_USERNAME 和 DB_PASSWORD");
        }
        if ("root".equalsIgnoreCase(dataSourceUsername.trim())) {
            throw new IllegalStateException("生产环境禁止使用 root 数据库账号");
        }
        String normalizedUrl = dataSourceUrl.toLowerCase(Locale.ROOT);
        boolean tlsRequired = normalizedUrl.contains("usessl=true")
                || normalizedUrl.contains("sslmode=required")
                || normalizedUrl.contains("sslmode=verify_ca")
                || normalizedUrl.contains("sslmode=verify_identity");
        if (!tlsRequired || normalizedUrl.contains("allowpublickeyretrieval=true")) {
            throw new IllegalStateException(
                    "生产 DB_URL 必须启用 TLS，且禁止 allowPublicKeyRetrieval=true");
        }
    }

    private void validateRequiredMigrations(JdbcTemplate jdbcTemplate) {
        try {
            Integer identityMigrationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_data_migration WHERE migration_key = ?",
                    Integer.class,
                    SITE_ACCESS_IDENTITY_HMAC_MIGRATION);
            if (identityMigrationCount == null || identityMigrationCount < 1) {
                throw new IllegalStateException(
                        "生产数据库缺少场内外访身份证指纹安全加固迁移标记，必须先执行安全迁移再启动新版本");
            }
            Integer electricianMigrationCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_data_migration WHERE migration_key = ?",
                    Integer.class,
                    INSPECTION_ELECTRICIAN_SUBMIT_MIGRATION);
            if (electricianMigrationCount == null || electricianMigrationCount < 1) {
                throw new IllegalStateException(
                        "生产数据库缺少电工日检提交权限迁移标记，必须先执行权限迁移再启动新版本");
            }
            Integer electricianPermissionCount = jdbcTemplate.queryForObject("""
                    SELECT COUNT(DISTINCT p.permission_code)
                    FROM sys_role r
                    JOIN sys_role_permission rp ON rp.role_id = r.id
                    JOIN sys_permission p ON p.id = rp.permission_id
                    WHERE r.role_code = 'ELECTRICIAN'
                      AND r.scope_type = 'PROJECT'
                      AND r.deleted = 0 AND r.enabled = 1
                      AND p.deleted = 0 AND p.enabled = 1
                      AND p.permission_code IN ('inspection.submit', 'INSPECTION_DAILY_SUBMIT')
                    """, Integer.class);
            if (electricianPermissionCount == null || electricianPermissionCount != 2) {
                throw new IllegalStateException(
                        "生产数据库的电工角色缺少日检提交权限，拒绝启动");
            }
        } catch (DataAccessException exception) {
            throw new IllegalStateException(
                    "无法确认生产数据库的必要安全及权限迁移状态，拒绝启动", exception);
        }
    }
}
