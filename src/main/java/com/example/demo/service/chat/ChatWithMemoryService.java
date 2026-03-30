package com.example.demo.service.chat;

import com.example.demo.service.ai.ModelManager;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带记忆功能的聊天服务
 * 每个会话(sessionId)拥有独立的对话记忆
 * 聊天统一使用当前启用模型
 */
@Slf4j
@Service
public class ChatWithMemoryService {

    private final ModelManager modelManager;

    public ChatWithMemoryService(ModelManager modelManager) {
        this.modelManager = modelManager;
    }

    /**
     * 带记忆的聊天接口
     */
    public interface ChatWithMemory {
        /**
         * 普通聊天 - 带记忆
         * @param memoryId 会话ID，用于区分不同会话的记忆
         * @param question 用户问题
         * @return AI回答
         */
        String chat(@V("memoryId") Object memoryId, @UserMessage String question);

        /**
         * 流式聊天 - 带记忆
         * @param memoryId 会话ID，用于区分不同会话的记忆
         * @param question 用户问题
         * @return AI流式回答
         */
        Flux<String> streamChat(@V("memoryId") Object memoryId, @UserMessage String question);
    }

    // 缓存不同模型的 ChatWithMemory 实例
    private final Map<Long, ChatWithMemory> chatWithMemoryCache = new ConcurrentHashMap<>();

    /**
     * 获取带记忆的聊天服务实例
     * 根据模型ID动态创建或获取缓存实例
     * @param modelId 模型ID
     */
    public ChatWithMemory getChatWithMemory(Long modelId) {
        // 从缓存获取或创建新实例
        return chatWithMemoryCache.computeIfAbsent(modelId, id -> {
            log.info("Creating ChatWithMemory for modelId: {}", id);

            return AiServices.builder(ChatWithMemory.class)
                    .chatModel(modelManager.getChatModel(id))
                    .streamingChatModel(modelManager.getStreamingChatModel(id))
                    .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                    .build();
        });
    }

    public Long resolveActiveModelId() {
        return modelManager.getDefaultModelId();
    }

    /**
     * 带记忆的普通聊天
     * @param sessionId 会话ID
     * @param question 用户问题
     * @return AI回答
     */
    public String chat(Long sessionId, String question) {
        Long activeModelId = resolveActiveModelId();
        log.info("Chat with sessionId={}, activeModelId={}", sessionId, activeModelId);
        return getChatWithMemory(activeModelId).chat(sessionId, question);
    }

    /**
     * 带记忆的普通聊天（指定模型）
     * @param sessionId 会话ID
     * @param question 用户问题
     * @param modelId 模型ID（兼容参数，当前会忽略并使用启用模型）
     * @return AI回答
     */
    public String chat(Long sessionId, String question, Long modelId) {
        if (modelId != null) {
            log.info("Ignore requested modelId={}, use active model instead", modelId);
        }
        return chat(sessionId, question);
    }

    /**
     * 带记忆的流式聊天
     * @param sessionId 会话ID
     * @param question 用户问题
     * @return AI流式回答
     */
    public Flux<String> streamChat(Long sessionId, String question) {
        Long activeModelId = resolveActiveModelId();
        log.info("Stream chat with sessionId={}, activeModelId={}", sessionId, activeModelId);
        return getChatWithMemory(activeModelId).streamChat(sessionId, question);
    }

    /**
     * 带记忆的流式聊天（指定模型）
     * @param sessionId 会话ID
     * @param question 用户问题
     * @param modelId 模型ID（兼容参数，当前会忽略并使用启用模型）
     * @return AI流式回答
     */
    public Flux<String> streamChat(Long sessionId, String question, Long modelId) {
        if (modelId != null) {
            log.info("Ignore requested modelId={}, use active model instead", modelId);
        }
        return streamChat(sessionId, question);
    }

    /**
     * 刷新模型缓存（当模型配置更新时调用）
     * @param modelId 模型ID
     */
    public void refreshModel(Long modelId) {
        chatWithMemoryCache.remove(modelId);
        log.info("Refreshed ChatWithMemory cache for modelId: {}", modelId);
    }
}
