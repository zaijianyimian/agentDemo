package com.example.demo.model.application;

import com.example.demo.infrastructure.properties.OpenAiChatProperties;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import com.example.demo.model.domain.AiModelConfig;
import com.example.demo.model.persistence.AiModelConfigMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多用户 AI 模型管理器。
 *
 * <p>数据库模型配置由 TenantLine 按 user_id 隔离；Spring Bean 模型仍作为系统级 fallback。
 * 即使模型对象已进入 JVM 缓存，每次按 ID 取模型前也必须先查一次当前用户可见配置，防止通过猜测 ID
 * 命中另一个用户之前加载过的缓存对象。</p>
 */
@Slf4j
@Service
public class ModelManager {

    public static final Long FALLBACK_MODEL_ID = -1L;

    private final AiModelConfigMapper configMapper;
    private final EncodingService encodingService;
    private final ChatModel fallbackChatModel;
    private final StreamingChatModel fallbackStreamingModel;
    private final OpenAiChatProperties chatProperties;
    private final CurrentUserProvider currentUserProvider;

    private final Map<Long, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<Long, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();
    private final Map<String, ChatModel> chatModelByPurposeCache = new ConcurrentHashMap<>();

    public ModelManager(
            AiModelConfigMapper configMapper,
            EncodingService encodingService,
            @Qualifier("chatModel") ChatModel fallbackChatModel,
            @Qualifier("streamingChatModel") StreamingChatModel fallbackStreamingModel,
            OpenAiChatProperties chatProperties,
            CurrentUserProvider currentUserProvider) {
        this.configMapper = configMapper;
        this.encodingService = encodingService;
        this.fallbackChatModel = fallbackChatModel;
        this.fallbackStreamingModel = fallbackStreamingModel;
        this.chatProperties = chatProperties;
        this.currentUserProvider = currentUserProvider;
    }

    private ChatModel createChatModel(AiModelConfig config) {
        return OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(encodingService.decode(config.getApiKey()))
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(120))
                .maxRetries(3)
                .logRequests(chatProperties.isLogRequests())
                .logResponses(chatProperties.isLogResponses())
                .build();
    }

    private StreamingChatModel createStreamingChatModel(AiModelConfig config) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(encodingService.decode(config.getApiKey()))
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(180))
                .build();
    }

    /** 获取当前用户可见的 ChatModel。 */
    public ChatModel getChatModel(Long id) {
        if (FALLBACK_MODEL_ID.equals(id)) {
            return fallbackChatModel;
        }
        AiModelConfig config = requireVisibleEnabledConfig(id);
        return chatModelCache.computeIfAbsent(id, ignored -> createChatModel(config));
    }

    /** 获取当前用户可见的 StreamingChatModel。 */
    public StreamingChatModel getStreamingChatModel(Long id) {
        if (FALLBACK_MODEL_ID.equals(id)) {
            return fallbackStreamingModel;
        }
        AiModelConfig config = requireVisibleEnabledConfig(id);
        return streamingModelCache.computeIfAbsent(id, ignored -> createStreamingChatModel(config));
    }

    /** 获取当前用户默认模型 ID；无数据库配置则使用系统 fallback。 */
    public Long getDefaultModelId() {
        AiModelConfig defaultConfig = configMapper.selectDefault();
        if (defaultConfig != null) {
            return defaultConfig.getId();
        }
        List<AiModelConfig> enabledConfigs = configMapper.selectEnabled();
        return enabledConfigs.isEmpty() ? FALLBACK_MODEL_ID : enabledConfigs.get(0).getId();
    }

    public boolean hasDatabaseModels() {
        return !configMapper.selectEnabled().isEmpty();
    }

    public ChatModel getDefaultChatModel() {
        return getChatModel(getDefaultModelId());
    }

    public StreamingChatModel getDefaultStreamingChatModel() {
        return getStreamingChatModel(getDefaultModelId());
    }

    /** 刷新当前用户可见模型缓存。 */
    public void refreshModel(Long id) {
        removeModel(id);
        AiModelConfig config = configMapper.selectById(id);
        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            chatModelCache.put(id, createChatModel(config));
            streamingModelCache.put(id, createStreamingChatModel(config));
        }
    }

    public void removeModel(Long id) {
        chatModelCache.remove(id);
        streamingModelCache.remove(id);
        chatModelByPurposeCache.clear();
    }

    /** 测试用户提交的新模型连接。 */
    public String testConnection(AiModelConfig config) {
        ChatModel testModel = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(10))
                .maxRetries(0)
                .build();
        try {
            String response = testModel.chat("Hello, please respond with 'OK' to confirm connection.");
            return "连接成功: " + (response != null && response.length() > 50
                    ? response.substring(0, 50) + "..." : response);
        } catch (Exception error) {
            return "连接失败: " + error.getMessage();
        }
    }

    /**
     * 按 purpose 获取当前用户模型。缓存键包含 userId，避免不同用户共用 purpose 缓存。
     */
    public ChatModel getChatModelByPurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return null;
        }
        long userId = currentUserProvider.requireUserId();
        String cacheKey = userId + ":" + purpose;
        return chatModelByPurposeCache.computeIfAbsent(cacheKey, ignored -> {
            List<AiModelConfig> configs = configMapper.selectEnabledByPurpose(purpose);
            if (configs == null || configs.isEmpty()) {
                return null;
            }
            return createChatModel(configs.get(0));
        });
    }

    private AiModelConfig requireVisibleEnabledConfig(Long id) {
        AiModelConfig config = configMapper.selectById(id);
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new IllegalArgumentException("模型不存在、无权访问或未启用: " + id);
        }
        return config;
    }
}
