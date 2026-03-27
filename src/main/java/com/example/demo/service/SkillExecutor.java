package com.example.demo.service;

import com.example.demo.dto.SkillExecutionResult;
import com.example.demo.dto.ToolExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.Skill;
import com.example.demo.entity.SkillToolMapping;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能执行器
 * 执行技能并自动调用关联的工具
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillExecutor {

    private final SkillService skillService;
    private final McpToolService mcpToolService;
    private final ObjectMapper objectMapper;

    /**
     * 执行技能
     *
     * @param skillCode 技能编码
     * @param params    参数
     * @return 执行结果
     */
    public SkillExecutionResult execute(String skillCode, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();

        // 获取技能
        Skill skill = skillService.getByCode(skillCode);
        if (skill == null) {
            return SkillExecutionResult.failure(skillCode, "技能不存在: " + skillCode);
        }

        if (!Boolean.TRUE.equals(skill.getEnabled())) {
            return SkillExecutionResult.failure(skillCode, "技能未启用: " + skillCode);
        }

        return execute(skill, params != null ? params : new HashMap<>(), startTime);
    }

    /**
     * 执行技能
     *
     * @param skill 技能
     * @param params 参数
     * @return 执行结果
     */
    public SkillExecutionResult execute(Skill skill, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        return execute(skill, params != null ? params : new HashMap<>(), startTime);
    }

    /**
     * 执行技能
     */
    private SkillExecutionResult execute(Skill skill, Map<String, Object> params, long startTime) {
        log.info("执行技能: {} ({})", skill.getCode(), skill.getName());

        // 获取技能关联的工具映射
        List<SkillToolMapping> mappings = skillService.getSkillToolMappings(skill.getId());

        if (mappings.isEmpty()) {
            return SkillExecutionResult.failure(skill.getCode(), "技能未关联任何工具");
        }

        // 获取工具列表
        List<McpTool> tools = skillService.getSkillTools(skill.getId());

        if (tools.isEmpty()) {
            return SkillExecutionResult.failure(skill.getCode(), "技能关联的工具不存在");
        }

        // 按顺序执行工具
        List<SkillExecutionResult.ToolExecutionStep> steps = new ArrayList<>();
        Object lastResult = null;
        boolean allSuccess = true;

        for (SkillToolMapping mapping : mappings) {
            // 查找对应的工具
            McpTool tool = tools.stream()
                    .filter(t -> t.getId().equals(mapping.getToolId()))
                    .findFirst()
                    .orElse(null);

            if (tool == null) {
                log.warn("工具不存在: {}", mapping.getToolId());
                if (Boolean.TRUE.equals(mapping.getIsRequired())) {
                    return SkillExecutionResult.failure(skill.getCode(), "必须的工具不存在: " + mapping.getToolId());
                }
                continue;
            }

            // 合并参数：用户参数 + 上一步结果
            Map<String, Object> executionParams = new HashMap<>(params);
            if (lastResult != null) {
                executionParams.put("_previous_result", lastResult);
            }

            // 解析技能配置中的参数映射
            executionParams = resolveParams(skill, tool, executionParams);

            // 执行工具
            ToolExecutionResult toolResult = mcpToolService.execute(tool, executionParams);

            // 记录步骤
            steps.add(SkillExecutionResult.ToolExecutionStep.builder()
                    .toolName(tool.getName())
                    .success(toolResult.isSuccess())
                    .result(toolResult.getResult())
                    .error(toolResult.getError())
                    .durationMs(toolResult.getDurationMs())
                    .build());

            if (toolResult.isSuccess()) {
                // 尝试解析结果
                lastResult = parseResult(toolResult.getResult());
            } else {
                allSuccess = false;
                if (Boolean.TRUE.equals(mapping.getIsRequired())) {
                    // 必须的工具执行失败
                    long duration = System.currentTimeMillis() - startTime;
                    return SkillExecutionResult.builder()
                            .success(false)
                            .skillCode(skill.getCode())
                            .error("工具执行失败: " + tool.getName())
                            .toolSteps(steps)
                            .totalDurationMs(duration)
                            .build();
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;

        return SkillExecutionResult.builder()
                .success(allSuccess)
                .skillCode(skill.getCode())
                .result(lastResult)
                .toolSteps(steps)
                .totalDurationMs(duration)
                .build();
    }

    /**
     * 解析参数映射
     */
    private Map<String, Object> resolveParams(Skill skill, McpTool tool, Map<String, Object> userParams) {
        // 如果技能有配置，尝试解析参数映射
        if (skill.getConfig() != null && !skill.getConfig().isEmpty()) {
            try {
                Map<String, Object> config = objectMapper.readValue(
                        skill.getConfig(),
                        new TypeReference<Map<String, Object>>() {}
                );

                @SuppressWarnings("unchecked")
                Map<String, String> paramMapping = (Map<String, String>) config.get("paramMapping");

                if (paramMapping != null) {
                    Map<String, Object> resolvedParams = new HashMap<>();
                    for (Map.Entry<String, String> entry : paramMapping.entrySet()) {
                        String targetParam = entry.getKey();
                        String sourceParam = entry.getValue();

                        if (userParams.containsKey(sourceParam)) {
                            resolvedParams.put(targetParam, userParams.get(sourceParam));
                        }
                    }
                    return resolvedParams;
                }
            } catch (Exception e) {
                log.warn("解析技能配置失败: {}", e.getMessage());
            }
        }

        // 默认直接使用用户参数
        return userParams;
    }

    /**
     * 解析结果
     */
    private Object parseResult(String result) {
        if (result == null || result.isEmpty()) {
            return null;
        }

        // 尝试解析为 JSON
        try {
            return objectMapper.readValue(result, new TypeReference<Object>() {});
        } catch (Exception e) {
            // 不是 JSON，直接返回字符串
            return result;
        }
    }
}