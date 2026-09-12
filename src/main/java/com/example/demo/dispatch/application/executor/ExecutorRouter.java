package com.example.demo.dispatch.application.executor;

import com.example.demo.dispatch.domain.DispatchedTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据 hint 选执行器。
 *
 * <p>两个 hint 之间互为 fallback：
 * <ul>
 *   <li>{@code claude-code} ↔ {@code codex}</li>
 * </ul>
 * 如果两个 hint 都不可用，抛 {@link Executor.ExecutorUnavailableException}，
 * 让 dispatcher 进入 {@code decision-layer-self} 阶段。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorRouter {

    private final List<Executor> executors;

    /**
     * @return 与 hint 匹配的执行器；不存在则抛异常
     */
    public Executor pick(String hint) {
        if (hint == null) {
            throw new Executor.ExecutorUnavailableException("executor_hint is null");
        }
        for (Executor e : executors) {
            if (hint.equals(e.hint())) {
                return e;
            }
        }
        throw new Executor.ExecutorUnavailableException("no executor registered for hint: " + hint);
    }

    /**
     * 选 fallback 执行器（与 hint 不同）。
     */
    public Executor pickFallback(String primaryHint) {
        for (Executor e : executors) {
            if (!primaryHint.equals(e.hint()) && e.isAvailable()) {
                return e;
            }
        }
        throw new Executor.ExecutorUnavailableException("no fallback executor available");
    }

    /**
     * 是否还有任何执行器可用。本方法只反映 PATH 上能找到 CLI 的情况。
     */
    public boolean anyAvailable() {
        return executors.stream().anyMatch(Executor::isAvailable);
    }

    /**
     * 快照所有注册执行器的可用性，便于控制器返回给前端。
     */
    public Map<String, Boolean> availabilitySnapshot() {
        Map<String, Boolean> snap = new HashMap<>();
        for (Executor e : executors) {
            snap.put(e.hint(), e.isAvailable());
        }
        return snap;
    }
}
