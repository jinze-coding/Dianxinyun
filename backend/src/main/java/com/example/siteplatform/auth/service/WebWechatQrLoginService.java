package com.example.siteplatform.auth.service;

import com.example.siteplatform.auth.dto.LoginResponse;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

@Service
public class WebWechatQrLoginService {

    private static final String CHALLENGE_PREFIX = "wechat:web-qr:challenge:";
    private static final String EXCHANGE_PREFIX = "wechat:web-qr:exchange:";
    private static final Duration TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redisTemplate;
    private final WechatPlatformClient platformClient;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();
    private final String miniPage;
    private final String fallbackUrl;
    private final String envVersion;

    public WebWechatQrLoginService(StringRedisTemplate redisTemplate,
                                   WechatPlatformClient platformClient,
                                   AuthService authService,
                                   @Value("${wechat.mini-program.web-login-page:pages/web-login-confirm/index}") String miniPage,
                                   @Value("${wechat.mini-program.public-fallback-url:http://localhost:3003}") String fallbackUrl,
                                   @Value("${wechat.mini-program.env-version:release}") String envVersion) {
        this.redisTemplate = redisTemplate;
        this.platformClient = platformClient;
        this.authService = authService;
        this.miniPage = miniPage;
        this.fallbackUrl = fallbackUrl;
        this.envVersion = envVersion;
    }

    public Map<String, Object> create(String browserInfo) {
        String challengeId = randomToken(18);
        String browserSecret = randomToken(32);
        String key = challengeKey(challengeId);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("state", "WAITING");
        values.put("browserSecretHash", digest(browserSecret));
        values.put("browserInfo", StringUtils.hasText(browserInfo) ? browserInfo.substring(0, Math.min(180, browserInfo.length())) : "未知浏览器");
        Map<String, String> serialized = new LinkedHashMap<>();
        values.forEach((field, value) -> serialized.put(field, String.valueOf(value)));
        redisTemplate.opsForHash().putAll(key, serialized);
        redisTemplate.expire(key, TTL);
        String scene = "L:" + challengeId;
        String browserFallback = fallbackUrl + (fallbackUrl.contains("?") ? "&" : "?")
                + "webLoginChallenge=" + challengeId;
        String qrCode = platformClient.generateUnlimitedCode(scene, miniPage, envVersion);
        if (!StringUtils.hasText(qrCode)) qrCode = generateQrDataUrl(browserFallback);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("challengeId", challengeId);
        response.put("id", challengeId);
        response.put("browserSecret", browserSecret);
        response.put("browserVerifier", browserSecret);
        response.put("state", "WAITING");
        response.put("status", "WAITING");
        response.put("expiresInSeconds", TTL.toSeconds());
        response.put("expiresIn", TTL.toSeconds());
        response.put("pollIntervalMs", 2000);
        response.put("scene", scene);
        response.put("qrCode", qrCode);
        response.put("qrCodeImage", qrCode);
        response.put("fallbackUrl", browserFallback);
        return response;
    }

    public Map<String, Object> status(String challengeId, String browserSecret) {
        Map<Object, Object> challenge = requireBrowser(challengeId, browserSecret, false);
        if (challenge.isEmpty()) return Map.of("state", "EXPIRED", "status", "EXPIRED");
        Map<String, Object> response = new LinkedHashMap<>();
        String state = String.valueOf(challenge.get("state"));
        response.put("state", state);
        response.put("status", state);
        response.put("browserInfo", String.valueOf(challenge.getOrDefault("browserInfo", "")));
        if ("CONFIRMED".equals(state) && challenge.get("exchangeCode") != null) {
            response.put("exchangeCode", String.valueOf(challenge.get("exchangeCode")));
        }
        return response;
    }

