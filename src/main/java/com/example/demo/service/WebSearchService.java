package com.example.demo.service;

import com.example.demo.dto.SearchResult;
import com.example.demo.properties.SearchProperties;
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
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public WebSearchService(SearchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
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
        if (!properties.getEnabled()) {
            log.warn("搜索功能未启用");
            return SearchResult.ListResult.builder()
                    .query(query)
                    .results(new ArrayList<>())
                    .totalResults(0)
                    .build();
        }

        if (properties.getApiKey() == null || properties.getApiKey().isEmpty()) {
            log.error("搜索API密钥未配置");
            throw new RuntimeException("搜索API密钥未配置，请在application.yaml中配置app.search.api-key");
        }

        return switch (properties.getEngine().toLowerCase()) {
            case "serper" -> searchWithSerper(query);
            case "tavily" -> searchWithTavily(query);
            case "bing" -> searchWithBing(query);
            default -> throw new RuntimeException("不支持的搜索引擎: " + properties.getEngine());
        };
    }

    /**
     * 使用 Serper (Google Search API) 搜索
     */
    private SearchResult.ListResult searchWithSerper(String query) {
        try {
            String requestBody = objectMapper.writeValueAsString(new SerperRequest(query));

            String response = webClient.post()
                    .uri("https://google.serper.dev/search")
                    .header("X-API-KEY", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseSerperResponse(query, response);
        } catch (Exception e) {
            log.error("Serper搜索失败: {}", e.getMessage());
            throw new RuntimeException("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 使用 Tavily 搜索
     */
    private SearchResult.ListResult searchWithTavily(String query) {
        try {
            String requestBody = objectMapper.writeValueAsString(new TavilyRequest(
                    properties.getApiKey(),
                    query,
                    properties.getMaxResults()
            ));

            String response = webClient.post()
                    .uri("https://api.tavily.com/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseTavilyResponse(query, response);
        } catch (Exception e) {
            log.error("Tavily搜索失败: {}", e.getMessage());
            throw new RuntimeException("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 使用 Bing 搜索
     */
    private SearchResult.ListResult searchWithBing(String query) {
        try {
            String response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.bing.microsoft.com")
                            .path("/v7.0/search")
                            .queryParam("q", query)
                            .queryParam("count", properties.getMaxResults())
                            .build())
                    .header("Ocp-Apim-Subscription-Key", properties.getApiKey())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseBingResponse(query, response);
        } catch (Exception e) {
            log.error("Bing搜索失败: {}", e.getMessage());
            throw new RuntimeException("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 解析 Serper 响应
     */
    private SearchResult.ListResult parseSerperResponse(String query, String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            List<SearchResult> results = new ArrayList<>();

            // 解析 organic 结果
            JsonNode organic = root.path("organic");
            if (organic.isArray()) {
                int count = 0;
                for (JsonNode item : organic) {
                    if (count >= properties.getMaxResults()) break;
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
    private SearchResult.ListResult parseTavilyResponse(String query, String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            List<SearchResult> results = new ArrayList<>();

            JsonNode resultsNode = root.path("results");
            if (resultsNode.isArray()) {
                for (JsonNode item : resultsNode) {
                    results.add(SearchResult.builder()
                            .title(item.path("title").asText())
                            .url(item.path("url").asText())
                            .snippet(item.path("content").asText())
                            .source(extractDomain(item.path("url").asText()))
                            .build());
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
    private SearchResult.ListResult parseBingResponse(String query, String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            List<SearchResult> results = new ArrayList<>();

            JsonNode webPages = root.path("webPages").path("value");
            if (webPages.isArray()) {
                for (JsonNode item : webPages) {
                    results.add(SearchResult.builder()
                            .title(item.path("name").asText())
                            .url(item.path("url").asText())
                            .snippet(item.path("snippet").asText())
                            .source(extractDomain(item.path("url").asText()))
                            .build());
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
}