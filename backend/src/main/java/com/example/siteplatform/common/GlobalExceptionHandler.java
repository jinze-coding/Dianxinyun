package com.example.siteplatform.common;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        int status = normalizeStatus(e.getCode());
        return ResponseEntity.status(HttpStatusCode.valueOf(status))
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<Result<?>> handleValidationException(BindException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(ObjectError::getDefaultMessage)
                .filter(value -> value != null && !value.isBlank())
                .orElse("请求参数不合法");
        return ResponseEntity.badRequest()
                .body(Result.error(400, "参数校验失败：" + message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        e.printStackTrace();
        return ResponseEntity.internalServerError()
                .body(Result.error(500, "系统异常，请稍后重试"));
    }

    private int normalizeStatus(Integer code) {
        return code != null && code >= 400 && code <= 599 ? code : 500;
    }
}
