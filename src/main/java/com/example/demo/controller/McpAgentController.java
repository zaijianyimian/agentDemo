package com.example.demo.controller;

import com.example.demo.service.McpAgentService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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

    /**
     * 普通对话 - AI 可自动调用工具
     *
     * @param message 用户消息
     * @return AI 响应
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return mcpAgentService.chat(message);
    }

    /**
     * POST 方式对话 - AI 可自动调用工具
     *
     * @param request 请求体
     * @return AI 响应
     */
    @PostMapping("/chat")
    public String chatPost(@RequestBody ChatRequest request) {
        return mcpAgentService.chat(request.message());
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
        return mcpAgentService.chatWithMemory(sessionId, message);
    }

    /**
     * 流式对话 - AI 可自动调用工具
     *
     * @param message 用户消息
     * @return SSE 流式响应
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String message) {
        return mcpAgentStreamingService.chatStream(message);
    }

    /**
     * 带会话记忆的流式对话 - AI 可自动调用工具
     *
     * @param sessionId 会话 ID
     * @param message   用户消息
     * @return SSE 流式响应
     */
    @GetMapping(value = "/chat/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStreamWithMemory(
            @PathVariable String sessionId,
            @RequestParam String message) {
        return mcpAgentStreamingService.chatStreamWithMemory(sessionId, message);
    }

    /**
     * 聊天请求
     */
    public record ChatRequest(String message) {}
}