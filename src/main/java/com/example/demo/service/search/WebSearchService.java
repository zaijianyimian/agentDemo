package com.example.demo.service.search;

import com.example.demo.dto.SearchResult;
import com.example.demo.properties.SearchProperties;
import com.example.demo.service.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络搜索服务
 * 支持多种搜索引擎: Serper, Tavily, Bing
 */
@Slf4j
@Service
public class WebSearchService {

    private final SearchProperties properties;
    private final SystemSettingsService settingsService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WebSearchService(SearchProperties properties,
                            SystemSettingsService settingsService,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * 执行网络搜索
     *
     * @param query 搜索关键词
     * @return 搜索结果列表
     */
    public SearchResult.ListResult search(String query) {
        RuntimeSearchConfig config = resolveConfig();

        if (!config.enabled()) {
            log.warn("搜索功能未启用");
            return SearchResult.ListResult.builder()
                    .query(query)
                    .results(new ArrayList<>())
                    .totalResults(0)
                    .build();
        }

        if (config.apiKey() == null || config.apiKey().isEmpty()) {
            log.error("搜索API密钥未配置");
            throw new RuntimeException("搜索API密钥未配置，请在系统设置或 application.yaml 中配置");
        }

        return switch (config.engine().toLowerCase()) {
            case "serper" -> searchWithSerper(query, config);
            case "tavily" -> searchWithTavily(query, config);
            case "bing" -> searchWithBing(query, config);
            default -> throw new RuntimeException("不支持的搜索引擎: " + config.engine());
        };
    }

    /**
     * 使用 Serper (Google Search API) 搜索
     */
    private SearchResult.ListResult searchWithSerper(String query, RuntimeSearchConfig config) {
        try {
            String requestBody = objectMapper.writeValueAsString(new SerperRequest(query));

            String response = webClient.post()
                    .uri("https://google.serper.dev/search")
                    .header("X-API-KEY", config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseSerperResponse(query, response, config.maxResults());
        } catch (Exception e) {
            log.error("Serper搜索失败: {}", e.getMessage());
            throw new RuntimeException("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 使用 Tavily 搜索
     */
    private SearchResult.ListResult searchWithTavily(String query, RuntimeSearchConfig config) {
        try {
            String requestBody = objectMapper.writeValueAsString(new TavilyRequest(
                    config.apiKey(),
                    query,
                    config.maxResults()
            ));

            String response = webClient.post()
                    .uri("https://api.tavily.com/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseTavilyResponse(query, response, config.maxResults());
        } catch (Exception e) {
            log.error("Tavily搜索失败: {}", e.getMessage());
            throw new RuntimeException("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 使用 Bing 搜索
     */
    private SearchResult.ListResult searchWithBing(String query, RuntimeSearchConfig config) {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.bing.microsoft.com")
                            .path("/v7.0/search")
                            .queryParam("q", query)
                            .queryParam("count", config.maxResults())
                            .build())
                    .header("Ocp-Apim-Subscription-Key", config.apiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseBingResponse(query, response, config.maxResults());
        } catch (Exception e) {
            log.error("Bing搜索失败: {}", e.getMessage());
            throw new RuntimeException("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 解析 Serper 响应
     */
    private SearchResult.ListResult parseSerperResponse(String query, String response, int maxResults) {
        try {
            JsonNode root = objectMapper.readTree(response);
            List<SearchResult> results = new ArrayList<>();

            // 解析 organic 结果
            JsonNode organic = root.path("organic");
            if (organic.isArray()) {
                int count = 0;
                for (JsonNode item : organic) {
                    if (count >= maxResults) break;
                    results.add(SearchResult.builder()
                            .title(item.path("title").asText())
                            .url(item.path("link").asText())
                            .snippet(item.path("snippet").asText())
                            .source(extractDomain(item.path("link").asText()))
                            .build());
                    count++;
                }
            }

            return SearchResult.ListResult.builder()
                    .query(query)
                    .results(results)
                    .totalResults(results.size())
                    .build();
        } catch (Exception e) {
            log.error("解析Serper响应失败: {}", e.getMessage());
            throw new RuntimeException("解析搜索结果失败");
        }
    }

    /**
     * 解析 Tavily 响应
     */
    private SearchResult.ListResult parseTavilyResponse(String query, String response, int maxResults) {
        try {
            JsonNode root = objectMapper.readTree(response);
            List<SearchResult> results = new ArrayList<>();

            JsonNode resultsNode = root.path("results");
            if (resultsNode.isArray()) {
                int count = 0;
                for (JsonNode item : resultsNode) {
                    if (count >= maxResults) {
                        break;
                    }
                    results.add(SearchResult.builder()
                            .title(item.path("title").asText())
                            .url(item.path("url").asText())
                            .snippet(item.path("content").asText())
                            .source(extractDomain(item.path("url").asText()))
                            .build());
                    count++;
                }
            }

            return SearchResult.ListResult.builder()
                    .query(query)
                    .results(results)
                    .totalResults(results.size())
                    .build();
        } catch (Exception e) {
            log.error("解析Tavily响应失败: {}", e.getMessage());
            throw new RuntimeException("解析搜索结果失败");
        }
    }

    /**
     * 解析 Bing 响应
     */
    private SearchResult.ListResult parseBingResponse(String query, String response, int maxResults) {
        try {
            JsonNode root = objectMapper.readTree(response);
            List<SearchResult> results = new ArrayList<>();

            JsonNode webPages = root.path("webPages").path("value");
            if (webPages.isArray()) {
                int count = 0;
                for (JsonNode item : webPages) {
                    if (count >= maxResults) {
                        break;
                    }
                    results.add(SearchResult.builder()
                            .title(item.path("name").asText())
                            .url(item.path("url").asText())
                            .snippet(item.path("snippet").asText())
                            .source(extractDomain(item.path("url").asText()))
                            .build());
                    count++;
                }
            }

            return SearchResult.ListResult.builder()
                    .query(query)
                    .results(results)
                    .totalResults(results.size())
                    .build();
        } catch (Exception e) {
            log.error("解析Bing响应失败: {}", e.getMessage());
            throw new RuntimeException("解析搜索结果失败");
        }
    }

    /**
     * 从URL中提取域名
     */
    private String extractDomain(String url) {
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getHost();
        } catch (Exception e) {
            return "";
        }
    }

    // Serper 请求体
    private record SerperRequest(String q) {}

    // Tavily 请求体
    private record TavilyRequest(String api_key, String query, int max_results) {}

    private RuntimeSearchConfig resolveConfig() {
        boolean enabled = settingsService.getBooleanSetting("search", "enabled", Boolean.TRUE.equals(properties.getEnabled()));
        String engine = settingsService.getSetting("search", "engine", properties.getEngine());
        String apiKey = settingsService.getSetting("search", "api_key", properties.getApiKey());
        int maxResults = settingsService.getIntSetting(
                "search",
                "max_results",
                properties.getMaxResults() != null ? properties.getMaxResults() : 3
        );
        return new RuntimeSearchConfig(enabled, engine, apiKey, Math.max(1, maxResults));
    }

    private record RuntimeSearchConfig(boolean enabled, String engine, String apiKey, int maxResults) {}
}
