package com.example.siteplatform.auth.service;

import com.example.siteplatform.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;

@Component
public class WechatPlatformClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private final String appId;
    private final String appSecret;
    private final boolean mockEnabled;
    private final boolean production;

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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public WechatIdentity login(String code) {
        if (!StringUtils.hasText(code)) throw new BusinessException("微信登录 code 不能为空");
        if (developmentMock()) {
            return new WechatIdentity(appId, "mock_" + digest(code).substring(0, 24), null);
        }
        String body = restClient.get()
                .uri("https://api.weixin.qq.com/sns/jscode2session?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code",
                        appId, appSecret, code)
                .retrieve().body(String.class);
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
        String body = restClient.post()
                .uri("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token={token}", accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", phoneCode))
                .retrieve().body(String.class);
        JsonNode json = read(body);
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
        byte[] bytes = restClient.post()
                .uri("https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token={token}", accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "scene", scene,
                        "page", page,
                        "env_version", StringUtils.hasText(envVersion) ? envVersion : "release",
                        "check_path", false,
                        "width", 430))
                .retrieve().body(byte[].class);
        if (bytes == null || bytes.length == 0) throw new BusinessException("微信小程序码生成失败：返回内容为空");
        if (bytes[0] == '{') {
            JsonNode json = read(new String(bytes, StandardCharsets.UTF_8));
            ensureSuccess(json, "微信小程序码生成失败");
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String accessToken() {
        String key = "wechat:access-token:" + appId;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null && StringUtils.hasText(String.valueOf(cached))) return String.valueOf(cached);
        String body = restClient.get()
                .uri("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid={appid}&secret={secret}", appId, appSecret)
                .retrieve().body(String.class);
        JsonNode json = read(body);
        ensureSuccess(json, "获取微信 access_token 失败");
        String token = json.path("access_token").asText();
        redisTemplate.opsForValue().set(key, token, Math.max(json.path("expires_in").asLong(7200) - 300, 60), TimeUnit.SECONDS);
        return token;
    }

    private JsonNode read(String body) {
        try { return objectMapper.readTree(body); }
        catch (Exception e) { throw new BusinessException("微信接口响应解析失败"); }
    }

    private void ensureSuccess(JsonNode json, String message) {
        if (json.has("errcode") && json.path("errcode").asInt() != 0) {
            throw new BusinessException(message + "：" + json.path("errmsg").asText());
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
