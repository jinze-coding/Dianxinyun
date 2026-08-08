package com.example.siteplatform.workcenter.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.workcenter.service.PersonalWorkCenterService;
import com.example.siteplatform.workcenter.vo.InboxNotificationVO;
import com.example.siteplatform.workcenter.vo.PersonalTodoVO;
import com.example.siteplatform.workcenter.vo.WorkSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "个人工作中心", description = "当前用户待办、抄送和站内通知")
@RestController
@RequestMapping("/api/v1/me")
public class PersonalWorkCenterController {

    private final PersonalWorkCenterService workCenterService;
    private final AuthService authService;

    public PersonalWorkCenterController(PersonalWorkCenterService workCenterService,
                                        AuthService authService) {
        this.workCenterService = workCenterService;
        this.authService = authService;
    }

    @Operation(summary = "分页获取当前用户待办或抄送事项")
    @GetMapping("/todos")
    public Result<PageResult<PersonalTodoVO>> todos(
            @RequestParam(defaultValue = "PENDING") String scope,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        SysUser currentUser = authService.getCurrentUser(authorization);
        return Result.success(workCenterService.listTodos(
                scope, projectId, type, pageNo, pageSize, currentUser));
    }

    @Operation(summary = "获取当前用户工作汇总")
    @GetMapping({"/work-summary", "/todos/summary"})
    public Result<WorkSummaryVO> workSummary(
            @RequestParam(required = false) Long projectId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        SysUser currentUser = authService.getCurrentUser(authorization);
        return Result.success(workCenterService.workSummary(projectId, currentUser));
    }

    @Operation(summary = "分页获取当前用户站内通知")
    @GetMapping({"/inbox", "/notifications"})
    public Result<PageResult<InboxNotificationVO>> inbox(
            @RequestParam(defaultValue = "ALL") String readStatus,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        SysUser currentUser = authService.getCurrentUser(authorization);
        return Result.success(workCenterService.inbox(
                readStatus, businessType, projectId, pageNo, pageSize, currentUser));
    }

    @Operation(summary = "获取当前用户未读通知数")
    @GetMapping({"/inbox/unread-count", "/notifications/unread-count"})
    public Result<Long> unreadCount(
            @RequestParam(required = false) Long projectId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        SysUser currentUser = authService.getCurrentUser(authorization);
        return Result.success(workCenterService.unreadCount(projectId, currentUser));
    }

    @Operation(summary = "将当前用户一条通知标记为已读")
    @PutMapping({"/inbox/{id:\\d+}/read", "/notifications/{id:\\d+}/read"})
    public Result<InboxNotificationVO> markRead(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        SysUser currentUser = authService.getCurrentUser(authorization);
        return Result.success(workCenterService.markRead(id, currentUser));
    }

    @Operation(summary = "将当前用户可访问范围内的通知全部标记为已读")
    @PutMapping({"/inbox/read-all", "/notifications/read-all"})
    public Result<Void> markAllRead(
            @RequestParam(required = false) Long projectId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        SysUser currentUser = authService.getCurrentUser(authorization);
        workCenterService.markAllRead(projectId, currentUser);
        return Result.success();
    }
}
