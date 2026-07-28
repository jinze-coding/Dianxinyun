package com.example.siteplatform.auth.service;

import com.example.siteplatform.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaptchaServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @Test
    void captchaAnswerIsReadAndDeletedAtomically() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getAndDelete("auth:captcha:captcha-1"))
                .thenReturn("9")
                .thenReturn(null);
        CaptchaService service = new CaptchaService(redisTemplate);

        assertDoesNotThrow(() -> service.verifyAndConsume("captcha-1", "9"));
        assertThrows(BusinessException.class, () -> service.verifyAndConsume("captcha-1", "9"));

        verify(valueOperations, org.mockito.Mockito.times(2))
                .getAndDelete("auth:captcha:captcha-1");
    }
}
