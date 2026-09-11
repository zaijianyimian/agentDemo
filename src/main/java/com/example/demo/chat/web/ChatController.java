package com.example.demo.chat.web;

import com.example.demo.chat.dto.ChatResponse;
import com.example.demo.infrastructure.graph.GraphGatewayClient;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 聊天网关控制器。
 *
 * <p>Java 仅负责鉴权、用户上下文和 HTTP/SSE 协议适配；所有 Agent、LLM、Memory、Tool
 * 执行均由 Python Graph 服务负责。</p>
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final GraphGatewayClient graphGatewayClient;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    /**
     * 非流式聊天。
     *
     * @param message 用户消息。
     * @return Python Agent 最终响应。
     */
    @GetMapping("/complete")
    public String complete(@RequestParam("message") String message) {
        return graphGatewayClient.chat(currentUserProvider.requireUserId(), null, message);
    }

    /**
     * 流式聊天。
     *
     * @param message 用户消息。
     * @return SSE 响应流。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam("message") String message) {
        return toSseStream(graphGatewayClient.streamChat(
                currentUserProvider.requireUserId(), null, message));
    }

    /**
     * JSON 格式流式聊天，保留旧前端接口兼容性。
     *
     * @param message 用户消息。
     * @return JSON SSE 响应流。
     */
    @GetMapping(value = "/stream/json", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamJson(@RequestParam("message") String message) {
        return toJsonSseStream(graphGatewayClient.streamChat(
                currentUserProvider.requireUserId(), null, message));
    }

    /**
     * 结构化聊天兼容接口。
     *
     * <p>内容分析已经迁移到 Python，因此 Java 只返回 Agent 文本和完成标记。</p>
     *
     * @param message 用户消息。
     * @return 结构化聊天响应。
     */
    @GetMapping("/structured")
    public ChatResponse chatStructured(@RequestParam("message") String message) {
        String content = graphGatewayClient.chat(currentUserProvider.requireUserId(), null, message);
        return ChatResponse.builder()
                .content(content)
                .isComplete(true)
                .build();
    }

    /**
     * 带会话 ID 的非流式聊天。
     *
     * @param message 用户消息。
     * @param sessionId 会话 ID。
     * @return Python Agent 最终响应。
     */
    @GetMapping("/complete/session")
    public String completeWithSession(
            @RequestParam("message") String message,
            @RequestParam("sessionId") Long sessionId) {
        return graphGatewayClient.chat(
                currentUserProvider.requireUserId(), String.valueOf(sessionId), message);
    }

    /**
     * 带会话 ID 的流式聊天。
     *
     * @param message 用户消息。
     * @param sessionId 会话 ID。
     * @return SSE 响应流。
     */
    @GetMapping(value = "/stream/session", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamWithSession(
            @RequestParam("message") String message,
            @RequestParam("sessionId") Long sessionId) {
        return toSseStream(graphGatewayClient.streamChat(
                currentUserProvider.requireUserId(), String.valueOf(sessionId), message));
    }

    /**
     * 带会话 ID 的 JSON 流式聊天。
     *
     * @param message 用户消息。
     * @param sessionId 会话 ID。
     * @return JSON SSE 响应流。
     */
    @GetMapping(value = "/stream/session/json", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamWithSessionJson(
            @RequestParam("message") String message,
            @RequestParam("sessionId") Long sessionId) {
        return toJsonSseStream(graphGatewayClient.streamChat(
                currentUserProvider.requireUserId(), String.valueOf(sessionId), message));
    }

    /**
     * SSE 传输层探针，不调用 Agent。
     *
     * @return 固定 SSE 数据流。
     */
    @GetMapping("/stream/probe")
    public ResponseEntity<Flux<ServerSentEvent<String>>> streamProbe() {
        Flux<ServerSentEvent<String>> ticks = Flux.interval(Duration.ofMillis(300))
                .take(5)
                .map(index -> ServerSentEvent.<String>builder()
                        .event("probe")
                        .data("tick-" + index + "@" + Instant.now())
                        .build());
        Flux<ServerSentEvent<String>> stream = ticks
                .startWith(ServerSentEvent.<String>builder().comment("connected").build())
                .concatWithValues(doneEvent());
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream);
    }

    /**
     * 旧测试接口兼容入口，实际调用 Python Graph 流式接口。
     *
     * @param message 用户消息。
     * @return SSE 响应流。
     */
    @GetMapping(value = "/stream/test", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> testStream(@RequestParam("message") String message) {
        return toSseStream(graphGatewayClient.streamChat(
                currentUserProvider.requireUserId(), null, message));
    }

    private Flux<ServerSentEvent<String>> toSseStream(Flux<String> contentStream) {
        return contentStream
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build())
                .startWith(ServerSentEvent.<String>builder().comment("connected").build())
                .concatWithValues(doneEvent());
    }

    private Flux<ServerSentEvent<String>> toJsonSseStream(Flux<String> contentStream) {
        AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());
        Flux<ServerSentEvent<String>> chunks = contentStream.map(chunk -> {
            fullResponse.get().append(chunk);
            return ServerSentEvent.<String>builder()
                    .data(writeJson(ChatResponse.contentChunk(chunk)))
                    .build();
        });
        Mono<ServerSentEvent<String>> completed = Mono.fromSupplier(() ->
                ServerSentEvent.<String>builder()
                        .event("complete")
                        .data(writeJson(ChatResponse.builder()
                                .content(fullResponse.get().toString())
                                .isComplete(true)
                                .build()))
                        .build());
        return chunks
                .startWith(ServerSentEvent.<String>builder().comment("connected").build())
                .concatWith(completed)
                .concatWithValues(doneEvent());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("聊天响应 JSON 序列化失败", error);
        }
    }

    private ServerSentEvent<String> doneEvent() {
        return ServerSentEvent.<String>builder()
                .event("done")
                .data("[DONE]")
                .build();
    }
}
