package com.example.demo.shared.context;

/** Shared authentication port for long-lived connections without retaining bearer tokens. */
public interface UserSessionValidator {

    UserSessionSnapshot captureAuthenticatedSession();

    boolean isActive(UserSessionSnapshot session);
}
