package com.example.siteplatform.auth.controller;

import com.example.siteplatform.auth.dto.WechatCurrentBindRequest;
import com.example.siteplatform.auth.dto.WechatSelfUnbindRequest;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.WechatAuthService;
import com.example.siteplatform.common.RedisRateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserWechatBindingControllerTest {

    @Mock private AuthService authService;
    @Mock private WechatAuthService wechatAuthService;
    @Mock private RedisRateLimitService rateLimitService;
    @Mock private HttpServletRequest request;

    private CurrentUserWechatBindingController controller;
    private SysUser currentUser;

    @BeforeEach
    void setUp() {
        controller = new CurrentUserWechatBindingController(
                authService, wechatAuthService, rateLimitService);
        currentUser = new SysUser();
        currentUser.setId(7L);
        currentUser.setUsername(" User@Example.COM ");
        when(request.getHeader("Authorization")).thenReturn("Bearer session-token");
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        when(authService.getCurrentUser("session-token")).thenReturn(currentUser);
    }

    @Test
    void compatibilityBindEndpointUsesSharedIpAndAccountLimits() {
        WechatCurrentBindRequest body = new WechatCurrentBindRequest();
        body.setCode("wechat-code");

        controller.bind(body, request);

        verify(rateLimitService).check(
                "wechat-self-bind-ip", "203.0.113.9", 40, Duration.ofMinutes(10));
        verify(rateLimitService).check(
                "wechat-self-bind-account", "user@example.com", 20, Duration.ofMinutes(10));
        verify(wechatAuthService).bindCurrent("wechat-code", currentUser);
    }

    @Test
    void compatibilityUnbindEndpointLimitsPasswordAttempts() {
        WechatSelfUnbindRequest body = new WechatSelfUnbindRequest();
        body.setPassword("Password123");

        controller.unbind(body, request);

        verify(rateLimitService).check(
                "wechat-self-unbind-ip", "203.0.113.9", 10, Duration.ofMinutes(10));
        verify(rateLimitService).check(
                "wechat-self-unbind-account", "user@example.com", 5, Duration.ofMinutes(10));
        verify(wechatAuthService).unbindCurrent(currentUser, "Password123");
    }
}
