package com.example.demo.dispatch.application.prompt;

import com.example.demo.dispatch.application.DispatchProperties;
import com.example.demo.memory.application.MemoryApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** LEGACY dispatch 使用的 Qdrant topK 召回封装。 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "legacy", matchIfMissing = true)
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
