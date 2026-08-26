package com.example.siteplatform.inspection.general.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.inspection.general.dto.*;
import com.example.siteplatform.inspection.general.entity.GeneralInspectionProjectSetting;
import com.example.siteplatform.inspection.general.service.EdgeInspectionConfigService;
import com.example.siteplatform.inspection.general.service.GeneralInspectionReportingService;
import com.example.siteplatform.inspection.general.service.GeneralInspectionTaskService;
import com.example.siteplatform.inspection.general.vo.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "临边巡检", description = "固定检查表的临边点位、周期、任务与整单整改闭环")
@RestController
@RequestMapping("/api/v1/edge-inspections")
@RequiredArgsConstructor
public class GeneralInspectionController {

    private final AuthService authService;
    private final EdgeInspectionConfigService configService;
    private final GeneralInspectionTaskService taskService;
    private final GeneralInspectionReportingService reportingService;

    @GetMapping("/projects/{projectId}/feature")
    public Result<GeneralInspectionProjectSetting> getFeature(@PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.getFeature(projectId, user(token)));
    }

    @PutMapping("/projects/{projectId}/feature")
    public Result<GeneralInspectionProjectSetting> updateFeature(@PathVariable Long projectId,
            @Valid @RequestBody GeneralInspectionFeatureRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.updateFeature(projectId, request, user(token)));
    }

    @GetMapping("/point-types")
    public Result<List<EdgeInspectionPointTypeVO>> listPointTypes(@RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.listPointTypes(projectId, user(token)));
    }

    @GetMapping("/points")
    public Result<List<EdgeInspectionPointVO>> listPoints(@RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.listPoints(projectId, status, user(token)));
    }

    @PostMapping("/points")
    public Result<EdgeInspectionPointVO> createPoint(@Valid @RequestBody EdgeInspectionPointSaveRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.savePoint(null, request, user(token)));
    }

    @PutMapping("/points/{id}")
    public Result<EdgeInspectionPointVO> updatePoint(@PathVariable Long id,
            @Valid @RequestBody EdgeInspectionPointSaveRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.savePoint(id, request, user(token)));
    }

    @PostMapping("/points/{id}/status/{status}")
    public Result<EdgeInspectionPointVO> changePointStatus(@PathVariable Long id, @PathVariable String status,
            @Valid @RequestBody GeneralInspectionPointActionRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.changePointStatus(id, status, request, user(token)));
    }

    @GetMapping("/projects/{projectId}/setting")
    public Result<EdgeInspectionSettingVO> getSetting(@PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.getSetting(projectId, user(token)));
    }

    @PutMapping("/projects/{projectId}/setting")
    public Result<EdgeInspectionSettingVO> saveSetting(@PathVariable Long projectId,
            @Valid @RequestBody EdgeInspectionSettingRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.saveSetting(projectId, request, user(token)));
    }

    @GetMapping("/user-options")
    public Result<List<GeneralInspectionUserOptionVO>> listUserOptions(@RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.listUserOptions(projectId, user(token)));
    }

    @GetMapping("/tasks")
    public Result<List<GeneralInspectionTaskVO>> listTasks(@RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "true") boolean mine,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.listTasks(projectId, status, startDate, endDate, mine, user(token)));
    }

    @GetMapping("/tasks/{id}")
    public Result<GeneralInspectionTaskVO> getTask(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.getTask(id, user(token)));
    }

    @PostMapping("/tasks/{id}/submit")
    public Result<GeneralInspectionTaskVO> submitTask(@PathVariable Long id,
            @Valid @RequestBody EdgeInspectionTaskSubmitRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.submitTask(id, request.toGeneralRequest(), user(token)));
    }

    @PostMapping("/tasks/cancel")
    public Result<List<Long>> cancelTasks(@Valid @RequestBody GeneralInspectionBatchCancelRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.cancelTasks(request, user(token)));
    }

    @PostMapping("/tasks/{id}/reassign")
    public Result<GeneralInspectionTaskVO> reassignTask(@PathVariable Long id,
            @Valid @RequestBody GeneralInspectionTaskActionRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.reassignTask(id, request, user(token)));
    }

    @GetMapping("/rectifications")
    public Result<List<EdgeInspectionRectificationSheetVO>> listRectifications(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scope,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.listEdgeRectificationSheets(projectId, status, scope, user(token)));
    }

    @GetMapping("/rectifications/{taskId}")
    public Result<EdgeInspectionRectificationSheetVO> getRectification(@PathVariable Long taskId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.getEdgeRectificationSheet(taskId, user(token)));
    }

    @PostMapping("/rectifications/{taskId}/reassign")
    public Result<EdgeInspectionRectificationSheetVO> reassignRectification(@PathVariable Long taskId,
            @Valid @RequestBody EdgeInspectionRectificationReassignRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.reassignEdgeRectificationSheet(taskId, request, user(token)));
    }

    @PostMapping("/rectifications/{taskId}/complete")
    public Result<EdgeInspectionRectificationSheetVO> completeRectification(@PathVariable Long taskId,
            @Valid @RequestBody EdgeInspectionRectificationCompleteRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.completeEdgeRectificationSheet(taskId, request, user(token)));
    }

    @PostMapping("/rectifications/{taskId}/close")
    public Result<EdgeInspectionRectificationSheetVO> closeRectification(@PathVariable Long taskId,
            @Valid @RequestBody EdgeInspectionRectificationReviewRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.reviewEdgeRectificationSheet(taskId, request, true, user(token)));
    }

    @PostMapping("/rectifications/{taskId}/reject")
    public Result<EdgeInspectionRectificationSheetVO> rejectRectification(@PathVariable Long taskId,
            @Valid @RequestBody EdgeInspectionRectificationReviewRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.reviewEdgeRectificationSheet(taskId, request, false, user(token)));
    }

    @GetMapping("/statistics")
    public Result<GeneralInspectionDashboardVO> statistics(@RequestParam Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(reportingService.edgeStatistics(projectId, startDate, endDate, user(token)));
    }

    private SysUser user(String token) {
        return authService.getCurrentUser(token);
    }
}
