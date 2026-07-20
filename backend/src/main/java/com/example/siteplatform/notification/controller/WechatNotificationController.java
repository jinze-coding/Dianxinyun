package com.example.siteplatform.notification.controller;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.notification.dto.WechatSubscriptionRequest;
import com.example.siteplatform.notification.service.WechatNotificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wechat/subscriptions")
public class WechatNotificationController {
    private final WechatNotificationService service;
    private final AuthService authService;
    public WechatNotificationController(WechatNotificationService service, AuthService authService) { this.service = service; this.authService = authService; }
    @PostMapping
    public Result<Void> record(@RequestBody WechatSubscriptionRequest request,
                               @RequestHeader(value = "Authorization", required = false) String token) {
        service.recordAuthorization(authService.getCurrentUser(token).getId(), request);
        return Result.success();
    }
}
