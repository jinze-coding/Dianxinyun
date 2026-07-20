package com.example.siteplatform.auth.controller;

import com.example.siteplatform.auth.dto.WechatAccessApplicationVO;
import com.example.siteplatform.auth.dto.WechatApplicationReviewRequest;
import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.auth.service.WechatAccessApplicationService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "微信内部人员申请", description = "微信账号匹配和项目权限审批")
@RestController
@RequestMapping("/api/v1/wechat-access-applications")
public class WechatAccessApplicationController {

    private final WechatAccessApplicationService service;
    private final AuthService authService;

    public WechatAccessApplicationController(WechatAccessApplicationService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @Operation(summary = "查询微信内部人员申请")
    @GetMapping
    public Result<PageResult<WechatAccessApplicationVO>> list(@RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") Integer pageNo,
                                                        @RequestParam(defaultValue = "20") Integer pageSize,
                                                        @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.list(projectId, status, keyword, pageNo, pageSize, authService.getCurrentUser(token)));
    }

    @Operation(summary = "通过微信内部人员申请")
    @PostMapping("/{id}/approve")
    public Result<WechatAccessApplicationVO> approve(@PathVariable Long id,
                                                     @RequestBody(required = false) WechatApplicationReviewRequest request,
                                                     @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.approve(id, request, authService.getCurrentUser(token)));
    }

    @Operation(summary = "拒绝微信内部人员申请")
    @PostMapping("/{id}/reject")
    public Result<WechatAccessApplicationVO> reject(@PathVariable Long id,
                                                    @RequestBody(required = false) WechatApplicationReviewRequest request,
                                                    @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.reject(id, request, authService.getCurrentUser(token)));
    }
}
