package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 技能执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillExecutionResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 技能编码
     */
    private String skillCode;

    /**
     * 执行结果数据
     */
    private Object result;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 工具执行结果列表
     */
    private List<ToolExecutionStep> toolSteps;

    /**
     * 总执行耗时（毫秒）
     */
    private long totalDurationMs;

    /**
     * 工具执行步骤
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolExecutionStep {
        private String toolName;
        private boolean success;
        private String result;
        private String error;
        private long durationMs;
    }

    public static SkillExecutionResult success(String skillCode, Object result, List<ToolExecutionStep> steps, long durationMs) {
        return SkillExecutionResult.builder()
                .success(true)
                .skillCode(skillCode)
                .result(result)
                .toolSteps(steps)
                .totalDurationMs(durationMs)
                .build();
    }

    public static SkillExecutionResult failure(String skillCode, String error) {
        return SkillExecutionResult.builder()
                .success(false)
                .skillCode(skillCode)
                .error(error)
                .build();
    }
}