package com.example.demo.shared.context;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** In-process event metadata; consumers must revalidate referenced resource ownership. */
public record EventContext(ExecutionContext execution, UUID eventId, String eventType,
                           int schemaVersion, Instant occurredAt, UUID causationId) {
    public EventContext {
        Objects.requireNonNull(execution, "Execution context required");
        Objects.requireNonNull(eventId, "Event ID required");
        Objects.requireNonNull(occurredAt, "Occurrence time required");
        if (eventType == null || eventType.isBlank() || schemaVersion < 1) {
            throw new IllegalArgumentException("Invalid event metadata");
        }
    }
}
