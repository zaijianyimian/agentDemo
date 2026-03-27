package com.example.demo.service.tool;

import com.example.demo.dto.ToolExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.ToolType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 本地脚本工具执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalScriptToolExecutor implements ToolExecutor {

    private final ObjectMapper objectMapper;

    @Override
    public ToolType getToolType() {
        return ToolType.LOCAL_SCRIPT;
    }

    @Override
    public ToolExecutionResult execute(McpTool tool, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();

        try {
            // 解析配置
            Map<String, Object> config = objectMapper.readValue(
                    tool.getConfig(),
                    new TypeReference<Map<String, Object>>() {}
            );

            String scriptPath = (String) config.get("scriptPath");
            int timeout = ((Number) config.getOrDefault("timeout", 30)).intValue();
            String workingDir = (String) config.getOrDefault("workingDir", ".");

            if (scriptPath == null || scriptPath.isEmpty()) {
                return ToolExecutionResult.failure("脚本路径不能为空");
            }

            // 构建命令
            List<String> command = new ArrayList<>();

            // 根据脚本类型选择解释器
            if (scriptPath.endsWith(".sh")) {
                command.add("bash");
                command.add(scriptPath);
            } else if (scriptPath.endsWith(".py")) {
                command.add("python");
                command.add(scriptPath);
            } else if (scriptPath.endsWith(".bat") || scriptPath.endsWith(".cmd")) {
                command.add("cmd");
                command.add("/c");
                command.add(scriptPath);
            } else {
                // 默认作为可执行文件
                command.add(scriptPath);
            }

            // 添加参数
            if (params != null) {
                params.values().forEach(value -> command.add(String.valueOf(value)));
            }

            // 创建进程构建器
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(workingDir));
            processBuilder.redirectErrorStream(true);

            // 设置环境变量
            Map<String, String> environment = processBuilder.environment();
            if (params != null) {
                params.forEach((key, value) -> environment.put("PARAM_" + key.toUpperCase(), String.valueOf(value)));
            }

            log.info("执行脚本: {}", String.join(" ", command));

            // 启动进程
            Process process = processBuilder.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 等待执行完成
            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                return ToolExecutionResult.failure("脚本执行超时", duration);
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                String result = output.toString().trim();
                return ToolExecutionResult.success(result, duration);
            } else {
                return ToolExecutionResult.failure(
                        "脚本执行失败，退出码: " + exitCode + ", 输出: " + output,
                        duration
                );
            }

        } catch (Exception e) {
            log.error("本地脚本工具执行失败: {}", tool.getName(), e);
            return ToolExecutionResult.failure(
                    "执行失败: " + e.getMessage(),
                    System.currentTimeMillis() - startTime
            );
        }
    }
}