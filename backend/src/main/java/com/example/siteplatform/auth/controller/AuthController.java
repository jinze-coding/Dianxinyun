package com.example.siteplatform.auth.controller;

import com.example.siteplatform.auth.dto.LoginRequest;
import com.example.siteplatform.auth.dto.LoginResponse;
import com.example.siteplatform.auth.dto.CurrentUserVO;
import com.example.siteplatform.auth.dto.WechatPhoneRequest;
import com.example.siteplatform.auth.dto.WechatSessionRequest;
import com.example.siteplatform.auth.dto.WechatSessionResponse;
import com.example.siteplatform.auth.dto.WechatProjectAccessRequest;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.WechatAuthService;
import com.example.siteplatform.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证管理", description = "用户登录、登出、用户信息接口")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private WechatAuthService wechatAuthService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    @Operation(summary = "微信小程序登录并查询绑定状态")
    @PostMapping("/wechat/session")
    public Result<WechatSessionResponse> wechatSession(@RequestBody WechatSessionRequest request) {
        return Result.success(wechatAuthService.session(request));
    }

    @Operation(summary = "微信手机号匹配账号或提交权限申请")
    @PostMapping("/wechat/phone")
    public Result<WechatSessionResponse> wechatPhone(@RequestBody WechatPhoneRequest request) {
        return Result.success(wechatAuthService.bindPhone(request));
    }

    @Operation(summary = "已绑定微信用户申请当前项目权限")
    @PostMapping("/wechat/project-access")
    public Result<WechatSessionResponse> wechatProjectAccess(@RequestBody WechatProjectAccessRequest request,
                                                             HttpServletRequest httpRequest) {
        return Result.success(wechatAuthService.requestProjectAccess(request,
                authService.getCurrentUser(extractToken(httpRequest))));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/user-info")
    public Result<CurrentUserVO> getUserInfo(HttpServletRequest request) {
        String token = extractToken(request);
        return Result.success(authService.getCurrentUserInfo(token));
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token != null) {
            authService.getCurrentUser(token);
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
}
