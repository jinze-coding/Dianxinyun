package com.example.siteplatform.document.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.document.dto.DocumentUploadInitRequest;
import com.example.siteplatform.document.service.DocumentUploadSessionService;
import com.example.siteplatform.document.vo.DocumentUploadSessionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/document-uploads")
public class DocumentUploadController {
    private final DocumentUploadSessionService service;
    private final AuthService authService;

    public DocumentUploadController(DocumentUploadSessionService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping
    public Result<DocumentUploadSessionVO> initialize(@Valid @RequestBody DocumentUploadInitRequest request,
                                                       @RequestHeader("Authorization") String token) {
        return Result.success(service.initialize(request, user(token)));
    }

    @GetMapping("/{sessionId}")
    public Result<DocumentUploadSessionVO> status(@PathVariable String sessionId,
                                                   @RequestHeader("Authorization") String token) {
        return Result.success(service.status(sessionId, user(token)));
    }

    @PutMapping(value = "/{sessionId}/chunks/{index}", consumes = "multipart/form-data")
    public Result<DocumentUploadSessionVO> chunk(@PathVariable String sessionId, @PathVariable int index,
                                                  @RequestParam String sha256, @RequestParam MultipartFile chunk,
                                                  @RequestHeader("Authorization") String token) {
        return Result.success(service.uploadChunk(sessionId, index, sha256, chunk, user(token)));
    }

    @PostMapping("/{sessionId}/complete")
    public Result<DocumentUploadSessionVO> complete(@PathVariable String sessionId,
                                                     @RequestHeader("Authorization") String token) {
        return Result.success(service.complete(sessionId, user(token)));
    }

    @DeleteMapping("/{sessionId}")
    public Result<Void> cancel(@PathVariable String sessionId, @RequestHeader("Authorization") String token) {
        service.cancel(sessionId, user(token));
        return Result.success();
    }

    private SysUser user(String token) { return authService.getCurrentUser(token); }
}
