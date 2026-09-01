package com.example.siteplatform.document.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.document.dto.DocumentDisputeRequest;
import com.example.siteplatform.document.dto.DocumentScanRequest;
import com.example.siteplatform.document.service.DocumentCirculationService;
import com.example.siteplatform.document.vo.DocumentDistributionBatchVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/me/document-distributions")
public class MyDocumentDistributionController {
    private final DocumentCirculationService service;
    private final AuthService authService;

    public MyDocumentDistributionController(DocumentCirculationService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping("/{id}")
    public Result<DocumentDistributionBatchVO> detail(@PathVariable Long id,
                                                       @RequestHeader("Authorization") String token) {
        return Result.success(service.myDistributionDetail(id, user(token)));
    }

    @PostMapping("/scan")
    public Result<DocumentDistributionBatchVO> scan(@Valid @RequestBody DocumentScanRequest request,
                                                     @RequestHeader("Authorization") String token) {
        return Result.success(service.scan(request.getScene(), user(token)));
    }

    @PostMapping("/{id}/confirm")
    public Result<DocumentDistributionBatchVO> confirm(@PathVariable Long id,
                                                        @RequestParam(required = false) String scene,
                                                        @RequestParam(required = false) MultipartFile signature,
                                                        @RequestHeader("Authorization") String token,
                                                        HttpServletRequest httpRequest) {
        return Result.success(service.confirm(id, scene, signature, user(token), httpRequest));
    }

    @PostMapping("/{id}/dispute")
    public Result<DocumentDistributionBatchVO> dispute(@PathVariable Long id,
                                                        @Valid @RequestBody DocumentDisputeRequest request,
                                                        @RequestHeader("Authorization") String token,
                                                        HttpServletRequest httpRequest) {
        return Result.success(service.dispute(id, request.getNote(), user(token), httpRequest));
    }

    private SysUser user(String token) { return authService.getCurrentUser(token); }
}
