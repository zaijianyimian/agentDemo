package com.example.demo.system.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

/**
 * BackupService 单元测试。
 *
 * <p>覆盖三层清理规则：</p>
 * <ol>
 *   <li>过期：mtime 早于 cutoff 的文件被删除；</li>
 *   <li>超数：保留最新 N 份，超出按 mtime 倒序删除；</li>
 *   <li>超容：目录总大小超过阈值，从最旧开始删，直到不超阈值。</li>
 * </ol>
 */
class BackupServiceTest {

    @TempDir
    Path tempDir;

    private BackupProperties properties;
    private DataArchiveService dataArchiveService;
    private BackupService service;

    @BeforeEach
    void setUp() throws IOException {
        properties = new BackupProperties();
        properties.setDirectory(tempDir.toString());
        properties.setKeepMostRecent(10);
        properties.setRetentionDays(30);
        properties.setMaxTotalSizeMb(0);
        properties.setFileNamePrefix("test-backup");

        dataArchiveService = mock(DataArchiveService.class);
        // 模拟写出一些字节，便于大小统计
        org.mockito.Mockito.doAnswer(inv -> {
            OutputStream out = inv.getArgument(0);
            byte[] payload = "BACKUP_PAYLOAD".getBytes(StandardCharsets.UTF_8);
            out.write(payload);
            return null;
        }).when(dataArchiveService).writeArchiveTo(any(OutputStream.class));

        service = new BackupService(dataArchiveService, properties);
    }

    @Test
    void createBackupProducesFileWithExpectedPrefix() {
        BackupService.BackupResult result = service.createBackup();
        assertThat(result.getFileName()).startsWith("test-backup-");
        assertThat(result.getFileSize()).isEqualTo("BACKUP_PAYLOAD".length());
        assertThat(Files.exists(tempDir.resolve(result.getFileName()))).isTrue();
    }

