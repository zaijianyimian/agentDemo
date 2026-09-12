package com.example.demo.dispatch.application.prompt;

import com.example.demo.dispatch.application.DispatchProperties;
import com.example.demo.infrastructure.graph.GraphGatewayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 通过 Python Graph 执行上下文召回，Java 不感知底层存储。带超时降级。
 *
 * <p>召回线程池由 {@link com.example.demo.dispatch.application.DispatchExecutors}
 * 统一提供并由 Spring 容器负责 shutdown。</p>
 */
@Slf4j
@Service
public class MemoryRecallService {

    private final GraphGatewayClient graphGatewayClient;
    private final DispatchProperties properties;
    private final ExecutorService recallExecutor;

    public MemoryRecallService(GraphGatewayClient graphGatewayClient,
                               DispatchProperties properties,
                               @Qualifier("dispatchRecallExecutor") ExecutorService recallExecutor) {
        this.graphGatewayClient = graphGatewayClient;
        this.properties = properties;
        this.recallExecutor = recallExecutor;
    }

    /**
     * 异步调用 Graph 上下文召回，带超时降级 —— 超时或失败返回空列表而不阻塞派发主链。
     */
    public List<Map<String, Object>> recallForTask(String subject, int topK) {
        return recallForTask(0L, subject, topK);
    }

    public List<Map<String, Object>> recallForTask(long userId, String subject, int topK) {
        if (subject == null || subject.isBlank()) {
            return Collections.emptyList();
        }
        Future<List<Map<String, Object>>> future = recallExecutor.submit(
                () -> graphGatewayClient.recallMemory(userId, subject, topK));
        try {
            return future.get(properties.getRecallTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("graph recall timed out after {}s, degrading to empty list", properties.getRecallTimeoutSeconds());
            return Collections.emptyList();
        } catch (Exception e) {
            log.warn("graph recall failed, degrading to empty list: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
