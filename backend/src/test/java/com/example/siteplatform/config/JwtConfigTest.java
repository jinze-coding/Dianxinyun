package com.example.siteplatform.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtConfigTest {

    private static final long EXPIRATION = 604_800_000L;
    private static final String PRODUCTION_SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void allowsDevelopmentDefaultOnlyWithExplicitDevelopmentProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertDoesNotThrow(() -> new JwtConfig(
                JwtConfig.DEVELOPMENT_SECRET, EXPIRATION, environment));
    }

    @Test
    void rejectsDevelopmentDefaultWithoutDevelopmentProfile() {
        assertThrows(IllegalStateException.class, () -> new JwtConfig(
                JwtConfig.DEVELOPMENT_SECRET, EXPIRATION, new MockEnvironment()));
    }

    @Test
    void rejectsDevelopmentDefaultWhenProductionProfileIsAlsoActive() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "prod");

        assertThrows(IllegalStateException.class, () -> new JwtConfig(
                JwtConfig.DEVELOPMENT_SECRET, EXPIRATION, environment));
    }

    @Test
    void acceptsIndependentProductionSecret() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertDoesNotThrow(() -> new JwtConfig(
                PRODUCTION_SECRET, EXPIRATION, environment));
    }

    @Test
    void rejectsSecretShorterThanHs256Minimum() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThrows(IllegalStateException.class, () -> new JwtConfig(
                "too-short", EXPIRATION, environment));
    }
}
