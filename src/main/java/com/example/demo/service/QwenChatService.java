package com.example.demo.service;

import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@AiService(
        wiringMode = EXPLICIT,
        chatModel = "chatModel",
        streamingChatModel = "streamingChatModel")
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
