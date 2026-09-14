package com.example.demo.infrastructure.storage;

import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.context.UserContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnedStorageResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void sameStorageKeyIsIsolatedByOwnerAndSupportsLifecycle() throws Exception {
        AtomicLong owner = new AtomicLong(101L);
        OwnedStorageResolver resolver = resolver(owner);
        Path a = resolver.resolveForCreate(101L, OwnedStorageResolver.Category.DOCUMENTS, "same.txt");
        Files.writeString(a, "A");

        owner.set(202L);
        Path b = resolver.resolveForCreate(202L, OwnedStorageResolver.Category.DOCUMENTS, "same.txt");
        Files.writeString(b, "B");

        owner.set(101L);
        Path existing = resolver.resolveExisting(101L, OwnedStorageResolver.Category.DOCUMENTS, "same.txt");
        assertThat(Files.readString(existing)).isEqualTo("A");
        Files.writeString(existing, "A2");
        assertThat(Files.deleteIfExists(existing)).isTrue();
        assertThat(Files.readString(b)).isEqualTo("B");
        assertThrows(AccessDeniedException.class,
                () -> resolver.resolveExisting(202L, OwnedStorageResolver.Category.DOCUMENTS, "same.txt"));
    }

    @Test
    void rejectsAbsoluteTraversalAndBackslashKeys() {
        AtomicLong owner = new AtomicLong(101L);
        OwnedStorageResolver resolver = resolver(owner);

        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveForCreate(101L, OwnedStorageResolver.Category.DOCUMENTS, "/tmp/x"));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveForCreate(101L, OwnedStorageResolver.Category.DOCUMENTS, "../202/x"));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolveForCreate(101L, OwnedStorageResolver.Category.DOCUMENTS, "..\\202\\x"));
    }

    @Test
    void rejectsSymlinkedParentsBeforeWritingOutsideOwnerRoot() throws Exception {
        AtomicLong owner = new AtomicLong(101L);
        OwnedStorageResolver resolver = resolver(owner);
        resolver.resolveForCreate(101L, OwnedStorageResolver.Category.DOCUMENTS, "safe.txt");
        Path outside = Files.createDirectory(temporaryDirectory.resolve("outside"));
        Path link = temporaryDirectory.resolve("users/101/documents/link");
        Files.createSymbolicLink(link, outside);

        assertThrows(AccessDeniedException.class,
                () -> resolver.resolveForCreate(101L, OwnedStorageResolver.Category.DOCUMENTS, "link/escape.txt"));
        assertThat(outside.resolve("escape.txt")).doesNotExist();
    }

    private OwnedStorageResolver resolver(AtomicLong owner) {
        CurrentUserContext context = () -> Optional.of(new UserContext(owner.get()));
        return new OwnedStorageResolver(context, temporaryDirectory.resolve("users").toString());
    }
}
