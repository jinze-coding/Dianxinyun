package com.example.siteplatform.quality.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.quality.dto.QualityWeeklyActionRequest;
import com.example.siteplatform.quality.dto.QualityWeeklyDraftCreateRequest;
import com.example.siteplatform.quality.dto.QualityWeeklyDraftSaveRequest;
import com.example.siteplatform.quality.dto.QualityWeeklyReminderSettingRequest;
import com.example.siteplatform.quality.service.QualityWeeklyReminderSettingService;
import com.example.siteplatform.quality.vo.QualityAssigneeVO;
import com.example.siteplatform.quality.service.QualityWeeklyInspectionService;
import com.example.siteplatform.quality.vo.QualityWeeklyInspectionVO;
import com.example.siteplatform.quality.vo.QualityWeeklyReminderSettingVO;
import com.example.siteplatform.quality.vo.QualityWeeklySummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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

@Tag(name = "质量周检", description = "质量周检共享草稿、批量问题提交与周检记录接口")
@RestController
@RequestMapping("/api/v1/quality/weekly-inspections")
public class QualityWeeklyInspectionController {

    @Autowired
    private QualityWeeklyInspectionService weeklyInspectionService;

    @Autowired
    private QualityWeeklyReminderSettingService reminderSettingService;

    @Autowired
    private AuthService authService;

    @Operation(summary = "获取质量周检未提交提醒设置")
    @GetMapping("/reminder-setting/{projectId}")
    public Result<QualityWeeklyReminderSettingVO> reminderSetting(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(reminderSettingService.getSetting(projectId, currentUser));
    }

    @Operation(summary = "保存质量周检未提交提醒设置")
    @PutMapping("/reminder-setting/{projectId}")
    public Result<QualityWeeklyReminderSettingVO> saveReminderSetting(
            @PathVariable Long projectId,
            @Valid @RequestBody QualityWeeklyReminderSettingRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(reminderSettingService.updateSetting(projectId, request, currentUser));
    }

    @Operation(summary = "获取质量周检提醒责任人候选")
    @GetMapping("/reminder-assignees")
    public Result<List<QualityAssigneeVO>> reminderAssignees(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(reminderSettingService.listReminderAssignees(projectId, currentUser));
    }

    @Operation(summary = "分页查询质量周检")
    @GetMapping("/page")
    public Result<PageResult<QualityWeeklyInspectionVO>> page(
            @RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(weeklyInspectionService.page(
                projectId, status, keyword, pageNo, pageSize, currentUser));
    }

    @Operation(summary = "获取本周质量周检摘要")
    @GetMapping("/summary")
    public Result<QualityWeeklySummaryVO> summary(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(weeklyInspectionService.summary(projectId, currentUser));
    }

    @Operation(summary = "获取质量周检详情")
    @GetMapping("/{id}")
    public Result<QualityWeeklyInspectionVO> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(weeklyInspectionService.detail(id, currentUser));
    }

    @Operation(summary = "创建或恢复指定周的共享草稿")
    @PostMapping("/drafts")
    public Result<QualityWeeklyInspectionVO> createOrRestore(
            @Valid @RequestBody QualityWeeklyDraftCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(weeklyInspectionService.createOrRestore(request, currentUser));
    }

    @Operation(summary = "整体保存质量周检共享草稿")
    @PutMapping("/{id}/draft")
    public Result<QualityWeeklyInspectionVO> saveDraft(
            @PathVariable Long id,
            @Valid @RequestBody QualityWeeklyDraftSaveRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(weeklyInspectionService.saveDraft(id, request, currentUser));
    }

    @Operation(summary = "原子提交质量周检及全部问题")
    @PostMapping("/{id}/submit")
    public Result<QualityWeeklyInspectionVO> submit(
            @PathVariable Long id,
            @Valid @RequestBody QualityWeeklyActionRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(weeklyInspectionService.submit(id, request, currentUser));
    }

    @Operation(summary = "放弃质量周检共享草稿")
    @PostMapping("/{id}/discard")
    public Result<Void> discard(
            @PathVariable Long id,
            @Valid @RequestBody QualityWeeklyActionRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        weeklyInspectionService.discard(id, request, currentUser);
        return Result.success();
    }
}
