package com.example.siteplatform.person.constant;

public final class PersonnelStatus {
    public static final String WAIT_EDUCATION = "WAIT_EDUCATION";
    public static final String EDUCATED = "EDUCATED";
    public static final String LEFT = "LEFT";

    private PersonnelStatus() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return WAIT_EDUCATION;
        }
        return switch (value.trim().toUpperCase()) {
            case "EDUCATED", "已教育" -> EDUCATED;
            case "LEFT", "已离场" -> LEFT;
            default -> WAIT_EDUCATION;
        };
    }

    public static String label(String value) {
        return switch (normalize(value)) {
            case EDUCATED -> "已教育";
            case LEFT -> "已离场";
            default -> "待教育";
        };
    }
}
