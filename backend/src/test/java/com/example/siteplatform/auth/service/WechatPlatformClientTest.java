package com.example.siteplatform.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.env.MockEnvironment;

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
    void missingOfficialConfigurationFailsClosedOutsideDevelopment() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client(false, false, new MockEnvironment()));

        assertTrue(exception.getMessage().contains("生产环境"));
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
