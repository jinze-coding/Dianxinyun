package com.example.siteplatform.system.constant;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 正式业务模块的跨端开关。
 *
 * <p>菜单资源仍然保留 Web / 小程序两条记录，以便各客户端继续使用自己的路由；
 * 角色配置和鉴权统一使用本类的三个模块编码，避免把同一业务拆成两次授权。</p>
 */
public final class BusinessModuleCodes {
    private BusinessModuleCodes() {}

    public static final String DOCUMENT = "DOCUMENT";
    public static final String INSPECTION = "INSPECTION";
    public static final String QUALITY = "QUALITY";

    public static final List<String> ALL = List.of(DOCUMENT, INSPECTION, QUALITY);

    public static final Set<String> DOCUMENT_MENUS = Set.of("WEB_DOCUMENT", "MINI_DOCUMENT");
    public static final Set<String> INSPECTION_MENUS = Set.of("WEB_INSPECTION", "MINI_INSPECTION");
    public static final Set<String> QUALITY_MENUS = Set.of("WEB_QUALITY", "MINI_QUALITY");

    public static String fromMenuCode(String menuCode) {
        String normalized = normalize(menuCode);
        if (DOCUMENT_MENUS.contains(normalized)) return DOCUMENT;
        if (INSPECTION_MENUS.contains(normalized)) return INSPECTION;
        if (QUALITY_MENUS.contains(normalized)) return QUALITY;
        return null;
    }

    public static String fromPermissionCode(String permissionCode) {
        String normalized = normalize(permissionCode);
        if (normalized.startsWith("DOCUMENT.")) return DOCUMENT;
        if (normalized.startsWith("INSPECTION.")
                || normalized.startsWith("BOX_")
                || normalized.startsWith("INSPECTION_")
                || normalized.startsWith("SUMMARY_")
                || normalized.startsWith("RECTIFICATION_")) return INSPECTION;
        if (normalized.startsWith("QUALITY.")) return QUALITY;
        return null;
    }

    public static boolean isBusinessModule(String moduleCode) {
        return ALL.contains(normalize(moduleCode));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
