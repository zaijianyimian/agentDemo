package com.example.demo.shared.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** Canonical versioned envelope for every Java event that belongs to a user resource. */
public record UserEventEnvelope<T>(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("user_id") long userId,
        @JsonProperty("occurred_at") LocalDateTime occurredAt,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("resource_type") String resourceType,
        @JsonProperty("resource_id") String resourceId,
        T payload) {

    public UserEventEnvelope {
        if (eventId == null || eventId.isBlank() || schemaVersion <= 0 || userId <= 0
                || occurredAt == null || eventType == null || eventType.isBlank()
                || resourceType == null || resourceType.isBlank()
                || resourceId == null || resourceId.isBlank() || payload == null) {
            throw new IllegalArgumentException("Invalid user event envelope");
        }
    }
}
