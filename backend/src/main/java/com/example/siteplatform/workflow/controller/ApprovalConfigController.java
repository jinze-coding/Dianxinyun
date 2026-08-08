package com.example.siteplatform.workflow.controller;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.seal.vo.SealUserOptionVO;
import com.example.siteplatform.workflow.dto.ApprovalConfigSaveRequest;
import com.example.siteplatform.workflow.service.WorkflowApprovalConfigService;
import com.example.siteplatform.workflow.vo.ApprovalConfigVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/approval-configs")
public class ApprovalConfigController {
    private final WorkflowApprovalConfigService service;
    private final AuthService authService;

    public ApprovalConfigController(WorkflowApprovalConfigService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public Result<List<ApprovalConfigVO>> list(@RequestParam(defaultValue = "SEAL_APPLICATION") String businessCode,
                                               @RequestParam Long projectId,
                                               @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.list(businessCode, projectId, authService.getCurrentUser(token)));
    }

    @PutMapping
    public Result<ApprovalConfigVO> save(@Valid @RequestBody ApprovalConfigSaveRequest request,
                                         @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.save(request, authService.getCurrentUser(token)));
    }

    @GetMapping("/candidates")
    public Result<List<SealUserOptionVO>> candidates(@RequestParam Long projectId,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.candidates(projectId, null, keyword,
                authService.getCurrentUser(token), true));
    }
}
