package com.example.siteplatform.system.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.system.dto.AdministrativeDeletionExecuteRequest;
import com.example.siteplatform.system.dto.AdministrativeDeletionPreviewRequest;
import com.example.siteplatform.system.dto.DeletionImpactVO;
import com.example.siteplatform.system.service.AdministrativeDeletionService;
import com.example.siteplatform.system.service.SystemPermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/v1/system/deletions")
public class AdministrativeDeletionController {
    private final AdministrativeDeletionService deletionService;
    private final SystemPermissionService permissionService;
    private final AuthService authService;

    public AdministrativeDeletionController(AdministrativeDeletionService deletionService,
                                            SystemPermissionService permissionService,
                                            AuthService authService) {
        this.deletionService = deletionService;
        this.permissionService = permissionService;
        this.authService = authService;
    }

    @PostMapping("/preview")
    public Result<DeletionImpactVO> preview(
            @Valid @RequestBody AdministrativeDeletionPreviewRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser operator = authService.getCurrentUser(token);
        permissionService.requirePlatformAdmin(operator);
        return Result.success(deletionService.preview(request, operator));
    }

    @PostMapping("/execute")
    public Result<Void> execute(
            @Valid @RequestBody AdministrativeDeletionExecuteRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser operator = authService.getCurrentUser(token);
        permissionService.requirePlatformAdmin(operator);
        deletionService.execute(request, operator);
        return Result.success();
    }

    @PostMapping("/failed-files/{fileId}/retry")
    public Result<Void> retryFailedFilePurge(
            @PathVariable Long fileId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser operator = authService.getCurrentUser(token);
        permissionService.requirePlatformAdmin(operator);
        deletionService.retryFailedFilePurge(fileId, operator);
        return Result.success();
    }
}
