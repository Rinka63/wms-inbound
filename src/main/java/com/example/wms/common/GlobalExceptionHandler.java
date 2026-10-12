package com.example.wms.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：400
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Service 主动抛出的 ResponseStatusException：保留原状态码和 reason
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException e) {
        return body(e.getStatusCode(), e.getReason());
    }

    /**
     * @Valid 校验失败：返回第一条字段错误的 message
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数不正确");

        return body(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * JSON 格式错误，或数量字符串无法转成 BigDecimal
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "请求格式不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleOther(Exception e) {
        /*
         * Spring MVC 自带异常（404 路径不存在、405 方法不支持等）保留原状态码，
         * 避免被统一变成 500
         */
        if (e instanceof ErrorResponse errorResponse) {
            return body(errorResponse.getStatusCode(), "请求无法处理");
        }

        log.error("未处理的异常", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    private ResponseEntity<Map<String, String>> body(HttpStatusCode status, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("message", message == null ? "操作失败" : message));
    }
}