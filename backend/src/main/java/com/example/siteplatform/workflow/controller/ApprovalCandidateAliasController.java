package com.example.siteplatform.workflow.controller;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.seal.vo.SealUserOptionVO;
import com.example.siteplatform.workflow.service.WorkflowApprovalConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/approval-candidates")
public class ApprovalCandidateAliasController {
    private final WorkflowApprovalConfigService service;
    private final AuthService authService;

    public ApprovalCandidateAliasController(WorkflowApprovalConfigService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public Result<List<SealUserOptionVO>> candidates(@RequestParam Long projectId,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.candidates(projectId, null, keyword,
                authService.getCurrentUser(token), true));
    }
}
