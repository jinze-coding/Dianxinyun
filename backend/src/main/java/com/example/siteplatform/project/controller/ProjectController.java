package com.example.siteplatform.project.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.project.dto.MiniProgramProjectVO;
import com.example.siteplatform.project.dto.MiniProgramWorkspaceOverviewVO;
import com.example.siteplatform.project.dto.ProjectLocationUpdateRequest;
import com.example.siteplatform.project.dto.ProjectMapPointVO;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectService;
import com.example.siteplatform.project.service.MiniProgramWorkspaceService;
import com.example.siteplatform.system.dto.AdministrativeDeletionExecuteRequest;
import com.example.siteplatform.system.service.AdministrativeDeletionService;
import com.example.siteplatform.system.service.SystemPermissionService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "项目管理", description = "项目信息管理接口")
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AuthService authService;

    @Autowired
    private MiniProgramWorkspaceService miniProgramWorkspaceService;

    @Autowired
    private AdministrativeDeletionService administrativeDeletionService;

    @Autowired
    private SystemPermissionService systemPermissionService;

    @Operation(summary = "获取项目列表")
    @GetMapping
    public Result<List<ProjectInfo>> getProjectList(
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        List<ProjectInfo> projects = projectService.getProjectList(currentUser);
        return Result.success(projects);
    }

    @Operation(summary = "获取小程序项目概览列表")
    @GetMapping("/mini-program/list")
    public Result<List<MiniProgramProjectVO>> getMiniProgramProjectList(
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(projectService.getMiniProgramProjectList(currentUser));
    }

    @Operation(summary = "获取小程序项目概览详情")
    @GetMapping("/mini-program/{projectId}")
    public Result<MiniProgramProjectVO> getMiniProgramProjectById(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(projectService.getMiniProgramProjectById(projectId, currentUser));
    }

    @Operation(summary = "获取小程序施工区域概况")
    @GetMapping("/mini-program/{projectId}/workspace-overview")
    public Result<MiniProgramWorkspaceOverviewVO> getMiniProgramWorkspaceOverview(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(miniProgramWorkspaceService.getOverview(projectId, currentUser));
    }

    @Operation(summary = "获取项目详情")
    @GetMapping("/{projectId}")
    public Result<ProjectInfo> getProjectById(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        ProjectInfo project = projectService.getProjectById(projectId, currentUser);
        return Result.success(project);
    }

    @Operation(summary = "获取项目地图点位")
    @GetMapping("/map-points")
    public Result<List<ProjectMapPointVO>> getProjectMapPoints(
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        List<ProjectMapPointVO> points = projectService.getProjectMapPoints(currentUser);
        return Result.success(points);
    }

    @Operation(summary = "获取项目地图详情")
    @GetMapping("/{projectId}/map-detail")
    public Result<ProjectMapPointVO> getProjectMapDetail(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        ProjectMapPointVO project = projectService.getProjectMapDetail(projectId, currentUser);
        return Result.success(project);
    }

    @Operation(summary = "更新项目定位信息")
    @PutMapping("/{projectId}/location")
    public Result<ProjectMapPointVO> updateProjectLocation(
            @PathVariable Long projectId,
            @RequestBody ProjectLocationUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        ProjectMapPointVO updated = projectService.updateProjectLocation(projectId, request, currentUser);
        return Result.success(updated);
    }

    @Operation(summary = "添加项目")
    @PostMapping
    public Result<ProjectInfo> addProject(
            @RequestBody ProjectInfo project,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        ProjectInfo newProject = projectService.addProject(project, currentUser);
        return Result.success(newProject);
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/{projectId}")
    public Result<Void> deleteProject(
            @PathVariable Long projectId,
            @Valid @RequestBody AdministrativeDeletionExecuteRequest confirmation,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        systemPermissionService.requirePlatformAdmin(currentUser);
        confirmation.setTargetType("PROJECT");
        confirmation.setTargetId(projectId);
        administrativeDeletionService.execute(confirmation, currentUser);
        return Result.success();
    }

    @Operation(summary = "更新项目")
    @PutMapping("/{projectId}")
    public Result<ProjectInfo> updateProject(
            @PathVariable Long projectId,
            @RequestBody ProjectInfo project,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        ProjectInfo updated = projectService.updateProject(projectId, project, currentUser);
        return Result.success(updated);
    }
}
