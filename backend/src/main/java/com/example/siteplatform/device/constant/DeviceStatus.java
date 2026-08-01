package com.example.siteplatform.device.constant;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class DeviceStatus {
    public static final String RUNNING = "running";
    public static final String STOPPED = "stopped";
    public static final String ABNORMAL = "abnormal";
    public static final String MAINTENANCE = "maintenance";

    private static final Set<String> SUPPORTED = Set.of(
            RUNNING, STOPPED, ABNORMAL, MAINTENANCE);

    private DeviceStatus() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String key = trimmed.toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (key) {
            case "running", "运行中", "正常" -> RUNNING;
            case "stopped", "停机", "已停机", "停用" -> STOPPED;
            case "abnormal", "alarm", "danger", "异常", "告警" -> ABNORMAL;
            case "maintenance", "维修中", "维护中" -> MAINTENANCE;
            default -> trimmed;
        };
    }

    public static boolean isSupported(String value) {
        String normalized = normalize(value);
        return normalized != null && SUPPORTED.contains(normalized);
    }

    public static List<String> compatibleQueryValues(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isEmpty()) {
            return List.of();
        }
        return switch (normalized) {
            case RUNNING -> List.of(RUNNING, "RUNNING", "运行中", "正常");
            case STOPPED -> List.of(STOPPED, "STOPPED", "停机", "已停机", "停用");
            case ABNORMAL -> List.of(
                    ABNORMAL, "ABNORMAL", "alarm", "ALARM", "danger", "DANGER", "异常", "告警");
            case MAINTENANCE -> List.of(MAINTENANCE, "MAINTENANCE", "维修中", "维护中");
            default -> List.of(normalized);
        };
    }
}
