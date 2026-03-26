package com.example.demo.controller;

import com.example.demo.dto.ChatResponse;
import com.example.demo.dto.ContentAnalysis;
import com.example.demo.service.ContentAnalysisService;
import com.example.demo.service.QwenChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天控制器
 * 处理AI对话相关接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private QwenChatService qwenChatService;

    @Resource
    private ContentAnalysisService contentAnalysisService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 普通聊天接口 - 返回完整响应
     */
    @GetMapping("/complete")
    public String complete(@RequestParam("message") String message) {
        return qwenChatService.complete(message);
    }

    /**
     * 流式聊天接口 - SSE 方式返回
     * 前端使用 EventSource 接收
     */
    @GetMapping("/stream")
    public ResponseEntity<Flux<String>> chatStream(@RequestParam("message") String message) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(qwenChatService.chat(message));
    }

    /**
     * 流式聊天接口 - JSON 格式返回
     * 每个数据块包装为 JSON 格式，便于解析
     */
    @GetMapping("/stream/json")
    public ResponseEntity<Flux<String>> chatStreamJson(@RequestParam("message") String message) {
        AtomicReference<StringBuilder> contentBuilder = new AtomicReference<>(new StringBuilder());

        Flux<String> stream = qwenChatService.chat(message)
                .map(chunk -> {
                    contentBuilder.get().append(chunk);
                    try {
                        return "data:" + objectMapper.writeValueAsString(ChatResponse.contentChunk(chunk));
                    } catch (JsonProcessingException e) {
                        return "data:{\"error\":\"serialization error\"}";
                    }
                })
                .concatWith(Mono.fromSupplier(() -> {
                    // 流结束后，添加分析元数据
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
     * 结构化聊天接口 - 返回含元数据的完整响应
     */
    @GetMapping("/structured")
    public ChatResponse chatStructured(@RequestParam("message") String message) {
        // 1. 获取完整响应
        String content = qwenChatService.complete(message);

        // 2. 分析内容
        ContentAnalysis analysis = contentAnalysisService.analyze(content);

        // 3. 构建结构化响应
        return ChatResponse.builder()
                .content(content)
                .importance(analysis.getImportance())
                .tags(analysis.getTags())
                .sentiment(analysis.getSentiment() != null ? analysis.getSentiment().name() : "NEUTRAL")
                .summary(analysis.getSummary())
                .isComplete(true)
                .build();
    }
}