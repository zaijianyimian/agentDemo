package com.example.demo.dispatch.application.fallback;

import com.example.demo.dispatch.application.DispatchProperties;
import com.example.demo.infrastructure.graph.GraphGatewayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;

/**
 * 决策层兜底：委托 Python Graph，由 Graph 统一负责模型调用。
 *
 * <p>作为失败级联的最后一站 —— 两个外部执行器都失败时，调这个拿到一个文本答复。
 * 线程池由 {@link com.example.demo.dispatch.application.DispatchExecutors} 统一管理。</p>
 */
@Slf4j
@Service
public class DecisionLayerFallback {

    private final GraphGatewayClient graphGatewayClient;
    private final DispatchProperties properties;
    private final ExecutorService fallbackExecutor;

    public DecisionLayerFallback(GraphGatewayClient graphGatewayClient,
                                  DispatchProperties properties,
                                  @Qualifier("dispatchFallbackExecutor") ExecutorService fallbackExecutor) {
        this.graphGatewayClient = graphGatewayClient;
        this.properties = properties;
        this.fallbackExecutor = fallbackExecutor;
    }

    /**
     * @throws FallbackFailedException 超时、空响应或异常
     */
    /**
     * 调用 LLM 自答作为 cascade 最后一道兜底。带超时与空响应保护。
     *
     * @throws FallbackFailedException 超时、空响应或底层异常
     */
    public String answer(String prompt) {
        return answer(0L, prompt);
    }

    public String answer(long userId, String prompt) {
        Future<String> future = fallbackExecutor.submit(() -> graphGatewayClient.completeDispatch(userId, prompt));
        try {
            String result = future.get(properties.getDecisionLayerFallbackTimeoutSeconds(), TimeUnit.SECONDS);
            if (result == null || result.isBlank()) {
                throw new FallbackFailedException("decision-layer fallback returned empty result");
            }
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new FallbackFailedException("decision-layer fallback timed out after "
                    + properties.getDecisionLayerFallbackTimeoutSeconds() + "s");
        } catch (FallbackFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new FallbackFailedException("decision-layer fallback failed: " + e.getMessage(), e);
        }
    }

    public static class FallbackFailedException extends RuntimeException {
        public FallbackFailedException(String message) {
            super(message);
        }

        public FallbackFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
