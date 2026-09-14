package com.example.demo.dispatch.domain;

/** Minimal cross-user relay scan result. */
public record OwnedOutboxRef(long userId, long outboxId) {
    public OwnedOutboxRef {
        if (userId <= 0 || outboxId <= 0) {
            throw new IllegalArgumentException("Invalid owned outbox reference");
        }
    }
}
