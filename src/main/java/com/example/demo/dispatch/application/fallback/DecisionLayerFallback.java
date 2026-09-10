package com.example.demo.dispatch.application.fallback;

import com.example.demo.dispatch.application.DispatchProperties;
import com.example.demo.model.application.QwenChatService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** LEGACY 模式下本地执行链的最后一级 LLM 兜底。 */
@Service
@ConditionalOnProperty(prefix = "app.agent", name = "mode", havingValue = "legacy", matchIfMissing = true)
public class DecisionLayerFallback {

    private final QwenChatService qwenChatService;
    private final DispatchProperties properties;
    private final ExecutorService fallbackExecutor;

    public DecisionLayerFallback(QwenChatService qwenChatService,
                                 DispatchProperties properties,
                                 @Qualifier("dispatchFallbackExecutor") ExecutorService fallbackExecutor) {
        this.qwenChatService = qwenChatService;
        this.properties = properties;
        this.fallbackExecutor = fallbackExecutor;
    }

    public String answer(String prompt) {
        Future<String> future = fallbackExecutor.submit(() -> qwenChatService.complete(prompt));
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
        public FallbackFailedException(String message) { super(message); }
        public FallbackFailedException(String message, Throwable cause) { super(message, cause); }
    }
}
