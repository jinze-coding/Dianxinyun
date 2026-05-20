package com.example.siteplatform.project.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.project.entity.ProjectInfo;
import com.example.siteplatform.project.service.ProjectService;
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

    @Operation(summary = "获取项目列表")
    @GetMapping
    public Result<List<ProjectInfo>> getProjectList(
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        List<ProjectInfo> projects = projectService.getProjectList(currentUser);
        return Result.success(projects);
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
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        projectService.deleteProject(projectId, currentUser);
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
