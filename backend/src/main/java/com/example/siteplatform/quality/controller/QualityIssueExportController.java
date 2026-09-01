package com.example.siteplatform.quality.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.quality.dto.QualityIssueExportRequest;
import com.example.siteplatform.quality.service.QualityIssueExportService;
import com.example.siteplatform.quality.vo.QualityIssueExportJobVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "质量管理", description = "质量问题按日筛选与异步含图报表")
@RestController
@RequestMapping("/api/v1/quality/issues/export-jobs")
@RequiredArgsConstructor
public class QualityIssueExportController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final QualityIssueExportService exportService;
    private final AuthService authService;

    @Operation(summary = "创建质量问题含图Excel导出任务")
    @PostMapping
    public Result<QualityIssueExportJobVO> create(
            @Valid @RequestBody QualityIssueExportRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(exportService.create(request, currentUser));
    }

    @Operation(summary = "查询质量问题导出任务")
    @GetMapping
    public Result<List<QualityIssueExportJobVO>> list(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(exportService.list(projectId, currentUser));
    }

    @Operation(summary = "查询单个质量问题导出任务进度")
    @GetMapping("/{id}")
    public Result<QualityIssueExportJobVO> get(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(exportService.get(id, currentUser));
    }

    @Operation(summary = "下载质量问题含图Excel")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        QualityIssueExportService.Download download = exportService.download(id, currentUser);
        String encoded = URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(XLSX)
                .body(download.resource());
    }
}
