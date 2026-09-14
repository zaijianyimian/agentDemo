package com.example.demo.dispatch.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** Versioned owner envelope published from the durable dispatch outbox. */
public record DispatchResultEvent(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("schema_version") int schemaVersion,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("occurred_at") LocalDateTime occurredAt,
        @JsonProperty("resource_type") String resourceType,
        @JsonProperty("resource_id") Long resourceId,
        @JsonProperty("request_id") String requestId,
        int attempt,
        String status,
        String executor,
        @JsonProperty("retry_count") Integer retryCount,
        String result,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("error_message") String errorMessage) {
}
