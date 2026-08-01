package com.example.siteplatform.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimitServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private RedisRateLimitService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new RedisRateLimitService(
                redisTemplate, "local-test-secret-with-at-least-32-bytes");
    }

    @Test
    void redisKeyDoesNotContainRawAccountOrIp() {
        when(valueOperations.increment(org.mockito.ArgumentMatchers.anyString())).thenReturn(1L);

        service.check("password-login-account", "13800000000", 10, Duration.ofMinutes(10));

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(key.capture());
        assertTrue(key.getValue().matches("^rate:password-login-account:[0-9a-f]{64}$"));
        assertFalse(key.getValue().contains("13800000000"));
        verify(redisTemplate).expire(key.getValue(), Duration.ofMinutes(10));
    }
}
