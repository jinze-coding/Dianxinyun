package com.example.siteplatform.auth.controller;

import com.example.siteplatform.auth.dto.LoginRequest;
import com.example.siteplatform.auth.dto.LoginResponse;
import com.example.siteplatform.auth.dto.CurrentUserVO;
import com.example.siteplatform.auth.dto.InitialPasswordRequest;
import com.example.siteplatform.auth.dto.WechatPhoneRequest;
import com.example.siteplatform.auth.dto.WechatSessionRequest;
import com.example.siteplatform.auth.dto.WechatSessionResponse;
import com.example.siteplatform.auth.dto.WechatProjectAccessRequest;
import com.example.siteplatform.auth.dto.WechatBindLoginRequest;
import com.example.siteplatform.auth.dto.WechatCurrentBindRequest;
import com.example.siteplatform.auth.dto.WechatSelfUnbindRequest;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.CaptchaService;
import com.example.siteplatform.auth.service.WechatAuthService;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

@Tag(name = "认证管理", description = "用户登录、登出、用户信息接口")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private WechatAuthService wechatAuthService;

    @Autowired
    private CaptchaService captchaService;

    @Autowired
    private RedisRateLimitService rateLimitService;

    @Operation(summary = "获取注册图形验证码")
    @GetMapping("/captcha")
    public Result<Map<String, Object>> captcha(HttpServletRequest request) {
        rateLimitService.check("captcha", request.getRemoteAddr(), 30, Duration.ofMinutes(10));
        return Result.success(captchaService.create());
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String remoteAddress = httpRequest.getRemoteAddr();
        rateLimitService.check("password-login-ip", remoteAddress, 30, Duration.ofMinutes(10));
        rateLimitService.check("password-login-account", normalizeAccount(request.getUsername()),
                10, Duration.ofMinutes(10));
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    @Operation(summary = "微信小程序登录并查询绑定状态")
    @PostMapping("/wechat/session")
    public Result<WechatSessionResponse> wechatSession(@Valid @RequestBody WechatSessionRequest request,
                                                        HttpServletRequest httpRequest) {
        rateLimitService.check("wechat-session", httpRequest.getRemoteAddr(), 20, Duration.ofMinutes(10));
        return Result.success(wechatAuthService.session(request));
    }

    @Operation(summary = "微信小程序快捷登录")
    @PostMapping("/wechat/mini/login")
    public Result<WechatSessionResponse> wechatMiniLogin(@Valid @RequestBody WechatSessionRequest request,
                                                         HttpServletRequest httpRequest) {
        rateLimitService.check("wechat-mini-login", httpRequest.getRemoteAddr(), 20, Duration.ofMinutes(10));
        return Result.success(wechatAuthService.miniLogin(request));
    }

    @Operation(summary = "账号密码验证后绑定微信并登录")
    @PostMapping("/wechat/mini/bind-login")
    public Result<WechatSessionResponse> wechatBindLogin(@Valid @RequestBody WechatBindLoginRequest request,
                                                         HttpServletRequest httpRequest) {
        String remoteAddress = httpRequest.getRemoteAddr();
        rateLimitService.check("wechat-bind-login-ip", remoteAddress, 30, Duration.ofMinutes(10));
        rateLimitService.check("wechat-bind-login-account", normalizeAccount(request.getUsername()),
                10, Duration.ofMinutes(10));
        return Result.success(wechatAuthService.bindLogin(request));
    }

    @Operation(summary = "当前账号绑定微信")
    @PostMapping("/wechat/bind")
    public Result<WechatSessionResponse> bindCurrentWechat(@Valid @RequestBody WechatCurrentBindRequest request,
                                                            HttpServletRequest httpRequest) {
        SysUser currentUser = authService.getCurrentUser(extractToken(httpRequest));
        limitWechatSelfService("bind", currentUser, httpRequest, 20);
        return Result.success(wechatAuthService.bindCurrent(request.getCode(), currentUser));
    }

    @Operation(summary = "当前账号解绑微信")
    @PostMapping("/wechat/unbind")
    public Result<Void> unbindCurrentWechat(@Valid @RequestBody WechatSelfUnbindRequest request,
                                             HttpServletRequest httpRequest) {
        SysUser currentUser = authService.getCurrentUser(extractToken(httpRequest));
        limitWechatSelfService("unbind", currentUser, httpRequest, 5);
        wechatAuthService.unbindCurrent(currentUser, request.getPassword());
        return Result.success();
    }

    @Operation(summary = "微信手机号匹配账号或提交权限申请")
    @PostMapping("/wechat/phone")
    public Result<WechatSessionResponse> wechatPhone(@Valid @RequestBody WechatPhoneRequest request,
                                                      HttpServletRequest httpRequest) {
        rateLimitService.check("wechat-phone", httpRequest.getRemoteAddr(), 10, Duration.ofMinutes(10));
        return Result.success(wechatAuthService.bindPhone(request));
    }

    @Operation(summary = "已绑定微信用户申请当前项目权限")
    @PostMapping("/wechat/project-access")
    public Result<WechatSessionResponse> wechatProjectAccess(@Valid @RequestBody WechatProjectAccessRequest request,
                                                             HttpServletRequest httpRequest) {
        SysUser currentUser = authService.getCurrentUser(extractToken(httpRequest));
        limitWechatSelfService("project-access", currentUser, httpRequest, 10);
        return Result.success(wechatAuthService.requestProjectAccess(request, currentUser));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/user-info")
    public Result<CurrentUserVO> getUserInfo(HttpServletRequest request) {
        String token = extractToken(request);
        return Result.success(authService.getCurrentUserInfo(token));
    }

    @Operation(summary = "微信快捷注册账号设置初始密码")
    @PostMapping("/initial-password")
    public Result<LoginResponse> setupInitialPassword(@Valid @RequestBody InitialPasswordRequest request,
                                                       HttpServletRequest httpRequest) {
        rateLimitService.check("initial-password", httpRequest.getRemoteAddr(), 10, Duration.ofMinutes(10));
        return Result.success(authService.setupInitialPassword(extractToken(httpRequest), request.getNewPassword()));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            authService.getCurrentUserAllowInitialPasswordSetup(token);
            authService.logoutSession(token);
        }
        return Result.success();
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private String normalizeAccount(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private void limitWechatSelfService(String action, SysUser currentUser,
                                        HttpServletRequest request, int maxAttempts) {
        rateLimitService.check("wechat-self-" + action + "-ip", request.getRemoteAddr(),
                maxAttempts * 2, Duration.ofMinutes(10));
        rateLimitService.check("wechat-self-" + action + "-account",
                accountLimitSubject(currentUser), maxAttempts, Duration.ofMinutes(10));
    }

    private String accountLimitSubject(SysUser currentUser) {
        String normalizedAccount = normalizeAccount(currentUser.getUsername());
        return normalizedAccount.isEmpty() ? "user-id:" + currentUser.getId() : normalizedAccount;
    }
}
