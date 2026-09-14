package com.example.demo.dispatch.application;

import com.example.demo.auth.application.ExecutionContextFactory;
import com.example.demo.dispatch.domain.DispatchResultEvent;
import com.example.demo.dispatch.domain.DispatchResultOutbox;
import com.example.demo.dispatch.persistence.DispatchResultOutboxMapper;
import com.example.demo.shared.context.ExecutionContextScope;
import com.example.demo.shared.context.ExecutionPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Relays durable terminal-result intents; broker failure never re-runs the task. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionResultPublisher {
    private static final int BATCH_SIZE = 50;
    private static final int LEASE_SECONDS = 30;

    private final RabbitTemplate rabbitTemplate;
    private final DispatchProperties properties;
    private final DispatchResultOutboxMapper outboxMapper;
    private final ExecutionContextFactory executionContexts;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.dispatch.result-relay-interval-ms:1000}")
    public void relayPending() {
        if (!properties.isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (var reference : outboxMapper.selectReadyForInternalRelay(now, BATCH_SIZE)) {
            try {
                var context = executionContexts.forPersistedOwner(
                        reference.userId(), "dispatch-result-relay", ExecutionPolicy.readOnly());
                try (var ignored = ExecutionContextScope.open(context)) {
                    relayOne(reference.userId(), reference.outboxId(), now);
                }
            } catch (RuntimeException error) {
                log.warn("Skipping dispatch result {} for invalid owner {}: {}",
                        reference.outboxId(), reference.userId(), error.getMessage());
            }
        }
    }

    void relayOne(long userId, long outboxId, LocalDateTime now) {
        if (outboxMapper.claimLease(userId, outboxId, now, now.plusSeconds(LEASE_SECONDS)) == 0) {
            return;
        }
        DispatchResultOutbox entry = outboxMapper.selectById(outboxId);
        if (entry == null) {
            return;
        }
        try {
            DispatchResultEvent event = objectMapper.readValue(entry.getPayload(), DispatchResultEvent.class);
            if (!entry.getUserId().equals(event.userId()) || !entry.getTaskId().equals(event.resourceId())) {
                throw new IllegalStateException("outbox envelope owner/resource mismatch");
            }
            rabbitTemplate.convertAndSend(properties.getResultQueue(), event);
            outboxMapper.markPublished(userId, outboxId);
        } catch (Exception error) {
            int attempts = entry.getPublishAttempts() == null ? 0 : entry.getPublishAttempts();
            long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
            outboxMapper.markRetry(userId, outboxId, now.plusSeconds(delaySeconds), safeMessage(error));
        }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
