package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.domain.PushConfig;
import com.example.demo.dispatch.persistence.PushConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 工作区管理：基于 git worktree 的隔离 + LRU 归档淘汰。
 *
 * <p>每次任务执行 = 一个新的 {@code data/workspaces/<email-id>/<task-id>/} worktree，
 * 分支名 {@code dispatch/<email-id>/<task-id>}。执行完成后 mv 到 {@code archive/}，
 * 由 {@link #evict(Long, PushConfig)} 按 LRU + 上限清理。</p>
 */
@Slf4j
@Service
public class WorkspaceManager {

    private final DispatchProperties properties;
    private final PushConfigMapper pushConfigMapper;
    private final Path projectRoot;

    public WorkspaceManager(DispatchProperties properties, PushConfigMapper pushConfigMapper) {
        this.properties = properties;
        this.pushConfigMapper = pushConfigMapper;
        this.projectRoot = resolveProjectRoot(properties);
    }

    /**
     * 根据 {@link DispatchProperties} 决定项目根：
     * <ul>
     *   <li>显式配置了 {@code projectRoot} → 直接使用（绝对路径或相对 CWD）；</li>
     *   <li>未配置 → 从 CWD 向上探测 {@code .git}，未找到则降级到 workspaceRoot 父目录。</li>
     * </ul>
     */
    private static Path resolveProjectRoot(DispatchProperties properties) {
        String configured = properties.getProjectRoot();
        if (configured != null && !configured.isBlank()) {
            Path explicit = Paths.get(configured);
            return explicit.isAbsolute()
                    ? explicit.normalize()
                    : Paths.get("").toAbsolutePath().resolve(explicit).normalize();
        }
        return detectProjectRoot(properties);
    }

    /**
     * 为任务创建新的 worktree。已存在则返回现有路径（幂等）。
     */
    public Path create(DispatchedTask task) {
        Path live = livePath(task);
        if (Files.isDirectory(live) && Files.exists(live.resolve(".git"))) {
            log.info("workspace already exists, reusing: {}", live);
            return live;
        }
        try {
            Files.createDirectories(live.getParent());
        } catch (IOException e) {
            throw new WorkspaceUnavailableException("failed to create parent dir " + live.getParent(), e);
        }

        Path repoRoot = projectRoot();
        if (!Files.exists(repoRoot.resolve(".git"))) {
            throw new WorkspaceUnavailableException("project is not a git repository: " + repoRoot);
        }

        String branch = branchFor(task);
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("worktree");
        cmd.add("add");
        cmd.add("-b");
        cmd.add(branch);
        cmd.add(live.toString());

        ProcessResult pr = runCommand(repoRoot, cmd, Duration.ofSeconds(30));
        if (pr.exitCode != 0) {
            throw new WorkspaceUnavailableException(
                    "git worktree add failed (exit " + pr.exitCode + "): " + pr.output);
        }
        log.info("workspace created: {} (branch {})", live, branch);
        return live;
    }

    /**
     * 归档工作区到 {@code archive/<task-id>/}。幂等。
     *
     * <p>失败时尽量保持原状：</p>
     * <ul>
     *   <li>git worktree remove 失败 → 仍尝试 mv（仅警告），因为 git bookkeeping 失败不应阻塞归档；</li>
     *   <li>Files.move 失败 → 不抛出 worktree 已删除但归档失败的中间状态；</li>
     *   <li>目录冲突 → 降级为非原子 move 并保留原 live。</li>
     * </ul>
     */
    public Path archive(DispatchedTask task) {
        Path live = livePath(task);
        Path archive = archivePath(task);
        if (Files.exists(archive)) {
            log.info("archive already exists, no-op: {}", archive);
            return archive;
        }
        if (!Files.exists(live)) {
            log.warn("live workspace does not exist, nothing to archive: {}", live);
            try {
                Files.createDirectories(archive);
                Files.writeString(archive.resolve("ARCHIVED_WITHOUT_LIVE.txt"),
                        "live workspace not found at archive time\n");
            } catch (IOException e) {
                throw new WorkspaceUnavailableException("failed to write archive marker", e);
            }
            return archive;
        }
        try {
            Files.createDirectories(archive.getParent());
            // 关键：先复制到 archive 目录，再让 git 移除 worktree。
            // 之前先 worktree remove 再 Files.move 的顺序会在 worktree 移除成功后
            // 让 live 目录消失，导致 Files.move 抛 NoSuchFileException。
            copyRecursive(live, archive);
            // 复制成功后再做 worktree 清理，失败仅警告不抛
            ProcessResult remove = runCommand(projectRoot(),
                    List.of("git", "worktree", "remove", "--force", live.toString()),
                    Duration.ofSeconds(15));
            if (remove.exitCode != 0) {
                log.warn("git worktree remove failed (exit {}): {}", remove.exitCode, remove.output);
            }
            log.info("workspace archived: {} -> {}", live, archive);
            return archive;
        } catch (IOException e) {
            throw new WorkspaceUnavailableException("failed to archive workspace: " + e.getMessage(), e);
        }
    }

    /**
     * 递归把 src 整个目录树拷到 dst。如果 dst 已存在则覆盖其中文件但保留其他内容。
     * 跨设备时 Files.move 走原子会失败，这个方法是替代方案。
     */
    private static void copyRecursive(Path src, Path dst) throws IOException {
        try (var stream = Files.walk(src)) {
            stream.forEach(srcPath -> {
                try {
                    Path relative = src.relativize(srcPath);
                    Path target = dst.resolve(relative.toString());
                    if (Files.isDirectory(srcPath)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(srcPath, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException ue) {
            throw ue.getCause();
        }
    }

    /**
     * LRU 淘汰某邮箱下的归档工作区。
     *
     * <p>规则：
     * <ol>
     *   <li>如果归档数 > workspace_max_count，删除最旧（按 mtime ASC）的归档直到合规。</li>
     *   <li>如果某个归档的 mtime 早于 now - workspace_max_age_days 且它不是该邮箱唯一剩下的归档，
     *       删除它（年龄淘汰，但保留一个最新的）。</li>
     * </ol>
     */
    /**
     * 对指定邮箱下的归档工作区实施 LRU 淘汰，规则详见类注释。返回被淘汰的数量。
     */
    public int evict(Long emailId, PushConfig cfg) {
        if (cfg == null || cfg.getWorkspaceMaxCount() == null) {
            return 0;
        }
        Path emailDir = emailWorkspaceRoot(emailId);
        Path archiveDir = emailDir.resolve("archive");
        if (!Files.exists(archiveDir)) {
            return 0;
        }
        List<Path> archives;
        try (Stream<Path> stream = Files.list(archiveDir)) {
            archives = stream.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            log.warn("failed to list archive dir {}", archiveDir, e);
            return 0;
        }
        if (archives.isEmpty()) {
            return 0;
        }

        int evicted = 0;

        // Sort by mtime ASC (least-recent first)
        archives = archives.stream()
                .sorted(Comparator.comparing(WorkspaceManager::safeLastModified))
                .toList();

        int max = cfg.getWorkspaceMaxCount();
        int oversize = archives.size() - max; // Dispatcher invokes eviction after archival.
        for (int i = 0; i < oversize && i < archives.size(); i++) {
            Path victim = archives.get(i);
            deleteRecursive(victim);
            log.info("evicted by count: {}", victim);
            evicted++;
        }

        // Age-based eviction
        int maxAge = cfg.getWorkspaceMaxAgeDays() == null ? 30 : cfg.getWorkspaceMaxAgeDays();
        Instant cutoff = Instant.now().minus(Duration.ofDays(maxAge));
        List<Path> remaining;
        try (Stream<Path> stream = Files.list(archiveDir)) {
            remaining = stream.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            return evicted;
        }
        // newest first
        remaining = remaining.stream()
                .sorted(Comparator.comparing(WorkspaceManager::safeLastModified).reversed())
                .toList();
        for (int i = 1; i < remaining.size(); i++) { // index 0 is the most-recent
            Path p = remaining.get(i);
            try {
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                    deleteRecursive(p);
                    log.info("evicted by age: {}", p);
                    evicted++;
                }
            } catch (IOException e) {
                log.warn("failed to read attributes of {}", p, e);
            }
        }
        return evicted;
    }

    /**
     * 加载推送配置单例；不存在则写入默认值。
     */
    public PushConfig loadPushConfig() {
        Optional<PushConfig> existing = Optional.ofNullable(pushConfigMapper.selectById(PushConfig.SINGLETON_ID));
        if (existing.isPresent()) {
            return existing.get();
        }
        PushConfig cfg = PushConfig.builder()
                .id(PushConfig.SINGLETON_ID)
                .pushThreshold("medium")
                .batchCron("0 0 9 * * ?")
                .immediateEnabled(true)
                .workspaceMaxCount(50)
                .workspaceMaxAgeDays(30)
                .retryMax(2)
                .executorTimeoutSeconds(properties.getExecutorTimeoutSeconds())
                .updatedAt(LocalDateTime.now())
                .build();
        pushConfigMapper.insert(cfg);
        return cfg;
    }

    /**
     * 返回任务对应的工作区（live）根目录的绝对路径。
     */
    public Path livePath(DispatchedTask task) {
        return emailWorkspaceRoot(task.getEmailId()).resolve(String.valueOf(task.getId()));
    }

    /**
     * 返回任务对应的归档目录的绝对路径。
     */
    public Path archivePath(DispatchedTask task) {
        return emailWorkspaceRoot(task.getEmailId()).resolve("archive").resolve(String.valueOf(task.getId()));
    }

    private Path emailWorkspaceRoot(Long emailId) {
        return Paths.get(properties.getWorkspaceRoot(), String.valueOf(emailId)).toAbsolutePath().normalize();
    }

    private String branchFor(DispatchedTask task) {
        return "dispatch/" + task.getEmailId() + "/" + task.getId();
    }

    private Path projectRoot() {
        return projectRoot;
    }

    private static Path detectProjectRoot(DispatchProperties properties) {
        // Walk up from CWD until we find a .git directory. The workspace_root is
        // typically a subdir of the project (e.g. ./data/workspaces), so walking up
        // from CWD reliably lands on the project root in standard layouts.
        Path cwd = Paths.get("").toAbsolutePath();
        Path cur = cwd;
        while (cur != null) {
            if (Files.isDirectory(cur.resolve(".git"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        // Fallback: parent of workspace root
        Path ws = Paths.get(properties.getWorkspaceRoot()).toAbsolutePath().normalize();
        return ws.getParent() != null ? ws.getParent() : cwd;
    }

    private static Instant safeLastModified(Path p) {
        try {
            return Files.readAttributes(p, BasicFileAttributes.class).lastModifiedTime().toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private static void deleteRecursive(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("failed to delete {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("failed to walk {}", root, e);
        }
    }

    private static ProcessResult runCommand(Path workDir, List<String> cmd, Duration timeout) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile()).redirectErrorStream(true);
            process = pb.start();
            boolean finished = process.waitFor(timeout.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                return new ProcessResult(-1, "timeout: " + String.join(" ", cmd));
            }
            byte[] out;
            try (java.io.InputStream in = process.getInputStream()) {
                out = in.readAllBytes();
            }
            return new ProcessResult(process.exitValue(), new String(out, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try {
                    process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            return new ProcessResult(-1, "exec failed: " + e.getMessage());
        }
    }

    private record ProcessResult(int exitCode, String output) {}

    public static class WorkspaceUnavailableException extends RuntimeException {
        public WorkspaceUnavailableException(String message) {
            super(message);
        }

        public WorkspaceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
