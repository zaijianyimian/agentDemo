package com.example.demo.dispatch.application;

import com.example.demo.dispatch.application.executor.Executor;
import com.example.demo.dispatch.application.executor.ExecutorRouter;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.PushConfig;
import com.example.demo.dispatch.persistence.PushConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Git/filesystem lifecycle; model calls and database persistence are test doubles. */
public class DispatcherLruVerificationTest {
    @TempDir
    Path tempDir;

    /** Standalone entry point retains artifacts for manual inspection. */
    public static void main(String[] args) throws Exception {
        DispatcherLruVerificationTest verification = new DispatcherLruVerificationTest();
        verification.tempDir = Files.createTempDirectory("dispatch-lru-verification-");
        System.out.println("Verification directory: " + verification.tempDir);
        verification.fiftyOneTasksRetainExactlyTheFiftyNewestArchives();
        System.out.println("PASS: 51 tasks completed; archives 2..51 retained; archive 1 evicted; 51 results preserved.");
    }

    @Test
    void fiftyOneTasksRetainExactlyTheFiftyNewestArchives() throws Exception {
        Path repo = Files.createDirectories(tempDir.resolve("repo"));
        git(repo, "init");
        git(repo, "-c", "user.name=LRU Verification", "-c", "user.email=lru@example.invalid",
                "-c", "commit.gpgsign=false", "commit", "--allow-empty", "-m", "verification seed");

        DispatchProperties properties = new DispatchProperties();
        properties.setProjectRoot(repo.toString());
        properties.setWorkspaceRoot(tempDir.resolve("workspaces").toString());
        properties.setDispatchedRoot(tempDir.resolve("dispatched").toString());
        PushConfigMapper mapper = mock(PushConfigMapper.class);
        when(mapper.selectById(PushConfig.SINGLETON_ID)).thenReturn(PushConfig.builder()
                .workspaceMaxCount(50).workspaceMaxAgeDays(30).executorTimeoutSeconds(30).build());
        WorkspaceManager manager = new WorkspaceManager(properties, mapper);
        DispatchedTaskService tasks = mock(DispatchedTaskService.class);
        Executor executor = mock(Executor.class);
        when(executor.execute(any(), anyString(), any(), anyInt())).thenAnswer(invocation -> {
            DispatchedTask task = invocation.getArgument(0);
            Path workspace = invocation.getArgument(2);
            assertEquals("dispatch/7/" + task.getId(), git(workspace, "branch", "--show-current").trim());
            String result = "Completed task " + task.getId();
            Files.writeString(workspace.resolve("result.txt"), result);
            return result;
        });
        ExecutorRouter router = mock(ExecutorRouter.class);
        when(router.pick("claude-code")).thenReturn(executor);
        ExecutionResultPublisher resultPublisher = mock(ExecutionResultPublisher.class);
        Dispatcher dispatcher = new Dispatcher(properties, tasks, manager, router, resultPublisher);
        Path archiveRoot = tempDir.resolve("workspaces/7/archive");
        Instant baseTime = Instant.now().minusSeconds(120);

        for (long id = 1; id <= 51; id++) {
            DispatchedTask task = DispatchedTask.builder().id(id).emailId(7L)
                    .subject("LRU verification " + id).bodyExcerpt("metadata")
                    .executor("claude-code").executionInstruction("Write a task marker")
                    .retryMax(0).executorTimeoutSeconds(30).sandboxLevel("workspace-write").build();
            dispatcher.run(task);
            Path result = tempDir.resolve("dispatched/" + id + ".md");
            verify(tasks).markDone(id, "claude-code", "Completed task " + id, result.toString());
            assertTrue(Files.readString(result).contains("Completed task " + id));
            assertFalse(Files.exists(manager.livePath(task)));
            assertEquals("Completed task " + id, Files.readString(manager.archivePath(task).resolve("result.txt")));
            // Explicit ordering avoids timestamp ties on fast filesystems without sleeping.
            Files.setLastModifiedTime(manager.archivePath(task), FileTime.from(baseTime.plusSeconds(id)));
            try (var archives = Files.list(archiveRoot)) {
                assertEquals(Math.min(id, 50), archives.filter(Files::isDirectory).count(), "after task " + id);
            }
        }
        assertFalse(Files.exists(archiveRoot.resolve("1")));
        for (long id = 2; id <= 51; id++) {
            assertTrue(Files.exists(archiveRoot.resolve(id + "/result.txt")));
        }
        try (var results = Files.list(tempDir.resolve("dispatched"))) {
            assertEquals(51, results.count());
        }
        assertEquals(1, git(repo, "worktree", "list", "--porcelain").lines()
                .filter(line -> line.startsWith("worktree ")).count());
        verify(tasks, never()).markFailed(anyLong(), any(), any());
    }

    private static String git(Path directory, String... arguments) throws Exception {
        var command = new java.util.ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Git command timed out: " + command);
        }
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
