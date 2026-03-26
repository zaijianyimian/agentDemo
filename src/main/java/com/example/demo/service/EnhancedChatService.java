package com.example.demo.service;

import com.example.demo.dto.ChatResponse;
import com.example.demo.dto.ContentAnalysis;
import com.example.demo.dto.SearchResult;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

import java.util.List;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

/**
 * 增强聊天服务 - 支持网络搜索
 */
@AiService(
        wiringMode = EXPLICIT,
        chatModel = "chatModel",
        streamingChatModel = "streamingChatModel")
public interface EnhancedChatService {

    /**
     * 带搜索上下文的聊天
     */
    String chatWithSearch(String question, String searchContext);

    /**
     * 带搜索上下文的流式聊天
     */
    Flux<String> streamChatWithSearch(String question, String searchContext);
}