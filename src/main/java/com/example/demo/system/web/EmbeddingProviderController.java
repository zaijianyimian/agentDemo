package com.example.demo.system.web;

import com.example.demo.infrastructure.properties.OllamaEmbeddingProperties;
import com.example.demo.infrastructure.properties.OpenAiEmbeddingProperties;
import com.example.demo.shared.dto.ApiResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Embedding provider diagnostics endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/system/embedding")
@RequiredArgsConstructor
public class EmbeddingProviderController {

    private static final long PROBE_TIMEOUT_MS = 2000L;
    private static final String PROBE_INPUT = "ping";

    private final EmbeddingModel embeddingModel;
    private final OpenAiEmbeddingProperties openAiEmbeddingProperties;
    private final OllamaEmbeddingProperties ollamaEmbeddingProperties;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> payload = new LinkedHashMap<>();
        boolean openAiActive = openAiEmbeddingProperties != null && openAiEmbeddingProperties.isEnabled();
        payload.put("provider", openAiActive ? "openai" : "ollama");
        payload.put("baseUrl", openAiActive
                ? openAiEmbeddingProperties.getBaseUrl()
                : (ollamaEmbeddingProperties == null ? "" : ollamaEmbeddingProperties.getBaseUrl()));
        payload.put("modelName", openAiActive
                ? openAiEmbeddingProperties.getModelName()
                : (ollamaEmbeddingProperties == null ? "" : ollamaEmbeddingProperties.getModelName()));
        payload.put("requestedDimensions", openAiActive ? openAiEmbeddingProperties.getDimensions() : 0);
        payload.put("reachable", probeReachable());
        return ApiResponse.success(payload);
    }

    private boolean probeReachable() {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "embedding-probe");
            thread.setDaemon(true);
            return thread;
        });
        AtomicBoolean ok = new AtomicBoolean(false);
        Future<?> future = executor.submit((Callable<Void>) () -> {
            Object response = embeddingModel.embed(PROBE_INPUT);
            ok.set(response != null);
            return null;
        });
        try {
            future.get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return ok.get();
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("embedding probe timed out after {}ms", PROBE_TIMEOUT_MS);
            return false;
        } catch (Exception e) {
            log.warn("embedding probe failed: {}", e.getMessage());
            return false;
        } finally {
            executor.shutdownNow();
        }
    }
}
