package com.example.demo.service.mcp;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

/**
 * MCP Agent 服务接口
 * 支持动态工具调用的 AI 服务
 */
public interface McpAgentService {

    /**
     * 与 AI 对话，可自动调用已注册的工具
     *
     * @param userMessage 用户消息
     * @return AI 响应
     */
    String chat(@UserMessage("{{message}}") String message);

    /**
     * 与 AI 对话（带会话记忆），可自动调用已注册的工具
     *
     * @param memoryId    会话 ID
     * @param userMessage 用户消息
     * @return AI 响应
     */
    String chatWithMemory(@MemoryId String memoryId, @UserMessage("{{message}}") String message);

    /**
     * 流式对话，可自动调用已注册的工具
     *
     * @param userMessage 用户消息
     * @return AI 响应流
     */
    Flux<String> chatStream(@UserMessage("{{message}}") String message);

    /**
     * 流式对话（带会话记忆），可自动调用已注册的工具
     *
     * @param memoryId    会话 ID
     * @param userMessage 用户消息
     * @return AI 响应流
     */
    Flux<String> chatStreamWithMemory(@MemoryId String memoryId, @UserMessage("{{message}}") String message);
}