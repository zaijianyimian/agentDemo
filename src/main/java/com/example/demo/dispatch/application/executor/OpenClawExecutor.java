package com.example.demo.dispatch.application.executor;

import com.example.demo.dispatch.application.DispatchProperties;
import com.example.demo.dispatch.application.ExecutorToggleService;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OpenClawExecutor implements Executor {

    private static final String HINT = "openclaw";

    private final ExecutorToggleService executorToggleService;
    private final DispatchProperties.OpenClaw properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenClawExecutor(ExecutorToggleService executorToggleService,
                            DispatchProperties dispatchProperties,
                            ObjectMapper objectMapper) {
        this(executorToggleService, dispatchProperties.getOpenclaw(), objectMapper,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
    }

    OpenClawExecutor(ExecutorToggleService executorToggleService,
                     DispatchProperties.OpenClaw properties,
                     ObjectMapper objectMapper,
                     HttpClient httpClient) {
        this.executorToggleService = executorToggleService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String hint() {
        return HINT;
    }

    @Override
    public boolean isAvailable() {
        if (!executorToggleService.isExecutorEnabled(HINT) || !isConfigured()) {
            return false;
        }
        try {
            HttpRequest request = requestBuilder("/v1/models")
                    .timeout(Duration.ofSeconds(properties.getHealthTimeoutSeconds()))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String execute(DispatchedTask task, String prompt, Path workspace, int timeoutSeconds) {
        if (!executorToggleService.isExecutorEnabled(HINT)) {
            throw new ExecutorUnavailableException("openclaw gateway executor is disabled");
        }
        if (!isConfigured()) {
            throw new ExecutorUnavailableException("openclaw gateway is not configured");
        }
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", properties.getModel(),
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "stream", false));
            HttpRequest request = requestBuilder("/v1/chat/completions")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                throw new ExecutorUnavailableException("openclaw gateway authentication failed: HTTP " + status);
            }
            if (status < 200 || status >= 300) {
                throw new ExecutorFailedException(
                        "openclaw gateway HTTP " + status + ": " + truncate(response.body(), 500));
            }
            String result = extractResult(response.body(), objectMapper);
            if (result.isBlank()) {
                throw new ExecutorFailedException("openclaw gateway returned no usable result");
            }
            return result;
        } catch (ExecutorUnavailableException | ExecutorFailedException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw new ExecutorTimeoutException("openclaw gateway timed out after " + timeoutSeconds + "s");
        } catch (ConnectException e) {
            throw new ExecutorUnavailableException("openclaw gateway is unreachable");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutorFailedException("openclaw gateway request interrupted", e);
        } catch (Exception e) {
            throw new ExecutorFailedException("openclaw gateway error: " + e.getMessage(), e);
        }
    }

    static String extractResult(String raw, ObjectMapper objectMapper) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            JsonNode content = objectMapper.readTree(raw)
                    .path("choices").path(0).path("message").path("content");
            return content.isTextual() ? content.asText().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.newBuilder(URI.create(normalizedBaseUrl() + path))
                .header("Authorization", "Bearer " + properties.getToken());
    }

    private boolean isConfigured() {
        return properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()
                && properties.getToken() != null && !properties.getToken().isBlank()
                && properties.getModel() != null && !properties.getModel().isBlank();
    }

    private String normalizedBaseUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) + "...[truncated]" : value;
    }
}
