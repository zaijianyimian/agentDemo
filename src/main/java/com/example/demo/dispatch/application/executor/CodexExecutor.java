package com.example.demo.dispatch.application.executor;

import com.example.demo.dispatch.application.ExecutorToggleService;
import com.example.demo.dispatch.domain.DispatchedTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Codex CLI 执行器。
 *
 * <p>拼装 {@code codex exec --json --sandbox <level> <prompt>}，
 * 把 stdout 按 NDJSON 解析，最后一条事件的文本作为结果返回。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class CodexExecutor implements Executor {

    private static final String CLI = "codex";
    private final ExecutorToggleService executorToggleService;

    public CodexExecutor(ExecutorToggleService executorToggleService) {
        this.executorToggleService = executorToggleService;
    }

    @Override
    public String hint() {
        return "codex";
    }

    @Override
    public boolean isAvailable() {
        if (!executorToggleService.isExecutorEnabled("codex")) {
            log.debug("codex executor disabled via settings");
            return false;
        }
        return isOnPath(CLI);
    }

    @Override
    public String execute(DispatchedTask task, String prompt, Path workspace, int timeoutSeconds) {
        if (!isAvailable()) {
            throw new ExecutorUnavailableException("codex CLI not found on PATH");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(CLI);
        cmd.add("exec");
        cmd.add("--json");
        cmd.add("--sandbox");
        cmd.add(SandboxTranslator.codexFlag(task.getSandboxLevel()));
        cmd.add(prompt);

        log.info("codex exec: {}", String.join(" ", cmd));
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
                        "codex timed out after " + timeoutSeconds + "s");
            }
            int exit = process.exitValue();
            String output = readAll(process);
            if (exit != 0) {
                throw new ExecutorFailedException(
                        "codex exit " + exit + ": " + truncate(output, 500));
            }
            return extractTextFromJsonl(output);
        } catch (ExecutorTimeoutException | ExecutorFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutorFailedException("codex exec error: " + e.getMessage(), e);
        }
    }

    static String extractTextFromJsonl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lastText = "";
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            for (String line : raw.split("\\R")) {
                String t = line.trim();
                if (t.isEmpty() || !t.startsWith("{")) {
                    continue;
                }
                try {
                    var node = om.readTree(t);
                    if (node.has("message")) {
                        var msg = node.get("message");
                        if (msg.isTextual()) {
                            lastText = msg.asText();
                        } else if (msg.has("content")) {
                            lastText = msg.get("content").asText();
                        }
                    } else if (node.has("content")) {
                        var content = node.get("content");
                        if (content.isTextual()) {
                            lastText = content.asText();
                        }
                    } else if (node.has("text")) {
                        lastText = node.get("text").asText();
                    } else if (node.has("result")) {
                        var result = node.get("result");
                        if (result.isTextual()) {
                            lastText = result.asText();
                        }
                    }
                } catch (Exception ignored) {
                    // 跳过非 JSON 行。
                }
            }
        } catch (Exception e) {
            return raw.trim();
        }
        return lastText.isEmpty() ? raw.trim() : lastText;
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
