package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ChatResponse;
import com.example.demo.dto.ContentAnalysis;
import com.example.demo.dto.SearchResult;
import com.example.demo.entity.SearchHistory;
import com.example.demo.entity.UserInterest;
import com.example.demo.service.chat.ContentAnalysisService;
import com.example.demo.service.chat.EnhancedChatService;
import com.example.demo.service.search.SearchHistoryService;
import com.example.demo.service.search.UserInterestService;
import com.example.demo.service.search.WebSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 搜索控制器
 * 处理网络搜索相关接口
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Resource
    private WebSearchService webSearchService;

    @Resource
    private EnhancedChatService enhancedChatService;

    @Resource
    private ContentAnalysisService contentAnalysisService;

    @Resource
    private SearchHistoryService historyService;

    @Resource
    private UserInterestService interestService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 执行网络搜索
     */
    @GetMapping
    public SearchResult.ListResult search(
            @RequestParam("query") String query,
            HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        SearchResult.ListResult result = webSearchService.search(query);
        long duration = System.currentTimeMillis() - startTime;

        // 记录搜索历史
        historyService.recordSearch(
                query,
                "normal",
                result.getResults() != null ? result.getResults().size() : 0,
                false,
                duration,
                null
        );

        // 分析用户兴趣
        interestService.analyzeAndUpdateInterest(query);

        return result;
    }

    /**
     * 搜索并返回搜索结果 + AI总结（SSE流式）
     * 先发送搜索结果，再流式发送AI总结
     */
    @GetMapping(value = "/summary", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> searchWithSummaryStream(@RequestParam("query") String query) {
        long startTime = System.currentTimeMillis();

        // 1. 执行搜索
        SearchResult.ListResult searchResult = webSearchService.search(query);

        // 记录搜索历史
        long duration = System.currentTimeMillis() - startTime;
        historyService.recordSearch(
                query,
                "summary",
                searchResult.getResults() != null ? searchResult.getResults().size() : 0,
                true,
                duration,
                null
        );

        // 分析用户兴趣
        interestService.analyzeAndUpdateInterest(query);

        // 2. 构建搜索上下文
        String searchContext = buildSearchContext(searchResult);

        // 3. 先发送搜索结果
        Flux<String> searchResultsFlux = Flux.defer(() -> {
            try {
                SearchSummaryResponse response = SearchSummaryResponse.builder()
                        .query(query)
                        .searchResults(searchResult.getResults())
                        .aiSummary(null)
                        .build();
                return Flux.just(objectMapper.writeValueAsString(response));
            } catch (JsonProcessingException e) {
                return Flux.just("{\"error\":\"serialization error\"}");
            }
        });

        // 4. 流式发送AI总结
        Flux<String> summaryFlux = enhancedChatService.streamChatWithSearch(
                "请用简洁的语言总结以下搜索结果的关键信息：" + query,
                searchContext
        ).map(chunk -> {
            try {
                SummaryChunk summaryChunk = new SummaryChunk(chunk, false);
                return objectMapper.writeValueAsString(summaryChunk);
            } catch (JsonProcessingException e) {
                return "{\"error\":\"serialization error\"}";
            }
        });

        // 5. 发送完成标记
        Flux<String> doneFlux = Flux.just("[DONE]");

        // 6. 合并流
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(searchResultsFlux.concatWith(summaryFlux).concatWith(doneFlux));
    }

    /**
     * AI总结流式响应DTO
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class SummaryChunk {
        private String summary;
        private boolean done;
    }

    /**
     * 带搜索的聊天接口 - 先搜索再回答
     * 返回完整结构化响应
     */
    @GetMapping("/chat")
    public ChatResponse chatWithSearch(@RequestParam("message") String message) {
        // 1. 执行搜索
        SearchResult.ListResult searchResult = webSearchService.search(message);

        // 记录搜索历史
        historyService.recordSearch(
                message,
                "chat",
                searchResult.getResults() != null ? searchResult.getResults().size() : 0,
                false,
                0,
                null
        );

        // 分析用户兴趣
        interestService.analyzeAndUpdateInterest(message);

        // 2. 构建搜索上下文
        String searchContext = buildSearchContext(searchResult);

        // 3. 基于搜索结果回答
        String content = enhancedChatService.chatWithSearch(message, searchContext);

        // 4. 分析内容
        ContentAnalysis analysis = contentAnalysisService.analyze(content);

        // 5. 构建结构化响应
        return ChatResponse.builder()
                .content(content)
                .importance(analysis.getImportance())
                .tags(analysis.getTags())
                .sentiment(analysis.getSentiment() != null ? analysis.getSentiment().name() : "NEUTRAL")
                .summary(analysis.getSummary())
                .isComplete(true)
                .build();
    }

    /**
     * 带搜索的流式聊天接口 - 先搜索再流式回答
     */
    @GetMapping("/chat/stream")
    public ResponseEntity<Flux<String>> chatWithSearchStream(@RequestParam("message") String message) {
        // 1. 执行搜索
        SearchResult.ListResult searchResult = webSearchService.search(message);

        // 记录搜索历史
        historyService.recordSearch(
                message,
                "stream",
                searchResult.getResults() != null ? searchResult.getResults().size() : 0,
                false,
                0,
                null
        );

        // 分析用户兴趣
        interestService.analyzeAndUpdateInterest(message);

        // 2. 构建搜索上下文
        String searchContext = buildSearchContext(searchResult);

        // 3. 流式回答
        AtomicReference<StringBuilder> contentBuilder = new AtomicReference<>(new StringBuilder());

        Flux<String> stream = enhancedChatService.streamChatWithSearch(message, searchContext)
                .map(chunk -> {
                    contentBuilder.get().append(chunk);
                    try {
                        return objectMapper.writeValueAsString(ChatResponse.contentChunk(chunk));
                    } catch (JsonProcessingException e) {
                        return "{\"error\":\"serialization error\"}";
                    }
                })
                .concatWith(Mono.fromSupplier(() -> {
                    try {
                        String fullContent = contentBuilder.get().toString();
                        ContentAnalysis analysis = contentAnalysisService.analyze(fullContent);
                        ChatResponse finalResponse = ChatResponse.builder()
                                .content(fullContent)
                                .importance(analysis.getImportance())
                                .tags(analysis.getTags())
                                .sentiment(analysis.getSentiment() != null ? analysis.getSentiment().name() : "NEUTRAL")
                                .summary(analysis.getSummary())
                                .isComplete(true)
                                .build();
                        return objectMapper.writeValueAsString(finalResponse);
                    } catch (JsonProcessingException e) {
                        return "{\"error\":\"analysis error\"}";
                    }
                }));

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream);
    }

    // ==================== 搜索历史接口 ====================

    /**
     * 获取搜索历史
     */
    @GetMapping("/history")
    public ApiResponse<List<SearchHistory>> getSearchHistory(
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.success(historyService.getRecentHistory(limit));
    }

    /**
     * 获取热门搜索
     */
    @GetMapping("/hot")
    public ApiResponse<List<Map<String, Object>>> getHotQueries(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(historyService.getHotQueries(limit));
    }

    /**
     * 获取搜索统计
     */
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        return ApiResponse.success(historyService.getStatistics());
    }

    /**
     * 清空搜索历史
     */
    @DeleteMapping("/history")
    public ApiResponse<Void> clearHistory() {
        historyService.clearHistory();
        return ApiResponse.success(null);
    }

    /**
     * 删除单条搜索历史
     */
    @DeleteMapping("/history/{id}")
    public ApiResponse<Void> deleteHistoryItem(@PathVariable Long id) {
        historyService.deleteHistory(id);
        return ApiResponse.success(null);
    }

    // ==================== 用户兴趣接口 ====================

    /**
     * 获取用户兴趣列表
     */
    @GetMapping("/interests")
    public ApiResponse<List<UserInterest>> getInterests() {
        return ApiResponse.success(interestService.getAllInterests());
    }

    /**
     * 获取用户主要兴趣
     */
    @GetMapping("/interests/top")
    public ApiResponse<List<UserInterest>> getTopInterests(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(interestService.getTopInterests(limit));
    }

    /**
     * 获取兴趣分析报告
     */
    @GetMapping("/interests/report")
    public ApiResponse<Map<String, Object>> getInterestReport() {
        return ApiResponse.success(interestService.getInterestReport());
    }

    /**
     * 清空用户兴趣
     */
    @DeleteMapping("/interests")
    public ApiResponse<Void> clearInterests() {
        interestService.clearInterests();
        return ApiResponse.success(null);
    }

    /**
     * 删除单个兴趣标签
     */
    @DeleteMapping("/interests/{id}")
    public ApiResponse<Void> deleteInterest(@PathVariable Long id) {
        interestService.deleteInterest(id);
        return ApiResponse.success(null);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建搜索上下文字符串
     */
    private String buildSearchContext(SearchResult.ListResult searchResult) {
        if (searchResult.getResults() == null || searchResult.getResults().isEmpty()) {
            return "未找到相关搜索结果";
        }

        return searchResult.getResults().stream()
                .map(r -> String.format("【%s】\n来源: %s\n摘要: %s\n链接: %s",
                        r.getTitle(), r.getSource(), r.getSnippet(), r.getUrl()))
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 搜索总结响应DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SearchSummaryResponse {
        private String query;
        private java.util.List<SearchResult> searchResults;
        private String aiSummary;
    }
}