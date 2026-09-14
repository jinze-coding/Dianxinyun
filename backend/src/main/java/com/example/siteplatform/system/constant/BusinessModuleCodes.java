package com.example.siteplatform.system.constant;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 正式业务模块的跨端开关。
 *
 * <p>菜单资源仍然保留 Web / 小程序两条记录，以便各客户端继续使用自己的路由；
 * 角色配置和鉴权统一使用本类的五个模块编码；场内管理 V1 仅有 Web 内部入口，
 * 其余模块避免把同一业务拆成两次授权。</p>
 */
public final class BusinessModuleCodes {
    private BusinessModuleCodes() {}

    public static final String DOCUMENT = "DOCUMENT";
    public static final String INSPECTION = "INSPECTION";
    public static final String QUALITY = "QUALITY";
    public static final String SAFETY_COMMITTEE = "SAFETY_COMMITTEE";
    public static final String SITE_ACCESS = "SITE_ACCESS";

    public static final List<String> ALL = List.of(SITE_ACCESS, DOCUMENT, INSPECTION, QUALITY, SAFETY_COMMITTEE);

    public static final Set<String> SITE_ACCESS_MENUS = Set.of(
            "WEB_SITE_ACCESS", "SITE_VISITOR");
    public static final Set<String> DOCUMENT_MENUS = Set.of(
            "WEB_DOCUMENT", "MINI_DOCUMENT", "DOCUMENT_LIBRARY", "DOCUMENT_SEAL",
            "DOCUMENT_CIRCULATION", "DOCUMENT_RECYCLE");
    public static final Set<String> INSPECTION_MENUS = Set.of(
            "WEB_INSPECTION", "MINI_INSPECTION", "INSPECTION_LEDGER", "INSPECTION_RECORDS",
            "INSPECTION_RECTIFICATIONS", "INSPECTION_EDGE");
    public static final Set<String> QUALITY_MENUS = Set.of(
            "WEB_QUALITY", "MINI_QUALITY", "QUALITY_ISSUES", "QUALITY_DOCUMENTS");

    public static String fromMenuCode(String menuCode) {
        String normalized = normalize(menuCode);
        if (SITE_ACCESS_MENUS.contains(normalized)) return SITE_ACCESS;
        if (DOCUMENT_MENUS.contains(normalized)) return DOCUMENT;
        if (INSPECTION_MENUS.contains(normalized)) return INSPECTION;
        if (QUALITY_MENUS.contains(normalized)) return QUALITY;
        if (Set.of("WEB_SAFETY_COMMITTEE", "MINI_SAFETY_COMMITTEE", "SAFETY_COMMITTEE_RECORDS").contains(normalized)) return SAFETY_COMMITTEE;
        return null;
    }

    public static String fromPermissionCode(String permissionCode) {
        String normalized = normalize(permissionCode);
        if (normalized.startsWith("SITE_ACCESS.")) return SITE_ACCESS;
        if (normalized.startsWith("DOCUMENT.")) return DOCUMENT;
        if (normalized.startsWith("INSPECTION.")
                || normalized.startsWith("BOX_")
                || normalized.startsWith("INSPECTION_")
                || normalized.startsWith("EDGE_INSPECTION_")
                || normalized.startsWith("CUSTOM_INSPECTION_")
                || normalized.startsWith("SUMMARY_")
                || normalized.startsWith("RECTIFICATION_")) return INSPECTION;
        if (normalized.startsWith("QUALITY.")) return QUALITY;
        if (normalized.startsWith("SAFETY_COMMITTEE.")) return SAFETY_COMMITTEE;
        if (normalized.startsWith("SEAL.")) return DOCUMENT;
        return null;
    }

    public static String fromBusinessType(String type) {
        String code = normalize(type);
        if (code.startsWith("SEAL_") || code.startsWith("DOCUMENT_")) return DOCUMENT;
        if (code.startsWith("QUALITY_")) return QUALITY;
        if (code.startsWith("COMMITTEE_") || code.startsWith("SAFETY_COMMITTEE")) return SAFETY_COMMITTEE;
        if (code.startsWith("INSPECTION_") || code.startsWith("ELECTRIC_BOX") || code.startsWith("EDGE_INSPECTION") || code.startsWith("GENERAL_INSPECTION")) return INSPECTION;
        if (code.startsWith("SITE_") || code.startsWith("MEETING_") || code.startsWith("GUARD_")) return SITE_ACCESS;
        return null;
    }

    private static final String NOTIFICATION_MODULE_SQL = "CASE "
            + "WHEN business_type LIKE 'SEAL\\_%' OR business_type LIKE 'DOCUMENT\\_%' THEN 'DOCUMENT' "
            + "WHEN business_type LIKE 'QUALITY\\_%' THEN 'QUALITY' "
            + "WHEN business_type LIKE 'COMMITTEE\\_%' OR business_type LIKE 'SAFETY_COMMITTEE%' THEN 'SAFETY_COMMITTEE' "
            + "WHEN business_type LIKE 'INSPECTION\\_%' OR business_type LIKE 'ELECTRIC_BOX%' OR business_type LIKE 'EDGE_INSPECTION%' OR business_type LIKE 'GENERAL_INSPECTION%' THEN 'INSPECTION' "
            + "WHEN business_type LIKE 'SITE\\_%' OR business_type LIKE 'MEETING\\_%' OR business_type LIKE 'GUARD\\_%' THEN 'SITE_ACCESS' ELSE NULL END";
    public static final String NOTIFICATION_ENABLED_SQL = "(user_notification.project_id IS NULL OR (" + NOTIFICATION_MODULE_SQL + ") IS NULL OR EXISTS (SELECT 1 FROM project_business_module pm WHERE pm.project_id=user_notification.project_id AND pm.enabled=1 AND pm.module_code=(" + NOTIFICATION_MODULE_SQL + ")))";

    public static boolean isBusinessModule(String moduleCode) {
        return ALL.contains(normalize(moduleCode));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
