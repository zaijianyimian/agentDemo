package com.example.demo.dispatch.application.executor;

import com.example.demo.dispatch.domain.DispatchedTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Claude Code CLI 执行器。
 *
 * <p>拼装 {@code claude -p <prompt> --output-format json --allowedTools <list> --add-dir <workspace>}，
 * 用 {@code ProcessBuilder} 启动，按 timeout 等待。</p>
 */
@Slf4j
@Component
public class ClaudeCodeExecutor implements Executor {

    private static final String CLI = "claude";

    @Override
    public String hint() {
        return "claude-code";
    }

    @Override
    public boolean isAvailable() {
        return isOnPath(CLI);
    }

    @Override
    public String execute(DispatchedTask task, String prompt, Path workspace, int timeoutSeconds) {
        if (!isAvailable()) {
            throw new ExecutorUnavailableException("claude CLI not found on PATH");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(CLI);
        cmd.add("-p");
        cmd.add(prompt);
        cmd.add("--output-format");
        cmd.add("json");
        List<String> tools = SandboxTranslator.claudeTools(task.getSandboxLevel());
        if (!tools.isEmpty()) {
            cmd.add("--allowedTools");
            cmd.add(String.join(",", tools));
        }
        cmd.add("--add-dir");
        cmd.add(workspace.toString());

        log.info("claude-code exec: {}", String.join(" ", cmd));
        return runProcess(cmd, workspace, timeoutSeconds);
    }

    private String runProcess(List<String> cmd, Path workDir, int timeoutSeconds) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(workDir.toFile())
                    .redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ExecutorTimeoutException(
                        "claude-code timed out after " + timeoutSeconds + "s");
            }
            int exit = process.exitValue();
            String output = readAll(process);
            if (exit != 0) {
                throw new ExecutorFailedException(
                        "claude-code exit " + exit + ": " + truncate(output, 500));
            }
            return extractTextFromJson(output);
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
        String trimmed = raw.trim();
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

    private static String readAll(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "...[truncated]" : s;
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
