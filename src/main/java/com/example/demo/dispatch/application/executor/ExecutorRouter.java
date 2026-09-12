package com.example.demo.dispatch.application.executor;

import com.example.demo.dispatch.domain.DispatchedTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Python 执行请求中的 executor 名称映射到已注册的 Java 进程执行器。
 * 本类不根据任务语义选择执行器，也不提供 fallback 决策。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorRouter {

    private final List<Executor> executors;

    /**
     * @return 与名称精确匹配的执行器；不存在则抛异常。
     */
    public Executor pick(String executorName) {
        if (executorName == null) {
            throw new Executor.ExecutorUnavailableException("executor is null");
        }
        for (Executor e : executors) {
            if (executorName.equals(e.hint())) {
                return e;
            }
        }
        throw new Executor.ExecutorUnavailableException("no executor registered with name: " + executorName);
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
