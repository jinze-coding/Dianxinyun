package com.example.siteplatform.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WechatPlatformClientTest {

    @Test
    void mockIsRejectedWithoutAnExplicitDevelopmentProfile() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client(true, false, new MockEnvironment()));

        assertTrue(exception.getMessage().contains("mock"));
    }

    @Test
    void localProfileMayUseDeterministicMockIdentity() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        WechatPlatformClient client = client(true, false, environment);

        WechatPlatformClient.WechatIdentity identity = client.login("local-code");

        assertEquals("touristappid", identity.appId());
        assertTrue(identity.openid().startsWith("mock_"));
    }

    @Test
    void localProfileDerivesAStableMockPhoneFromWechatAuthorizationCode() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        WechatPlatformClient client = client(true, false, environment);

        String first = client.getPhoneNumber("phone-authorization-code", null);
        String second = client.getPhoneNumber("phone-authorization-code", null);

        assertEquals(first, second);
        assertTrue(first.matches("^1\\d{10}$"));
    }

    @Test
    void missingOfficialConfigurationFailsClosedOutsideDevelopment() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client(false, false, new MockEnvironment()));

        assertTrue(exception.getMessage().contains("生产环境"));
    }

    @Test
    void officialPostBodyIsSerializedAsJsonText() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        WechatPlatformClient client = client(true, false, environment);

        String body = client.jsonBody(Map.of(
                "scene", "L:test",
                "page", "pages/web-login-confirm/index",
                "env_version", "trial",
                "check_path", false,
                "width", 430));

        assertTrue(body.startsWith("{"));
        assertTrue(body.endsWith("}"));
        assertTrue(body.contains("\"scene\":\"L:test\""));
        assertTrue(body.contains("\"env_version\":\"trial\""));
        assertTrue(body.contains("\"check_path\":false"));
    }

    @SuppressWarnings("unchecked")
    private WechatPlatformClient client(boolean mockEnabled, boolean production, MockEnvironment environment) {
        return new WechatPlatformClient(
                new ObjectMapper(),
                (RedisTemplate<String, Object>) mock(RedisTemplate.class),
                "touristappid",
                "",
                mockEnabled,
                production,
                "",
                "http://localhost:3003",
                1000,
                1000,
                environment);
    }
}
