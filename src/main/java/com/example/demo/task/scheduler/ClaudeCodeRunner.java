package com.example.demo.task.scheduler;

import com.example.demo.task.domain.ScheduledTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Claude Code CLI 调用器（task 模块专用）。
 *
 * <p>独立实现而非跨模块依赖 {@code dispatch.ClaudeCodeExecutor}，避免：
 * 1. task 模块被迫在 allowedDependencies 中加 dispatch；
 * 2. 任务运行被 dispatch 的 workspace/git worktree 体系绑架（定时任务无邮件上下文，
 *    不需要 git 分支隔离）。</p>
 *
 * <p>每次调用在临时目录建一个独立 workspace（避免跨任务串扰），调 {@code claude -p}
 * 写 prompt，捕 stdout 并提取 JSON 里的 result 文本。</p>
 */
@Slf4j
@Component
public class ClaudeCodeRunner {

    private static final String CLI = "claude";
    private static final long DEFAULT_TIMEOUT_SECONDS = 180;

    /**
     * 跑一次 Claude Code。返回 result 文本（失败抛异常）。
     */
    public String run(ScheduledTask task) {
        if (!isOnPath(CLI)) {
            throw new IllegalStateException(
                    "claude CLI not found on PATH; install Claude Code or set PATH");
        }
        String prompt = buildPrompt(task);
        Path workspace = createTempWorkspace(task);
        log.info("claude-code run task={} workspace={}", task.getId(), workspace);
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(CLI);
            cmd.add("-p");
            cmd.add(prompt);
            cmd.add("--output-format");
            cmd.add("json");
            cmd.add("--add-dir");
            cmd.add(workspace.toString());
            cmd.add("--max-turns");
            cmd.add("1");
            cmd.add("--allowedTools"); cmd.add("Read,Glob,Grep");
            return runProcess(cmd, workspace, DEFAULT_TIMEOUT_SECONDS);
        } finally {
            deleteRecursive(workspace);
        }
    }

    /**
     * 把任务字段组装成 Claude Code 的 prompt。
     */
    String buildPrompt(ScheduledTask task) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 定时任务\n\n");
        sb.append("请按下面描述完成任务，并把最终结果以 Markdown 格式返回。\n\n");

        sb.append("# 任务元数据\n\n");
        sb.append("- 名称: ").append(nullSafe(task.getName())).append("\n");
        sb.append("- 类型: ").append(nullSafe(task.getTaskType())).append("\n");
        if (task.getSkillCode() != null && !task.getSkillCode().isEmpty()) {
            sb.append("- 技能: ").append(task.getSkillCode()).append("\n");
        }
        sb.append("\n");

        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            sb.append("# 任务描述\n\n").append(task.getDescription()).append("\n\n");
        }
        if (task.getParams() != null && !task.getParams().isEmpty()) {
            sb.append("# 任务参数\n\n```\n").append(task.getParams()).append("\n```\n\n");
        }
        return sb.toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private Path createTempWorkspace(ScheduledTask task) {
        try {
            Path base = Files.createTempDirectory("scheduled-task-");
            Path sub = base.resolve(task.getId() + "-" + UUID.randomUUID().toString().substring(0, 8));
            Files.createDirectories(sub);
            return sub;
        } catch (Exception e) {
            throw new IllegalStateException("failed to create workspace: " + e.getMessage(), e);
        }
    }

    private static void deleteRecursive(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private String runProcess(List<String> cmd, Path workDir, long timeoutSeconds) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .directory(workDir.toFile())
                    .redirectErrorStream(true);
            process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new IllegalStateException("claude-code timed out after " + timeoutSeconds + "s");
            }
            int exit = process.exitValue();
            String output = readAll(process);
            if (exit != 0) {
                throw new IllegalStateException("claude-code exit " + exit + ": " + truncate(output, 500));
            }
            return extractTextFromJson(output);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            throw new IllegalStateException("claude-code exec error: " + e.getMessage(), e);
        }
    }

    private static String readAll(Process process) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Claude CLI stdout 可能前置 "Warning: ..." 行，从首个 '{' 开始解析。
     */
    String extractTextFromJson(String raw) {
        if (raw == null) {
            return "";
        }
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

    /** 是否在本机 PATH 上找到 Claude Code CLI（启动检查用）。 */
    public boolean isAvailable() {
        return isOnPath(CLI);
    }
}