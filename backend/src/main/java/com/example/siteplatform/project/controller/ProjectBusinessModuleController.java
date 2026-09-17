package com.example.siteplatform.project.controller;

import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.Result;
import com.example.siteplatform.project.service.ProjectBusinessModuleService;
import com.example.siteplatform.project.service.ProjectPermissionService;
import com.example.siteplatform.system.service.SystemPermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectBusinessModuleController {
    private final ProjectBusinessModuleService modules;
    private final SystemPermissionService permissions;
    private final ProjectPermissionService projects;
    private final AuthService auth;
    public record Update(@NotNull @Size(max=5) List<@NotBlank String> moduleCodes,
                         @NotNull @Min(1) Long expectedVersion, Boolean inboxEntryVisible) {}

    @GetMapping("/system/project-modules")
    public Result<?> list(@RequestParam(required=false) String keyword,
                          @RequestParam(defaultValue="1") int pageNo, @RequestParam(defaultValue="20") int pageSize,
                          @RequestHeader("Authorization") String token) {
        permissions.requirePlatformAdmin(auth.getCurrentUser(token));
        return Result.success(modules.list(keyword, pageNo, pageSize));
    }

    @PutMapping("/system/project-modules/{projectId}")
    public Result<?> update(@PathVariable Long projectId, @Valid @RequestBody Update body,
                            @RequestHeader("Authorization") String token) {
        var user = auth.getCurrentUser(token); permissions.requirePlatformAdmin(user);
        return Result.success(modules.update(projectId, body.moduleCodes(), body.expectedVersion(), body.inboxEntryVisible(), user));
    }

    @GetMapping("/projects/{projectId}/business-modules")
    public Result<?> state(@PathVariable Long projectId, @RequestHeader("Authorization") String token) {
        projects.checkProjectPermission(auth.getCurrentUser(token).getId(), projectId);
        return Result.success(modules.state(projectId));
    }
}
