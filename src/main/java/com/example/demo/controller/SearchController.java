package com.example.demo.controller;

import com.example.demo.dto.ChatResponse;
import com.example.demo.dto.ContentAnalysis;
import com.example.demo.dto.SearchResult;
import com.example.demo.service.ContentAnalysisService;
import com.example.demo.service.EnhancedChatService;
import com.example.demo.service.WebSearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
    private ObjectMapper objectMapper;

    /**
     * 执行网络搜索
     */
    @GetMapping
    public SearchResult.ListResult search(@RequestParam("query") String query) {
        return webSearchService.search(query);
    }

    /**
     * 搜索并返回搜索结果 + AI总结
     */
    @GetMapping("/summary")
    public SearchSummaryResponse searchWithSummary(@RequestParam("query") String query) {
        // 1. 执行搜索
        SearchResult.ListResult searchResult = webSearchService.search(query);

        // 2. AI总结搜索结果
        String searchContext = buildSearchContext(searchResult);
        String summary = enhancedChatService.chatWithSearch(
                "请用简洁的语言总结以下搜索结果的关键信息：" + query,
                searchContext
        );

        // 3. 返回结果
        return SearchSummaryResponse.builder()
                .query(query)
                .searchResults(searchResult.getResults())
                .aiSummary(summary)
                .build();
    }

    /**
     * 带搜索的聊天接口 - 先搜索再回答
     * 返回完整结构化响应
     */
    @GetMapping("/chat")
    public ChatResponse chatWithSearch(@RequestParam("message") String message) {
        // 1. 执行搜索
        SearchResult.ListResult searchResult = webSearchService.search(message);

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

        // 2. 构建搜索上下文
        String searchContext = buildSearchContext(searchResult);

        // 3. 流式回答
        AtomicReference<StringBuilder> contentBuilder = new AtomicReference<>(new StringBuilder());

        Flux<String> stream = enhancedChatService.streamChatWithSearch(message, searchContext)
                .map(chunk -> {
                    contentBuilder.get().append(chunk);
                    try {
                        return "data:" + objectMapper.writeValueAsString(ChatResponse.contentChunk(chunk));
                    } catch (JsonProcessingException e) {
                        return "data:{\"error\":\"serialization error\"}";
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
                        return "data:" + objectMapper.writeValueAsString(finalResponse);
                    } catch (JsonProcessingException e) {
                        return "data:{\"error\":\"analysis error\"}";
                    }
                }));

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream);
    }

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