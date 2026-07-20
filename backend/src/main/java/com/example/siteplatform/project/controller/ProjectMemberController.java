package com.example.siteplatform.project.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.project.dto.CreateProjectUserRequest;
import com.example.siteplatform.project.dto.ProjectMemberRequest;
import com.example.siteplatform.project.dto.ProjectMemberVO;
import com.example.siteplatform.project.dto.ProjectMemberStatusRequest;
import com.example.siteplatform.project.dto.ProjectUserOptionVO;
import com.example.siteplatform.project.service.ProjectMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "项目成员与巡检授权", description = "项目内成员授权、权限模板分配和用户选择接口")
@RestController
@RequestMapping("/api/v1/project-members")
public class ProjectMemberController {

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private AuthService authService;

    @Operation(summary = "查询项目成员")
    @GetMapping
    public Result<List<ProjectMemberVO>> listMembers(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(projectMemberService.listMembers(projectId, currentUser));
    }

    @Operation(summary = "查询可选用户")
    @GetMapping("/users")
    public Result<List<ProjectUserOptionVO>> listUserOptions(
            @RequestParam Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(projectMemberService.listUserOptions(projectId, keyword, currentUser));
    }

    @Operation(summary = "新增用户并加入项目")
    @PostMapping("/users")
    public Result<ProjectMemberVO> createUser(
            @RequestBody CreateProjectUserRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(projectMemberService.createUserAndJoinProject(request, currentUser));
    }

    @Operation(summary = "添加项目成员")
    @PostMapping
    public Result<ProjectMemberVO> addMember(
            @RequestBody ProjectMemberRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(projectMemberService.saveMember(request, currentUser));
    }

    @Operation(summary = "修改项目成员授权")
    @PutMapping("/{projectId}/{userId}")
    public Result<ProjectMemberVO> updateMember(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @RequestBody ProjectMemberRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        if (request == null) {
            request = new ProjectMemberRequest();
        }
        request.setProjectId(projectId);
        request.setUserId(userId);
        return Result.success(projectMemberService.saveMember(request, currentUser));
    }

    @Operation(summary = "移除项目成员")
    @DeleteMapping("/{projectId}/{userId}")
    public Result<Void> removeMember(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        projectMemberService.removeMember(projectId, userId, currentUser);
        return Result.success();
    }

    @Operation(summary = "暂停或恢复项目成员访问")
    @PutMapping("/{projectId}/{userId}/status")
    public Result<ProjectMemberVO> updateMemberStatus(
            @PathVariable Long projectId,
            @PathVariable Long userId,
            @RequestBody ProjectMemberStatusRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(projectMemberService.updateMemberStatus(projectId, userId, request, authService.getCurrentUser(token)));
    }
}
