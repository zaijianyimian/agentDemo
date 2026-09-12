package com.example.demo.dispatch.application.executor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Drain CLI output to a file so a full stdout pipe cannot deadlock the child. */
final class CliProcessRunner {
    private CliProcessRunner() {}

    static String run(List<String> command, Path directory, int timeoutSeconds) throws Exception {
        Path output = Files.createTempFile("agent-cli-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command).directory(directory.toFile())
                    .redirectErrorStream(true).redirectOutput(output.toFile()).start();
            process.getOutputStream().close();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                throw new Executor.ExecutorTimeoutException("CLI timed out after " + timeoutSeconds + "s");
            }
            String text = Files.readString(output, StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new Executor.ExecutorFailedException("CLI exit " + process.exitValue() + ": "
                        + text.substring(0, Math.min(text.length(), 500)));
            }
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(child -> child.destroyForcibly());
                process.destroyForcibly();
            }
            Files.deleteIfExists(output);
        }
    }
}
