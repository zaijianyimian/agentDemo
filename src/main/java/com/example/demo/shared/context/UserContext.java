package com.example.demo.shared.context;

/** Resource ownership, established by authentication or a trusted persisted resource. */
public record UserContext(long userId) {
    public UserContext {
        if (userId <= 0) {
            throw new IllegalArgumentException("Invalid user identity");
        }
    }
}
