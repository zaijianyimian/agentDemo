package com.example.demo.dispatch.domain;

/** Minimal cross-user scan result. It authorizes no work until the persisted owner is restored. */
public record OwnedTaskRef(long userId, long taskId, long version, int attempt) {
    public OwnedTaskRef {
        if (userId <= 0 || taskId <= 0 || version < 0 || attempt < 0) {
            throw new IllegalArgumentException("Invalid owned task reference");
        }
    }
}
