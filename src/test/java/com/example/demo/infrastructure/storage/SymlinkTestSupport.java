package com.example.demo.infrastructure.storage;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 符号链接测试的前置条件探测。
 *
 * <p>Windows 上 {@link Files#createSymbolicLink} 需要管理员权限或开启开发者模式
 * （{@code SeCreateSymbolicLinkPrivilege}），否则会抛
 * {@code FileSystemException: 客户端没有所需的特权}。</p>
 *
 * <p>这里只探测「当前环境能否创建符号链接」，并在不能创建时把用例标记为跳过，
 * 而不是失败。这样做不改动任何生产安全逻辑：
 * {@link OwnedStorageResolver} 与 {@link LegacyOwnedFileMigrationService} 中
 * 拒绝符号链接的检查保持原样，在具备权限的环境（CI、Linux、已开启开发者模式的
 * Windows）中原样执行并断言。</p>
 */
final class SymlinkTestSupport {

    private SymlinkTestSupport() {
    }

    /**
     * 探测当前环境是否支持创建符号链接，不支持则跳过当前用例。
     *
     * @param workingDirectory 用于探测的临时目录
     */
    static void assumeSymlinkSupported(Path workingDirectory) {
        Path probeTarget = workingDirectory.resolve("symlink-probe-target.txt");
        Path probeLink = workingDirectory.resolve("symlink-probe-link");
        try {
            Files.writeString(probeTarget, "probe");
            Files.createSymbolicLink(probeLink, probeTarget);
        } catch (IOException | UnsupportedOperationException | SecurityException error) {
            Assumptions.assumeTrue(false,
                    "当前环境无法创建符号链接（Windows 需管理员权限或开启开发者模式），"
                            + "跳过符号链接安全用例：" + error.getMessage());
        } finally {
            deleteQuietly(probeLink);
            deleteQuietly(probeTarget);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 探测文件清理失败不影响用例结论。
        }
    }
}
