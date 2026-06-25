package com.example.demo.service.chat;

import com.example.demo.service.ai.ModelFailoverService;
import com.example.demo.service.ai.ModelManager;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 带记忆功能的聊天服务
 * 每个会话(sessionId)拥有独立的对话记忆
 * 聊天统一使用当前启用模型
 * 支持模型失败时自动切换到备用模型
 */
@Slf4j
@Service
public class ChatWithMemoryService {

    private final ModelManager modelManager;
    private final ModelFailoverService failoverService;
    private final ToolProvider toolProvider;

    // 最大重试次数
    private static final int MAX_RETRY_COUNT = 3;

    public ChatWithMemoryService(ModelManager modelManager, ModelFailoverService failoverService, ToolProvider toolProvider) {
        this.modelManager = modelManager;
        this.failoverService = failoverService;
        this.toolProvider = toolProvider;
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
        @SystemMessage("""
                你是一个支持工具调用的个人 AI 助手。
                当用户明确要求安排日程、创建提醒、记录会议/课程/待办时间时，优先调用可用的日程工具完成创建。
                工具调用完成后，用自然语言简洁告知用户创建结果；不要虚构未执行的操作。
                """)
        String chat(@V("memoryId") Object memoryId, @UserMessage String question);

        /**
         * 流式聊天 - 带记忆
         * @param memoryId 会话ID，用于区分不同会话的记忆
         * @param question 用户问题
         * @return AI流式回答
         */
        @SystemMessage("""
                你是一个支持工具调用的个人 AI 助手。
                当用户明确要求安排日程、创建提醒、记录会议/课程/待办时间时，优先调用可用的日程工具完成创建。
                工具调用完成后，用自然语言简洁告知用户创建结果；不要虚构未执行的操作。
                """)
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
                    .toolProvider(toolProvider)
                    .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                    .build();
        });
    }

    public Long resolveActiveModelId() {
        // 使用 failoverService 获取首选模型（考虑健康状态）
        return failoverService.getPreferredModelId();
    }

    /**
     * 带记忆的普通聊天（支持自动故障转移）
     * @param sessionId 会话ID
     * @param question 用户问题
     * @return AI回答
     */
    public String chat(Long sessionId, String question) {
        Long currentModelId = resolveActiveModelId();
        int retryCount = 0;

        while (retryCount < MAX_RETRY_COUNT) {
            try {
                log.info("Chat attempt {} with sessionId={}, modelId={}", retryCount + 1, sessionId, currentModelId);
                String response = getChatWithMemory(currentModelId).chat(sessionId, withRuntimeContext(question));

                // 记录成功
                failoverService.recordSuccess(currentModelId);
                return response;

            } catch (Exception e) {
                log.error("Chat failed with modelId={}, attempt {}: {}", currentModelId, retryCount + 1, e.getMessage());

                // 记录失败
                failoverService.recordFailure(currentModelId, e.getMessage());

                // 获取下一个可用模型
                Long nextModelId = failoverService.getNextAvailableModelId(currentModelId);
                if (nextModelId == null) {
                    log.error("No available backup model, throwing exception");
                    throw new RuntimeException("所有模型均不可用: " + e.getMessage(), e);
                }

                currentModelId = nextModelId;
                retryCount++;
            }
        }

        throw new RuntimeException("达到最大重试次数，无法完成聊天");
    }

    /**
     * 带记忆的流式聊天（支持自动故障转移）
     * @param sessionId 会话ID
     * @param question 用户问题
     * @return AI流式回答
     */
    public Flux<String> streamChat(Long sessionId, String question) {
        Long initialModelId = resolveActiveModelId();
        AtomicReference<Long> currentModelId = new AtomicReference<>(initialModelId);
        AtomicReference<Integer> retryCount = new AtomicReference<>(0);
        AtomicReference<StringBuilder> collectedError = new AtomicReference<>(new StringBuilder());

        return Flux.defer(() -> {
            Long modelId = currentModelId.get();
            log.info("Stream chat attempt {} with sessionId={}, modelId={}", retryCount.get() + 1, sessionId, modelId);

            return getChatWithMemory(modelId).streamChat(sessionId, withRuntimeContext(question))
                    .doOnNext(chunk -> {
                        // 成功收到数据，记录成功
                        failoverService.recordSuccess(modelId);
                    })
                    .doOnError(error -> {
                        log.error("Stream chat failed with modelId={}, attempt {}: {}",
                                modelId, retryCount.get() + 1, error.getMessage());

                        // 记录失败
                        failoverService.recordFailure(modelId, error.getMessage());
                        collectedError.get().append(error.getMessage());

                        // 获取下一个可用模型
                        Long nextModelId = failoverService.getNextAvailableModelId(modelId);
                        if (nextModelId != null && retryCount.get() < MAX_RETRY_COUNT) {
                            currentModelId.set(nextModelId);
                            retryCount.updateAndGet(v -> v + 1);
                        }
                    })
                    .onErrorResume(error -> {
                        // 检查是否还有备用模型可用
                        if (retryCount.get() < MAX_RETRY_COUNT) {
                            Long nextModelId = failoverService.getNextAvailableModelId(currentModelId.get());
                            if (nextModelId != null) {
                                log.info("Retrying stream chat with backup model: {}", nextModelId);
                                // 递归调用自身进行重试
                return streamChatWithModel(sessionId, question, nextModelId, retryCount.get() + 1);
                            }
                        }
                        // 没有备用模型或达到最大重试次数
                        return Flux.error(new RuntimeException(
                                "流式聊天失败，已尝试所有可用模型: " + collectedError.get().toString(), error));
                    });
        });
    }

    /**
     * 带记忆的流式聊天（指定模型，用于重试）
     */
    private Flux<String> streamChatWithModel(Long sessionId, String question, Long modelId, int currentRetry) {
        log.info("Stream chat retry {} with sessionId={}, modelId={}", currentRetry, sessionId, modelId);

        return getChatWithMemory(modelId).streamChat(sessionId, withRuntimeContext(question))
                .doOnNext(chunk -> failoverService.recordSuccess(modelId))
                .doOnError(error -> {
                    failoverService.recordFailure(modelId, error.getMessage());

                    Long nextModelId = failoverService.getNextAvailableModelId(modelId);
                    if (nextModelId != null && currentRetry < MAX_RETRY_COUNT) {
                        log.info("Further retry with model: {}", nextModelId);
                    }
                });
    }

    /**
     * 刷新模型缓存（当模型配置更新时调用）
     * @param modelId 模型ID
     */
    public void refreshModel(Long modelId) {
        chatWithMemoryCache.remove(modelId);
        log.info("Refreshed ChatWithMemory cache for modelId: {}", modelId);
    }

    private String withRuntimeContext(String question) {
        return """
                当前服务器时间: %s
                当用户使用“今天/明天/后天/下周”等相对日期时，请基于这个时间换算为明确日期后再调用工具。

                用户消息:
                %s
                """.formatted(
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                question
        );
    }
}
