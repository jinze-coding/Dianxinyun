package com.example.siteplatform.document.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.document.dto.DocumentIncomingBatchSaveRequest;
import com.example.siteplatform.document.dto.DocumentIncomingPublishRequest;
import com.example.siteplatform.document.dto.DocumentVoidRequest;
import com.example.siteplatform.document.service.DocumentCirculationService;
import com.example.siteplatform.document.vo.DocumentDistributionBatchVO;
import com.example.siteplatform.document.vo.DocumentIncomingBatchVO;
import com.example.siteplatform.document.vo.DocumentMatchCandidateVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/document-incoming-batches")
public class DocumentIncomingBatchController {
    private final DocumentCirculationService service;
    private final AuthService authService;

    public DocumentIncomingBatchController(DocumentCirculationService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping
    public Result<DocumentIncomingBatchVO> create(@Valid @RequestBody DocumentIncomingBatchSaveRequest request,
                                                   @RequestHeader("Authorization") String token) {
        return Result.success(service.createIncoming(request, user(token)));
    }

    @PutMapping("/{id}")
    public Result<DocumentIncomingBatchVO> update(@PathVariable Long id,
                                                   @Valid @RequestBody DocumentIncomingBatchSaveRequest request,
                                                   @RequestHeader("Authorization") String token) {
        return Result.success(service.updateIncoming(id, request, user(token)));
    }

    @GetMapping
    public Result<PageResult<DocumentIncomingBatchVO>> list(@RequestParam Long projectId,
                                                             @RequestParam(required = false) String status,
                                                             @RequestParam(defaultValue = "1") int pageNo,
                                                             @RequestParam(defaultValue = "20") int pageSize,
                                                             @RequestHeader("Authorization") String token) {
        return Result.success(service.listIncoming(projectId, status, pageNo, pageSize, user(token)));
    }

    @GetMapping("/{id}")
    public Result<DocumentIncomingBatchVO> detail(@PathVariable Long id,
                                                   @RequestHeader("Authorization") String token) {
        return Result.success(service.incomingDetail(id, user(token)));
    }

    @GetMapping("/match-candidates")
    public Result<List<DocumentMatchCandidateVO>> candidates(@RequestParam Long projectId,
                                                              @RequestParam String documentType,
                                                              @RequestParam String documentNo,
                                                              @RequestHeader("Authorization") String token) {
        return Result.success(service.matchCandidates(projectId, documentType, documentNo, user(token)));
    }

    @PostMapping("/{id}/publish")
    public Result<DocumentDistributionBatchVO> publish(@PathVariable Long id,
                                                        @Valid @RequestBody DocumentIncomingPublishRequest request,
                                                        @RequestHeader("Authorization") String token,
                                                        HttpServletRequest httpRequest) {
        return Result.success(service.publishIncoming(id, request, user(token), httpRequest));
    }

    @PostMapping("/{id}/void")
    public Result<Void> voidBatch(@PathVariable Long id, @Valid @RequestBody DocumentVoidRequest request,
                                  @RequestHeader("Authorization") String token,
                                  HttpServletRequest httpRequest) {
        service.voidIncoming(id, request.getReason(), user(token), httpRequest);
        return Result.success();
    }

    private SysUser user(String token) { return authService.getCurrentUser(token); }
}
