package com.example.siteplatform.common;

import java.util.Locale;

/**
 * 兼容部分小程序/H5 上传实现对可选表单字段的非标准空值序列化。
 */
public final class RequestParameterParser {

    private RequestParameterParser() {
    }

    public static Long parseOptionalLong(String parameterName, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String value = rawValue.trim();
        String normalized = value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if ("undefined".equals(normalized)
                || "null".equals(normalized)
                || "[objectundefined]".equals(normalized)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw BusinessException.of(400, "请求参数格式错误：" + parameterName);
        }
    }
}
