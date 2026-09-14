package com.example.demo.infrastructure.storage;

import com.example.demo.shared.context.CurrentUserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Resolves relative storage keys inside data/users/{userId}/{category} without following links. */
@Component
public class OwnedStorageResolver {
    public enum Category {
        DOCUMENTS("documents"),
        EMAIL_ATTACHMENTS("email-attachments"),
        SCHEDULES("schedules");

        private final String directory;

        Category(String directory) {
            this.directory = directory;
        }
    }

    private final CurrentUserContext currentUser;
    private final Path usersRoot;

    public OwnedStorageResolver(CurrentUserContext currentUser,
                                @Value("${app.storage.root:./data/users}") String usersRoot) {
        this.currentUser = currentUser;
        this.usersRoot = Path.of(usersRoot).toAbsolutePath().normalize();
    }

    public Path resolveForCreate(long userId, Category category, String storageKey) throws IOException {
        Path categoryRoot = resolveCategory(userId, category, true);
        Path target = resolveRelative(categoryRoot, storageKey);
        Path parent = target.getParent();
        createDirectoriesWithoutLinks(categoryRoot, parent);
        ensureRealPathInside(categoryRoot, parent);
        return target;
    }

    public Path resolveExisting(long userId, Category category, String storageKey) throws IOException {
        Path categoryRoot = resolveCategory(userId, category, false);
        Path target = resolveRelative(categoryRoot, storageKey);
        rejectSymlinks(usersRoot, target);
        Path realTarget = target.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path realRoot = categoryRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realTarget.startsWith(realRoot)) {
            throw new AccessDeniedException("Storage key escapes the owned category");
        }
        return realTarget;
    }

    public Path resolveCategory(long userId, Category category, boolean create) throws IOException {
        Path categoryRoot = categoryRoot(userId, category);
        if (create) {
            createDirectoriesWithoutLinks(usersRoot, categoryRoot);
        } else {
            rejectSymlinks(usersRoot, categoryRoot);
            Path realRoot = categoryRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path realUsersRoot = usersRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realRoot.startsWith(realUsersRoot)) {
                throw new AccessDeniedException("Storage category escapes the user root");
            }
        }
        return categoryRoot;
    }

    private Path categoryRoot(long userId, Category category) {
        long trustedUserId = currentUser.requireUserId();
        if (userId <= 0 || trustedUserId != userId) {
            throw new AccessDeniedException("Storage owner does not match execution context");
        }
        Path root = usersRoot.resolve(Long.toString(userId)).resolve(category.directory).normalize();
        if (!root.startsWith(usersRoot)) {
            throw new AccessDeniedException("Invalid storage owner");
        }
        return root;
    }

    private static Path resolveRelative(Path categoryRoot, String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("\\")) {
            throw new IllegalArgumentException("Storage key must be a non-empty portable relative path");
        }
        Path relative = Path.of(storageKey);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Absolute storage keys are forbidden");
        }
        for (Path component : relative) {
            String value = component.toString();
            if ("..".equals(value) || ".".equals(value) || value.isBlank()) {
                throw new IllegalArgumentException("Storage key contains an invalid segment");
            }
        }
        Path target = categoryRoot.resolve(relative).normalize();
        if (!target.startsWith(categoryRoot)) {
            throw new AccessDeniedException("Storage key escapes the owned category");
        }
        return target;
    }

    private static void rejectSymlinks(Path trustedRoot, Path target) throws IOException {
        Path normalizedRoot = trustedRoot.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new AccessDeniedException("Storage path escapes configured root");
        }
        Path cursor = normalizedRoot;
        if (Files.isSymbolicLink(cursor)) {
            throw new AccessDeniedException("Symbolic links are forbidden in owned storage");
        }
        for (Path component : normalizedRoot.relativize(normalizedTarget)) {
            cursor = cursor.resolve(component);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(cursor)) {
                throw new AccessDeniedException("Symbolic links are forbidden in owned storage");
            }
        }
    }

    private static void createDirectoriesWithoutLinks(Path trustedRoot, Path target) throws IOException {
        Path normalizedRoot = trustedRoot.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!normalizedTarget.startsWith(normalizedRoot)) {
            throw new AccessDeniedException("Storage path escapes configured root");
        }
        Files.createDirectories(normalizedRoot);
        if (Files.isSymbolicLink(normalizedRoot)) {
            throw new AccessDeniedException("Symbolic links are forbidden in owned storage");
        }
        Path cursor = normalizedRoot;
        for (Path component : normalizedRoot.relativize(normalizedTarget)) {
            cursor = cursor.resolve(component);
            if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(cursor) || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) {
                    throw new AccessDeniedException("Owned storage parent is not a safe directory");
                }
            } else {
                Files.createDirectory(cursor);
            }
        }
    }

    private static void ensureRealPathInside(Path categoryRoot, Path existingPath) throws IOException {
        Path realRoot = categoryRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path realPath = existingPath.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realPath.startsWith(realRoot)) {
            throw new AccessDeniedException("Storage path escapes the owned category");
        }
    }
}
