package com.example.siteplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
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

    public DeploymentSecurityGuard(
            @Value("${spring.datasource.url:}") String dataSourceUrl,
            @Value("${spring.datasource.username:}") String dataSourceUsername,
            @Value("${spring.datasource.password:}") String dataSourcePassword,
            @Value("${spring.data.redis.password:}") String redisPassword,
            @Value("${knife4j.enable:false}") boolean knife4jEnabled,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled,
            @Value("${springdoc.swagger-ui.enabled:false}") boolean swaggerUiEnabled,
            @Value("${server.forward-headers-strategy:NONE}") String forwardHeadersStrategy,
            Environment environment) {
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
}
