package com.example.demo.chat.web;

import com.example.demo.infrastructure.graph.GraphGatewayClient;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * MCP Agent 兼容网关。
 *
 * <p>保留旧前端路由，但 Java 不再执行 MCP Agent。所有推理、记忆和工具调用均转发给 Python
 * Graph，由 Python 决定是否以及如何调用 MCP 工具。</p>
 */
@RestController
@RequestMapping("/api/mcp/agent")
@RequiredArgsConstructor
public class McpAgentController {

    private final GraphGatewayClient graphGatewayClient;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 兼容旧 GET 聊天接口。
     *
     * @param message 用户消息。
     * @return Python Agent 响应。
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return graphGatewayClient.chat(currentUserProvider.requireUserId(), null, message);
    }

    /**
     * 兼容旧 POST 聊天接口。
     *
     * @param request 聊天请求。
     * @return Python Agent 响应。
     */
    @PostMapping("/chat")
    public String chatPost(@RequestBody ChatRequest request) {
        return graphGatewayClient.chat(currentUserProvider.requireUserId(), null, request.message());
    }

    /**
     * 兼容旧带会话聊天接口。
     *
     * @param sessionId 会话 ID。
     * @param message 用户消息。
     * @return Python Agent 响应。
     */
    @GetMapping("/chat/{sessionId}")
    public String chatWithSession(
            @PathVariable String sessionId,
            @RequestParam String message) {
        return graphGatewayClient.chat(currentUserProvider.requireUserId(), sessionId, message);
    }

    /**
     * 兼容旧流式聊天接口。
     *
     * @param message 用户消息。
     * @return SSE 响应流。
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam String message) {
        return toSseStream(graphGatewayClient.streamChat(
                currentUserProvider.requireUserId(), null, message));
    }

    /**
     * 兼容旧带会话流式聊天接口。
     *
     * @param sessionId 会话 ID。
     * @param message 用户消息。
     * @return SSE 响应流。
     */
    @GetMapping(value = "/chat/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamWithSession(
            @PathVariable String sessionId,
            @RequestParam String message) {
        return toSseStream(graphGatewayClient.streamChat(
                currentUserProvider.requireUserId(), sessionId, message));
    }

    private Flux<ServerSentEvent<String>> toSseStream(Flux<String> contentStream) {
        return contentStream
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build())
                .startWith(ServerSentEvent.<String>builder().comment("connected").build())
                .concatWithValues(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("[DONE]")
                        .build());
    }

    /** 聊天请求。 */
    public record ChatRequest(String message) {
    }
}
