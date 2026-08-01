package com.example.siteplatform.file.constant;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FileStatus {
    public static final String UPLOADED = "UPLOADED";
    public static final String PENDING_CONFIRM = "PENDING_CONFIRM";
    public static final String ARCHIVED = "ARCHIVED";

    private static final Set<String> SUPPORTED = Set.of(
            UPLOADED, PENDING_CONFIRM, ARCHIVED);

    private FileStatus() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String key = trimmed.toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "UPLOADED", "已上传" -> UPLOADED;
            case "PENDING_CONFIRM", "待确认" -> PENDING_CONFIRM;
            case "ARCHIVED", "已归档" -> ARCHIVED;
            default -> trimmed;
        };
    }

    public static boolean isSupported(String value) {
        String normalized = normalize(value);
        return normalized != null && SUPPORTED.contains(normalized);
    }

    public static boolean isArchived(String value) {
        return ARCHIVED.equals(normalize(value));
    }

    public static List<String> compatibleQueryValues(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isEmpty()) {
            return List.of();
        }
        return switch (normalized) {
            case UPLOADED -> List.of(UPLOADED, "uploaded", "已上传");
            case PENDING_CONFIRM -> List.of(PENDING_CONFIRM, "pending_confirm", "待确认");
            case ARCHIVED -> List.of(ARCHIVED, "archived", "已归档");
            default -> List.of(normalized);
        };
    }
}
