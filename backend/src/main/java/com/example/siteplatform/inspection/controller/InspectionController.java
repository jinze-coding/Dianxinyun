package com.example.siteplatform.inspection.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.inspection.dto.InspectionRecordRequest;
import com.example.siteplatform.inspection.dto.ProjectInspectionSettingRequest;
import com.example.siteplatform.inspection.entity.InspectionTemplate;
import com.example.siteplatform.inspection.service.InspectionService;
import com.example.siteplatform.inspection.service.ProjectInspectionSettingService;
import com.example.siteplatform.inspection.vo.InspectionMonthSummaryVO;
import com.example.siteplatform.inspection.vo.InspectionRecordVO;
import com.example.siteplatform.inspection.vo.InspectionTodoVO;
import com.example.siteplatform.inspection.vo.ProjectInspectionSettingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "电箱巡检", description = "电箱巡检、记录查询与月度导出接口")
@RestController
@RequestMapping("/api/v1/inspection")
public class InspectionController {

    @Autowired
    private InspectionService inspectionService;

    @Autowired
    private ProjectInspectionSettingService projectInspectionSettingService;

    @Autowired
    private AuthService authService;

    @Operation(summary = "获取检查模板")
    @GetMapping("/templates")
    public Result<List<InspectionTemplate>> listTemplates() {
        return Result.success(inspectionService.listTemplates());
    }

    @Operation(summary = "获取项目巡检设置")
    @GetMapping("/settings/{projectId}")
    public Result<ProjectInspectionSettingVO> getProjectSetting(
            @PathVariable Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(projectInspectionSettingService.get(projectId, currentUser));
    }

    @Operation(summary = "更新项目巡检设置")
    @PutMapping("/settings/{projectId}")
    public Result<ProjectInspectionSettingVO> updateProjectSetting(
            @PathVariable Long projectId,
            @RequestBody ProjectInspectionSettingRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(projectInspectionSettingService.save(projectId, request, currentUser));
    }

    @Operation(summary = "获取检查记录")
    @GetMapping("/records")
    public Result<List<InspectionRecordVO>> listRecords(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long electricBoxId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String checkDate,
            @RequestParam(required = false) String reviewScope,
            @RequestParam(required = false) Boolean reviewOverdue,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(inspectionService.listRecords(
                projectId, electricBoxId, status, month, checkDate, reviewScope, reviewOverdue, currentUser));
    }

    @Operation(summary = "获取今日待巡检任务")
    @GetMapping("/todos")
    public Result<List<InspectionTodoVO>> listTodos(
            @RequestParam(required = false) Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(inspectionService.listTodos(projectId, currentUser));
    }

    @Operation(summary = "获取检查记录详情")
    @GetMapping("/records/{id:\\d+}")
    public Result<InspectionRecordVO> getRecord(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(inspectionService.getRecord(id, currentUser));
    }

    @Operation(summary = "完成电箱巡检")
    @PostMapping("/records")
    public Result<InspectionRecordVO> createRecord(
            @RequestBody InspectionRecordRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(inspectionService.createRecord(request, currentUser));
    }

    @Operation(summary = "兼容旧客户端提交检查记录")
    @PostMapping("/records/{id}/submit")
    public Result<InspectionRecordVO> submitRecord(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(inspectionService.submitRecord(id, currentUser));
    }

    @Operation(summary = "获取月度或单日巡检汇总")
    @GetMapping("/records/summary")
    public Result<InspectionMonthSummaryVO> getMonthSummary(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long boxId,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String checkDate,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(inspectionService.getMonthSummary(projectId, boxId, month, checkDate, currentUser));
    }

    @Operation(summary = "导出月度巡检记录")
    @GetMapping("/records/export")
    public ResponseEntity<byte[]> exportRecords(
            @RequestParam Long projectId,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long boxId,
            @RequestParam(required = false) Long inspectorId,
            @RequestParam(required = false) String result,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        InspectionService.ExportFile exportFile = inspectionService.exportRecords(
                projectId, templateCode, month, boxId, inspectorId, result, currentUser);
        String encodedFileName = URLEncoder.encode(exportFile.fileName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(exportFile.content());
    }

}
