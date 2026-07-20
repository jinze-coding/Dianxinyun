package com.example.siteplatform.document.controller;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.document.dto.DocumentFolderCreateRequest;
import com.example.siteplatform.document.dto.DocumentFolderUpdateRequest;
import com.example.siteplatform.document.service.DocumentFolderService;
import com.example.siteplatform.document.vo.DocumentFolderVO;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/document-folders")
public class DocumentFolderController {
    private final DocumentFolderService folderService;
    private final AuthService authService;

    public DocumentFolderController(DocumentFolderService folderService, AuthService authService) {
        this.folderService = folderService;
        this.authService = authService;
    }

    @GetMapping
    public Result<List<DocumentFolderVO>> list(@RequestParam Long projectId,
                                               @RequestHeader("Authorization") String token) {
        return Result.success(folderService.list(projectId, authService.getCurrentUser(token)));
    }

    @PostMapping
    public Result<DocumentFolderVO> create(@Valid @RequestBody DocumentFolderCreateRequest request,
                                           @RequestHeader("Authorization") String token) {
        return Result.success(folderService.create(request, authService.getCurrentUser(token)));
    }

    @PutMapping("/{id}")
    public Result<DocumentFolderVO> update(@PathVariable Long id,
                                           @Valid @RequestBody DocumentFolderUpdateRequest request,
                                           @RequestHeader("Authorization") String token) {
        return Result.success(folderService.update(id, request, authService.getCurrentUser(token)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        folderService.delete(id, authService.getCurrentUser(token));
        return Result.success();
    }
}
