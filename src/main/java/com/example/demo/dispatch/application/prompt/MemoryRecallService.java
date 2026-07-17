package com.example.demo.dispatch.application.prompt;

import com.example.demo.memory.application.MemoryApplicationService;
import com.example.demo.dispatch.application.DispatchProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 对外暴露的 Qdrant topK 召回封装。带超时降级。
 *
 * <p>召回线程池由 {@link com.example.demo.dispatch.application.DispatchExecutors}
 * 统一提供并由 Spring 容器负责 shutdown。</p>
 */
@Slf4j
@Service
public class MemoryRecallService {

    private final MemoryApplicationService memoryApplicationService;
    private final DispatchProperties properties;
    private final ExecutorService recallExecutor;

    public MemoryRecallService(MemoryApplicationService memoryApplicationService,
                               DispatchProperties properties,
                               @Qualifier("dispatchRecallExecutor") ExecutorService recallExecutor) {
        this.memoryApplicationService = memoryApplicationService;
        this.properties = properties;
        this.recallExecutor = recallExecutor;
    }

    /**
     * 异步调用 Qdrant topK 召回，带超时降级 —— 超时或失败返回空列表而不阻塞派发主链。
     */
    public List<Map<String, Object>> recallForTask(String subject, int topK) {
        if (subject == null || subject.isBlank()) {
            return Collections.emptyList();
        }
        Future<List<Map<String, Object>>> future = recallExecutor.submit(
                () -> memoryApplicationService.recall(subject, topK));
        try {
            return future.get(properties.getRecallTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("qdrant recall timed out after {}s, degrading to empty list", properties.getRecallTimeoutSeconds());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("qdrant recall failed, degrading to empty list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}