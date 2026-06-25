package com.example.demo.controller;

import com.example.demo.service.chat.ChatHistoryService;
import com.example.demo.service.mcp.McpAgentService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MCP Agent 聊天控制器
 * 提供带工具调用能力的 AI 对话接口
 */
@RestController
@RequestMapping("/api/mcp/agent")
public class McpAgentController {

    @Resource
    private McpAgentService mcpAgentService;

    @Resource(name = "mcpAgentStreamingService")
    private McpAgentService mcpAgentStreamingService;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * 普通对话 - AI 可自动调用工具
     *
     * @param message 用户消息
     * @return AI 响应
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return mcpAgentService.chat(withRuntimeContext(message));
    }

    /**
     * POST 方式对话 - AI 可自动调用工具
     *
     * @param request 请求体
     * @return AI 响应
     */
    @PostMapping("/chat")
    public String chatPost(@RequestBody ChatRequest request) {
        return mcpAgentService.chat(withRuntimeContext(request.message()));
    }

    /**
     * 带会话记忆的对话 - AI 可自动调用工具
     *
     * @param sessionId 会话 ID
     * @param message   用户消息
     * @return AI 响应
     */
    @GetMapping("/chat/{sessionId}")
    public String chatWithMemory(
            @PathVariable String sessionId,
            @RequestParam String message) {
        Long chatSessionId = parseSessionId(sessionId);
        if (chatSessionId != null) {
            chatHistoryService.addMessage(chatSessionId, "user", message, null);
        }
        String response = mcpAgentService.chatWithMemory(sessionId, withRuntimeContext(message));
        if (chatSessionId != null) {
            chatHistoryService.addMessage(chatSessionId, "assistant", response, "mcp-agent");
        }
        return response;
    }

    /**
     * 流式对话 - AI 可自动调用工具
     *
     * @param message 用户消息
     * @return SSE 流式响应
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam String message) {
        return mcpAgentStreamingService.chatStream(withRuntimeContext(message))
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * 带会话记忆的流式对话 - AI 可自动调用工具
     *
     * @param sessionId 会话 ID
     * @param message   用户消息
     * @return SSE 流式响应
     */
    @GetMapping(value = "/chat/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStreamWithMemory(
            @PathVariable String sessionId,
            @RequestParam String message) {
        Long chatSessionId = parseSessionId(sessionId);
        if (chatSessionId != null) {
            chatHistoryService.addMessage(chatSessionId, "user", message, null);
        }
        AtomicReference<StringBuilder> fullResponse = new AtomicReference<>(new StringBuilder());
        return mcpAgentStreamingService.chatStreamWithMemory(sessionId, withRuntimeContext(message))
                .doOnNext(chunk -> fullResponse.get().append(chunk))
                .doOnComplete(() -> {
                    if (chatSessionId != null) {
                        chatHistoryService.addMessage(chatSessionId, "assistant", fullResponse.get().toString(), "mcp-agent");
                    }
                })
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * 聊天请求
     */
    public record ChatRequest(String message) {}

    private String withRuntimeContext(String message) {
        return """
                当前服务器时间: %s
                当用户使用“今天/明天/后天/下周”等相对日期时，请基于这个时间换算为明确日期后再调用工具。

                用户消息:
                %s
                """.formatted(
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                message
        );
    }

    private Long parseSessionId(String sessionId) {
        try {
            return Long.parseLong(sessionId);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
