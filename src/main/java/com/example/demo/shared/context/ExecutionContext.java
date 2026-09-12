package com.example.demo.shared.context;

import java.util.Objects;
import java.util.UUID;

/** Immutable context passed explicitly between application services. */
public record ExecutionContext(UserContext user, UUID executionId, UUID traceId,
                               String trigger, Actor actor, ExecutionPolicy policy) {
    public enum Actor { USER, SYSTEM, WORKER }

    public ExecutionContext {
        Objects.requireNonNull(user, "User context required");
        Objects.requireNonNull(executionId, "Execution ID required");
        Objects.requireNonNull(traceId, "Trace ID required");
        Objects.requireNonNull(actor, "Actor required");
        Objects.requireNonNull(policy, "Execution policy required");
        if (trigger == null || trigger.isBlank()) {
            throw new IllegalArgumentException("Trigger required");
        }
    }

    public static ExecutionContext start(UserContext user, String trigger, Actor actor, ExecutionPolicy policy) {
        return new ExecutionContext(user, UUID.randomUUID(), UUID.randomUUID(), trigger, actor, policy);
    }

    public ExecutionContext nextAttempt(UUID executionId) {
        return new ExecutionContext(user, executionId, traceId, trigger, Actor.WORKER, policy);
    }
}
