package com.example.siteplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentSecurityGuardTest {

    private static final String SECURE_URL =
            "jdbc:mysql://db.example:3306/dianxinyun?sslMode=VERIFY_IDENTITY";

    @Test
    void allowsRelaxedSettingsOnlyForExplicitDevelopmentProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertDoesNotThrow(() -> new DeploymentSecurityGuard(
                "jdbc:mysql://localhost:3306/dianxinyun?useSSL=false",
                "root", "", "", true, true, true, "NONE", environment, readyMigrations()));
    }

    @Test
    void rejectsMissingProductionDatabaseCredentials() {
        MockEnvironment environment = production();

        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "", "redis-secret",
                false, false, false, "NATIVE", environment, readyMigrations()));
    }

    @Test
    void rejectsRootOrInsecureProductionDatabase() {
        MockEnvironment environment = production();

        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "root", "db-secret", "redis-secret",
                false, false, false, "NATIVE", environment, readyMigrations()));
        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                "jdbc:mysql://db.example:3306/dianxinyun?useSSL=false&allowPublicKeyRetrieval=true",
                "site_platform", "db-secret", "redis-secret",
                false, false, false, "NATIVE", environment, readyMigrations()));
    }

    @Test
    void rejectsMissingRedisPasswordOrEnabledApiDocumentation() {
        MockEnvironment environment = production();

        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "",
                false, false, false, "NATIVE", environment, readyMigrations()));
        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "redis-secret",
                true, true, true, "NATIVE", environment, readyMigrations()));
    }

    @Test
    void rejectsProductionWithoutTrustedProxyHeaderHandling() {
        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "redis-secret",
                false, false, false, "NONE", production(), readyMigrations()));
    }

    @Test
    void acceptsFailClosedProductionSettings() {
        assertDoesNotThrow(() -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "redis-secret",
                false, false, false, "NATIVE", production(), readyMigrations()));
    }

    @Test
    void rejectsProductionWhenIdentityHmacMigrationIsMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                eq(DeploymentSecurityGuard.SITE_ACCESS_IDENTITY_HMAC_MIGRATION))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "redis-secret",
                false, false, false, "NATIVE", production(), jdbcTemplate));
    }

    @Test
    void rejectsProductionWhenElectricianSubmitMigrationIsMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                eq(DeploymentSecurityGuard.SITE_ACCESS_IDENTITY_HMAC_MIGRATION))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                eq(DeploymentSecurityGuard.INSPECTION_ELECTRICIAN_SUBMIT_MIGRATION))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "redis-secret",
                false, false, false, "NATIVE", production(), jdbcTemplate));
    }

    @Test
    void rejectsProductionWhenElectricianRoleStillLacksSubmitPermissions() {
        JdbcTemplate jdbcTemplate = readyMigrations();
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "redis-secret",
                false, false, false, "NATIVE", production(), jdbcTemplate));
    }

    private MockEnvironment production() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }

    private JdbcTemplate readyMigrations() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                eq(DeploymentSecurityGuard.SITE_ACCESS_IDENTITY_HMAC_MIGRATION))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class),
                eq(DeploymentSecurityGuard.INSPECTION_ELECTRICIAN_SUBMIT_MIGRATION))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(2);
        return jdbcTemplate;
    }
}
