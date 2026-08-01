package com.example.siteplatform.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class RedisRateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final byte[] fingerprintSecret;

    public RedisRateLimitService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${jwt.secret}") String fingerprintSecret) {
        this.redisTemplate = redisTemplate;
        this.fingerprintSecret = fingerprintSecret.getBytes(StandardCharsets.UTF_8);
    }

    public void check(String scope, String subject, int maximum, Duration window) {
        String safeSubject = subject == null || subject.isBlank() ? "anonymous" : subject;
        String key = "rate:" + scope + ":" + fingerprint(safeSubject);
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, window);
        }
        if (current != null && current > maximum) {
            throw BusinessException.of(429, "操作过于频繁，请稍后再试");
        }
    }

    private String fingerprint(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fingerprintSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("无法生成限流主体摘要", ex);
        }
    }
}
