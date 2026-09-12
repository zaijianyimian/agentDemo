package com.example.demo.shared.context;

import reactor.core.publisher.Flux;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.Objects;
import java.util.function.Function;

/** Subscriber-local identity. A thread scope is opened only around synchronous callbacks. */
public final class ReactiveExecutionContext {
    private ReactiveExecutionContext() {}

    public static Context attach(Context context, ExecutionContext execution) {
        return context.put(ExecutionContext.class, Objects.requireNonNull(execution));
    }

    public static ExecutionContext require(ContextView context) {
        return context.<ExecutionContext>getOrEmpty(ExecutionContext.class)
                .orElseThrow(() -> new IllegalStateException("Execution context required"));
    }

    public static <T> Flux<T> withContext(ExecutionContext context,
                                        Function<ExecutionContext, Flux<T>> action) {
        Objects.requireNonNull(context, "Execution context required");
        Objects.requireNonNull(action, "Action required");
        return Flux.deferContextual(view -> {
            ExecutionContext execution = require(view);
            try (var ignored = ExecutionContextScope.open(execution)) {
                return action.apply(execution);
            }
        }).contextWrite(view -> attach(view, context));
    }
}
