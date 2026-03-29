package com.example.demo.service.chat;

import com.example.demo.dto.ChatResponse;
import com.example.demo.dto.ContentAnalysis;
import com.example.demo.dto.SearchResult;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
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
     * @param question 用户问题
     * @param searchContext 搜索上下文
     * @return AI回答
     */
    @UserMessage("""
        请根据以下搜索结果内容，回答用户的问题。

        ===== 搜索结果 =====
        {{searchContext}}
        ===== 搜索结果结束 =====

        用户问题：{{question}}

        要求：
        1. 必须基于上面的搜索结果内容进行回答
        2. 如果搜索结果为空或显示"未找到"，请告知用户没有找到相关信息
        3. 回答要准确、简洁，并引用具体的搜索结果来源
        """)
    String chatWithSearch(@V("question") String question, @V("searchContext") String searchContext);

    /**
     * 带搜索上下文的流式聊天
     * @param question 用户问题
     * @param searchContext 搜索上下文
     * @return AI流式回答
     */
    @UserMessage("""
        请根据以下搜索结果内容，回答用户的问题。

        ===== 搜索结果 =====
        {{searchContext}}
        ===== 搜索结果结束 =====

        用户问题：{{question}}

        要求：
        1. 必须基于上面的搜索结果内容进行回答
        2. 如果搜索结果为空或显示"未找到"，请告知用户没有找到相关信息
        3. 回答要准确、简洁，并引用具体的搜索结果来源
        """)
    Flux<String> streamChatWithSearch(@V("question") String question, @V("searchContext") String searchContext);
}