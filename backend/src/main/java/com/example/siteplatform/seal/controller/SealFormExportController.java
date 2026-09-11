package com.example.siteplatform.seal.controller;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.seal.dto.SealFormExportRequest;
import com.example.siteplatform.seal.service.SealFormExportService;
import com.example.siteplatform.seal.vo.SealFormExportJobVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seal/applications/form-export-jobs")
@RequiredArgsConstructor
public class SealFormExportController {
    private final SealFormExportService service;
    private final AuthService auth;

    @PostMapping
    public Result<SealFormExportJobVO> create(@Valid @RequestBody SealFormExportRequest request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.create(request, auth.getCurrentUser(token)));
    }
    @GetMapping
    public Result<List<SealFormExportJobVO>> list(@RequestParam Long projectId,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.list(projectId, auth.getCurrentUser(token)));
    }
    @GetMapping("/{id}")
    public Result<SealFormExportJobVO> get(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.get(id, auth.getCurrentUser(token)));
    }
    @PostMapping("/{id}/retry")
    public Result<SealFormExportJobVO> retry(@PathVariable Long id, @Valid @RequestBody Retry request,
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(service.retry(id, request.requestKey(), auth.getCurrentUser(token)));
    }
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String token) {
        var download = service.download(id, auth.getCurrentUser(token));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header("X-Content-Type-Options", "nosniff").body(download.resource());
    }
    public record Retry(@NotBlank @Size(max = 64) String requestKey) {}
}
