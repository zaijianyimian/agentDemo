package com.example.demo.email.domain;

/** Minimal owner-bearing reference returned only by trusted background validation. */
public record OwnedEmailConfigRef(long userId, long configId) {
    public OwnedEmailConfigRef {
        if (userId <= 0 || configId <= 0) {
            throw new IllegalArgumentException("Invalid owned email configuration reference");
        }
    }
}
