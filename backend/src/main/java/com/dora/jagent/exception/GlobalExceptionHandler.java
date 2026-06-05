package com.dora.jagent.exception;

import com.dora.jagent.model.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// @RestControllerAdvice 会对所有 Controller 生效，
// 统一把异常转换成结构化 JSON，方便前端直接展示。
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        log.warn("biz exception: {}", e.getMessage(), e);
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("illegal argument: {}", e.getMessage(), e);
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("validation exception", e);
        return ApiResponse.error(400, "request validation failed");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("unexpected exception", e);
        return ApiResponse.error(500, e.getMessage() == null ? "internal server error" : e.getMessage());
    }
}
