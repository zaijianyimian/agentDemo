package com.example.demo.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 全局异常处理器
 * 处理AI调用相关的网络异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理请求体参数校验异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : e.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        String message = fieldErrors.values().stream().findFirst().orElse("请求参数校验失败");
        log.warn("请求参数校验失败: {}", fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "error", "VALIDATION_ERROR",
                        "message", message,
                        "fieldErrors", fieldErrors
                ));
    }

    /**
     * 处理客户端断开连接异常（SSE流式响应）
     * 这不是错误，只是客户端提前关闭了连接
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException e) {
        // 仅记录debug日志，不返回错误响应
        log.debug("客户端断开SSE连接: {}", e.getMessage());
    }

    /**
     * 处理网络连接重置异常
     */
    @ExceptionHandler(SocketException.class)
    public ResponseEntity<Map<String, Object>> handleSocketException(SocketException e) {
        log.error("网络连接异常: {}", e.getMessage());

        String message = "网络连接异常，请稍后重试";
        if (e.getMessage() != null && e.getMessage().contains("Connection reset")) {
            message = "AI服务连接被重置，可能是网络不稳定或服务繁忙，请稍后重试";
        }

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "success", false,
                        "error", "NETWORK_ERROR",
                        "message", message,
                        "suggestion", "建议：1. 检查网络连接 2. 稍后重试 3. 如果问题持续，请联系管理员"
                ));
    }

    /**
     * 处理资源访问异常（包括网络超时）
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String, Object>> handleResourceAccessException(ResourceAccessException e) {
        log.error("资源访问异常: {}", e.getMessage());

        String message = "AI服务访问超时";
        if (e.getCause() instanceof SocketTimeoutException) {
            message = "AI服务响应超时，请稍后重试";
        } else if (e.getCause() instanceof SocketException) {
            message = "AI服务连接异常，请稍后重试";
        }

        return ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(Map.of(
                        "success", false,
                        "error", "TIMEOUT_ERROR",
                        "message", message,
                        "suggestion", "建议：1. 等待几秒后重试 2. 检查API密钥是否有效 3. 确认API服务是否正常"
                ));
    }

    /**
     * 处理超时异常
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeoutException(TimeoutException e) {
        log.error("请求超时: {}", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(Map.of(
                        "success", false,
                        "error", "TIMEOUT_ERROR",
                        "message", "请求处理超时，请稍后重试"
                ));
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("参数错误: {}", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "success", false,
                        "error", "INVALID_PARAM",
                        "message", e.getMessage()
                ));
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常: ", e);

        // 检查是否是AI相关的错误
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("API key") || message.contains("api-key")) {
                return ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "success", false,
                                "error", "API_KEY_ERROR",
                                "message", "API密钥无效或已过期"
                        ));
            }
            if (message.contains("rate limit") || message.contains("quota")) {
                return ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of(
                                "success", false,
                                "error", "RATE_LIMIT_ERROR",
                                "message", "API调用频率超限，请稍后重试"
                        ));
            }
            if (message.contains("model") && message.contains("not found")) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "error", "MODEL_ERROR",
                                "message", "AI模型不可用，请检查模型配置"
                        ));
            }
        }

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "success", false,
                        "error", "INTERNAL_ERROR",
                        "message", "服务器内部错误，请稍后重试"
                ));
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        log.error("未处理的异常: ", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "success", false,
                        "error", "UNKNOWN_ERROR",
                        "message", "未知错误，请稍后重试"
                ));
    }
}
