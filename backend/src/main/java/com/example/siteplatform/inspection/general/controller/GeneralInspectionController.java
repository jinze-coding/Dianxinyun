package com.example.siteplatform.inspection.general.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.inspection.general.dto.*;
import com.example.siteplatform.inspection.general.entity.*;
import com.example.siteplatform.inspection.general.service.*;
import com.example.siteplatform.inspection.general.vo.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "通用巡检", description = "通用巡检配置、任务、整改、统计与异步导出")
@RestController
@RequestMapping("/api/v1/general-inspections")
@RequiredArgsConstructor
public class GeneralInspectionController {

    private final AuthService authService;
    private final GeneralInspectionConfigService configService;
    private final GeneralInspectionTaskGenerationService generationService;
    private final GeneralInspectionTaskService taskService;
    private final GeneralInspectionReportingService reportingService;
    private final GeneralInspectionExportService exportService;

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

    @GetMapping("/templates")
    public Result<List<GeneralInspectionTemplateVO>> listTemplates(@RequestParam Long projectId,
                                                                    @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.listTemplates(projectId, user(token)));
    }

    @GetMapping("/templates/{id}")
    public Result<GeneralInspectionTemplateVO> getTemplate(@PathVariable Long id,
                                                            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.getTemplate(id, user(token)));
    }

    @GetMapping("/templates/{id}/preview")
    public Result<GeneralInspectionTemplateVO> previewTemplate(@PathVariable Long id,
                                                                @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.previewTemplate(id, user(token)));
    }

    @PostMapping("/templates")
    public Result<GeneralInspectionTemplateVO> createTemplate(@Valid @RequestBody GeneralInspectionTemplateSaveRequest request,
                                                               @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.createTemplate(request, user(token)));
    }

    @PostMapping("/templates/{id}/copy")
    public Result<GeneralInspectionTemplateVO> copyTemplate(@PathVariable Long id,
                                                             @Valid @RequestBody GeneralInspectionTemplateCopyRequest request,
                                                             @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.copyTemplate(id, request, user(token)));
    }

    @PutMapping("/templates/{id}")
    public Result<GeneralInspectionTemplateVO> saveTemplate(@PathVariable Long id,
                                                             @Valid @RequestBody GeneralInspectionTemplateSaveRequest request,
                                                             @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.saveTemplate(id, request, user(token)));
    }

    @PostMapping("/templates/{id}/publish")
    public Result<GeneralInspectionTemplateVO> publishTemplate(@PathVariable Long id,
                                                                @Valid @RequestBody GeneralInspectionPublishRequest request,
                                                                @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.publishTemplate(id, request, user(token)));
    }

    @PostMapping("/templates/{id}/archive")
    public Result<Void> archiveTemplate(@PathVariable Long id,
                                        @Valid @RequestBody GeneralInspectionTaskActionRequest request,
                                        @RequestHeader(value = "Authorization", required = false) String token) {
        configService.archiveTemplate(id, request, user(token));
        return Result.success();
    }

    @GetMapping("/point-categories")
    public Result<List<GeneralInspectionPointCategory>> listCategories(@RequestParam Long projectId,
                                                                       @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.listCategories(projectId, user(token)));
    }

    @PostMapping("/point-categories")
    public Result<GeneralInspectionPointCategory> createCategory(@Valid @RequestBody GeneralInspectionCategoryRequest request,
                                                                  @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.createCategory(request, user(token)));
    }

    @GetMapping("/points")
    public Result<List<GeneralInspectionPoint>> listPoints(@RequestParam Long projectId,
                                                            @RequestParam(required = false) String status,
                                                            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.listPoints(projectId, status, user(token)));
    }

    @GetMapping("/user-options")
    public Result<List<GeneralInspectionUserOptionVO>> listUserOptions(@RequestParam Long projectId,
                                                                       @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.listUserOptions(projectId, user(token)));
    }

    @PostMapping("/points")
    public Result<GeneralInspectionPoint> createPoint(@Valid @RequestBody GeneralInspectionPointRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.savePoint(null, request, user(token)));
    }

    @PutMapping("/points/{id}")
    public Result<GeneralInspectionPoint> updatePoint(@PathVariable Long id,
                                                       @Valid @RequestBody GeneralInspectionPointRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.savePoint(id, request, user(token)));
    }

    @PostMapping("/points/{id}/rotate-code")
    public Result<GeneralInspectionPoint> rotatePointCode(@PathVariable Long id,
                                                           @Valid @RequestBody GeneralInspectionPointActionRequest request,
                                                           @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.rotatePointCode(id, request, user(token)));
    }

    @GetMapping("/points/{id}/qr")
    public Result<Map<String, Object>> getPointQr(@PathVariable Long id,
                                                   @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.getPointQr(id, user(token)));
    }

    @PostMapping("/points/{id}/status/{status}")
    public Result<GeneralInspectionPoint> changePointStatus(@PathVariable Long id, @PathVariable String status,
                                                             @Valid @RequestBody GeneralInspectionPointActionRequest request,
                                                             @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.changePointStatus(id, status, request, user(token)));
    }

    @GetMapping("/plans")
    public Result<List<GeneralInspectionPlanVO>> listPlans(@RequestParam Long projectId,
                                                            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.listPlans(projectId, user(token)));
    }

    @PostMapping("/plans")
    public Result<GeneralInspectionPlanVO> createPlan(@Valid @RequestBody GeneralInspectionPlanSaveRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.savePlan(null, request, user(token)));
    }

    @PutMapping("/plans/{id}")
    public Result<GeneralInspectionPlanVO> updatePlan(@PathVariable Long id,
                                                       @Valid @RequestBody GeneralInspectionPlanSaveRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.savePlan(id, request, user(token)));
    }

    @PostMapping("/plans/preview")
    public Result<List<Map<String, Object>>> previewPlan(@Valid @RequestBody GeneralInspectionPlanSaveRequest request,
                                                         @RequestParam(defaultValue = "14") int days,
                                                         @RequestHeader(value = "Authorization", required = false) String token) {
        GeneralInspectionPlanConfig config = configService.validatePlanPreview(request, user(token));
        return Result.success(generationService.preview(config, LocalDateTime.now(), days));
    }

    @PostMapping("/plans/{id}/publish")
    public Result<GeneralInspectionPlanVO> publishPlan(@PathVariable Long id,
                                                        @Valid @RequestBody GeneralInspectionPublishRequest request,
                                                        @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.publishPlan(id, request, user(token)));
    }

    @PostMapping("/plans/{id}/status/{status}")
    public Result<GeneralInspectionPlanVO> changePlanStatus(@PathVariable Long id, @PathVariable String status,
                                                            @Valid @RequestBody GeneralInspectionTaskActionRequest request,
                                                            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(configService.changePlanState(id, status, request, user(token)));
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

    @PostMapping("/scan/resolve")
    public Result<GeneralInspectionScanVO> resolveScan(@Valid @RequestBody GeneralInspectionScanRequest request,
                                                        @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.resolveScan(request.getSceneCode(), user(token)));
    }

    @PostMapping("/tasks/{id}/scan")
    public Result<GeneralInspectionTaskVO> verifyScan(@PathVariable Long id,
                                                       @Valid @RequestBody GeneralInspectionScanRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.verifyScan(id, request, user(token)));
    }

    @PostMapping("/tasks/{id}/submit")
    public Result<GeneralInspectionTaskVO> submitTask(@PathVariable Long id,
                                                       @Valid @RequestBody GeneralInspectionTaskSubmitRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.submitTask(id, request, user(token)));
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

    @PostMapping("/tasks/{id}/correct")
    public Result<GeneralInspectionTaskVO> correctTask(@PathVariable Long id,
                                                        @Valid @RequestBody GeneralInspectionCorrectionRequest request,
                                                        @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.correctTask(id, request, user(token)));
    }

    @PostMapping("/tasks/{id}/correction-note")
    public Result<GeneralInspectionTaskVO> appendCorrectionNote(@PathVariable Long id,
                                                                 @Valid @RequestBody GeneralInspectionCorrectionNoteRequest request,
                                                                 @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.appendCorrectionNote(id, request, user(token)));
    }

    @PostMapping("/tasks/{id}/void-reinspect")
    public Result<GeneralInspectionTaskVO> voidAndReinspect(@PathVariable Long id,
                                                            @Valid @RequestBody GeneralInspectionTaskActionRequest request,
                                                            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.voidAndReinspect(id, request, user(token)));
    }

    @GetMapping("/rectifications")
    public Result<List<GeneralInspectionRectificationVO>> listRectifications(@RequestParam(required = false) Long projectId,
                                                                              @RequestParam(required = false) String status,
                                                                              @RequestParam(required = false) String scope,
                                                                              @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.listRectifications(projectId, status, scope, user(token)));
    }

    @GetMapping("/rectifications/{id}")
    public Result<GeneralInspectionRectificationVO> getRectification(@PathVariable Long id,
                                                                      @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.getRectification(id, user(token)));
    }

    @PostMapping("/rectifications/{id}/assign")
    public Result<GeneralInspectionRectificationVO> assignRectification(@PathVariable Long id,
                                                                         @Valid @RequestBody GeneralInspectionRectificationRequest request,
                                                                         @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.assignRectification(id, request, user(token)));
    }

    @PostMapping("/rectifications/{id}/complete")
    public Result<GeneralInspectionRectificationVO> completeRectification(@PathVariable Long id,
                                                                           @Valid @RequestBody GeneralInspectionRectificationRequest request,
                                                                           @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.completeRectification(id, request, user(token)));
    }

    @PostMapping("/rectifications/{id}/close")
    public Result<GeneralInspectionRectificationVO> closeRectification(@PathVariable Long id,
                                                                        @Valid @RequestBody GeneralInspectionRectificationRequest request,
                                                                        @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.reviewRectification(id, request, true, user(token)));
    }

    @PostMapping("/rectifications/{id}/reject")
    public Result<GeneralInspectionRectificationVO> rejectRectification(@PathVariable Long id,
                                                                         @Valid @RequestBody GeneralInspectionRectificationRequest request,
                                                                         @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(taskService.reviewRectification(id, request, false, user(token)));
    }

    @GetMapping("/dashboard")
    public Result<GeneralInspectionDashboardVO> dashboard(@RequestParam Long projectId,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                                           @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(reportingService.dashboard(projectId, startDate, endDate, user(token)));
    }

    @PostMapping("/exports")
    public Result<GeneralInspectionExportJob> createExport(@Valid @RequestBody GeneralInspectionExportRequest request,
                                                            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(exportService.create(request, user(token)));
    }

    @GetMapping("/exports")
    public Result<List<GeneralInspectionExportJob>> listExports(@RequestParam Long projectId,
                                                                 @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(exportService.list(projectId, user(token)));
    }

    @GetMapping("/exports/{id}/download")
    public ResponseEntity<Resource> downloadExport(@PathVariable Long id,
                                                    @RequestHeader(value = "Authorization", required = false) String token) {
        GeneralInspectionExportService.Download download = exportService.download(id, user(token));
        String encoded = URLEncoder.encode(download.filename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    private SysUser user(String token) {
        return authService.getCurrentUser(token);
    }
}
