package com.example.siteplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeploymentSecurityGuardTest {

    private static final String SECURE_URL =
            "jdbc:mysql://db.example:3306/dianxinyun?sslMode=VERIFY_IDENTITY";

    @Test
    void allowsRelaxedSettingsOnlyForExplicitDevelopmentProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertDoesNotThrow(() -> new DeploymentSecurityGuard(
                "jdbc:mysql://localhost:3306/dianxinyun?useSSL=false",
                "root", "", "", true, true, true, "NONE", environment));
    }

    @Test
    void rejectsMissingProductionDatabaseCredentials() {
        MockEnvironment environment = production();

        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "", "redis-secret",
                false, false, false, "NATIVE", environment));
    }

    @Test
    void rejectsRootOrInsecureProductionDatabase() {
        MockEnvironment environment = production();

        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "root", "db-secret", "redis-secret",
                false, false, false, "NATIVE", environment));
        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                "jdbc:mysql://db.example:3306/dianxinyun?useSSL=false&allowPublicKeyRetrieval=true",
                "site_platform", "db-secret", "redis-secret",
                false, false, false, "NATIVE", environment));
    }

    @Test
    void rejectsMissingRedisPasswordOrEnabledApiDocumentation() {
        MockEnvironment environment = production();

        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "",
                false, false, false, "NATIVE", environment));
        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "redis-secret",
                true, true, true, "NATIVE", environment));
    }

    @Test
    void rejectsProductionWithoutTrustedProxyHeaderHandling() {
        assertThrows(IllegalStateException.class, () -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "redis-secret",
                false, false, false, "NONE", production()));
    }

    @Test
    void acceptsFailClosedProductionSettings() {
        assertDoesNotThrow(() -> new DeploymentSecurityGuard(
                SECURE_URL, "site_platform", "db-secret", "redis-secret",
                false, false, false, "NATIVE", production()));
    }

    private MockEnvironment production() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        return environment;
    }
}
