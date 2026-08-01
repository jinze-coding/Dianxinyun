package com.example.siteplatform.device.constant;

import java.util.List;
import java.util.Locale;

public final class DeviceType {
    public static final String TOWER_CRANE = "tower_crane";
    public static final String ELEVATOR = "elevator";
    public static final String MONITOR = "monitor";
    public static final String PUMP = "pump";
    public static final String OTHER = "other";

    private DeviceType() {
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
            case "tower_crane", "塔吊", "塔式起重机" -> TOWER_CRANE;
            case "elevator", "施工电梯", "升降机" -> ELEVATOR;
            case "monitor", "监测设备", "环境监测" -> MONITOR;
            case "pump", "泵车" -> PUMP;
            case "other", "其他" -> OTHER;
            default -> trimmed;
        };
    }

    public static List<String> compatibleQueryValues(String value) {
        String normalized = normalize(value);
        if (normalized == null || normalized.isEmpty()) {
            return List.of();
        }
        return switch (normalized) {
            case TOWER_CRANE -> List.of(TOWER_CRANE, "塔吊", "塔式起重机");
            case ELEVATOR -> List.of(ELEVATOR, "施工电梯", "升降机");
            case MONITOR -> List.of(MONITOR, "监测设备", "环境监测");
            case PUMP -> List.of(PUMP, "泵车");
            case OTHER -> List.of(OTHER, "其他");
            default -> List.of(normalized);
        };
    }
}
