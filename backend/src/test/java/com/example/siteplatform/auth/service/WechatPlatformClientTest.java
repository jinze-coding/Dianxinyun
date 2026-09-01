package com.example.siteplatform.auth.service;

import com.example.siteplatform.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

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

    @Test
    void officialCodeUsesStableAccessTokenAndCachesItSeparately() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        RedisTemplate<String, Object> redis = redisTemplate();
        ValueOperations<String, Object> values = redis.opsForValue();
        String cacheKey = "wechat:stable-access-token:wx-test";
        when(values.get(cacheKey)).thenReturn(null);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatPlatformClient client = officialClient(environment, redis, builder.build());

        server.expect(once(), requestTo("https://api.weixin.qq.com/cgi-bin/stable_token"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"grant_type":"client_credential","appid":"wx-test","secret":"secret-test","force_refresh":false}
                        """))
                .andRespond(withSuccess("{\"access_token\":\"stable-token\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=stable-token"))
                .andExpect(method(POST))
                .andRespond(withSuccess(pngBytes(), MediaType.IMAGE_PNG));

        String image = client.generateUnlimitedCode("S:scene", "pages/seal/entry", "trial");

        assertTrue(image.startsWith("data:image/png;base64,"));
        verify(values).set(eq(cacheKey), eq("stable-token"), eq(6900L), eq(TimeUnit.SECONDS));
        server.verify();
    }

    @Test
    void invalidCachedTokenIsRefreshedOnceBeforeRetryingCodeGeneration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        RedisTemplate<String, Object> redis = redisTemplate();
        ValueOperations<String, Object> values = redis.opsForValue();
        String cacheKey = "wechat:stable-access-token:wx-test";
        when(values.get(cacheKey)).thenReturn("expired-token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatPlatformClient client = officialClient(environment, redis, builder.build());

        server.expect(once(), requestTo("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=expired-token"))
                .andRespond(withSuccess("{\"errcode\":40001,\"errmsg\":\"invalid credential\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.weixin.qq.com/cgi-bin/stable_token"))
                .andExpect(content().json("""
                        {"grant_type":"client_credential","appid":"wx-test","secret":"secret-test","force_refresh":true}
                        """))
                .andRespond(withSuccess("{\"access_token\":\"fresh-token\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=fresh-token"))
                .andRespond(withSuccess(pngBytes(), MediaType.IMAGE_PNG));

        String image = client.generateUnlimitedCode("S:scene", "pages/seal/entry", "trial");

        assertTrue(image.startsWith("data:image/png;base64,"));
        verify(redis).delete(cacheKey);
        server.verify();
    }

    @Test
    void officialCodePreservesWechatJpegMediaType() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        RedisTemplate<String, Object> redis = redisTemplate();
        when(redis.opsForValue().get("wechat:stable-access-token:wx-test")).thenReturn("stable-token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatPlatformClient client = officialClient(environment, redis, builder.build());

        server.expect(once(), requestTo("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=stable-token"))
                .andRespond(withSuccess(new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00},
                        MediaType.IMAGE_JPEG));

        String image = client.generateUnlimitedCode("S:scene", "pages/seal/entry", "trial");

        assertTrue(image.startsWith("data:image/jpeg;base64,"));
        server.verify();
    }

    @Test
    void officialLoginNetworkFailureDoesNotExposeSecretCodeUriOrCause() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        RedisTemplate<String, Object> redis = redisTemplate();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatPlatformClient client = officialClient(environment, redis, builder.build());
        String sensitiveCode = "one-time-sensitive-code";
        String expandedUri = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=wx-test&secret=secret-test&js_code=" + sensitiveCode
                + "&grant_type=authorization_code";
        server.expect(once(), requestTo(expandedUri))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection failed: " + expandedUri);
                });

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.login(sensitiveCode));

        assertEquals(502, exception.getCode());
        assertEquals("微信登录服务暂时不可用", exception.getMessage());
        assertFalse(exception.getMessage().contains("secret-test"));
        assertFalse(exception.getMessage().contains(sensitiveCode));
        assertFalse(exception.getMessage().contains("api.weixin.qq.com"));
        assertNull(exception.getCause());
        server.verify();
    }

    @Test
    void officialLoginRejectsBlankOpenidWithoutEchoingSensitiveResponseOrCode() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        RedisTemplate<String, Object> redis = redisTemplate();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatPlatformClient client = officialClient(environment, redis, builder.build());
        String sensitiveCode = "one-time-sensitive-code";
        server.expect(once(), requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wx-test&secret=secret-test&js_code=" + sensitiveCode
                        + "&grant_type=authorization_code"))
                .andRespond(withSuccess("{\"openid\":\"   \"}", MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.login(sensitiveCode));

        assertEquals("微信登录未返回有效身份", exception.getMessage());
        assertFalse(exception.getMessage().contains(sensitiveCode));
        assertFalse(exception.getMessage().contains("secret-test"));
        assertNull(exception.getCause());
        server.verify();
    }

    @Test
    void officialWechatErrorDoesNotEchoUntrustedErrorMessage() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        RedisTemplate<String, Object> redis = redisTemplate();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatPlatformClient client = officialClient(environment, redis, builder.build());
        String sensitiveCode = "one-time-sensitive-code";
        server.expect(once(), requestTo("https://api.weixin.qq.com/sns/jscode2session"
                        + "?appid=wx-test&secret=secret-test&js_code=" + sensitiveCode
                        + "&grant_type=authorization_code"))
                .andRespond(withSuccess("{\"errcode\":40029,\"errmsg\":\"invalid "
                        + sensitiveCode + " secret-test\"}", MediaType.APPLICATION_JSON));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> client.login(sensitiveCode));

        assertEquals("微信登录失败（微信错误码 40029）", exception.getMessage());
        assertFalse(exception.getMessage().contains("secret-test"));
        assertFalse(exception.getMessage().contains(sensitiveCode));
        server.verify();
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

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> redis = (RedisTemplate<String, Object>) mock(RedisTemplate.class);
        ValueOperations<String, Object> values = (ValueOperations<String, Object>) mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        return redis;
    }

    private WechatPlatformClient officialClient(MockEnvironment environment,
                                                RedisTemplate<String, Object> redis,
                                                RestClient restClient) {
        return new WechatPlatformClient(
                new ObjectMapper(), redis, "wx-test", "secret-test", false, false,
                "", "http://localhost:3003", environment, restClient);
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    }
}
