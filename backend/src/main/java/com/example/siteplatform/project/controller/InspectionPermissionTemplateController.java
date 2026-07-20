package com.example.siteplatform.project.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.project.dto.InspectionPermissionCatalogGroupVO;
import com.example.siteplatform.project.dto.InspectionPermissionTemplateRequest;
import com.example.siteplatform.project.dto.InspectionPermissionTemplateStatusRequest;
import com.example.siteplatform.project.dto.InspectionPermissionTemplateVO;
import com.example.siteplatform.project.service.InspectionPermissionTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "电箱巡检权限模板", description = "电箱巡检功能权限模板和权限目录")
@RestController
@RequestMapping("/api/v1/inspection-permission-templates")
public class InspectionPermissionTemplateController {

    @Autowired
    private InspectionPermissionTemplateService templateService;

    @Autowired
    private AuthService authService;

    @Operation(summary = "查询权限模板")
    @GetMapping
    public Result<List<InspectionPermissionTemplateVO>> listTemplates(
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(templateService.listTemplates(currentUser));
    }

    @Operation(summary = "查询权限码目录")
    @GetMapping("/catalog")
    public Result<List<InspectionPermissionCatalogGroupVO>> catalog(
            @RequestHeader(value = "Authorization", required = false) String token) {
        authService.getCurrentUser(token);
        return Result.success(templateService.permissionCatalog());
    }

    @Operation(summary = "创建权限模板")
    @PostMapping
    public Result<InspectionPermissionTemplateVO> createTemplate(
            @RequestBody InspectionPermissionTemplateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(templateService.createTemplate(request, currentUser));
    }

    @Operation(summary = "编辑权限模板")
    @PutMapping("/{id}")
    public Result<InspectionPermissionTemplateVO> updateTemplate(
            @PathVariable Long id,
            @RequestBody InspectionPermissionTemplateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(templateService.updateTemplate(id, request, currentUser));
    }

    @Operation(summary = "启停权限模板")
    @PostMapping("/{id}/status")
    public Result<InspectionPermissionTemplateVO> updateStatus(
            @PathVariable Long id,
            @RequestBody InspectionPermissionTemplateStatusRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(templateService.updateStatus(id, request, currentUser));
    }
}
