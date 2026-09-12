package com.example.demo.dispatch.application.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CliProcessRunnerTest {
    @TempDir Path directory;

    public static class Child {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("sleep")) { Thread.sleep(30000); return; }
            System.out.print("x".repeat(200000));
        }
    }

    private List<String> command(String mode) throws Exception {
        String classes = Path.of(Child.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classes, Child.class.getName(), mode);
    }

    @Test void drainsOutputLargerThanPipeCapacity() throws Exception {
        assertEquals(200000, CliProcessRunner.run(command("output"), directory, 10).length());
    }

    @Test void enforcesTimeout() {
        assertThrows(Executor.ExecutorTimeoutException.class,
                () -> CliProcessRunner.run(command("sleep"), directory, 1));
    }

    @Test void extractsFinalAgentMessage() {
        String raw = "{\"type\":\"item.completed\",\"item\":{\"type\":\"agent_message\",\"text\":\"完成\"}}\n"
                + "{\"type\":\"turn.completed\",\"usage\":{}}";
        assertEquals("完成", CodexExecutor.extractTextFromJsonl(raw));
    }
}
