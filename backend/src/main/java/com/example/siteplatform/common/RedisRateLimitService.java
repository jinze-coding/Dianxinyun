package com.example.siteplatform.common;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisRateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisRateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void check(String scope, String subject, int maximum, Duration window) {
        String safeSubject = subject == null || subject.isBlank() ? "anonymous" : subject;
        String key = "rate:" + scope + ":" + safeSubject;
        Long current = redisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            redisTemplate.expire(key, window);
        }
        if (current != null && current > maximum) {
            throw BusinessException.of(429, "操作过于频繁，请稍后再试");
        }
    }
}