    public Map<String, Object> markScanned(String challengeId, SysUser user) {
        String key = challengeKey(challengeId);
        String scriptText = """
                if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
                local state = redis.call('HGET', KEYS[1], 'state')
                local existing = redis.call('HGET', KEYS[1], 'userId')
                if state == 'WAITING' or (state == 'SCANNED' and existing == ARGV[1]) then
                  redis.call('HSET', KEYS[1], 'state', 'SCANNED', 'userId', ARGV[1])
                  return 1
                end
                return 2
                """;
        Long result = redisTemplate.execute(new DefaultRedisScript<>(scriptText, Long.class),
                List.of(key), user.getId().toString());
        if (result == null || result == 0L) throw new BusinessException("二维码已过期");
        if (result != 1L) throw new BusinessException("二维码状态已变化");
        String browserName = String.valueOf(redisTemplate.opsForHash().get(key, "browserInfo"));
        return Map.of("state", "SCANNED", "status", "SCANNED",
                "browserInfo", browserName, "browserName", browserName, "siteName", "电信云平台");
    }

    public Map<String, Object> confirm(String challengeId, SysUser user) {
        String exchangeCode = randomToken(32);
        String key = challengeKey(challengeId);
        String exchangeKey = EXCHANGE_PREFIX + digest(exchangeCode);
        String scriptText = """
                if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
                if redis.call('HGET', KEYS[1], 'state') ~= 'SCANNED' then return 2 end
                if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1] then return 3 end
                if redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3], 'NX') == false then return 4 end
                redis.call('HSET', KEYS[1], 'state', 'CONFIRMED', 'exchangeCode', ARGV[4], 'exchangeHash', ARGV[5])
                return 1
                """;
        Long result = redisTemplate.execute(new DefaultRedisScript<>(scriptText, Long.class),
                List.of(key, exchangeKey), user.getId().toString(), challengeId + ":" + user.getId(),
                String.valueOf(TTL.toMillis()), exchangeCode, digest(exchangeCode));
        if (result == null || result == 0L) throw new BusinessException("二维码已过期");
        if (result == 2L) throw new BusinessException("请先完成扫码识别或二维码已确认");
        if (result == 3L) throw BusinessException.forbidden("当前账号不是扫码账号");
        if (result != 1L) throw new BusinessException("确认失败，请重新扫码");
        return Map.of("state", "CONFIRMED", "status", "CONFIRMED");
    }

    public void cancelByUser(String challengeId, SysUser user) {
        String scriptText = """
                if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
                local state = redis.call('HGET', KEYS[1], 'state')
                if state ~= 'WAITING' and state ~= 'SCANNED' then return 2 end
                local scannedUser = redis.call('HGET', KEYS[1], 'userId')
                if scannedUser and scannedUser ~= ARGV[1] then return 3 end
                redis.call('HSET', KEYS[1], 'state', 'CANCELLED')
                return 1
                """;
        Long result = redisTemplate.execute(new DefaultRedisScript<>(scriptText, Long.class),
                List.of(challengeKey(challengeId)), user.getId().toString());
        if (result == null || result == 0L) return;
        if (result == 3L) throw BusinessException.forbidden("当前账号不能取消该登录");
        if (result != 1L) throw new BusinessException("二维码已确认或已结束，不能取消");
    }

    public void cancelByBrowser(String challengeId, String browserSecret) {
        if (!StringUtils.hasText(browserSecret)) throw new BusinessException("浏览器校验参数不能为空");
        String scriptText = """
                if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
                if redis.call('HGET', KEYS[1], 'browserSecretHash') ~= ARGV[1] then return 3 end
                local state = redis.call('HGET', KEYS[1], 'state')
                if state ~= 'WAITING' and state ~= 'SCANNED' then return 2 end
                redis.call('HSET', KEYS[1], 'state', 'CANCELLED')
                return 1
                """;
        Long result = redisTemplate.execute(new DefaultRedisScript<>(scriptText, Long.class),
                List.of(challengeKey(challengeId)), digest(browserSecret));
        if (result == null || result == 0L) throw new BusinessException("二维码已过期");
        if (result == 3L) throw BusinessException.forbidden("浏览器校验失败");
        if (result != 1L) throw new BusinessException("二维码已确认或已结束，不能取消");
    }

