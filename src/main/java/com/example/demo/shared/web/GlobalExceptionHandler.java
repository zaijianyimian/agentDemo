package com.example.demo.shared.web;

import com.example.demo.shared.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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
 * 全局异常处理器。
 *
 * <p>所有非流式异常统一返回 {@link ApiResponse}，前端可以稳定依赖
 * success、code、message、timestamp 和 details 字段处理错误。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Object>> handleAuthentication(AuthenticationException exception) {
        return failure(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "未登录或令牌失效");
    }

    @ExceptionHandler(UserResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleUserResourceNotFound(
            UserResourceNotFoundException exception) {
        return failure(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在");
    }

    /**
     * 处理携带稳定错误码的受控异常。
     *
     * <p>对外只返回稳定 code 与面向调用方的说明，堆栈与内部细节留在日志。</p>
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException exception) {
        if (exception.getStatus() >= 500) {
            log.error("受控异常: code={} message={}", exception.getCode(), exception.getMessage());
        } else {
            log.warn("受控异常: code={} message={}", exception.getCode(), exception.getMessage());
        }
        return ResponseEntity.status(exception.getStatus())
                .body(ApiResponse.failure(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(AccessDeniedException exception) {
        return failure(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权限访问该资源");
    }

    /**
     * 处理请求体参数校验异常。
     *
     * @param exception 参数校验异常
     * @return 标准错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        String message = fieldErrors.values().stream()
                .findFirst()
                .orElse("请求参数校验失败");
        log.warn("请求参数校验失败: {}", fieldErrors);

        return failure(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                message,
                Map.of("fieldErrors", fieldErrors));
    }

    /**
     * 处理客户端主动断开 SSE 连接。
     *
     * @param exception 客户端断开连接异常
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException exception) {
        // SSE 客户端提前关闭连接属于正常生命周期事件，只记录调试日志。
        log.debug("客户端断开SSE连接: {}", exception.getMessage());
    }

    /**
     * 处理网络连接重置异常。
     *
     * @param exception 网络异常
     * @return 标准错误响应
     */
    @ExceptionHandler(SocketException.class)
    public ResponseEntity<ApiResponse<Object>> handleSocketException(SocketException exception) {
        log.error("网络连接异常: {}", exception.getMessage());

        String message = "网络连接异常，请稍后重试";
        if (exception.getMessage() != null
                && exception.getMessage().contains("Connection reset")) {
            message = "AI服务连接被重置，可能是网络不稳定或服务繁忙，请稍后重试";
        }

        return failure(
                HttpStatus.SERVICE_UNAVAILABLE,
                "NETWORK_ERROR",
                message,
                Map.of("suggestion", "检查网络连接后重试；如果问题持续，请联系管理员"));
    }

    /**
     * 处理资源访问异常，包括网络超时。
     *
     * @param exception 资源访问异常
     * @return 标准错误响应
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ApiResponse<Object>> handleResourceAccessException(
            ResourceAccessException exception) {
        log.error("资源访问异常: {}", exception.getMessage());

        String message = "服务访问超时";
        if (exception.getCause() instanceof SocketTimeoutException) {
            message = "服务响应超时，请稍后重试";
        } else if (exception.getCause() instanceof SocketException) {
            message = "服务连接异常，请稍后重试";
        }

        return failure(
                HttpStatus.GATEWAY_TIMEOUT,
                "TIMEOUT_ERROR",
                message,
                Map.of("suggestion", "稍后重试，并检查上游服务状态"));
    }

    /**
     * 处理通用超时异常。
     *
     * @param exception 超时异常
     * @return 标准错误响应
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ApiResponse<Object>> handleTimeoutException(TimeoutException exception) {
        log.error("请求超时: {}", exception.getMessage());
        return failure(
                HttpStatus.GATEWAY_TIMEOUT,
                "TIMEOUT_ERROR",
                "请求处理超时，请稍后重试");
    }

    /**
     * 处理非法参数异常。
     *
     * @param exception 非法参数异常
     * @return 标准错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Object>> handleIllegalArgumentException(
            IllegalArgumentException exception) {
        log.error("参数错误: {}", exception.getMessage());
        String message = exception.getMessage() == null ? "请求参数不合法" : exception.getMessage();
        return failure(HttpStatus.BAD_REQUEST, "INVALID_PARAM", message);
    }

    /**
     * 处理运行时异常。
     *
     * @param exception 运行时异常
     * @return 标准错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntimeException(RuntimeException exception) {
        log.error("运行时异常: ", exception);

        return failure(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "服务器内部错误，请稍后重试");
    }

    /**
     * 处理所有未捕获异常。
     *
     * @param exception 未捕获异常
     * @return 标准错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleException(Exception exception) {
        log.error("未处理的异常: ", exception);
        return failure(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "UNKNOWN_ERROR",
                "未知错误，请稍后重试");
    }

    private ResponseEntity<ApiResponse<Object>> failure(
            HttpStatus status,
            String code,
            String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message));
    }

    private ResponseEntity<ApiResponse<Object>> failure(
            HttpStatus status,
            String code,
            String message,
            Map<String, Object> details) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message, details));
    }
}
