package com.example.siteplatform.system.security;

import com.example.siteplatform.auth.entity.SysUser;
import com.example.siteplatform.auth.service.AuthService;
import com.example.siteplatform.common.BusinessException;
import com.example.siteplatform.file.entity.FileResource;
import com.example.siteplatform.file.mapper.FileResourceMapper;
import com.example.siteplatform.system.constant.SystemPermissionCodes;
import com.example.siteplatform.system.service.SystemPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;

/**
 * 正式业务模块的统一 RBAC 入口守卫。
 *
 * <p>本守卫只负责跨项目通用的操作权限；控制器和服务中的项目成员、项目角色及
 * 业务对象范围校验仍须继续执行。公开电箱码、登录、注册和微信扫码接口不在注册
 * 路径内，因此不会被本守卫拦截。</p>
 */
@Component
public class BusinessModulePermissionInterceptor implements HandlerInterceptor {

    static final String PROJECT_DOCUMENTS = "/api/v1/project-documents";
    static final String DOCUMENT_FOLDERS = "/api/v1/document-folders";
    static final String INSPECTION = "/api/v1/inspection";
    static final String ELECTRIC_BOXES = "/api/v1/electric-boxes";
    static final String FILES = "/api/v1/files";
    static final String QUALITY_ISSUES = "/api/v1/quality/issues";
    static final String SITE_ACCESS = "/api/v1/site-access";

    private final AuthService authService;
    private final SystemPermissionService permissionService;
    private final FileResourceMapper fileMapper;

    public BusinessModulePermissionInterceptor(AuthService authService,
                                               SystemPermissionService permissionService,
                                               FileResourceMapper fileMapper) {
        this.authService = authService;
        this.permissionService = permissionService;
        this.fileMapper = fileMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String path = applicationPath(request);
        String permissionCode = resolveStaticPermission(request.getMethod(), path);
        SysUser currentUser = null;
        Long targetProjectId = null;

        if (permissionCode == null && matchesModule(path, FILES)) {
            // /files 是多个业务共享的附件通道，只对本期正式巡检、质量附件启用
            // 新 RBAC；人员、安全教育等旧模块继续沿用原项目范围规则。
            currentUser = authService.getCurrentUser(request.getHeader("Authorization"));
            SharedFilePermission sharedPermission = resolveSharedFilePermission(request, path);
            if (sharedPermission != null) {
                permissionCode = sharedPermission.permissionCode();
                targetProjectId = sharedPermission.projectId();
            }
        }
        if (permissionCode == null) {
            return true;
        }
        if (currentUser == null) {
            currentUser = authService.getCurrentUser(request.getHeader("Authorization"));
        }
        boolean permitted = targetProjectId == null
                ? permissionService.hasPermission(currentUser.getId(), permissionCode)
                : permissionService.hasProjectPermission(currentUser.getId(), targetProjectId, permissionCode);
        if (!permitted) {
            throw BusinessException.forbidden("无操作权限：" + permissionCode);
        }
        return true;
    }

