package com.example.siteplatform.auth.controller;

import com.example.siteplatform.auth.dto.WechatCurrentBindRequest;
import com.example.siteplatform.auth.dto.WechatSelfUnbindRequest;
import com.example.siteplatform.auth.dto.WechatSessionResponse;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.WechatAuthService;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/users/me/wechat-bindings")
public class CurrentUserWechatBindingController {
    private final AuthService authService;
    private final WechatAuthService wechatAuthService;
    private final RedisRateLimitService rateLimitService;

    public CurrentUserWechatBindingController(AuthService authService, WechatAuthService wechatAuthService,
                                              RedisRateLimitService rateLimitService) {
        this.authService = authService;
        this.wechatAuthService = wechatAuthService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public Result<WechatSessionResponse> bind(@Valid @RequestBody WechatCurrentBindRequest body,
                                              HttpServletRequest request) {
        SysUser currentUser = current(request);
        limit("bind", currentUser, request, 20);
        return Result.success(wechatAuthService.bindCurrent(body.getCode(), currentUser));
    }

    @DeleteMapping
    public Result<Void> unbind(@Valid @RequestBody WechatSelfUnbindRequest body,
                               HttpServletRequest request) {
        SysUser currentUser = current(request);
        limit("unbind", currentUser, request, 5);
        wechatAuthService.unbindCurrent(currentUser, body.getPassword());
        return Result.success();
    }

    private SysUser current(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        String token = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7).trim() : null;
        return authService.getCurrentUser(token);
    }

    private void limit(String action, SysUser currentUser, HttpServletRequest request, int maxAttempts) {
        rateLimitService.check("wechat-self-" + action + "-ip", request.getRemoteAddr(),
                maxAttempts * 2, Duration.ofMinutes(10));
        rateLimitService.check("wechat-self-" + action + "-account",
                accountLimitSubject(currentUser),
                maxAttempts, Duration.ofMinutes(10));
    }

    private String accountLimitSubject(SysUser currentUser) {
        String username = currentUser.getUsername();
        return username == null || username.isBlank()
                ? "user-id:" + currentUser.getId()
                : username.trim().toLowerCase(Locale.ROOT);
    }
}
