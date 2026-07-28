package com.example.siteplatform.auth.service;

import com.example.siteplatform.common.BusinessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CaptchaService {
    private static final String PREFIX = "auth:captcha:";
    private final SecureRandom random = new SecureRandom();
    private final RedisTemplate<String, Object> redisTemplate;

    public CaptchaService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Map<String, Object> create() {
        int left = random.nextInt(8) + 1;
        int right = random.nextInt(8) + 1;
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(PREFIX + captchaId, String.valueOf(left + right), 5, TimeUnit.MINUTES);
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="160" height="52">
                  <rect width="160" height="52" rx="8" fill="#eef5ff"/>
                  <path d="M8 40 L150 9 M20 8 L142 44" stroke="#b5cdf4" stroke-width="1"/>
                  <text x="80" y="35" text-anchor="middle" font-size="24" font-family="sans-serif" fill="#245fb8">%d + %d = ?</text>
                </svg>
                """.formatted(left, right);
        String image = "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return Map.of("captchaId", captchaId, "image", image, "expiresInSeconds", 300);
    }

    public void verifyAndConsume(String captchaId, String answer) {
        if (captchaId == null || answer == null) throw new BusinessException("请输入图形验证码");
        String key = PREFIX + captchaId.trim();
        Object expected = redisTemplate.opsForValue().getAndDelete(key);
        if (expected == null || !String.valueOf(expected).equals(answer.trim())) {
            throw new BusinessException("图形验证码错误或已过期");
        }
    }
}
