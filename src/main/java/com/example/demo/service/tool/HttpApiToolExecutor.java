package com.example.demo.service.tool;

import com.example.demo.dto.ToolExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.ToolType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP API 工具执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpApiToolExecutor implements ToolExecutor {

    private final ObjectMapper objectMapper;

    /**
     * RestTemplate 缓存：按超时时间缓存实例
     */
    private final Map<Integer, RestTemplate> restTemplateCache = new ConcurrentHashMap<>();

    @Override
    public ToolType getToolType() {
        return ToolType.HTTP_API;
    }

    @Override
    public ToolExecutionResult execute(McpTool tool, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();

        try {
            // 解析配置
            Map<String, Object> config = objectMapper.readValue(
                    tool.getConfig(),
                    new TypeReference<Map<String, Object>>() {}
            );

            String url = (String) config.get("url");
            String method = (String) config.getOrDefault("method", "GET");
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) config.getOrDefault("headers", Map.of());
            int timeout = ((Number) config.getOrDefault("timeout", 30)).intValue();

            // 获取或创建 RestTemplate（按超时时间缓存）
            RestTemplate restTemplate = restTemplateCache.computeIfAbsent(timeout, t -> {
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(t * 1000);
                factory.setReadTimeout(t * 1000);
                return new RestTemplate(factory);
            });

            // 构建请求头
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);

            // 替换环境变量占位符
            url = resolveEnvVariables(url);
            Map<String, String> resolvedHeaders = new HashMap<>();
            headers.forEach((k, v) -> resolvedHeaders.put(k, resolveEnvVariables(v)));
            resolvedHeaders.forEach(httpHeaders::set);

            // 构建请求体
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(params, httpHeaders);

            // 发送请求
            ResponseEntity<String> response;
            if ("POST".equalsIgnoreCase(method)) {
                response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            } else {
                // GET 请求，将参数拼接到 URL
                String queryString = buildQueryString(params);
                String fullUrl = url + (url.contains("?") ? "&" : "?") + queryString;
                response = restTemplate.exchange(fullUrl, HttpMethod.GET, entity, String.class);
            }

            long duration = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful()) {
                return ToolExecutionResult.success(response.getBody(), duration);
            } else {
                return ToolExecutionResult.failure(
                        "HTTP请求失败: " + response.getStatusCode(),
                        duration
                );
            }

        } catch (Exception e) {
            log.error("HTTP API 工具执行失败: {}", tool.getName(), e);
            return ToolExecutionResult.failure(
                    "执行失败: " + e.getMessage(),
                    System.currentTimeMillis() - startTime
            );
        }
    }

    /**
     * 构建查询字符串
     */
    private String buildQueryString(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (sb.length() > 0) sb.append("&");
            sb.append(key).append("=").append(value != null ? value.toString() : "");
        });
        return sb.toString();
    }

    /**
     * 解析环境变量占位符 ${ENV_VAR}
     */
    private String resolveEnvVariables(String value) {
        if (value == null) return null;

        int start;
        while ((start = value.indexOf("${")) != -1) {
            int end = value.indexOf("}", start);
            if (end == -1) break;

            String envVar = value.substring(start + 2, end);
            String envValue = System.getenv(envVar);
            if (envValue == null) {
                envValue = System.getProperty(envVar, "");
            }
            value = value.substring(0, start) + envValue + value.substring(end + 1);
        }
        return value;
    }
}