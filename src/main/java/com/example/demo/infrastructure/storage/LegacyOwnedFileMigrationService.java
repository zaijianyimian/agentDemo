package com.example.demo.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Offline, journal-backed migration from legacy shared paths to an explicit user's storage root. */
@Service
public class LegacyOwnedFileMigrationService {

    private final LegacyFileMigrationRepository repository;
    private final Path usersRoot;

    LegacyOwnedFileMigrationService(
            LegacyFileMigrationRepository repository,
            @Value("${app.storage.root:./data/users}") String usersRoot) {
        this.repository = repository;
        this.usersRoot = Path.of(usersRoot).toAbsolutePath().normalize();
    }

    public MigrationSummary migrate(long ownerUserId, Path legacyPathBase) throws IOException {
        return migrate(ownerUserId, legacyPathBase, List.of());
    }

    public MigrationSummary migrate(long ownerUserId, Path legacyPathBase,
                                    List<Path> inventoryRoots) throws IOException {
        Path trustedLegacyBase = requireDirectoryWithoutSymlinks(legacyPathBase);
        repository.requireActiveOwner(ownerUserId);
        List<LegacyFileMigrationRepository.LegacyFileRecord> pending = repository.findPending(ownerUserId);
        Set<Path> rootsToCheck = new LinkedHashSet<>();
        for (var record : pending) {
            Path parent = resolveLegacySource(trustedLegacyBase, record.filePath()).getParent();
            if (parent != null) rootsToCheck.add(parent);
        }
        for (Path inventoryRoot : inventoryRoots) {
            rootsToCheck.add(resolveInventoryRoot(trustedLegacyBase, inventoryRoot));
        }
        int completed = 0;
        for (var record : pending) {
            migrateOne(ownerUserId, trustedLegacyBase, record);
            completed++;
        }
        assertNoUnreferencedLegacyFiles(rootsToCheck);
        return new MigrationSummary(pending.size(), completed);
    }

    private void migrateOne(long ownerUserId, Path legacyBase,
                            LegacyFileMigrationRepository.LegacyFileRecord record) throws IOException {
        Path source = resolveLegacySource(legacyBase, record.filePath());
        String storageKey = storageKey(record, source);
        Path target = resolveTarget(ownerUserId, record.category(), storageKey);
        String checksum = Files.exists(source, LinkOption.NOFOLLOW_LINKS)
                ? checksum(source)
                : Files.exists(target, LinkOption.NOFOLLOW_LINKS) ? checksum(target) : "missing";
        repository.start(ownerUserId, record, storageKey, checksum);
        try {
            if ("missing".equals(checksum)) {
                throw new IOException("legacy source and owned target are both missing: " + record.filePath());
            }
            copyIdempotently(source, target, checksum);
            repository.markCopied(ownerUserId, record, storageKey, checksum);
            Files.deleteIfExists(source);
            repository.complete(ownerUserId, record, storageKey, checksum);
        } catch (IOException | RuntimeException error) {
            repository.fail(ownerUserId, record, storageKey, checksum, error.getMessage());
            throw error;
        }
    }

    private Path resolveLegacySource(Path legacyBase, String filePath) throws IOException {
        if (filePath == null || filePath.isBlank()) {
            throw new IOException("legacy file_path is blank");
        }
        Path raw = Path.of(filePath);
        Path source = (raw.isAbsolute() ? raw : legacyBase.resolve(raw)).toAbsolutePath().normalize();
        if (!source.startsWith(legacyBase)) {
            throw new AccessDeniedException("legacy path escapes LEGACY_PATH_BASE");
        }
        rejectSymlinks(legacyBase, source);
        return source;
    }

    private Path resolveTarget(long ownerUserId, OwnedStorageResolver.Category category,
                               String storageKey) throws IOException {
        if (ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        Path categoryRoot = usersRoot.resolve(Long.toString(ownerUserId))
                .resolve(categoryDirectory(category)).normalize();
        Path target = categoryRoot.resolve(storageKey).normalize();
        if (!target.startsWith(categoryRoot)) {
            throw new AccessDeniedException("migration target escapes owned category");
        }
        Files.createDirectories(target.getParent());
        rejectSymlinks(usersRoot, target);
        return target;
    }

    private Path resolveInventoryRoot(Path legacyBase, Path configuredRoot) throws IOException {
        Path root = (configuredRoot.isAbsolute() ? configuredRoot : legacyBase.resolve(configuredRoot))
                .toAbsolutePath().normalize();
        if (!root.startsWith(legacyBase)) {
            throw new AccessDeniedException("legacy inventory root escapes LEGACY_PATH_BASE");
        }
        rejectSymlinks(legacyBase, root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("legacy inventory root does not exist: " + root);
        }
        return root;
    }

    private void assertNoUnreferencedLegacyFiles(Set<Path> inventoryRoots) throws IOException {
        for (Path root : inventoryRoots) {
            try (var paths = Files.walk(root)) {
                Path leftover = paths
                        .filter(path -> !path.toAbsolutePath().normalize().startsWith(usersRoot))
                        .filter(path -> Files.isSymbolicLink(path)
                                || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .findFirst()
                        .orElse(null);
                if (leftover != null) {
                    throw new IOException("unreferenced legacy file blocks cutover: " + leftover);
                }
            }
        }
    }

    private static void copyIdempotently(Path source, Path target, String expectedChecksum) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!checksum(target).equals(expectedChecksum)) {
                throw new IOException("owned target checksum conflicts with migration journal");
            }
            return;
        }
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("legacy source is not a regular file: " + source);
        }
        Path temporary = target.resolveSibling(target.getFileName() + ".migration-part");
        Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
        if (!checksum(temporary).equals(expectedChecksum)) {
            Files.deleteIfExists(temporary);
            throw new IOException("copied file checksum mismatch");
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path requireDirectoryWithoutSymlinks(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw new IOException("LEGACY_PATH_BASE must be an existing non-symlink directory");
        }
        return normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    private static void rejectSymlinks(Path root, Path target) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new AccessDeniedException("migration path escapes trusted root");
        }
        Path cursor = normalizedRoot;
        for (Path component : normalizedRoot.relativize(normalizedTarget)) {
            cursor = cursor.resolve(component);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                throw new AccessDeniedException("symbolic links are forbidden during migration");
            }
        }
    }

    private static String storageKey(LegacyFileMigrationRepository.LegacyFileRecord record, Path source) {
        String name = source.getFileName() == null ? "file" : source.getFileName().toString();
        String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 120) {
            safe = safe.substring(safe.length() - 120);
        }
        return "legacy/" + record.resourceType() + "-" + record.resourceId() + "-" + safe;
    }

    private static String categoryDirectory(OwnedStorageResolver.Category category) {
        return switch (category) {
            case DOCUMENTS -> "documents";
            case EMAIL_ATTACHMENTS -> "email-attachments";
            case SCHEDULES -> "schedules";
        };
    }

    private static String checksum(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record MigrationSummary(int discovered, int completed) {
    }
}
