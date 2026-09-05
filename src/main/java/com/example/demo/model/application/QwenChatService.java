package com.example.demo.model.application;

import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

/**
 * 通义千问聊天服务接口，提供流式与非流式的问答能力。
 * <p>
 * 由 LangChain4j AI 服务机制绑定指定聊天模型并生成实现。
 */
@AiService(
        wiringMode = EXPLICIT,
        chatModel = "chatModel",
        streamingChatModel = "streamingChatModel",
        tools = {"scheduleTaskTools", "markdownSkillTools", "scheduleEventTools", "emailTools"})
public interface QwenChatService {
    /**
     * 流式聊天
     */
    Flux<String> chat(String question);

    /**
     * 普通聊天 - 一次性返回完整响应
     */
    String complete(String question);
}
