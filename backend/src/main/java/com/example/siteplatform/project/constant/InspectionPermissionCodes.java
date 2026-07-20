package com.example.siteplatform.project.constant;

import com.example.siteplatform.project.service.ProjectPermissionService;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class InspectionPermissionCodes {

    private InspectionPermissionCodes() {
    }

    public static final String BOX_VIEW = "BOX_VIEW";
    public static final String BOX_MANAGE = "BOX_MANAGE";
    public static final String BOX_QR_MANAGE = "BOX_QR_MANAGE";
    public static final String BOX_PUBLIC_ACCESS = "BOX_PUBLIC_ACCESS";
    public static final String INSPECTION_DAILY_SUBMIT = "INSPECTION_DAILY_SUBMIT";
    public static final String INSPECTION_REVIEW = "INSPECTION_REVIEW";
    public static final String INSPECTION_RECORD_VIEW = "INSPECTION_RECORD_VIEW";
    public static final String RECTIFICATION_VIEW = "RECTIFICATION_VIEW";
    public static final String RECTIFICATION_REVIEW = "RECTIFICATION_REVIEW";
    public static final String SUMMARY_VIEW = "SUMMARY_VIEW";
    public static final String SUMMARY_EXPORT = "SUMMARY_EXPORT";
    public static final String PERMISSION_MANAGE = "PERMISSION_MANAGE";

    public static final List<String> ALL_CODES = List.of(
            BOX_VIEW,
            BOX_MANAGE,
            BOX_QR_MANAGE,
            BOX_PUBLIC_ACCESS,
            INSPECTION_DAILY_SUBMIT,
            INSPECTION_REVIEW,
            INSPECTION_RECORD_VIEW,
            RECTIFICATION_VIEW,
            RECTIFICATION_REVIEW,
            SUMMARY_VIEW,
            SUMMARY_EXPORT,
            PERMISSION_MANAGE
    );

    public static final List<String> PROJECT_ADMIN_CODES = List.of(
            BOX_VIEW,
            BOX_MANAGE,
            BOX_QR_MANAGE,
            BOX_PUBLIC_ACCESS,
            INSPECTION_DAILY_SUBMIT,
            INSPECTION_RECORD_VIEW,
            SUMMARY_VIEW,
            SUMMARY_EXPORT,
            PERMISSION_MANAGE
    );

    public static final List<String> SAFETY_ADMIN_CODES = List.of(
            BOX_VIEW,
            BOX_MANAGE,
            BOX_QR_MANAGE,
            BOX_PUBLIC_ACCESS,
            INSPECTION_RECORD_VIEW,
            SUMMARY_VIEW,
            SUMMARY_EXPORT
    );

    public static final List<String> USER_CODES = List.of(
            BOX_VIEW,
            INSPECTION_DAILY_SUBMIT
    );

    public static List<String> defaultCodesForProjectRole(String projectRoleCode) {
        if (ProjectPermissionService.ROLE_PROJECT_ADMIN.equals(projectRoleCode)
                || ProjectPermissionService.ROLE_PLATFORM_ADMIN.equals(projectRoleCode)) {
            return PROJECT_ADMIN_CODES;
        }
        if (ProjectPermissionService.ROLE_SAFETY_ADMIN.equals(projectRoleCode)) {
            return SAFETY_ADMIN_CODES;
        }
        return USER_CODES;
    }

    public static List<String> normalize(Collection<String> codes) {
        if (codes == null) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String code : codes) {
            if (!StringUtils.hasText(code)) {
                continue;
            }
            String normalized = code.trim().toUpperCase();
            if (!ALL_CODES.contains(normalized)) {
                throw new IllegalArgumentException("不支持的电箱巡检权限码：" + normalized);
            }
            result.add(normalized);
        }
        return new ArrayList<>(result);
    }

    public static List<String> parse(String csv) {
        if (!StringUtils.hasText(csv)) {
            return List.of();
        }
        return normalize(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList());
    }

    public static String join(Collection<String> codes) {
        return String.join(",", normalize(codes));
    }
}
