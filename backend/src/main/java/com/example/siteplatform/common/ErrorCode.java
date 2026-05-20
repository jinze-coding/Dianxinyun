package com.example.siteplatform.common;

public class ErrorCode {
    public static final Integer SUCCESS = 200;
    public static final Integer BAD_REQUEST = 400;
    public static final Integer UNAUTHORIZED = 401;
    public static final Integer FORBIDDEN = 403;
    public static final Integer NOT_FOUND = 404;
    public static final Integer SYSTEM_ERROR = 500;

    private ErrorCode() {}
}
