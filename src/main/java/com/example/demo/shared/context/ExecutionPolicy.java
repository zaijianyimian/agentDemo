package com.example.demo.shared.context;

import java.util.Set;

/** Server-selected capabilities. Never deserialize this value from model/tool input. */
public record ExecutionPolicy(Set<String> allowedTools, boolean allowFileWrite, boolean allowNetwork) {
    public ExecutionPolicy {
        allowedTools = Set.copyOf(allowedTools);
    }

    public static ExecutionPolicy readOnly() {
        return new ExecutionPolicy(Set.of(), false, false);
    }
}
