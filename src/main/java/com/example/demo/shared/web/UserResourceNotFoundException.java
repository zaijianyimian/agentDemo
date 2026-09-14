package com.example.demo.shared.web;

/** Deliberately conflates an absent resource with a resource owned by another user. */
public final class UserResourceNotFoundException extends RuntimeException {
    public UserResourceNotFoundException(String message) {
        super(message);
    }
}