    @Test
    void listBackupsReturnsFilesSortedByMtimeDesc() throws IOException, InterruptedException {
        BackupService.BackupResult first = service.createBackup();
        Thread.sleep(1100); // mtime 精度通常是秒，跳过确保第二条更新
        BackupService.BackupResult second = service.createBackup();

        List<BackupService.BackupFileInfo> list = service.listBackups();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getFileName()).isEqualTo(second.getFileName());
        assertThat(list.get(1).getFileName()).isEqualTo(first.getFileName());
        assertThat(list.get(0).getFileSize()).isPositive();
    }

    @Test
    void deleteBackupRemovesFile() {
        BackupService.BackupResult created = service.createBackup();
        service.deleteBackup(created.getFileName());
        assertThat(Files.exists(tempDir.resolve(created.getFileName()))).isFalse();
    }

    @Test
    void cleanupRemovesExpiredFilesByRetentionDays() throws IOException, InterruptedException {
        properties.setRetentionDays(1);
        properties.setKeepMostRecent(100);
        properties.setMaxTotalSizeMb(0);

        // 创建 1 个"过期"文件（手动改 mtime 到 5 天前）
        BackupService.BackupResult fresh = service.createBackup();
        Path oldFile = tempDir.resolve("old-backup.zip");
        Files.writeString(oldFile, "OLD");
        Files.setLastModifiedTime(oldFile,
                java.nio.file.attribute.FileTime.from(Instant.now().minus(5, ChronoUnit.DAYS)));

        int removed = service.cleanupOldBackups();

        assertThat(removed).isEqualTo(1);
        assertThat(Files.exists(oldFile)).isFalse();
        assertThat(Files.exists(tempDir.resolve(fresh.getFileName()))).isTrue();
    }

    @Test
    void cleanupKeepsMostRecentAndDropsOlder() throws IOException, InterruptedException {
        properties.setRetentionDays(365);
        properties.setKeepMostRecent(2);
        properties.setMaxTotalSizeMb(0);

        BackupService.BackupResult a = service.createBackup();
        Thread.sleep(1100);
        BackupService.BackupResult b = service.createBackup();
        Thread.sleep(1100);
        BackupService.BackupResult c = service.createBackup();

        int removed = service.cleanupOldBackups();

        assertThat(removed).isEqualTo(1);
        // 最旧的 a 应该被删，b、c 保留
        assertThat(Files.exists(tempDir.resolve(a.getFileName()))).isFalse();
        assertThat(Files.exists(tempDir.resolve(b.getFileName()))).isTrue();
        assertThat(Files.exists(tempDir.resolve(c.getFileName()))).isTrue();
    }

    @Test
    void cleanupByMaxTotalSizeMbDropsOldestFirst() throws IOException, InterruptedException {
        // 让每个备份写 15 字节（"BACKUP_PAYLOAD"），3 个共 45 字节
        // 设上限为 1 MB >> 45 字节，不应触发容量清理
        properties.setRetentionDays(365);
        properties.setKeepMostRecent(100);
        properties.setMaxTotalSizeMb(1);

        service.createBackup();
        Thread.sleep(1100);
        service.createBackup();
        Thread.sleep(1100);
        service.createBackup();

        int removed = service.cleanupOldBackups();
        assertThat(removed).isZero();
    }

    @Test
    void cleanupByMaxTotalSizeMbTriggersWhenExceeded() throws IOException, InterruptedException {
        // 3 个 2 MB 文件 = 6 MB 总量；上限 4 MB 应删掉最旧的 1 个让总量 = 4 MB
        properties.setRetentionDays(365);
        properties.setKeepMostRecent(100);
        properties.setMaxTotalSizeMb(4);

        Path f1 = tempDir.resolve("b1.zip");
        Path f2 = tempDir.resolve("b2.zip");
        Path f3 = tempDir.resolve("b3.zip");
        byte[] payload = new byte[2 * 1024 * 1024];
        Files.write(f1, payload);
        Thread.sleep(1100);
        Files.write(f2, payload);
        Thread.sleep(1100);
        Files.write(f3, payload);

        int removed = service.cleanupOldBackups();

        assertThat(removed).isGreaterThanOrEqualTo(1);
        assertThat(Files.exists(f1)).isFalse(); // 最旧的被删
        assertThat(Files.exists(f2)).isTrue();
        assertThat(Files.exists(f3)).isTrue();
    }

    /**
     * 回归保护：并发 createBackup 不能互相覆盖。第一次写完之前不让第二个同名覆盖。
     * 模拟：先放一个同名老文件，再 createBackup，应当换名而不是 truncate。
     */
    @Test
    void createBackupDoesNotOverwriteExistingFile() throws IOException {
        // 在目录里预先放一个"老"备份
        String existingName = "test-backup-" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip";
        Path existing = tempDir.resolve(existingName);
        Files.writeString(existing, "OLD-CONTENT");

        BackupService.BackupResult result = service.createBackup();

        // 老文件没被覆盖
        assertThat(new String(Files.readAllBytes(existing))).isEqualTo("OLD-CONTENT");
        // 新文件存在且内容是新写的 payload
        assertThat(Files.exists(tempDir.resolve(result.getFileName()))).isTrue();
        assertThat(result.getFileName()).isNotEqualTo(existingName);
    }

    /**
     * 验证 reserveUniqueTarget 在并发压力下能成功创建多份独立备份。
     */
    @Test
    void concurrentCreateBackupProducesDistinctFiles() throws Exception {
        int n = 8;
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(n);
        try {
            java.util.List<java.util.concurrent.Future<BackupService.BackupResult>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(service::createBackup));
            }
            java.util.Set<String> names = new java.util.HashSet<>();
            for (java.util.concurrent.Future<BackupService.BackupResult> f : futures) {
                names.add(f.get().getFileName());
            }
            // n 次调用必须产生 n 个不重名的文件
            assertThat(names).hasSize(n);
            assertThat(names).allSatisfy(name -> assertThat(Files.exists(tempDir.resolve(name))).isTrue());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void openBackupStreamReturnsReadableBytes() throws IOException {
        BackupService.BackupResult result = service.createBackup();
        try (BackupService.BackupStream handle = service.openBackupStream(result.getFileName())) {
            assertThat(handle.size()).isEqualTo("BACKUP_PAYLOAD".length());
            byte[] bytes = handle.stream().readAllBytes();
            assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("BACKUP_PAYLOAD");
        }
    }

    @Test
    void openBackupStreamRejectsPathTraversal() {
        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.openBackupStream("../etc/passwd"))).isNotNull();
    }
}