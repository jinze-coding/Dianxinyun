package com.example.siteplatform.quality.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.quality.dto.QualityIssueCreateRequest;
import com.example.siteplatform.quality.dto.QualityAssignRequest;
import com.example.siteplatform.quality.dto.QualityRectificationRequest;
import com.example.siteplatform.quality.dto.QualityReviewRequest;
import com.example.siteplatform.quality.service.QualityIssueService;
import com.example.siteplatform.quality.vo.QualityIssueSummaryVO;
import com.example.siteplatform.quality.vo.QualityIssueVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "质量管理", description = "质量问题检查、整改和复查闭环接口")
@RestController
@RequestMapping("/api/v1/quality/issues")
public class QualityIssueController {

    @Autowired
    private QualityIssueService qualityIssueService;

    @Autowired
    private AuthService authService;

    @Operation(summary = "获取质量问题列表")
    @GetMapping
    public Result<List<QualityIssueVO>> listIssues(
            @RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(qualityIssueService.listIssues(projectId, status, keyword, currentUser));
    }

    @Operation(summary = "获取质量问题统计")
    @GetMapping("/summary")
    public Result<QualityIssueSummaryVO> getSummary(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(qualityIssueService.getSummary(projectId, currentUser));
    }

    @Operation(summary = "获取质量问题详情")
    @GetMapping("/{id}")
    public Result<QualityIssueVO> getIssue(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(qualityIssueService.getIssue(id, currentUser));
    }

    @Operation(summary = "发起质量检查问题")
    @PostMapping
    public Result<QualityIssueVO> createIssue(
            @RequestBody QualityIssueCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(qualityIssueService.createIssue(request, currentUser));
    }

    @Operation(summary = "提交质量整改")
    @PostMapping("/{id}/rectify")
    public Result<QualityIssueVO> submitRectification(
            @PathVariable Long id,
            @RequestBody QualityRectificationRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(qualityIssueService.submitRectification(id, request, currentUser));
    }

    @Operation(summary = "复查质量整改")
    @PostMapping("/{id}/review")
    public Result<QualityIssueVO> reviewIssue(
            @PathVariable Long id,
            @RequestBody QualityReviewRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(qualityIssueService.reviewIssue(id, request, currentUser));
    }

    @Operation(summary = "改派质量整改人或调整期限")
    @PostMapping("/{id}/assign")
    public Result<QualityIssueVO> assignIssue(
            @PathVariable Long id,
            @RequestBody QualityAssignRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        return Result.success(qualityIssueService.assignIssue(id, request, currentUser));
    }
}
