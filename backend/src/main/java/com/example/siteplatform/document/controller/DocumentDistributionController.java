package com.example.siteplatform.document.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.document.dto.DocumentDistributionCreateRequest;
import com.example.siteplatform.document.dto.DocumentVoidRequest;
import com.example.siteplatform.document.service.DocumentCirculationService;
import com.example.siteplatform.document.vo.DocumentDistributionBatchVO;
import com.example.siteplatform.document.vo.DocumentRecipientOptionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/document-distributions")
public class DocumentDistributionController {
    private final DocumentCirculationService service;
    private final AuthService authService;

    public DocumentDistributionController(DocumentCirculationService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping
    public Result<DocumentDistributionBatchVO> create(@Valid @RequestBody DocumentDistributionCreateRequest request,
                                                       @RequestHeader("Authorization") String token,
                                                       HttpServletRequest httpRequest) {
        return Result.success(service.createDistribution(request, user(token), httpRequest));
    }

    @GetMapping
    public Result<PageResult<DocumentDistributionBatchVO>> list(@RequestParam Long projectId,
                                                                 @RequestParam(required = false) String status,
                                                                 @RequestParam(defaultValue = "1") int pageNo,
                                                                 @RequestParam(defaultValue = "20") int pageSize,
                                                                 @RequestHeader("Authorization") String token) {
        return Result.success(service.listDistributions(projectId, status, pageNo, pageSize, user(token)));
    }

    @GetMapping("/{id}")
    public Result<DocumentDistributionBatchVO> detail(@PathVariable Long id,
                                                       @RequestHeader("Authorization") String token) {
        return Result.success(service.distributionDetail(id, user(token)));
    }

    @GetMapping("/recipient-candidates")
    public Result<List<DocumentRecipientOptionVO>> recipients(@RequestParam Long projectId,
                                                               @RequestParam(required = false) List<Long> versionIds,
                                                               @RequestHeader("Authorization") String token) {
        return Result.success(service.recipientCandidates(projectId, versionIds, user(token)));
    }

    @GetMapping(value = "/{id}/qr", produces = "image/svg+xml")
    public ResponseEntity<String> qr(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml"))
                .header("Cache-Control", "no-store").header("X-Content-Type-Options", "nosniff")
                .body(service.distributionQrSvg(id, user(token)));
    }

    @PostMapping("/{id}/void")
    public Result<Void> voidBatch(@PathVariable Long id, @Valid @RequestBody DocumentVoidRequest request,
                                  @RequestHeader("Authorization") String token,
                                  HttpServletRequest httpRequest) {
        service.voidDistribution(id, request.getReason(), user(token), httpRequest);
        return Result.success();
    }

    private SysUser user(String token) { return authService.getCurrentUser(token); }
}
