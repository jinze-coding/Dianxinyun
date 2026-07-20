package com.example.siteplatform.document.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.PageResult;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.document.dto.ProjectDocumentBatchRequest;
import com.example.siteplatform.document.dto.ProjectDocumentUpdateRequest;
import com.example.siteplatform.document.service.ProjectDocumentContent;
import com.example.siteplatform.document.service.ProjectDocumentService;
import com.example.siteplatform.document.vo.ProjectDocumentActivityVO;
import com.example.siteplatform.document.vo.ProjectDocumentDetailVO;
import com.example.siteplatform.document.vo.ProjectDocumentSummaryVO;
import com.example.siteplatform.document.vo.ProjectDocumentVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/project-documents")
public class ProjectDocumentController {
    private final ProjectDocumentService documentService;
    private final AuthService authService;

    public ProjectDocumentController(ProjectDocumentService documentService, AuthService authService) {
        this.documentService = documentService;
        this.authService = authService;
    }

    @GetMapping
    public Result<PageResult<ProjectDocumentVO>> list(
            @RequestParam Long projectId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") Integer pageNo,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader("Authorization") String token) {
        return Result.success(documentService.list(projectId, folderId, keyword, category, status,
                startDate, endDate, pageNo, pageSize, currentUser(token)));
    }

    @GetMapping("/summary")
    public Result<ProjectDocumentSummaryVO> summary(@RequestParam Long projectId,
                                                    @RequestHeader("Authorization") String token) {
        return Result.success(documentService.summary(projectId, currentUser(token)));
    }

    @GetMapping("/recycle-bin")
    public Result<PageResult<ProjectDocumentVO>> recycleBin(@RequestParam Long projectId,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(defaultValue = "1") Integer pageNo,
                                                             @RequestParam(defaultValue = "20") Integer pageSize,
                                                             @RequestHeader("Authorization") String token) {
        return Result.success(documentService.recycleBin(projectId, keyword, pageNo, pageSize, currentUser(token)));
    }

    @GetMapping("/activities")
    public Result<List<ProjectDocumentActivityVO>> activities(@RequestParam Long projectId,
                                                               @RequestParam(required = false) Long documentId,
                                                               @RequestParam(defaultValue = "50") Integer limit,
                                                               @RequestHeader("Authorization") String token) {
        return Result.success(documentService.activities(projectId, documentId, limit, currentUser(token)));
    }

    @GetMapping("/{id}")
    public Result<ProjectDocumentDetailVO> detail(@PathVariable Long id,
                                                  @RequestHeader("Authorization") String token) {
        return Result.success(documentService.detail(id, currentUser(token)));
    }

    @PostMapping
    public Result<ProjectDocumentDetailVO> create(
            @RequestParam MultipartFile file,
            @RequestParam Long projectId,
            @RequestParam(required = false, defaultValue = "0") Long folderId,
            @RequestParam(required = false) String documentNo,
            @RequestParam String title,
            @RequestParam(defaultValue = "PROJECT_DATA") String category,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) String changeNote,
            @RequestHeader("Authorization") String token,
            HttpServletRequest request) {
        return Result.success(documentService.create(projectId, folderId, documentNo, title, category,
                remark, changeNote, file, currentUser(token), request));
    }

    @PostMapping("/{id}/versions")
    public Result<ProjectDocumentDetailVO> uploadVersion(@PathVariable Long id,
                                                         @RequestParam MultipartFile file,
                                                         @RequestParam(required = false) String changeNote,
                                                         @RequestHeader("Authorization") String token,
                                                         HttpServletRequest request) {
        return Result.success(documentService.uploadVersion(id, changeNote, file, currentUser(token), request));
    }

    @PutMapping("/{id}")
    public Result<ProjectDocumentDetailVO> update(@PathVariable Long id,
                                                  @RequestBody ProjectDocumentUpdateRequest update,
                                                  @RequestHeader("Authorization") String token,
                                                  HttpServletRequest request) {
        return Result.success(documentService.update(id, update, currentUser(token), request));
    }

    @PostMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id, @RequestHeader("Authorization") String token,
                                HttpServletRequest request) {
        documentService.archive(id, currentUser(token), request);
        return Result.success();
    }

    @PostMapping("/{id}/unarchive")
    public Result<Void> unarchive(@PathVariable Long id, @RequestHeader("Authorization") String token,
                                  HttpServletRequest request) {
        documentService.unarchive(id, currentUser(token), request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestHeader("Authorization") String token,
                               HttpServletRequest request) {
        documentService.delete(id, currentUser(token), request);
        return Result.success();
    }

    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable Long id, @RequestHeader("Authorization") String token,
                                HttpServletRequest request) {
        documentService.restore(id, currentUser(token), request);
        return Result.success();
    }

    @DeleteMapping("/{id}/purge")
    public Result<Void> purge(@PathVariable Long id, @RequestHeader("Authorization") String token,
                              HttpServletRequest request) {
        documentService.purge(id, currentUser(token), request);
        return Result.success();
    }

    @PostMapping("/batch")
    public Result<Void> batch(@Valid @RequestBody ProjectDocumentBatchRequest batch,
                              @RequestHeader("Authorization") String token,
                              HttpServletRequest request) {
        documentService.batch(batch, currentUser(token), request);
        return Result.success();
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> preview(@PathVariable Long id,
                                            @RequestParam(required = false) Long versionId,
                                            @RequestHeader("Authorization") String token,
                                            HttpServletRequest request) {
        return contentResponse(documentService.content(id, versionId, currentUser(token), request, true), true);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @RequestParam(required = false) Long versionId,
                                             @RequestHeader("Authorization") String token,
                                             HttpServletRequest request) {
        return contentResponse(documentService.content(id, versionId, currentUser(token), request, false), false);
    }

    private ResponseEntity<Resource> contentResponse(ProjectDocumentContent content, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(content.fileName(), StandardCharsets.UTF_8).build();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.mimeType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(content.resource());
    }

    private SysUser currentUser(String token) {
        return authService.getCurrentUser(token);
    }
}
