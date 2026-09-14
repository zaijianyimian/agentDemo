package com.example.demo.shared.context;

/** Immutable authentication generation bound to a long-lived HTTP subscription. */
public record UserSessionSnapshot(long userId, int tokenVersion) {
    public UserSessionSnapshot {
        if (userId <= 0 || tokenVersion < 0) {
            throw new IllegalArgumentException("Invalid user session snapshot");
        }
    }
}
