package com.example.siteplatform.auth.service;

import com.example.siteplatform.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.function.Supplier;

@Component
public class WechatPlatformClient {

    private static final String STABLE_TOKEN_ENDPOINT = "https://api.weixin.qq.com/cgi-bin/stable_token";
    private static final Set<Integer> INVALID_ACCESS_TOKEN_CODES = Set.of(40001, 40014, 42001);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private final String appId;
    private final String appSecret;
    private final boolean mockEnabled;
    private final boolean production;

    @Autowired
    public WechatPlatformClient(ObjectMapper objectMapper, RedisTemplate<String, Object> redisTemplate,
                                @Value("${wechat.mini-program.app-id:touristappid}") String appId,
                                @Value("${wechat.mini-program.app-secret:}") String appSecret,
                                @Value("${wechat.mini-program.mock-enabled:false}") boolean mockEnabled,
                                @Value("${wechat.mini-program.production:false}") boolean production,
                                @Value("${wechat.mini-program.legal-domain:}") String legalDomain,
                                @Value("${wechat.mini-program.public-fallback-url:http://localhost:3003}") String publicFallbackUrl,
                                @Value("${wechat.mini-program.connect-timeout-millis:5000}") int connectTimeout,
                                @Value("${wechat.mini-program.read-timeout-millis:8000}") int readTimeout,
                                Environment environment) {
        this(objectMapper, redisTemplate, appId, appSecret, mockEnabled, production,
                legalDomain, publicFallbackUrl, environment, restClient(connectTimeout, readTimeout));
    }

