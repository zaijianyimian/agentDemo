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
import java.util.regex.Pattern;

/**
 * 本地脚本工具执行器
 * 包含安全防护措施防止命令注入
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalScriptToolExecutor implements ToolExecutor {

    private final ObjectMapper objectMapper;

    /**
     * 安全参数正则：只允许字母、数字、下划线、连字符、点、空格、斜杠和中文
     * 拒绝任何 shell 特殊字符（如 ; | & $ ` 等）
     */
    private static final Pattern SAFE_PARAM_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-\\.\\s/\\u4e00-\\u9fa5]+$");

    /**
     * Shell 特殊字符黑名单 - 用于检测危险字符
     */
    private static final String[] DANGEROUS_CHARS = {
            ";", "|", "&", "$", "`", "(", ")", "{", "}", "[", "]",
            "<", ">", "\n", "\r", "\\", "'", "\"", "~", "!", "*", "?"
    };

    /**
     * 允许执行的脚本扩展名
     */
    private static final List<String> ALLOWED_EXTENSIONS = List.of(".sh", ".py", ".bat", ".cmd");

    @Override
    public ToolType getToolType() {
        return ToolType.LOCAL_SCRIPT;
    }

    /**
     * 验证参数是否安全，防止命令注入
     * @param value 参数值
     * @return 是否安全
     */
    private boolean isParamSafe(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        // 检查是否包含危险字符
        for (String dangerous : DANGEROUS_CHARS) {
            if (value.contains(dangerous)) {
                log.warn("参数包含危险字符 '{}'，已拒绝: {}", dangerous, value);
                return false;
            }
        }
        // 使用正则进行白名单验证
        return SAFE_PARAM_PATTERN.matcher(value).matches();
    }

    /**
     * 验证脚本路径是否安全
     * @param scriptPath 脚本路径
     * @return 是否安全
     */
    private boolean isScriptPathSafe(String scriptPath) {
        if (scriptPath == null || scriptPath.isEmpty()) {
            return false;
        }
        // 检查路径遍历攻击
        if (scriptPath.contains("..") || scriptPath.contains("~")) {
            log.warn("脚本路径包含路径遍历字符，已拒绝: {}", scriptPath);
            return false;
        }
        // 检查扩展名是否在允许列表中
        boolean hasAllowedExtension = ALLOWED_EXTENSIONS.stream()
                .anyMatch(ext -> scriptPath.toLowerCase().endsWith(ext));
        if (!hasAllowedExtension) {
            log.warn("脚本扩展名不在允许列表中，已拒绝: {}", scriptPath);
            return false;
        }
        return true;
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

            // 安全检查：验证脚本路径
            if (!isScriptPathSafe(scriptPath)) {
                log.error("脚本路径安全验证失败: {}", scriptPath);
                return ToolExecutionResult.failure("脚本路径不安全或不在允许列表中");
            }

            // 安全检查：验证工作目录（防止路径遍历）
            if (workingDir.contains("..") || workingDir.contains("~")) {
                log.error("工作目录包含路径遍历字符: {}", workingDir);
                return ToolExecutionResult.failure("工作目录不安全");
            }

            // 安全检查：验证所有参数
            if (params != null) {
                for (Map.Entry<String, Object> entry : params.entrySet()) {
                    String paramValue = String.valueOf(entry.getValue());
                    if (!isParamSafe(paramValue)) {
                        log.error("参数 '{}' 包含危险字符: {}", entry.getKey(), paramValue);
                        return ToolExecutionResult.failure("参数包含不允许的字符，已拒绝执行");
                    }
                }
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