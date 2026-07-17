package com.example.demo.system.application;

import com.example.demo.shared.dto.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 服务端数据备份管理服务。
 *
 * <p>在 {@link BackupProperties#getDirectory()} 中创建、列出、下载、删除数据快照文件。
 * 实际的 ZIP 内容由 {@link DataArchiveService} 生成，本服务负责"备份文件目录"层面的管理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DataArchiveService dataArchiveService;
    private final BackupProperties properties;

    /**
     * 创建一份新的数据快照备份，落地为 ZIP 文件。
     *
     * <p>并发安全：用 {@link StandardOpenOption#CREATE_NEW} 原子创建，
     * 命中已存在文件则换名重试；避免 {@code CREATE | TRUNCATE_EXISTING} 互相覆盖
     * 导致前一份备份丢失。同时通过单次循环重试应对极端的并发密度。</p>
     */
    public BackupResult createBackup() {
        Path dir = ensureBackupDir();
        String prefix = properties.getFileNamePrefix();
        for (int attempt = 0; attempt < 100; attempt++) {
            Path target = dir.resolve(buildFileName(prefix, suffixFor(attempt)));
            try (OutputStream out = Files.newOutputStream(target,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                dataArchiveService.writeArchiveTo(out);
                long size;
                try {
                    size = Files.size(target);
                } catch (IOException e) {
                    size = -1L;
                }
                log.info("已创建数据备份: file={}, size={}", target.getFileName(), size);
                return new BackupResult(target.getFileName().toString(), size);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                // 同名已被另一个并发请求占用：换名重试
                continue;
            } catch (IOException e) {
                log.error("写入备份文件失败: {}", target, e);
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                    // best effort
                }
                throw new IllegalStateException("写入备份文件失败: " + e.getMessage(), e);
            }
        }
        throw new IllegalStateException("无法生成唯一备份文件名，已重试 100 次");
    }

    private static final java.util.concurrent.atomic.AtomicLong NAME_SEQ = new java.util.concurrent.atomic.AtomicLong();

    private static String suffixFor(int attempt) {
        if (attempt == 0) {
            return "";
        }
        // 后缀：进程内单调序号 + 当前毫秒（避免序号在多 JVM/重启后冲突）
        return "-" + NAME_SEQ.incrementAndGet() + "-" + (System.currentTimeMillis() % 1000);
    }

    /**
     * 列出所有备份文件，按 mtime 倒序。
     */
    public List<BackupFileInfo> listBackups() {
        Path dir = ensureBackupDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<BackupFileInfo> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(this::safeLastModified).reversed())
                    .forEach(p -> result.add(toInfo(p)));
        } catch (IOException e) {
            log.error("读取备份目录失败: {}", dir, e);
            throw new IllegalStateException("读取备份目录失败: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 打开备份文件并返回大小 + InputStream。由调用方负责关闭流；流式读取避免大文件 OOM。
     *
     * @param fileName 已校验的合法文件名（控制器层负责校验）
     */
    public BackupStream openBackupStream(String fileName) {
        Path file = resolveAndValidate(fileName);
        try {
            long size = Files.size(file);
            InputStream stream = Files.newInputStream(file, StandardOpenOption.READ);
            return new BackupStream(stream, size);
        } catch (IOException e) {
            log.error("打开备份文件失败: {}", file, e);
            throw new IllegalStateException("打开备份文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 备份文件读取句柄，封装 InputStream + size，避免调用方需要重新解析路径。
     */
    public record BackupStream(InputStream stream, long size) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    /**
     * 删除指定备份文件。
     */
    public void deleteBackup(String fileName) {
        Path file = resolveAndValidate(fileName);
        try {
            Files.deleteIfExists(file);
            log.info("已删除备份文件: {}", file.getFileName());
        } catch (IOException e) {
            log.error("删除备份文件失败: {}", file, e);
            throw new IllegalStateException("删除备份文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理过期与超出保留数量、总容量阈值的备份文件，返回被清理的文件数量。
     *
     * <p>清理规则按以下顺序执行（任意一条命中即删除）：</p>
     * <ol>
     *   <li>超过 {@code retentionDays} 天的备份；</li>
     *   <li>超出 {@code keepMostRecent} 份的旧备份（按 mtime 倒序，保留最新 N 份）；</li>
     *   <li>目录总大小超过 {@code maxTotalSizeMb}（0 表示不限制）时，从最旧的备份开始删除直到低于阈值。</li>
     * </ol>
     */
    public int cleanupOldBackups() {
        Path dir = ensureBackupDir();
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(Files::isRegularFile).collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            log.error("读取备份目录失败: {}", dir, e);
            return 0;
        }
        if (files.isEmpty()) {
            return 0;
        }
        // 按 mtime 倒序
        files.sort(Comparator.comparing(this::safeLastModified).reversed());

        Instant cutoff = Instant.now().minusSeconds(properties.getRetentionDays() * 86400L);
        int removed = 0;
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            Instant mtime = safeLastModified(file);
            boolean overAge = mtime.isBefore(cutoff);
            boolean overCount = i >= properties.getKeepMostRecent();
            if (!overAge && !overCount) {
                continue;
            }
            if (tryDelete(file)) {
                removed++;
            }
        }

        // 第三步：按总容量阈值清理
        long maxBytes = properties.getMaxTotalSizeMb() * 1024L * 1024L;
        if (maxBytes > 0) {
            // 重新扫描目录剩余文件并按 mtime 升序（旧 → 新）
            List<Path> remaining;
            try (Stream<Path> stream = Files.list(dir)) {
                remaining = stream.filter(Files::isRegularFile).collect(Collectors.toCollection(ArrayList::new));
            } catch (IOException e) {
                log.error("重新扫描备份目录失败: {}", dir, e);
                return removed;
            }
            if (!remaining.isEmpty()) {
                remaining.sort(Comparator.comparing(this::safeLastModified));
                long totalSize = remaining.stream().mapToLong(this::safeSize).sum();
                for (Path file : remaining) {
                    if (totalSize <= maxBytes) {
                        break;
                    }
                    long size = safeSize(file);
                    if (tryDelete(file)) {
                        removed++;
                        totalSize -= size;
                        log.info("按容量清理备份文件: {}, 当前目录总大小 {} MB", file.getFileName(), totalSize / (1024 * 1024));
                    }
                }
            }
        }

        return removed;
    }

    private boolean tryDelete(Path file) {
        try {
            Files.deleteIfExists(file);
            log.info("已清理备份文件: {}", file.getFileName());
            return true;
        } catch (IOException e) {
            log.warn("清理备份文件失败: {}", file.getFileName(), e);
            return false;
        }
    }

    private long safeSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    private Path ensureBackupDir() {
        Path dir = Paths.get(properties.getDirectory());
        if (!dir.isAbsolute()) {
            dir = Paths.get("").toAbsolutePath().resolve(dir).normalize();
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建备份目录: " + dir, e);
        }
        return dir;
    }

    private Path resolveAndValidate(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("非法备份文件名: " + fileName);
        }
        Path dir = ensureBackupDir();
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir)) {
            throw new IllegalArgumentException("备份文件路径越界: " + fileName);
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("备份文件不存在: " + fileName);
        }
        return file;
    }

    private Instant safeLastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private BackupFileInfo toInfo(Path file) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            size = -1L;
        }
        Instant mtime = safeLastModified(file);
        LocalDateTime createdAt = LocalDateTime.ofInstant(mtime, ZoneId.systemDefault());
        return new BackupFileInfo(file.getFileName().toString(), size, createdAt);
    }

    private static String buildFileName(String prefix, String suffix) {
        return prefix
                + "-" + LocalDateTime.now().format(FILE_TIME)
                + suffix
                + ".zip";
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackupResult {
        private String fileName;
        private long fileSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackupFileInfo {
        private String fileName;
        private long fileSize;
        private LocalDateTime createdAt;
    }
}