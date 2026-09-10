package com.example.demo.dispatch.application.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** LEGACY 模式下根据 hint 选择本地执行器。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class ExecutorRouter {

    private final List<Executor> executors;

    public Executor pick(String hint) {
        if (hint == null) {
            throw new Executor.ExecutorUnavailableException("executor_hint is null");
        }
        for (Executor executor : executors) {
            if (hint.equals(executor.hint())) {
                return executor;
            }
        }
        throw new Executor.ExecutorUnavailableException("no executor registered for hint: " + hint);
    }

    public Executor pickFallback(String primaryHint) {
        for (Executor executor : executors) {
            if (!primaryHint.equals(executor.hint())) {
                return executor;
            }
        }
        throw new Executor.ExecutorUnavailableException("no fallback executor available");
    }

    public boolean anyAvailable() {
        return executors.stream().anyMatch(Executor::isAvailable);
    }

    public Map<String, Boolean> availabilitySnapshot() {
        Map<String, Boolean> snapshot = new HashMap<>();
        for (Executor executor : executors) {
            snapshot.put(executor.hint(), executor.isAvailable());
        }
        return snapshot;
    }
}
