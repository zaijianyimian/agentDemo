package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolExecutionResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 执行结果数据
     */
    private String result;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 执行耗时（毫秒）
     */
    private long durationMs;

    public static ToolExecutionResult success(String result) {
        return ToolExecutionResult.builder()
                .success(true)
                .result(result)
                .build();
    }

    public static ToolExecutionResult success(String result, long durationMs) {
        return ToolExecutionResult.builder()
                .success(true)
                .result(result)
                .durationMs(durationMs)
                .build();
    }

    public static ToolExecutionResult failure(String error) {
        return ToolExecutionResult.builder()
                .success(false)
                .error(error)
                .build();
    }

    public static ToolExecutionResult failure(String error, long durationMs) {
        return ToolExecutionResult.builder()
                .success(false)
                .error(error)
                .durationMs(durationMs)
                .build();
    }
}