    String resolveStaticPermission(String method, String path) {
        String normalizedPath = normalizePath(path);
        boolean read = isReadMethod(method);

        if (matchesModule(normalizedPath, PROJECT_DOCUMENTS)) {
            if (read) return SystemPermissionCodes.DOCUMENT_VIEW;
            if (HttpMethod.POST.matches(method)
                    && (PROJECT_DOCUMENTS.equals(normalizedPath)
                    || normalizedPath.matches(PROJECT_DOCUMENTS + "/[^/]+/versions"))) {
                return SystemPermissionCodes.DOCUMENT_UPLOAD;
            }
            return SystemPermissionCodes.DOCUMENT_MANAGE;
        }
        if (matchesModule(normalizedPath, DOCUMENT_FOLDERS)) {
            return read ? SystemPermissionCodes.DOCUMENT_VIEW : SystemPermissionCodes.DOCUMENT_MANAGE;
        }
        if (matchesModule(normalizedPath, INSPECTION)) {
            if (normalizedPath.equals(INSPECTION + "/records/export")) {
                return SystemPermissionCodes.INSPECTION_EXPORT;
            }
            if (read) return SystemPermissionCodes.INSPECTION_VIEW;
            if (HttpMethod.POST.matches(method)
                    && (normalizedPath.equals(INSPECTION + "/records")
                    || normalizedPath.matches(INSPECTION + "/records/[^/]+/submit"))) {
                return SystemPermissionCodes.INSPECTION_SUBMIT;
            }
            return SystemPermissionCodes.INSPECTION_MANAGE;
        }
        if (matchesModule(normalizedPath, ELECTRIC_BOXES)) {
            return read ? SystemPermissionCodes.INSPECTION_VIEW : SystemPermissionCodes.INSPECTION_MANAGE;
        }
        if (matchesModule(normalizedPath, QUALITY_ISSUES)) {
            if (read) return SystemPermissionCodes.QUALITY_VIEW;
            if (normalizedPath.matches(QUALITY_ISSUES + "/[^/]+/rectify")) {
                return SystemPermissionCodes.QUALITY_RECTIFY;
            }
            if (normalizedPath.matches(QUALITY_ISSUES + "/[^/]+/review")) {
                return SystemPermissionCodes.QUALITY_REVIEW;
            }
            return SystemPermissionCodes.QUALITY_MANAGE;
        }
        if (matchesModule(normalizedPath, SITE_ACCESS)) {
            if (normalizedPath.equals(SITE_ACCESS + "/visitors/export")) {
                return SystemPermissionCodes.SITE_ACCESS_EXPORT;
            }
            return read ? SystemPermissionCodes.SITE_ACCESS_VIEW : SystemPermissionCodes.SITE_ACCESS_MANAGE;
        }
        return null;
    }

    private SharedFilePermission resolveSharedFilePermission(HttpServletRequest request, String path) {
        String businessType = request.getParameter("businessType");
        Long projectId = parseLong(request.getParameter("projectId"));
        Long fileId = fileId(path);
        if (fileId != null) {
            FileResource file = fileMapper.selectById(fileId);
            if (file != null) {
                businessType = file.getBusinessType();
                projectId = file.getProjectId();
            }
        }
        String normalizedType = normalizeBusinessType(businessType);
        boolean read = isReadMethod(request.getMethod());
        String permissionCode = null;
        if (normalizedType.startsWith("QUALITY_")) {
            if (read) permissionCode = SystemPermissionCodes.QUALITY_VIEW;
            else if (normalizedType.contains("RECTIFICATION")) permissionCode = SystemPermissionCodes.QUALITY_RECTIFY;
            else if (normalizedType.contains("REVIEW")) permissionCode = SystemPermissionCodes.QUALITY_REVIEW;
            else permissionCode = SystemPermissionCodes.QUALITY_MANAGE;
        } else if (normalizedType.startsWith("INSPECTION_")) {
            if (read) permissionCode = SystemPermissionCodes.INSPECTION_VIEW;
            else if (normalizedType.contains("RECTIFICATION")) permissionCode = SystemPermissionCodes.INSPECTION_MANAGE;
            else permissionCode = SystemPermissionCodes.INSPECTION_SUBMIT;
        }
        return permissionCode == null ? null : new SharedFilePermission(permissionCode, projectId);
    }

    private Long fileId(String path) {
        String normalizedPath = normalizePath(path);
        if (!normalizedPath.startsWith(FILES + "/")) return null;
        String tail = normalizedPath.substring((FILES + "/").length());
        String firstSegment = tail.contains("/") ? tail.substring(0, tail.indexOf('/')) : tail;
        try {
            return Long.valueOf(firstSegment);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String applicationPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return normalizePath(path);
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) return "/";
        String normalized = path.trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeBusinessType(String businessType) {
        return businessType == null ? "" : businessType.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isReadMethod(String method) {
        return HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method);
    }

    private boolean matchesModule(String path, String moduleRoot) {
        return moduleRoot.equals(path) || path.startsWith(moduleRoot + "/");
    }

    private record SharedFilePermission(String permissionCode, Long projectId) {
    }
}
