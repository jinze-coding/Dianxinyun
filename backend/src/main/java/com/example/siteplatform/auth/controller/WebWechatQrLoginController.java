package com.example.siteplatform.auth.controller;

import com.example.siteplatform.auth.dto.LoginResponse;
import com.example.siteplatform.auth.dto.WebQrBrowserRequest;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.WebWechatQrLoginService;
import com.example.siteplatform.common.RedisRateLimitService;
import com.example.siteplatform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/web-qr/challenges")
public class WebWechatQrLoginController {
    private final WebWechatQrLoginService service;
    private final AuthService authService;
    private final RedisRateLimitService rateLimitService;

    public WebWechatQrLoginController(WebWechatQrLoginService service, AuthService authService,
                                      RedisRateLimitService rateLimitService) {
        this.service = service;
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public Result<Map<String, Object>> create(HttpServletRequest request) {
        rateLimitService.check("web-qr-create", request.getRemoteAddr(), 20, Duration.ofMinutes(10));
        return Result.success(service.create(request.getHeader("User-Agent")));
    }

    @PostMapping("/{challengeId}/status")
    public Result<Map<String, Object>> status(@PathVariable String challengeId,
                                              @RequestBody WebQrBrowserRequest request,
                                              HttpServletRequest httpRequest) {
        rateLimitService.check("web-qr-status-ip", httpRequest.getRemoteAddr(), 180, Duration.ofMinutes(10));
        rateLimitService.check("web-qr-status-challenge",
                httpRequest.getRemoteAddr() + ":" + challengeId, 90, Duration.ofMinutes(5));
        return Result.success(service.status(challengeId, request.resolvedBrowserSecret()));
    }

    @PostMapping("/{challengeId}/mark-scanned")
    public Result<Map<String, Object>> markScanned(@PathVariable String challengeId, HttpServletRequest request) {
        limitAction("scan", challengeId, request);
        return Result.success(service.markScanned(challengeId, currentUser(request)));
    }

    @PostMapping("/{challengeId}/confirm")
    public Result<Map<String, Object>> confirm(@PathVariable String challengeId, HttpServletRequest request) {
        limitAction("confirm", challengeId, request);
        return Result.success(service.confirm(challengeId, currentUser(request)));
    }

    @PostMapping("/{challengeId}/cancel")
    public Result<Void> cancel(@PathVariable String challengeId,
                               @RequestBody(required = false) WebQrBrowserRequest body,
                               HttpServletRequest request) {
        limitAction("cancel", challengeId, request);
        String token = extractToken(request);
        if (token != null) service.cancelByUser(challengeId, authService.getCurrentUser(token));
        else service.cancelByBrowser(challengeId, body == null ? null : body.resolvedBrowserSecret());
        return Result.success();
    }

    @PostMapping("/{challengeId}/exchange")
    public Result<LoginResponse> exchange(@PathVariable String challengeId,
                                          @Valid @RequestBody WebQrBrowserRequest request,
                                          HttpServletRequest httpRequest) {
        limitAction("exchange", challengeId, httpRequest);
        return Result.success(service.exchange(challengeId, request.resolvedBrowserSecret(), request.getExchangeCode()));
    }

    private void limitAction(String action, String challengeId, HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        rateLimitService.check("web-qr-" + action + "-ip", remoteAddress, 30, Duration.ofMinutes(10));
        rateLimitService.check("web-qr-" + action + "-challenge",
                remoteAddress + ":" + challengeId, 10, Duration.ofMinutes(10));
    }

    private SysUser currentUser(HttpServletRequest request) {
        return authService.getCurrentUser(extractToken(request));
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7).trim() : null;
    }
}
