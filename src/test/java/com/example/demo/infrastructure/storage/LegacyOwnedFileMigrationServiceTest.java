package com.example.demo.infrastructure.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyOwnedFileMigrationServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void interruptedCopyCanBeRerunWithoutDuplicateOrLoss() throws Exception {
        Path legacyBase = Files.createDirectories(temporaryDirectory.resolve("legacy-base"));
        Path source = legacyBase.resolve("data/documents/report.txt");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "owned-content");
        var record = new LegacyFileMigrationRepository.LegacyFileRecord(
                "document", 41L, "data/documents/report.txt", OwnedStorageResolver.Category.DOCUMENTS);
        InMemoryRepository repository = new InMemoryRepository(record);
        repository.failFirstCopyCommit = true;
        LegacyOwnedFileMigrationService service = new LegacyOwnedFileMigrationService(
                repository, temporaryDirectory.resolve("users").toString());

        assertThrows(IllegalStateException.class, () -> service.migrate(7L, legacyBase));
        assertThat(source).exists();
        Path target = temporaryDirectory.resolve(
                "users/7/documents/legacy/document-41-report.txt");
        assertThat(target).hasContent("owned-content");

        LegacyOwnedFileMigrationService.MigrationSummary summary = service.migrate(7L, legacyBase);

        assertThat(summary.completed()).isEqualTo(1);
        assertThat(repository.starts).isEqualTo(2);
        assertThat(repository.completed).isTrue();
        assertThat(repository.storageKey).isEqualTo("legacy/document-41-report.txt");
        assertThat(source).doesNotExist();
        assertThat(target).hasContent("owned-content");
        assertThat(Files.walk(target.getParent()).filter(Files::isRegularFile).count()).isEqualTo(1);
    }

    @Test
    void rejectsTraversalBeforeReadingAFile() throws Exception {
        Path legacyBase = Files.createDirectories(temporaryDirectory.resolve("legacy-base"));
        Path outside = temporaryDirectory.resolve("secret.txt");
        Files.writeString(outside, "secret");
        var record = new LegacyFileMigrationRepository.LegacyFileRecord(
                "schedule_event", 9L, "../secret.txt", OwnedStorageResolver.Category.SCHEDULES);
        InMemoryRepository repository = new InMemoryRepository(record);
        LegacyOwnedFileMigrationService service = new LegacyOwnedFileMigrationService(
                repository, temporaryDirectory.resolve("users").toString());

        assertThrows(AccessDeniedException.class, () -> service.migrate(7L, legacyBase));

        assertThat(outside).hasContent("secret");
        assertThat(repository.starts).isZero();
    }

    @Test
    void rejectsSymlinkedLegacySourceWithoutTouchingAnotherOwnersFiles() throws Exception {
        Path legacyBase = Files.createDirectories(temporaryDirectory.resolve("legacy-base"));
        Path outside = temporaryDirectory.resolve("outside-secret.txt");
        Files.writeString(outside, "secret");
        Path linkedSource = legacyBase.resolve("data/documents/linked.txt");
        Files.createDirectories(linkedSource.getParent());
        Files.createSymbolicLink(linkedSource, outside);
        Path otherOwnerFile = temporaryDirectory.resolve("users/8/documents/keep.txt");
        Files.createDirectories(otherOwnerFile.getParent());
        Files.writeString(otherOwnerFile, "owner-b");
        var record = new LegacyFileMigrationRepository.LegacyFileRecord(
                "document", 12L, "data/documents/linked.txt", OwnedStorageResolver.Category.DOCUMENTS);
        InMemoryRepository repository = new InMemoryRepository(record);
        LegacyOwnedFileMigrationService service = new LegacyOwnedFileMigrationService(
                repository, temporaryDirectory.resolve("users").toString());

        assertThrows(AccessDeniedException.class, () -> service.migrate(7L, legacyBase));

        assertThat(outside).hasContent("secret");
        assertThat(otherOwnerFile).hasContent("owner-b");
        assertThat(repository.starts).isZero();
    }

    @Test
    void unreferencedFileBlocksCutoverAndNoSiblingOwnerDirectoryIsCreated() throws Exception {
        Path legacyBase = Files.createDirectories(temporaryDirectory.resolve("legacy-base"));
        Path legacyDocuments = Files.createDirectories(legacyBase.resolve("data/documents"));
        Files.writeString(legacyDocuments.resolve("known.txt"), "known");
        Files.writeString(legacyDocuments.resolve("orphan.txt"), "orphan");
        var record = new LegacyFileMigrationRepository.LegacyFileRecord(
                "document", 41L, "data/documents/known.txt", OwnedStorageResolver.Category.DOCUMENTS);
        InMemoryRepository repository = new InMemoryRepository(record);
        LegacyOwnedFileMigrationService service = new LegacyOwnedFileMigrationService(
                repository, temporaryDirectory.resolve("users").toString());

        IOException error = assertThrows(IOException.class,
                () -> service.migrate(7L, legacyBase, List.of(Path.of("data/documents"))));

        assertThat(error).hasMessageContaining("unreferenced legacy file blocks cutover");
        assertThat(temporaryDirectory.resolve("users/8")).doesNotExist();
        assertThat(legacyDocuments.resolve("orphan.txt")).hasContent("orphan");
    }

    private static final class InMemoryRepository implements LegacyFileMigrationRepository {
        private final LegacyFileRecord record;
        private boolean failFirstCopyCommit;
        private int starts;
        private boolean completed;
        private String storageKey;

        private InMemoryRepository(LegacyFileRecord record) {
            this.record = record;
        }

        @Override
        public void requireActiveOwner(long ownerUserId) {
            if (ownerUserId != 7L) throw new IllegalStateException("inactive owner");
        }

        @Override
        public List<LegacyFileRecord> findPending(long ownerUserId) {
            return completed ? List.of() : List.of(record);
        }

        @Override
        public void start(long ownerUserId, LegacyFileRecord record, String storageKey, String checksum) {
            starts++;
        }

        @Override
        public void markCopied(long ownerUserId, LegacyFileRecord record, String storageKey, String checksum) {
            if (failFirstCopyCommit) {
                failFirstCopyCommit = false;
                throw new IllegalStateException("simulated interruption");
            }
            this.storageKey = storageKey;
        }

        @Override
        public void complete(long ownerUserId, LegacyFileRecord record, String storageKey, String checksum) {
            completed = true;
        }

        @Override
        public void fail(long ownerUserId, LegacyFileRecord record, String storageKey,
                         String checksum, String details) {
            // Journal remains retryable.
        }
    }
}
