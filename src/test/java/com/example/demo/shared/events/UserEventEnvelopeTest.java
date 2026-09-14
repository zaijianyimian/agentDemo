package com.example.demo.shared.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserEventEnvelopeTest {

    @Test
    void serializesAndReadsCanonicalOwnerFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        UserEventEnvelope<Map<String, String>> envelope = new UserEventEnvelope<>(
                "event-1", 1, 7L, LocalDateTime.parse("2026-09-13T12:00:00"),
                "updated", "schedule_event", "11", Map.of("title", "mine"));

        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(envelope));

        assertThat(json.get("event_id").asText()).isEqualTo("event-1");
        assertThat(json.get("schema_version").asInt()).isEqualTo(1);
        assertThat(json.get("user_id").asLong()).isEqualTo(7L);
        assertThat(json.get("resource_type").asText()).isEqualTo("schedule_event");
        assertThat(json.get("payload").get("title").asText()).isEqualTo("mine");
    }
}
