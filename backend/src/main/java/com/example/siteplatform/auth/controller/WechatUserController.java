package com.example.siteplatform.auth.controller;

import com.example.siteplatform.auth.dto.*;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.WechatUserManagementService;
import com.example.siteplatform.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@Tag(name = "小程序用户管理", description = "小程序注册用户、微信绑定和项目权限管理")
@RestController
@RequestMapping("/api/v1/wechat-users")
public class WechatUserController {
    private final WechatUserManagementService service;
    private final AuthService authService;

    public WechatUserController(WechatUserManagementService service, AuthService authService) {
        this.service = service; this.authService = authService;
    }

    @Operation(summary = "分页查询小程序注册用户")
    @GetMapping
    public Result<WechatUserPageVO> list(
            @RequestParam(required = false) Long projectId, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bindingStatus, @RequestParam(required = false) String projectAccessStatus,
            @RequestParam(required = false) String projectRoleCode, @RequestParam(required = false) Long permissionTemplateId,
            @RequestParam(defaultValue = "1") Integer pageNo, @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.list(projectId, keyword, bindingStatus, projectAccessStatus, projectRoleCode,
                permissionTemplateId, pageNo, pageSize, authService.getCurrentUser(token)));
    }

    @Operation(summary = "查询小程序用户详情")
    @GetMapping("/{userId}")
    public Result<WechatUserDetailVO> detail(@PathVariable Long userId,
                                             @RequestParam(required = false) Long projectId,
                                             @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.detail(userId, projectId, authService.getCurrentUser(token)));
    }

    @Operation(summary = "启用或停用微信登录")
    @PutMapping("/{userId}/bindings/{bindingId}/status")
    public Result<WechatBindingVO> updateBindingStatus(@PathVariable Long userId, @PathVariable Long bindingId,
                                                       @RequestBody WechatBindingStatusRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.updateBindingStatus(userId, bindingId, request, authService.getCurrentUser(token)));
    }

    @Operation(summary = "解绑微信")
    @PostMapping("/{userId}/bindings/{bindingId}/unbind")
    public Result<WechatBindingVO> unbind(@PathVariable Long userId, @PathVariable Long bindingId,
                                          @RequestBody WechatUnbindRequest request,
                                          @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.unbind(userId, bindingId, request, authService.getCurrentUser(token)));
    }
}
