package com.example.siteplatform.auth.controller;

import com.example.siteplatform.auth.dto.LoginRequest;
import com.example.siteplatform.auth.dto.WechatBindLoginRequest;
import com.example.siteplatform.auth.dto.WechatCurrentBindRequest;
import com.example.siteplatform.auth.dto.WechatProjectAccessRequest;
import com.example.siteplatform.auth.dto.WechatSelfUnbindRequest;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.CaptchaService;
import com.example.siteplatform.auth.service.WechatAuthService;
import com.example.siteplatform.common.RedisRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerRateLimitTest {

    @Mock private AuthService authService;
    @Mock private WechatAuthService wechatAuthService;
    @Mock private CaptchaService captchaService;
    @Mock private RedisRateLimitService rateLimitService;
    @Mock private HttpServletRequest httpRequest;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "wechatAuthService", wechatAuthService);
        ReflectionTestUtils.setField(controller, "captchaService", captchaService);
        ReflectionTestUtils.setField(controller, "rateLimitService", rateLimitService);
        when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.9");
    }

    @Test
    void passwordLoginSeparatesIpAndGlobalAccountLimits() {
        LoginRequest request = new LoginRequest();
        request.setUsername("  User@Example.COM  ");
        request.setPassword("Password123");

        controller.login(request, httpRequest);

        verify(rateLimitService).check(
                "password-login-ip", "203.0.113.9", 30, Duration.ofMinutes(10));
        verify(rateLimitService).check(
                "password-login-account", "user@example.com", 10, Duration.ofMinutes(10));
    }

    @Test
    void wechatBindLoginUsesTheSameGlobalAccountDimension() {
        WechatBindLoginRequest request = new WechatBindLoginRequest();
        request.setCode("wechat-code");
        request.setUsername("  13800000000 ");
        request.setPassword("Password123");

        controller.wechatBindLogin(request, httpRequest);

        verify(rateLimitService).check(
                "wechat-bind-login-ip", "203.0.113.9", 30, Duration.ofMinutes(10));
        verify(rateLimitService).check(
                "wechat-bind-login-account", "13800000000", 10, Duration.ofMinutes(10));
    }

    @Test
    void currentWechatBindLimitsIpAndAccountBeforeCallingWechat() {
        SysUser user = currentUser();
        WechatCurrentBindRequest request = new WechatCurrentBindRequest();
        request.setCode("wechat-code");
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer session-token");
        when(authService.getCurrentUser("session-token")).thenReturn(user);

        controller.bindCurrentWechat(request, httpRequest);

        verify(rateLimitService).check(
                "wechat-self-bind-ip", "203.0.113.9", 40, Duration.ofMinutes(10));
        verify(rateLimitService).check(
                "wechat-self-bind-account", "user@example.com", 20, Duration.ofMinutes(10));
        verify(wechatAuthService).bindCurrent("wechat-code", user);
    }

    @Test
    void currentWechatUnbindLimitsPasswordAttemptsByIpAndAccount() {
        SysUser user = currentUser();
        WechatSelfUnbindRequest request = new WechatSelfUnbindRequest();
        request.setPassword("Password123");
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer session-token");
        when(authService.getCurrentUser("session-token")).thenReturn(user);

        controller.unbindCurrentWechat(request, httpRequest);

        verify(rateLimitService).check(
                "wechat-self-unbind-ip", "203.0.113.9", 10, Duration.ofMinutes(10));
        verify(rateLimitService).check(
                "wechat-self-unbind-account", "user@example.com", 5, Duration.ofMinutes(10));
        verify(wechatAuthService).unbindCurrent(user, "Password123");
    }

    @Test
    void projectAccessApplicationLimitsIpAndAccount() {
        SysUser user = currentUser();
        WechatProjectAccessRequest request = new WechatProjectAccessRequest();
        request.setScene("B:PUBLIC-CODE");
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer session-token");
        when(authService.getCurrentUser("session-token")).thenReturn(user);

        controller.wechatProjectAccess(request, httpRequest);

        verify(rateLimitService).check(
                "wechat-self-project-access-ip", "203.0.113.9", 20, Duration.ofMinutes(10));
        verify(rateLimitService).check(
                "wechat-self-project-access-account", "user@example.com", 10, Duration.ofMinutes(10));
        verify(wechatAuthService).requestProjectAccess(request, user);
    }

    private SysUser currentUser() {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername(" User@Example.COM ");
        return user;
    }
}