    public LoginResponse exchange(String challengeId, String browserSecret, String exchangeCode) {
        Map<Object, Object> challenge = requireBrowser(challengeId, browserSecret, true);
        if (!"CONFIRMED".equals(String.valueOf(challenge.get("state")))) {
            throw new BusinessException("二维码尚未确认或已失效");
        }
        if (!StringUtils.hasText(exchangeCode)) throw new BusinessException("登录交换码不能为空");
        String exchangeHash = digest(exchangeCode);
        String scriptText = """
                if redis.call('EXISTS', KEYS[1]) == 0 then return 'EXPIRED' end
                if redis.call('HGET', KEYS[1], 'browserSecretHash') ~= ARGV[1] then return 'BROWSER' end
                if redis.call('HGET', KEYS[1], 'state') ~= 'CONFIRMED' then return 'STATE' end
                if redis.call('HGET', KEYS[1], 'exchangeHash') ~= ARGV[2] then return 'CODE' end
                local exchanged = redis.call('GET', KEYS[2])
                if not exchanged or exchanged ~= ARGV[3] then return 'CODE' end
                redis.call('DEL', KEYS[2])
                local userId = redis.call('HGET', KEYS[1], 'userId')
                redis.call('HSET', KEYS[1], 'state', 'CONSUMED')
                redis.call('HDEL', KEYS[1], 'exchangeCode', 'exchangeHash')
                return userId
                """;
        String exchangedUser = redisTemplate.execute(new DefaultRedisScript<>(scriptText, String.class),
                List.of(challengeKey(challengeId), EXCHANGE_PREFIX + exchangeHash),
                digest(browserSecret), exchangeHash, challengeId + ":" + challenge.get("userId"));
        if ("EXPIRED".equals(exchangedUser)) throw new BusinessException("二维码已过期");
        if ("BROWSER".equals(exchangedUser)) throw BusinessException.forbidden("浏览器校验失败");
        if ("STATE".equals(exchangedUser)) throw new BusinessException("二维码尚未确认或已失效");
        if ("CODE".equals(exchangedUser) || !StringUtils.hasText(exchangedUser)) {
            throw new BusinessException("登录交换码已失效或已使用");
        }
        Long userId = Long.valueOf(exchangedUser);
        SysUser user = authService.getUserInfo(userId);
        if (!Integer.valueOf(1).equals(user.getStatus())) throw BusinessException.forbidden("账号已被禁用");
        if (authService.requiresInitialPasswordSetup(user)) {
            throw BusinessException.forbidden("请先在小程序使用微信登录并完成初始密码设置");
        }
        String token = authService.issueToken(user);
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName());
    }

    private Map<Object, Object> requireBrowser(String challengeId, String browserSecret, boolean failIfMissing) {
        if (!StringUtils.hasText(challengeId) || !StringUtils.hasText(browserSecret)) {
            throw new BusinessException("二维码会话参数不能为空");
        }
        Map<Object, Object> challenge = redisTemplate.opsForHash().entries(challengeKey(challengeId));
        if (challenge.isEmpty()) {
            if (failIfMissing) throw new BusinessException("二维码已过期");
            return challenge;
        }
        if (!digest(browserSecret).equals(String.valueOf(challenge.get("browserSecretHash")))) {
            throw BusinessException.forbidden("浏览器校验失败");
        }
        return challenge;
    }

    private String challengeKey(String challengeId) {
        return CHALLENGE_PREFIX + challengeId;
    }

    private String randomToken(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String generateQrDataUrl(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 360, 360);
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x111827 : 0xFFFFFF);
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new BusinessException("登录二维码生成失败");
        }
    }
}
