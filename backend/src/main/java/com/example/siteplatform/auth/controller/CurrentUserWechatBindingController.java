package com.example.siteplatform.auth.controller;

import com.example.siteplatform.auth.dto.WechatCurrentBindRequest;
import com.example.siteplatform.auth.dto.WechatSelfUnbindRequest;
import com.example.siteplatform.auth.dto.WechatSessionResponse;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.WechatAuthService;
import com.example.siteplatform.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/wechat-bindings")
public class CurrentUserWechatBindingController {
    private final AuthService authService;
    private final WechatAuthService wechatAuthService;

    public CurrentUserWechatBindingController(AuthService authService, WechatAuthService wechatAuthService) {
        this.authService = authService;
        this.wechatAuthService = wechatAuthService;
    }

    @PostMapping
    public Result<WechatSessionResponse> bind(@Valid @RequestBody WechatCurrentBindRequest body,
                                              HttpServletRequest request) {
        return Result.success(wechatAuthService.bindCurrent(body.getCode(), current(request)));
    }

    @DeleteMapping
    public Result<Void> unbind(@Valid @RequestBody WechatSelfUnbindRequest body,
                               HttpServletRequest request) {
        wechatAuthService.unbindCurrent(current(request), body.getPassword());
        return Result.success();
    }

    private SysUser current(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        String token = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7).trim() : null;
        return authService.getCurrentUser(token);
    }
}
