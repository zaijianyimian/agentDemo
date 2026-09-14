package com.example.demo.dispatch.application;

import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.dispatch.persistence.ConsumedEventMapper;
import com.example.demo.email.domain.OwnedEmailConfigRef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Transactional idempotency boundary for the Python-to-Java execution request consumer. */
@Service
public class ConsumedExecutionRequestService {

    static final String CONSUMER_NAME = "java-dispatch-execution-request";

    private final DispatchedTaskService taskService;
    private final ConsumedEventMapper consumedEvents;
    private final ObjectMapper objectMapper;

    public ConsumedExecutionRequestService(
            DispatchedTaskService taskService,
            ConsumedEventMapper consumedEvents,
            ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.consumedEvents = consumedEvents;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DispatchedTask consume(
            OwnedEmailConfigRef source,
            ExecutionRequestListener.ExecutionRequest request) {
        int acquired = consumedEvents.tryAcquire(
                source.userId(), CONSUMER_NAME, request.requestId(), "email_config",
                Long.toString(source.configId()));
        if (acquired == 0) {
            return null;
        }
        DispatchedTask task = DispatchedTask.builder()
                .requestId(request.requestId())
                .emailId(source.configId())
                .emailUid(request.emailUid())
                .subject(request.subject())
                .bodyExcerpt(request.bodyExcerpt())
                .importance(request.importance())
                .executor(request.executor())
                .executionInstruction(request.executionInstruction())
                .retryMax(request.retryMax())
                .executorTimeoutSeconds(request.executorTimeoutSeconds())
                .sandboxLevel(request.sandboxLevel())
                .toolAllowlist(toJson(request.toolAllowlist()))
                .status(DispatchedTask.STATUS_PENDING)
                .pushStatus(DispatchedTask.PUSH_PENDING)
                .retries(0)
                .attempt(0)
                .version(0L)
                .build();
        DispatchedTask persisted = taskService.create(task);
        if (consumedEvents.markCommitted(
                source.userId(), CONSUMER_NAME, request.requestId(), "dispatched_task",
                Long.toString(persisted.getId())) != 1) {
            throw new IllegalStateException("execution request idempotency commit failed");
        }
        return persisted;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(OwnedEmailConfigRef source, String eventId, RuntimeException error) {
        consumedEvents.recordFailure(
                source.userId(), CONSUMER_NAME, eventId, "email_config",
                Long.toString(source.configId()), limit(error.getMessage(), 2000));
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("tool_allowlist must be a JSON string list", error);
        }
    }

    private String limit(String value, int maxLength) {
        String safe = value == null ? "consumer failure" : value;
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }
}
