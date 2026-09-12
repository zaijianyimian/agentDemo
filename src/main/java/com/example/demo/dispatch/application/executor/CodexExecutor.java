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
 * Codex CLI 执行器。
 *
 * <p>拼装 {@code codex exec --json --sandbox <level> <prompt>}，
 * 把 stdout 按 NDJSON 解析，最后一条事件的文本作为结果返回。</p>
 */
@Slf4j
@Component
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

        log.info("Starting codex task {} in {}", task.getId(), workspace);
        return runProcess(cmd, workspace, timeoutSeconds);
    }

    private String runProcess(List<String> cmd, Path workDir, int timeoutSeconds) {
        try {
            return extractTextFromJsonl(CliProcessRunner.run(cmd, workDir, timeoutSeconds));
        } catch (ExecutorTimeoutException | ExecutorFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new ExecutorFailedException("codex exec error: " + e.getMessage(), e);
        }
    }

    /**
     * Codex NDJSON: 每行一个 JSON 事件，从后往前找带 {@code message} 或 {@code content} 字段的事件。
     * 找不到则回退到原始字符串。
     */
    /**
     * Codex NDJSON: 每行一个 JSON 事件，从后往前找带 {@code message} / {@code content} 字段的事件；
     * 找不到则回退到原始字符串。
     */
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
                    if ("item.completed".equals(node.path("type").asText())
                            && "agent_message".equals(node.path("item").path("type").asText())) {
                        lastText = node.path("item").path("text").asText();
                    } else if (node.has("message")) {
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
                    // skip malformed line
                }
            }
        } catch (Exception e) {
            return raw.trim();
        }
        return lastText.isEmpty() ? raw.trim() : lastText;
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
