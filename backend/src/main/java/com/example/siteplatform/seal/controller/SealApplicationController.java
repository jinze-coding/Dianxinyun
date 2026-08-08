package com.example.siteplatform.seal.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.seal.dto.SealApplicationCopyRequest;
import com.example.siteplatform.seal.dto.SealApplicationSaveRequest;
import com.example.siteplatform.seal.dto.SealDecisionRequest;
import com.example.siteplatform.seal.dto.SealTransferRequest;
import com.example.siteplatform.seal.service.SealApplicationService;
import com.example.siteplatform.seal.vo.SealApplicationVO;
import com.example.siteplatform.seal.vo.SealUserOptionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seal/applications")
public class SealApplicationController {
    private final SealApplicationService service;
    private final AuthService authService;

    public SealApplicationController(SealApplicationService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @GetMapping
    public Result<PageResult<SealApplicationVO>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "INITIATED") String scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String dateBasis,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.list(projectId, scope, status, keyword, startDate, endDate, dateBasis,
                pageNo, pageSize, authService.getCurrentUser(token)));
    }

    @GetMapping("/cc-candidates")
    public Result<List<SealUserOptionVO>> ccCandidates(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long sealId,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.ccCandidates(projectId, sealId, keyword, authService.getCurrentUser(token)));
    }

    @GetMapping("/{id}")
    public Result<SealApplicationVO> detail(@PathVariable Long id,
                                            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.detail(id, authService.getCurrentUser(token)));
    }

    @PostMapping
    public Result<SealApplicationVO> create(@Valid @RequestBody SealApplicationSaveRequest request,
                                            @RequestHeader(value = "Authorization", required = false) String token,
                                            HttpServletRequest servletRequest) {
        return Result.success(service.create(request, authService.getCurrentUser(token), servletRequest));
    }

    @PutMapping("/{id}")
    public Result<SealApplicationVO> update(@PathVariable Long id,
                                            @Valid @RequestBody SealApplicationSaveRequest request,
                                            @RequestHeader(value = "Authorization", required = false) String token,
                                            HttpServletRequest servletRequest) {
        return Result.success(service.update(id, request, authService.getCurrentUser(token), servletRequest));
    }

    @PostMapping("/{id}/copy")
    public Result<SealApplicationVO> copy(@PathVariable Long id,
                                          @Valid @RequestBody SealApplicationCopyRequest request,
                                          @RequestHeader(value = "Authorization", required = false) String token,
                                          HttpServletRequest servletRequest) {
        return Result.success(service.copy(id, request, authService.getCurrentUser(token), servletRequest));
    }

    @PostMapping("/{id}/submit")
    public Result<SealApplicationVO> submit(@PathVariable Long id,
                                            @RequestHeader(value = "Authorization", required = false) String token,
                                            HttpServletRequest servletRequest) {
        return Result.success(service.submit(id, authService.getCurrentUser(token), servletRequest));
    }

    @PostMapping("/{id}/approve")
    public Result<SealApplicationVO> approve(@PathVariable Long id,
                                             @Valid @RequestBody SealDecisionRequest request,
                                             @RequestHeader(value = "Authorization", required = false) String token,
                                             HttpServletRequest servletRequest) {
        return Result.success(service.approve(id, request.getOpinion(), authService.getCurrentUser(token), servletRequest));
    }

    @PostMapping("/{id}/reject")
    public Result<SealApplicationVO> reject(@PathVariable Long id,
                                            @Valid @RequestBody SealDecisionRequest request,
                                            @RequestHeader(value = "Authorization", required = false) String token,
                                            HttpServletRequest servletRequest) {
        return Result.success(service.reject(id, request.getOpinion(), authService.getCurrentUser(token), servletRequest));
    }

    @PostMapping({"/{id}/cancel", "/{id}/withdraw"})
    public Result<SealApplicationVO> withdraw(@PathVariable Long id,
                                              @RequestHeader(value = "Authorization", required = false) String token,
                                              HttpServletRequest servletRequest) {
        return Result.success(service.withdraw(id, authService.getCurrentUser(token), servletRequest));
    }

    @PostMapping({"/{id}/transfer", "/{id}/reassign"})
    public Result<SealApplicationVO> transfer(@PathVariable Long id,
                                              @Valid @RequestBody SealTransferRequest request,
                                              @RequestHeader(value = "Authorization", required = false) String token,
                                              HttpServletRequest servletRequest) {
        return Result.success(service.transfer(id, request, authService.getCurrentUser(token), servletRequest));
    }

    @GetMapping("/{id}/transfer-candidates")
    public Result<List<SealUserOptionVO>> transferCandidates(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.transferCandidates(id, keyword, authService.getCurrentUser(token)));
    }
}
