package com.example.siteplatform.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestParameterParserTest {

    @Test
    void treatsWechatOptionalFieldSentinelsAsMissing() {
        assertNull(RequestParameterParser.parseOptionalLong("businessId", null));
        assertNull(RequestParameterParser.parseOptionalLong("businessId", ""));
        assertNull(RequestParameterParser.parseOptionalLong("businessId", "undefined"));
        assertNull(RequestParameterParser.parseOptionalLong("businessId", "[objectUndefined]"));
        assertNull(RequestParameterParser.parseOptionalLong("businessId", "[object Undefined]"));
        assertNull(RequestParameterParser.parseOptionalLong("businessId", "null"));
    }

    @Test
    void parsesValidOptionalLong() {
        assertEquals(1024L, RequestParameterParser.parseOptionalLong("businessId", "1024"));
    }

    @Test
    void rejectsOtherInvalidValues() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> RequestParameterParser.parseOptionalLong("businessId", "[object Object]")
        );
        assertEquals(400, exception.getCode());
        assertEquals("请求参数格式错误：businessId", exception.getMessage());
    }
}
