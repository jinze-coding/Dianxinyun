package com.example.siteplatform.seal.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.seal.dto.SealArchiveRequest;
import com.example.siteplatform.seal.service.SealApplicationFileService;
import com.example.siteplatform.seal.service.SealFileContent;
import com.example.siteplatform.seal.vo.SealApplicationFileVO;
import com.example.siteplatform.seal.vo.SealApplicationVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/seal/applications")
public class SealApplicationFileController {
    private final SealApplicationFileService service;
    private final AuthService authService;

    public SealApplicationFileController(SealApplicationFileService service, AuthService authService) {
        this.service = service;
        this.authService = authService;
    }

    @PostMapping(value = "/{id}/files", consumes = "multipart/form-data")
    public Result<SealApplicationFileVO> upload(@PathVariable Long id,
                                                @RequestPart("file") MultipartFile file,
                                                @RequestParam String fileRole,
                                                @RequestParam(required = false) Long itemId,
                                                @RequestHeader(value = "Authorization", required = false) String token,
                                                HttpServletRequest request) {
        return Result.success(service.upload(id, fileRole, itemId, file,
                authService.getCurrentUser(token), request));
    }

    @DeleteMapping("/{id}/files/{fileId}")
    public Result<Void> delete(@PathVariable Long id, @PathVariable Long fileId,
                               @RequestHeader(value = "Authorization", required = false) String token,
                               HttpServletRequest request) {
        service.delete(id, fileId, authService.getCurrentUser(token), request);
        return Result.success();
    }

    @GetMapping("/{id}/files/{fileId}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id, @PathVariable Long fileId,
                                            @RequestHeader(value = "Authorization", required = false) String token,
                                            HttpServletRequest request) {
        return response(service.content(id, fileId, true, authService.getCurrentUser(token), request));
    }

    @GetMapping("/{id}/files/{fileId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long fileId,
                                             @RequestHeader(value = "Authorization", required = false) String token,
                                             HttpServletRequest request) {
        return response(service.content(id, fileId, false, authService.getCurrentUser(token), request));
    }

    @PostMapping("/{id}/archive")
    public Result<SealApplicationVO> archive(@PathVariable Long id,
                                             @Valid @RequestBody SealArchiveRequest request,
                                             @RequestHeader(value = "Authorization", required = false) String token,
                                             HttpServletRequest servletRequest) {
        return Result.success(service.archive(id, request, authService.getCurrentUser(token), servletRequest));
    }

    private ResponseEntity<Resource> response(SealFileContent content) {
        ContentDisposition disposition = (content.inline()
                ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(content.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(content.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }
}
