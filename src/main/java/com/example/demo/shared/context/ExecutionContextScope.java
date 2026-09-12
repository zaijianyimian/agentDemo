package com.example.demo.shared.context;

import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;

/** Local bridge for libraries requiring thread state. Explicit context remains authoritative. */
public final class ExecutionContextScope implements AutoCloseable {
    private static final ThreadLocal<ExecutionContext> LOCAL = new ThreadLocal<>();
    private final ExecutionContext previous;
    private final Map<String, String> previousMdc;
    private final Thread owner;
    private boolean closed;

    private ExecutionContextScope(ExecutionContext context) {
        Objects.requireNonNull(context, "Execution context required");
        owner = Thread.currentThread();
        previous = LOCAL.get();
        previousMdc = MDC.getCopyOfContextMap();
        LOCAL.set(context);
        MDC.put("userId", Long.toString(context.user().userId()));
        MDC.put("executionId", context.executionId().toString());
        MDC.put("traceId", context.traceId().toString());
    }

    public static ExecutionContextScope open(ExecutionContext context) {
        return new ExecutionContextScope(context);
    }

    public static ExecutionContext requireCurrent() {
        ExecutionContext context = LOCAL.get();
        if (context == null) {
            throw new IllegalStateException("Execution context required");
        }
        return context;
    }

    public static Runnable wrap(ExecutionContext context, Runnable action) {
        Objects.requireNonNull(context, "Execution context required");
        Objects.requireNonNull(action, "Action required");
        return () -> {
            try (var ignored = open(context)) {
                action.run();
            }
        };
    }

    public static <T> Callable<T> wrapCallable(ExecutionContext context, Callable<T> action) {
        Objects.requireNonNull(context, "Execution context required");
        Objects.requireNonNull(action, "Action required");
        return () -> {
            try (var ignored = open(context)) {
                return action.call();
            }
        };
    }

    public static <T, R> Function<T, R> wrapFunction(ExecutionContext context, Function<T, R> action) {
        Objects.requireNonNull(context, "Execution context required");
        Objects.requireNonNull(action, "Action required");
        return input -> {
            try (var ignored = open(context)) {
                return action.apply(input);
            }
        };
    }

    @Override
    public void close() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Context scope must close on its opening thread");
        }
        if (closed) return;
        closed = true;
        if (previous == null) LOCAL.remove(); else LOCAL.set(previous);
        if (previousMdc == null) MDC.clear(); else MDC.setContextMap(previousMdc);
    }
}
