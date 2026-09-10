package com.example.demo.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 通用 API 响应类。
 *
 * @param <T> 响应数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 是否成功。 */
    private boolean success;

    /** 稳定的业务响应码，便于前端按类型处理。 */
    private String code;

    /**
     * 兼容旧异常响应中的 error 字段。
     *
     * @deprecated 新代码统一读取 {@link #code}。
     */
    @Deprecated
    private String error;

    /** 面向用户的响应消息。 */
    private String message;

    /** 业务数据。 */
    private T data;

    /** 总数，用于兼容现有分页接口。 */
    private Long total;

    /** 服务端生成响应的时间。 */
    private Instant timestamp;

    /** 可选的结构化附加信息，主要用于错误详情。 */
    private Map<String, Object> details;

    /**
     * 创建成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 标准成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(data, "success");
    }

    /**
     * 创建带消息的成功响应。
     *
     * @param data 响应数据
     * @param message 响应消息
     * @param <T> 响应数据类型
     * @return 标准成功响应
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("OK")
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 创建带总数的成功响应。
     *
     * @param data 响应数据
     * @param total 数据总数
     * @param <T> 响应数据类型
     * @return 标准成功响应
     */
    public static <T> ApiResponse<T> success(T data, Long total) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("OK")
                .message("success")
                .data(data)
                .total(total)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 创建兼容现有调用的错误响应。
     *
     * @param message 错误消息
     * @param <T> 响应数据类型
     * @return 标准错误响应
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code("REQUEST_FAILED")
                .error("REQUEST_FAILED")
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 创建兼容现有调用且携带数据的错误响应。
     *
     * @param message 错误消息
     * @param data 错误相关数据
     * @param <T> 响应数据类型
     * @return 标准错误响应
     */
    public static <T> ApiResponse<T> error(String message, T data) {
        return ApiResponse.<T>builder()
                .success(false)
                .code("REQUEST_FAILED")
                .error("REQUEST_FAILED")
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 创建带稳定错误码的失败响应。
     *
     * @param code 错误码
     * @param message 错误消息
     * @return 标准错误响应
     */
    public static ApiResponse<Object> failure(String code, String message) {
        return failure(code, message, null);
    }

    /**
     * 创建带稳定错误码和结构化详情的失败响应。
     *
     * @param code 错误码
     * @param message 错误消息
     * @param details 结构化错误详情
     * @return 标准错误响应
     */
    public static ApiResponse<Object> failure(
            String code,
            String message,
            Map<String, Object> details) {
        return ApiResponse.<Object>builder()
                .success(false)
                .code(code)
                .error(code)
                .message(message)
                .timestamp(Instant.now())
                .details(details)
                .build();
    }
}
