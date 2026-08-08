package com.example.siteplatform.siteaccess.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.siteaccess.dto.SiteVisitInvitationCreateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitInvitationUpdateRequest;
import com.example.siteplatform.siteaccess.dto.SiteVisitVoidRequest;
import com.example.siteplatform.siteaccess.service.SiteAccessService;
import com.example.siteplatform.siteaccess.vo.SiteVisitHostOptionVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitInvitationVO;
import com.example.siteplatform.siteaccess.vo.SiteVisitMiniCodeVO;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/site-access")
public class SiteAccessController {
    private final SiteAccessService service;
    private final AuthService authService;

    public SiteAccessController(SiteAccessService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping("/invitations")
    public Result<PageResult<SiteVisitInvitationVO>> page(
            @RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.page(projectId, status, keyword, startDate, endDate,
                pageNo, pageSize, authService.getCurrentUser(token)));
    }

    @GetMapping("/invitations/{id}")
    public Result<SiteVisitInvitationVO> detail(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.detail(id, authService.getCurrentUser(token)));
    }

    @GetMapping("/host-options")
    public Result<List<SiteVisitHostOptionVO>> hostOptions(
            @RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.hostOptions(projectId, authService.getCurrentUser(token)));
    }

    @PostMapping("/invitations")
    public Result<SiteVisitInvitationVO> create(
            @Valid @RequestBody SiteVisitInvitationCreateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.create(request, authService.getCurrentUser(token)));
    }

    @PutMapping("/invitations/{id}")
    public Result<SiteVisitInvitationVO> update(
            @PathVariable Long id,
            @Valid @RequestBody SiteVisitInvitationUpdateRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.update(id, request, authService.getCurrentUser(token)));
    }

    @PostMapping("/invitations/{id}/void")
    public Result<SiteVisitInvitationVO> voidInvitation(
            @PathVariable Long id,
            @Valid @RequestBody SiteVisitVoidRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.voidInvitation(id, request.getReason(), authService.getCurrentUser(token)));
    }

    @GetMapping("/invitations/{id}/mini-code")
    public Result<SiteVisitMiniCodeVO> miniCode(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.miniCode(id, authService.getCurrentUser(token)));
    }

    @GetMapping("/visitors/export")
    public ResponseEntity<byte[]> export(
            @RequestParam Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestHeader(value = "Authorization", required = false) String token) {
        SysUser currentUser = authService.getCurrentUser(token);
        SiteAccessService.ExportFile file = service.export(
                projectId, status, keyword, startDate, endDate, currentUser);
        String fileName = URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file.content());
    }
}
