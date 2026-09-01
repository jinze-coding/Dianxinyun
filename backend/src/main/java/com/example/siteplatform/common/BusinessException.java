package com.example.siteplatform.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final Integer code;
    private final Object data;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
        this.data = null;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.data = null;
    }

    public BusinessException(Integer code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    public static BusinessException of(Integer code, String message) {
        return new BusinessException(code, message);
    }

    public static BusinessException of(Integer code, String message, Object data) {
        return new BusinessException(code, message, data);
    }
}
