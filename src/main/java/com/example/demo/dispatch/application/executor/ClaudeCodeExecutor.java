package com.example.demo.dispatch.application.executor;

import com.example.demo.dispatch.application.ExecutorToggleService;
import com.example.demo.dispatch.domain.DispatchedTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Claude Code CLI 执行器。
 *
 * <p>拼装 {@code claude -p <instruction> --output-format json --allowedTools <list> --add-dir <workspace>}，
 * 用 {@code ProcessBuilder} 启动，按 timeout 等待。</p>
 */
@Slf4j
@Component
public class ClaudeCodeExecutor implements Executor {

    private static final String CLI = "claude";
    private final ExecutorToggleService executorToggleService;

    public ClaudeCodeExecutor(ExecutorToggleService executorToggleService) {
        this.executorToggleService = executorToggleService;
    }

    @Override
    public String hint() {
        return "claude-code";
    }

    @Override
    public boolean isAvailable() {
        // 双重检查：用户在前端禁用 → 直接不可用，不依赖 CLI
        if (!executorToggleService.isExecutorEnabled("claude-code")) {
            log.debug("claude-code executor disabled via settings");
            return false;
        }
        return isOnPath(CLI);
    }

    @Override
    public String execute(DispatchedTask task, String instruction, Path workspace, int timeoutSeconds) {
        if (!isAvailable()) {
            throw new ExecutorUnavailableException("claude CLI not found on PATH");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(CLI);
        cmd.add("-p");
        cmd.add(instruction);
        cmd.add("--output-format");
        cmd.add("json");
        List<String> tools = SandboxTranslator.claudeTools(task.getSandboxLevel());
        if (!tools.isEmpty()) {
            cmd.add("--allowedTools");
            cmd.add(String.join(",", tools));
        }
        cmd.add("--add-dir");
        cmd.add(workspace.toString());

        log.info("Starting claude-code task {} in {}", task.getId(), workspace);
        return runProcess(cmd, workspace, timeoutSeconds);
    }

    private String runProcess(List<String> cmd, Path workDir, int timeoutSeconds) {
        try {
            return extractTextFromJson(CliProcessRunner.run(cmd, workDir, timeoutSeconds));
        } catch (ExecutorTimeoutException | ExecutorFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutorFailedException("claude-code exec error: " + e.getMessage(), e);
        }
    }

    /**
     * 从 Claude Code 的 {@code --output-format json} 输出中提取文本。
     *
     * <p>Claude Code 输出一个 JSON 对象，文本通常在 {@code result} 字段；
     * 这里做一个稳健的提取，失败则回退到原始字符串。</p>
     */
    /**
     * 从 Claude Code 的 {@code --output-format json} 输出中提取文本。失败时回退原始字符串。
     */
    static String extractTextFromJson(String raw) {
        if (raw == null) {
            return "";
        }
        // Claude CLI 可能在前置输出"Warning: ..."之类的非 JSON 行，先扫描找到第一个 '{' 起始。
        String trimmed = raw.trim();
        int brace = trimmed.indexOf('{');
        if (brace < 0) {
            return trimmed;
        }
        if (brace > 0) {
            trimmed = trimmed.substring(brace);
        }
        if (!trimmed.startsWith("{")) {
            return trimmed;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = om.readTree(trimmed);
            if (node.has("result") && node.get("result").isTextual()) {
                return node.get("result").asText();
            }
            if (node.has("content")) {
                return node.get("content").toString();
            }
            if (node.has("text")) {
                return node.get("text").asText();
            }
            return trimmed;
        } catch (Exception e) {
            return trimmed;
        }
    }

    private static boolean isOnPath(String cmd) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return false;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String[] dirs = path.split(windows ? ";" : ":");
        for (String dir : dirs) {
            try {
                Path candidate = Path.of(dir, cmd);
                if (Files.exists(candidate)) {
                    return true;
                }
                if (windows && Files.exists(Path.of(dir, cmd + ".exe"))) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}
