package com.example.demo.service.mcp;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

/**
 * MCP Agent 服务接口
 * 支持动态工具调用的 AI 服务
 */
public interface McpAgentService {

    String TOOL_CALLING_SYSTEM_MESSAGE = """
            你是一个支持工具调用的个人 AI 助手。
            当用户明确要求安排日程、创建提醒、记录会议/课程/待办时间时，优先调用可用的日程工具完成创建。
            当用户询问今天、明天、某天、某段时间、最近、某个ID或关键词对应的日程时，必须先调用日程读取工具获取真实数据，再基于工具结果回答。
            工具调用完成后，用自然语言简洁告知用户操作结果；不要虚构未执行的操作。
            """;

    /**
     * 与 AI 对话，可自动调用已注册的工具
     *
     * @param userMessage 用户消息
     * @return AI 响应
     */
    @SystemMessage(TOOL_CALLING_SYSTEM_MESSAGE)
    String chat(@UserMessage("{{message}}") String message);

    /**
     * 与 AI 对话（带会话记忆），可自动调用已注册的工具
     *
     * @param memoryId    会话 ID
     * @param userMessage 用户消息
     * @return AI 响应
     */
    @SystemMessage(TOOL_CALLING_SYSTEM_MESSAGE)
    String chatWithMemory(@MemoryId String memoryId, @UserMessage("{{message}}") String message);

    /**
     * 流式对话，可自动调用已注册的工具
     *
     * @param userMessage 用户消息
     * @return AI 响应流
     */
    @SystemMessage(TOOL_CALLING_SYSTEM_MESSAGE)
    Flux<String> chatStream(@UserMessage("{{message}}") String message);

    /**
     * 流式对话（带会话记忆），可自动调用已注册的工具
     *
     * @param memoryId    会话 ID
     * @param userMessage 用户消息
     * @return AI 响应流
     */
    @SystemMessage(TOOL_CALLING_SYSTEM_MESSAGE)
    Flux<String> chatStreamWithMemory(@MemoryId String memoryId, @UserMessage("{{message}}") String message);
}