    WechatPlatformClient(ObjectMapper objectMapper, RedisTemplate<String, Object> redisTemplate,
                         String appId, String appSecret, boolean mockEnabled, boolean production,
                         String legalDomain, String publicFallbackUrl, Environment environment,
                         RestClient restClient) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.appId = appId;
        this.appSecret = appSecret;
        boolean developmentProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equalsIgnoreCase(profile)
                        || "local".equalsIgnoreCase(profile)
                        || "test".equalsIgnoreCase(profile));
        boolean productionProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));
        if (mockEnabled && !developmentProfile) {
            throw new IllegalStateException("微信 mock 仅允许在 dev、local 或 test 环境启用");
        }
        this.mockEnabled = mockEnabled && developmentProfile;
        this.production = production || productionProfile || !developmentProfile;
        if (this.production && (this.mockEnabled || !StringUtils.hasText(appId)
                || !StringUtils.hasText(appSecret) || "touristappid".equals(appId)
                || !legalHttpsUrl(legalDomain) || !legalHttpsUrl(publicFallbackUrl))) {
            throw new IllegalStateException(
                    "生产环境必须配置正式微信 AppID/AppSecret、合法 HTTPS 域名和 HTTPS 扫码回跳地址，并关闭 mock");
        }
        this.restClient = restClient;
    }

    private static RestClient restClient(int connectTimeout, int readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    public WechatIdentity login(String code) {
        if (!StringUtils.hasText(code)) throw new BusinessException("微信登录 code 不能为空");
        if (developmentMock()) {
            return new WechatIdentity(appId, "mock_" + digest(code).substring(0, 24), null);
        }
        String body = callWechat("微信登录服务暂时不可用", () -> restClient.get()
                .uri("https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code",
                        appId, appSecret, code)
                .retrieve().body(String.class));
        JsonNode json = read(body);
        ensureSuccess(json, "微信登录失败");
        if (!json.hasNonNull("openid")) throw new BusinessException("微信登录未返回 openid");
        return new WechatIdentity(appId, json.path("openid").asText(), json.path("unionid").asText(null));
    }

    public String getPhoneNumber(String phoneCode, String mockPhone) {
        if (developmentMock()) {
            if (StringUtils.hasText(mockPhone)) return mockPhone.trim();
            if (!StringUtils.hasText(phoneCode)) throw new BusinessException("手机号授权 code 不能为空");
            // 本地/测试环境没有真实微信手机号接口。用授权 code 生成稳定的 11 位
            // 测试手机号，使快捷注册仍遵循“服务端由授权结果决定账号”的流程。
            long suffix = Long.parseUnsignedLong(digest(phoneCode).substring(0, 15), 16) % 10_000_000_000L;
            return "1" + String.format(Locale.ROOT, "%010d", suffix);
        }
        if (!StringUtils.hasText(phoneCode)) throw new BusinessException("手机号授权 code 不能为空");
        JsonNode json = phoneNumberResponse(phoneCode, accessToken(false));
        if (invalidAccessToken(json)) {
            evictAccessToken();
            json = phoneNumberResponse(phoneCode, accessToken(true));
        }
        ensureSuccess(json, "获取微信手机号失败");
        String phone = json.path("phone_info").path("phoneNumber").asText();
        if (!StringUtils.hasText(phone)) throw new BusinessException("微信未返回手机号");
        return phone;
    }

    public String appId() {
        return appId;
    }

    public boolean officialCodeEnabled() {
        return !mockEnabled && StringUtils.hasText(appSecret) && !"touristappid".equals(appId);
    }

    private boolean developmentMock() {
        if (production) return false;
        return mockEnabled;
    }

    public String generateUnlimitedCode(String scene, String page, String envVersion) {
        if (!officialCodeEnabled()) return null;
        Map<String, ?> request = Map.of(
                "scene", scene,
                "page", page,
                "env_version", StringUtils.hasText(envVersion) ? envVersion : "release",
                "check_path", false,
                "width", 430);
        byte[] bytes = unlimitedCodeResponse(request, accessToken(false));
        JsonNode error = responseError(bytes);
        if (invalidAccessToken(error)) {
            evictAccessToken();
            bytes = unlimitedCodeResponse(request, accessToken(true));
            error = responseError(bytes);
        }
        if (bytes == null || bytes.length == 0) throw new BusinessException("微信小程序码生成失败：返回内容为空");
        if (error != null) ensureSuccess(error, "微信小程序码生成失败");
        String imageMediaType = imageMediaType(bytes);
        if (imageMediaType == null) throw new BusinessException("微信小程序码生成失败：返回内容不是有效图片");
        return "data:" + imageMediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private JsonNode phoneNumberResponse(String phoneCode, String token) {
        String body = callWechat("微信手机号服务暂时不可用", () -> restClient.post()
                .uri("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token={token}", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody(Map.of("code", phoneCode)))
                .retrieve().body(String.class));
        return read(body);
    }

    private byte[] unlimitedCodeResponse(Map<String, ?> request, String token) {
        return callWechat("微信小程序码服务暂时不可用", () -> restClient.post()
                .uri("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token={token}", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody(request))
                .retrieve().body(byte[].class));
    }

    private JsonNode responseError(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        int index = 0;
        while (index < bytes.length && Character.isWhitespace(bytes[index])) index++;
        return index < bytes.length && bytes[index] == '{'
                ? read(new String(bytes, StandardCharsets.UTF_8)) : null;
    }

    private String imageMediaType(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) {
            return "image/png";
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        return null;
    }

    private String accessToken(boolean forceRefresh) {
        String key = accessTokenCacheKey();
        if (!forceRefresh) {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null && StringUtils.hasText(String.valueOf(cached))) return String.valueOf(cached);
        }
        String body = callWechat("微信凭证服务暂时不可用", () -> restClient.post()
                .uri(STABLE_TOKEN_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody(Map.of(
                        "grant_type", "client_credential",
                        "appid", appId,
                        "secret", appSecret,
                        "force_refresh", forceRefresh)))
                .retrieve().body(String.class));
        JsonNode json = read(body);
        ensureSuccess(json, "获取微信 access_token 失败");
        String token = json.path("access_token").asText();
        if (!StringUtils.hasText(token)) throw new BusinessException("微信未返回 access_token");
        redisTemplate.opsForValue().set(key, token, Math.max(json.path("expires_in").asLong(7200) - 300, 60), TimeUnit.SECONDS);
        return token;
    }

    private boolean invalidAccessToken(JsonNode json) {
        return json != null && INVALID_ACCESS_TOKEN_CODES.contains(json.path("errcode").asInt());
    }

    private void evictAccessToken() {
        redisTemplate.delete(accessTokenCacheKey());
    }

    private String accessTokenCacheKey() {
        return "wechat:stable-access-token:" + appId;
    }

    private JsonNode read(String body) {
        if (!StringUtils.hasText(body)) throw new BusinessException("微信接口响应解析失败");
        try { return objectMapper.readTree(body); }
        catch (Exception e) { throw new BusinessException("微信接口响应解析失败"); }
    }

    String jsonBody(Map<String, ?> body) {
        try { return objectMapper.writeValueAsString(body); }
        catch (Exception e) { throw new BusinessException("微信接口请求生成失败"); }
    }

    private void ensureSuccess(JsonNode json, String message) {
        if (json == null) throw new BusinessException("微信接口响应解析失败");
        if (json.has("errcode") && json.path("errcode").asInt() != 0) {
            throw new BusinessException(message + "（微信错误码 " + json.path("errcode").asInt() + "）");
        }
    }

    private <T> T callWechat(String safeMessage, Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientException exception) {
            // RestClient exception messages can contain the fully expanded URI, including
            // AppSecret, one-time codes or access tokens. Never propagate or attach the cause.
            throw BusinessException.of(502, safeMessage);
        }
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean legalHttpsUrl(String value) {
        if (!StringUtils.hasText(value)) return false;
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme())
                    && StringUtils.hasText(host)
                    && !"localhost".equalsIgnoreCase(host)
                    && !"127.0.0.1".equals(host)
                    && !"::1".equals(host);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public record WechatIdentity(String appId, String openid, String unionid) {}
}
