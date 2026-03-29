package com.example.demo.controller;

import com.example.demo.dto.ChatResponse;
import com.example.demo.dto.ContentAnalysis;
import com.example.demo.service.chat.ChatHistoryService;
import com.example.demo.service.chat.ContentAnalysisService;
import com.example.demo.service.chat.QwenChatService;
import com.example.demo.service.chat.ChatWithMemoryService;
import com.example.demo.service.memory.MemoryApplicationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天控制器
 * 处理AI对话相关接口
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Resource
    private QwenChatService qwenChatService;

    @Resource
    private ChatWithMemoryService chatWithMemoryService;

    @Resource
    private ContentAnalysisService contentAnalysisService;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private MemoryApplicationService memoryApplicationService;

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
     * 使用 ServerSentEvent 确保标准 SSE 格式
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam("message") String message) {
        return qwenChatService.chat(message)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * 流式聊天接口 - JSON 格式返回
     * 每个数据块包装为 JSON 格式，便于解析
     */
    @GetMapping(value = "/stream/json", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamJson(@RequestParam("message") String message) {
        AtomicReference<StringBuilder> contentBuilder = new AtomicReference<>(new StringBuilder());

        return qwenChatService.chat(message)
                .map(chunk -> {
                    contentBuilder.get().append(chunk);
                    try {
                        String jsonData = objectMapper.writeValueAsString(ChatResponse.contentChunk(chunk));
                        return ServerSentEvent.<String>builder()
                                .data(jsonData)
                                .build();
                    } catch (JsonProcessingException e) {
                        return ServerSentEvent.<String>builder()
                                .data("{\"error\":\"serialization error\"}")
                                .build();
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
                        String jsonData = objectMapper.writeValueAsString(finalResponse);
                        return ServerSentEvent.<String>builder()
                                .data(jsonData)
                                .event("complete")
                                .build();
                    } catch (JsonProcessingException e) {
                        return ServerSentEvent.<String>builder()
                                .data("{\"error\":\"analysis error\"}")
                                .build();
                    }
                }));
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

    // ==================== 带会话记忆的接口 ====================

    /**
     * 带记忆的普通聊天接口 - 返回完整响应
     * @param message 用户消息
     * @param sessionId 会话ID（用于记忆和历史存储）
     * @param modelId 模型ID（可选，不传则使用默认模型）
     */
    @GetMapping("/complete/session")
    public String completeWithSession(
            @RequestParam("message") String message,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam(value = "model", required = false) Long modelId) {
        // 1. 保存用户消息
        chatHistoryService.addMessage(sessionId, "user", message, null);
        log.info("保存用户消息: sessionId={}, message={}, modelId={}", sessionId, message, modelId);

        // 2. 获取带记忆的AI响应
        String response = chatWithMemoryService.chat(sessionId, message, modelId);

        // 3. 保存AI响应
        chatHistoryService.addMessage(sessionId, "assistant", response, "model-" + modelId);
        log.info("保存AI响应: sessionId={}, responseLength={}", sessionId, response.length());

        // 4. 提取并存储记忆
        try {
            memoryApplicationService.extractAndStore(
                    sessionId.toString(),
                    List.of("user: " + message, "assistant: " + response));
            log.info("提取记忆成功: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("提取记忆失败: sessionId={}, error={}", sessionId, e.getMessage());
        }

        return response;
    }

    /**
     * 带记忆的流式聊天接口 - SSE 方式返回
     * 同时保存用户消息和AI响应到数据库
     * @param message 用户消息
     * @param sessionId 会话ID（用于记忆和历史存储）
     * @param modelId 模型ID（可选，不传则使用默认模型）
     */
    @GetMapping(value = "/stream/session", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamWithSession(
            @RequestParam("message") String message,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam(value = "model", required = false) Long modelId) {

        // 1. 保存用户消息
        chatHistoryService.addMessage(sessionId, "user", message, null);
        log.info("保存用户消息: sessionId={}, message={}, modelId={}", sessionId, message, modelId);

        // 2. 收集完整响应的容器
        AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());

        // 3. 流式调用带记忆的AI服务
        return chatWithMemoryService.streamChat(sessionId, message, modelId)
                .doOnNext(chunk -> fullResponse.get().append(chunk))
                .doOnComplete(() -> {
                    // 4. 流完成后保存AI响应
                    String response = fullResponse.get().toString();
                    chatHistoryService.addMessage(sessionId, "assistant", response, "model-" + modelId);
                    log.info("保存AI响应: sessionId={}, responseLength={}", sessionId, response.length());

                    // 5. 提取并存储记忆
                    try {
                        memoryApplicationService.extractAndStore(
                                sessionId.toString(),
                                List.of("user: " + message, "assistant: " + response));
                        log.info("提取记忆成功: sessionId={}", sessionId);
                    } catch (Exception e) {
                        log.warn("提取记忆失败: sessionId={}, error={}", sessionId, e.getMessage());
                    }
                })
                .doOnError(error -> log.error("流式响应错误: sessionId={}, error={}", sessionId, error.getMessage()))
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * 带记忆的流式聊天接口 - JSON 格式返回
     * 每个数据块包装为 JSON 格式，同时保存消息和记忆
     * @param message 用户消息
     * @param sessionId 会话ID（用于记忆和历史存储）
     * @param modelId 模型ID（可选，不传则使用默认模型）
     */
    @GetMapping(value = "/stream/session/json", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamWithSessionJson(
            @RequestParam("message") String message,
            @RequestParam("sessionId") Long sessionId,
            @RequestParam(value = "model", required = false) Long modelId) {

        // 1. 保存用户消息
        chatHistoryService.addMessage(sessionId, "user", message, null);
        log.info("保存用户消息: sessionId={}, message={}, modelId={}", sessionId, message, modelId);

        // 2. 收集完整响应的容器
        AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());

        // 3. 流式调用带记忆的AI服务
        return chatWithMemoryService.streamChat(sessionId, message, modelId)
                .map(chunk -> {
                    fullResponse.get().append(chunk);
                    try {
                        String jsonData = objectMapper.writeValueAsString(ChatResponse.contentChunk(chunk));
                        return ServerSentEvent.<String>builder()
                                .data(jsonData)
                                .build();
                    } catch (JsonProcessingException e) {
                        return ServerSentEvent.<String>builder()
                                .data("{\"error\":\"serialization error\"}")
                                .build();
                    }
                })
                .concatWith(Mono.fromRunnable(() -> {
                    // 4. 流完成后保存AI响应和记忆
                    String response = fullResponse.get().toString();
                    chatHistoryService.addMessage(sessionId, "assistant", response, "model-" + modelId);
                    log.info("保存AI响应: sessionId={}, responseLength={}", sessionId, response.length());

                    // 5. 提取并存储记忆
                    try {
                        memoryApplicationService.extractAndStore(
                                sessionId.toString(),
                                List.of("user: " + message, "assistant: " + response));
                        log.info("提取记忆成功: sessionId={}", sessionId);
                    } catch (Exception e) {
                        log.warn("提取记忆失败: sessionId={}, error={}", sessionId, e.getMessage());
                    }
                }))
                .concatWith(Mono.fromSupplier(() -> {
                    // 6. 流结束后，添加分析元数据
                    try {
                        String fullContent = fullResponse.get().toString();
                        ContentAnalysis analysis = contentAnalysisService.analyze(fullContent);
                        ChatResponse finalResponse = ChatResponse.builder()
                                .content(fullContent)
                                .importance(analysis.getImportance())
                                .tags(analysis.getTags())
                                .sentiment(analysis.getSentiment() != null ? analysis.getSentiment().name() : "NEUTRAL")
                                .summary(analysis.getSummary())
                                .isComplete(true)
                                .build();
                        String jsonData = objectMapper.writeValueAsString(finalResponse);
                        return ServerSentEvent.<String>builder()
                                .data(jsonData)
                                .event("complete")
                                .build();
                    } catch (JsonProcessingException e) {
                        return ServerSentEvent.<String>builder()
                                .data("{\"error\":\"analysis error\"}")
                                .build();
                    }
                }));
    }

    // ==================== 测试接口 ====================

    /**
     * 测试流式响应 - 用于诊断问题
     * 每个 token 都会记录时间戳
     */
    @GetMapping(value = "/stream/test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> testStream(@RequestParam("message") String message) {
        log.info("测试流式响应开始: message={}, time={}", message, Instant.now());

        return qwenChatService.chat(message)
                .doOnSubscribe(s -> log.info("客户端订阅: time={}", Instant.now()))
                .doOnNext(chunk -> log.info("收到 chunk: time={}, content={}", Instant.now(), chunk))
                .doOnComplete(() -> log.info("流完成: time={}", Instant.now()))
                .doOnCancel(() -> log.info("客户端取消: time={}", Instant.now()))
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }
}