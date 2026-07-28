package com.example.siteplatform.auth.service;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebWechatQrLoginServiceTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private WechatPlatformClient platformClient;
    @Mock private AuthService authService;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    private WebWechatQrLoginService service() {
        return new WebWechatQrLoginService(
                redisTemplate, platformClient, authService,
                "pages/web-login-confirm/index", "http://localhost:3003", "develop");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void onlyTheFirstAtomicConfirmationCanSucceed() {
        WebWechatQrLoginService service = service();
        SysUser user = new SysUser();
        user.setId(7L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L, 2L);

        assertEquals("CONFIRMED", service.confirm("challenge", user).get("state"));
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.confirm("challenge", user));
        assertEquals("请先完成扫码识别或二维码已确认", exception.getMessage());
    }

    @Test
    void expiredChallengeIsReportedWithoutIssuingCredentials() {
        WebWechatQrLoginService service = service();
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(any())).thenReturn(Map.of());

        Map<String, Object> status = service.status("expired", "browser-secret");

        assertEquals("EXPIRED", status.get("state"));
        verify(authService, never()).issueToken(any());
    }

    @Test
    void copiedChallengeCannotBePolledFromAnotherBrowser() {
        WebWechatQrLoginService service = service();
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(any())).thenReturn(Map.of(
                "state", "WAITING",
                "browserSecretHash", digest("first-browser")
        ));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.status("challenge", "second-browser"));

        assertEquals(403, exception.getCode());
        assertEquals("浏览器校验失败", exception.getMessage());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aChallengeCannotBeClaimedByASecondWechatAccount() {
        WebWechatQrLoginService service = service();
        SysUser user = new SysUser();
        user.setId(8L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(2L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.markScanned("challenge", user));

        assertEquals("二维码状态已变化", exception.getMessage());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void disabledAccountCannotExchangeAConfirmedChallenge() {
        WebWechatQrLoginService service = service();
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(any())).thenReturn(Map.of(
                "state", "CONFIRMED",
                "userId", "7",
                "browserSecretHash", digest("browser-secret")
        ));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn("7");
        SysUser disabled = new SysUser();
        disabled.setId(7L);
        disabled.setStatus(0);
        when(authService.getUserInfo(7L)).thenReturn(disabled);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.exchange("challenge", "browser-secret", "one-time-code"));

        assertEquals(403, exception.getCode());
        verify(authService, never()).issueToken(any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void consumedExchangeCodeCannotBeUsedTwice() {
        WebWechatQrLoginService service = service();
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(any())).thenReturn(Map.of(
                "state", "CONFIRMED",
                "userId", "7",
                "browserSecretHash", digest("browser-secret")
        ));
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn("7", "STATE");
        SysUser active = new SysUser();
        active.setId(7L);
        active.setStatus(1);
        active.setUsername("user");
        when(authService.getUserInfo(7L)).thenReturn(active);
        when(authService.issueToken(active)).thenReturn("jwt-token");

        assertEquals("jwt-token",
                service.exchange("challenge", "browser-secret", "one-time-code").getToken());
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.exchange("challenge", "browser-secret", "one-time-code"));

        assertTrue(exception.getMessage().contains("尚未确认") || exception.getMessage().contains("失效"));
    }

    @Test
    void parallelChallengeCreationUsesIndependentIdentifiersAndBrowserSecrets() {
        WebWechatQrLoginService service = service();
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(platformClient.generateUnlimitedCode(any(), any(), any()))
                .thenReturn("data:image/png;base64,AA==");

        Map<String, Object> first = service.create("Browser A");
        Map<String, Object> second = service.create("Browser B");

        assertNotEquals(first.get("challengeId"), second.get("challengeId"));
        assertNotEquals(first.get("browserSecret"), second.get("browserSecret"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void scannedUserCanCancelBeforeConfirmation() {
        WebWechatQrLoginService service = service();
        SysUser user = new SysUser();
        user.setId(7L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        service.cancelByUser("challenge", user);

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void anotherWechatAccountCannotCancelTheChallenge() {
        WebWechatQrLoginService service = service();
        SysUser user = new SysUser();
        user.setId(8L);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(3L);

        BusinessException exception = assertThrows(
                BusinessException.class, () -> service.cancelByUser("challenge", user));

        assertEquals(403, exception.getCode());
    }

    @Test
    void confirmedExchangeCodeCanBePolledAgainAfterAClientNetworkInterruption() {
        WebWechatQrLoginService service = service();
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(any())).thenReturn(Map.of(
                "state", "CONFIRMED",
                "userId", "7",
                "exchangeCode", "one-time-code",
                "browserSecretHash", digest("browser-secret")
        ));

        Map<String, Object> first = service.status("challenge", "browser-secret");
        Map<String, Object> retry = service.status("challenge", "browser-secret");

        assertEquals("one-time-code", first.get("exchangeCode"));
        assertEquals(first, retry);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
