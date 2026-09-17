package com.example.siteplatform.project.controller;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.project.dto.*;
import com.example.siteplatform.project.service.ProjectAccessBatchService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/users/project-role-assignments/batch")
public class ProjectAccessBatchController {
    private final AuthService auth;
    private final ProjectAccessBatchService service;
    @PostMapping("/preview")
    public Result<ProjectAccessBatchPreview> preview(@Valid @RequestBody ProjectAccessBatchRequest body, HttpServletRequest request) {
        return Result.success(service.preview(body, current(request)));
    }
    @PostMapping("/confirm")
    public Result<ProjectAccessBatchPreview> confirm(@Valid @RequestBody ProjectAccessBatchConfirmRequest body, HttpServletRequest request) {
        return Result.success(service.confirm(body, current(request)));
    }
    private SysUser current(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return auth.getCurrentUser(bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7).trim() : null);
    }
}
