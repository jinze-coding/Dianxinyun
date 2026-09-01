package com.example.siteplatform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e) {
        int status = normalizeStatus(e.getCode());
        Result<Object> result = Result.error(e.getCode(), e.getMessage());
        result.setData(e.getData());
        return ResponseEntity.status(HttpStatusCode.valueOf(status))
                .body(result);
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<?>> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String parameterName = e.getName() == null || e.getName().isBlank() ? "请求参数" : e.getName();
        return ResponseEntity.badRequest()
                .body(Result.error(400, "请求参数格式错误：" + parameterName));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<?>> handleMissingRequestParameterException(
            MissingServletRequestParameterException e) {
        String parameterName = e.getParameterName() == null || e.getParameterName().isBlank()
                ? "请求参数"
                : e.getParameterName();
        return ResponseEntity.badRequest()
                .body(Result.error(400, "缺少必要请求参数：" + parameterName));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<?>> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        if ("X-Visitor-Session".equalsIgnoreCase(e.getHeaderName())) {
            return ResponseEntity.status(401)
                    .body(Result.error(401, "外访临时会话无效，请重新打开邀请"));
        }
        return ResponseEntity.badRequest()
                .body(Result.error(400, "缺少必要请求头：" + e.getHeaderName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleMessageNotReadableException(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(Result.error(400, "请求体格式错误"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405)
                .body(Result.error(405, "请求方法不支持"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(415)
                .body(Result.error(415, "请求内容类型不支持"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<?>> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413)
                .body(Result.error(413, "上传文件过大，单次请求不能超过50MB"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNoResourceFoundException(NoResourceFoundException exception) {
        return ResponseEntity.status(404)
                .body(Result.error(404, "请求的资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        LOGGER.error("Unhandled request exception", e);
        return ResponseEntity.internalServerError()
                .body(Result.error(500, "系统异常，请稍后重试"));
    }

    private int normalizeStatus(Integer code) {
        return code != null && code >= 400 && code <= 599 ? code : 500;
    }
}
