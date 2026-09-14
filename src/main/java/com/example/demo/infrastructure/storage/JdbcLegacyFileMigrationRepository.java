package com.example.demo.infrastructure.storage;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

@Repository
class JdbcLegacyFileMigrationRepository implements LegacyFileMigrationRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    JdbcLegacyFileMigrationRepository(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public void requireActiveOwner(long ownerUserId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE id = ? AND enabled = 1",
                Integer.class, ownerUserId);
        if (ownerUserId <= 0 || count == null || count != 1) {
            throw new IllegalStateException("LEGACY_OWNER_USER_ID must identify one enabled user");
        }
    }

    @Override
    public List<LegacyFileRecord> findPending(long ownerUserId) {
        List<LegacyFileRecord> pending = new ArrayList<>();
        pending.addAll(find("document", OwnedStorageResolver.Category.DOCUMENTS, ownerUserId));
        pending.addAll(find("schedule_event", OwnedStorageResolver.Category.SCHEDULES, ownerUserId));
        return pending;
    }

    private List<LegacyFileRecord> find(
            String table, OwnedStorageResolver.Category category, long ownerUserId) {
        return jdbc.query(
                "SELECT id, file_path FROM " + table
                        + " WHERE user_id = ? AND file_path IS NOT NULL AND file_path <> '' ORDER BY id",
                (resultSet, rowNum) -> new LegacyFileRecord(
                        table, resultSet.getLong("id"), resultSet.getString("file_path"), category),
                ownerUserId);
    }

    @Override
    public void start(long ownerUserId, LegacyFileRecord record, String storageKey, String checksum) {
        jdbc.update("""
                INSERT INTO file_migration_journal
                    (owner_user_id, resource_type, resource_id, source_path, category, storage_key,
                     checksum, status, attempt_count, started_at, completed_at, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'RUNNING', 1, NOW(), NULL, NULL)
                ON DUPLICATE KEY UPDATE
                    source_path = VALUES(source_path), category = VALUES(category),
                    storage_key = VALUES(storage_key), checksum = VALUES(checksum),
                    status = IF(status = 'COMPLETED', status, 'RUNNING'),
                    attempt_count = attempt_count + 1, started_at = NOW(), details = NULL
                """, ownerUserId, record.resourceType(), record.resourceId(), record.filePath(),
                record.category().name(), storageKey, checksum);
    }

    @Override
    public void markCopied(
            long ownerUserId, LegacyFileRecord record, String storageKey, String checksum) {
        transactions.executeWithoutResult(ignored -> {
            int updated = jdbc.update("UPDATE " + table(record.resourceType())
                            + " SET storage_key = ? WHERE id = ? AND user_id = ?"
                            + " AND (storage_key IS NULL OR storage_key = ?)",
                    storageKey, record.resourceId(), ownerUserId, storageKey);
            if (updated != 1) {
                throw new IllegalStateException("legacy resource owner/storage changed during migration");
            }
            updateJournal(ownerUserId, record, storageKey, checksum, "COPIED", null, false);
        });
    }

    @Override
    public void complete(
            long ownerUserId, LegacyFileRecord record, String storageKey, String checksum) {
        transactions.executeWithoutResult(ignored -> {
            int updated = jdbc.update("UPDATE " + table(record.resourceType())
                            + " SET file_path = NULL WHERE id = ? AND user_id = ? AND storage_key = ?",
                    record.resourceId(), ownerUserId, storageKey);
            if (updated != 1) {
                throw new IllegalStateException("legacy resource could not be finalized");
            }
            updateJournal(ownerUserId, record, storageKey, checksum, "COMPLETED", null, true);
        });
    }

    @Override
    public void fail(long ownerUserId, LegacyFileRecord record, String storageKey,
                     String checksum, String details) {
        updateJournal(ownerUserId, record, storageKey, checksum, "FAILED",
                details == null ? "unknown failure" : details.substring(0, Math.min(details.length(), 900)), false);
    }

    private void updateJournal(long ownerUserId, LegacyFileRecord record, String storageKey,
                               String checksum, String status, String details, boolean completed) {
        jdbc.update("UPDATE file_migration_journal SET status = ?, checksum = ?, details = ?, "
                        + "completed_at = " + (completed ? "NOW()" : "NULL")
                        + " WHERE owner_user_id = ? AND resource_type = ? AND resource_id = ? AND storage_key = ?",
                status, checksum, details, ownerUserId, record.resourceType(), record.resourceId(), storageKey);
    }

    private static String table(String resourceType) {
        return switch (resourceType) {
            case "document" -> "document";
            case "schedule_event" -> "schedule_event";
            default -> throw new IllegalArgumentException("Unsupported legacy resource: " + resourceType);
        };
    }
}
