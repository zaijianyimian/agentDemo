package com.example.demo.infrastructure.storage;

import java.util.List;

interface LegacyFileMigrationRepository {

    record LegacyFileRecord(String resourceType, long resourceId, String filePath,
                            OwnedStorageResolver.Category category) {
    }

    void requireActiveOwner(long ownerUserId);

    List<LegacyFileRecord> findPending(long ownerUserId);

    void start(long ownerUserId, LegacyFileRecord record, String storageKey, String checksum);

    void markCopied(long ownerUserId, LegacyFileRecord record, String storageKey, String checksum);

    void complete(long ownerUserId, LegacyFileRecord record, String storageKey, String checksum);

    void fail(long ownerUserId, LegacyFileRecord record, String storageKey, String checksum, String details);
}
