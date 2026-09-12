package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.PushConfig;
import com.example.demo.dispatch.application.DispatchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WorkspaceManager 测试。覆盖：
 *   · archive 二次调用幂等
 *   · 超出 max_count 触发 LRU 淘汰
 *   · 项目不是 git 仓库时抛 WorkspaceUnavailableException
 */
class WorkspaceManagerTest {

    @TempDir
    Path tempDir;

    private DispatchProperties properties;
    private com.example.demo.dispatch.persistence.PushConfigMapper mapper;
    private WorkspaceManager manager;

    @BeforeEach
    void setUp() {
        properties = new DispatchProperties();
        properties.setWorkspaceRoot(tempDir.resolve("workspaces").toString());
        properties.setDispatchedRoot(tempDir.resolve("dispatched").toString());
        // 指向一个不存在的根目录，确保 create() 触发 "不是 git 仓库" 分支。
        properties.setProjectRoot(tempDir.resolve("not-a-git-repo").toString());
        mapper = mock(com.example.demo.dispatch.persistence.PushConfigMapper.class);
        manager = new WorkspaceManager(properties, mapper);
    }

    @Test
    void archiveIsIdempotent() throws IOException {
        Path archiveDir = tempDir.resolve("workspaces/1/archive/100");
        Files.createDirectories(archiveDir);
        Files.writeString(archiveDir.resolve("note.txt"), "hi");

        DispatchedTask task = DispatchedTask.builder()
                .id(100L).emailId(1L)
                .build();

        Path first = manager.archive(task);
        assertTrue(Files.exists(first));
        // second call is no-op
        Path second = manager.archive(task);
        assertEquals(first, second);
    }

    @Test
    void evictRemovesOldestWhenAboveLimit() throws IOException {
        long emailId = 7L;
        Path emailRoot = tempDir.resolve("workspaces/" + emailId + "/archive");
        Files.createDirectories(emailRoot);
        // task-0 is newest; task-4 is oldest.
        for (int i = 0; i < 5; i++) {
            Path a = emailRoot.resolve("task-" + i);
            Files.createDirectories(a);
            Files.writeString(a.resolve("f.txt"), "x");
            Files.setLastModifiedTime(a, java.nio.file.attribute.FileTime.from(
                    Instant.now().minus(i, ChronoUnit.MINUTES)));
        }

        PushConfig cfg = PushConfig.builder()
                .workspaceMaxCount(3)
                .workspaceMaxAgeDays(30)
                .build();

        int evicted = manager.evict(emailId, cfg);
        assertEquals(2, evicted);

        try (Stream<Path> stream = Files.list(emailRoot)) {
            long remaining = stream.filter(Files::isDirectory).count();
            assertEquals(3, remaining);
        }
        for (int i = 0; i < 5; i++) {
            assertEquals(i < 3, Files.exists(emailRoot.resolve("task-" + i)));
        }
        assertEquals(0, manager.evict(emailId, cfg), "a sweep at capacity must keep all archives");
    }

    @Test
    void ageEvictionPreservesNewestEvenWhenAllArchivesExpired() throws IOException {
        Path archiveRoot = tempDir.resolve("workspaces/7/archive");
        Instant now = Instant.now();
        for (int i = 0; i < 3; i++) {
            Path archive = Files.createDirectories(archiveRoot.resolve("task-" + i));
            Files.setLastModifiedTime(archive, java.nio.file.attribute.FileTime.from(
                    now.minus(31 + i, ChronoUnit.DAYS)));
        }
        Path live = Files.createDirectories(tempDir.resolve("workspaces/7/live"));
        Path otherMailbox = Files.createDirectories(tempDir.resolve("workspaces/8/archive/old"));
        PushConfig cfg = PushConfig.builder().workspaceMaxCount(50).workspaceMaxAgeDays(30).build();

        assertEquals(2, manager.evict(7L, cfg));
        assertTrue(Files.exists(archiveRoot.resolve("task-0")));
        assertFalse(Files.exists(archiveRoot.resolve("task-1")));
        assertFalse(Files.exists(archiveRoot.resolve("task-2")));
        assertTrue(Files.exists(live));
        assertTrue(Files.exists(otherMailbox));
        assertEquals(0, manager.evict(7L, cfg));
    }

    @Test
    void createFailsWhenProjectNotGitRepo() throws IOException {
        // temp dir is NOT a git repo
        DispatchedTask task = DispatchedTask.builder()
                .id(99L).emailId(2L)
                .build();
        assertThrows(WorkspaceManager.WorkspaceUnavailableException.class,
                () -> manager.create(task));
    }
}
