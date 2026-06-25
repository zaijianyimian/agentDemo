package com.example.demo.service.ai;

import com.example.demo.entity.AiModelConfig;
import com.example.demo.mapper.AiModelConfigMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型管理器
 * 管理多个 ChatModel 实例
 * 当数据库没有配置模型时，fallback 到 Spring Bean 配置
 */
@Slf4j
@Service
public class ModelManager {

    private final AiModelConfigMapper configMapper;
    private final EncodingService encodingService;

    // Spring Bean fallback models (from AiConfiguration)
    private final ChatModel fallbackChatModel;
    private final StreamingChatModel fallbackStreamingModel;

    // Fallback model ID constant
    public static final Long FALLBACK_MODEL_ID = -1L;

    // ChatModel 缓存
    private final Map<Long, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<Long, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    public ModelManager(
            AiModelConfigMapper configMapper,
            EncodingService encodingService,
            @Qualifier("chatModel") ChatModel fallbackChatModel,
            @Qualifier("streamingChatModel") StreamingChatModel fallbackStreamingModel) {
        this.configMapper = configMapper;
        this.encodingService = encodingService;
        this.fallbackChatModel = fallbackChatModel;
        this.fallbackStreamingModel = fallbackStreamingModel;
    }

    @PostConstruct
    public void init() {
        // 加载所有启用的模型
        List<AiModelConfig> configs = configMapper.selectEnabled();
        for (AiModelConfig config : configs) {
            try {
                chatModelCache.put(config.getId(), createChatModel(config));
                streamingModelCache.put(config.getId(), createStreamingChatModel(config));
                log.info("Loaded model: {} ({})", config.getName(), config.getModelName());
            } catch (Exception e) {
                log.error("Failed to load model: {}", config.getName(), e);
            }
        }
        log.info("Loaded {} AI models", configs.size());
    }

    /**
     * 创建 ChatModel 实例
     */
    private ChatModel createChatModel(AiModelConfig config) {
        String decodedApiKey = encodingService.decode(config.getApiKey());

        return OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(decodedApiKey)
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(120))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 创建 StreamingChatModel 实例
     */
    private StreamingChatModel createStreamingChatModel(AiModelConfig config) {
        String decodedApiKey = encodingService.decode(config.getApiKey());

        return OpenAiStreamingChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(decodedApiKey)
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(180))
                .build();
    }

    /**
     * 获取 ChatModel
     */
    public ChatModel getChatModel(Long id) {
        // Fallback to Spring Bean when using fallback ID
        if (id.equals(FALLBACK_MODEL_ID)) {
            return fallbackChatModel;
        }

        return chatModelCache.computeIfAbsent(id, key -> {
            AiModelConfig config = configMapper.selectById(id);
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                throw new IllegalArgumentException("模型不存在或未启用: " + id);
            }
            return createChatModel(config);
        });
    }

    /**
     * 获取 StreamingChatModel
     */
    public StreamingChatModel getStreamingChatModel(Long id) {
        // Fallback to Spring Bean when using fallback ID
        if (id.equals(FALLBACK_MODEL_ID)) {
            return fallbackStreamingModel;
        }

        return streamingModelCache.computeIfAbsent(id, key -> {
            AiModelConfig config = configMapper.selectById(id);
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                throw new IllegalArgumentException("模型不存在或未启用: " + id);
            }
            return createStreamingChatModel(config);
        });
    }

    /**
     * 获取默认模型ID
     * 如果数据库没有配置，返回 FALLBACK_MODEL_ID 使用 Spring Bean 配置
     */
    public Long getDefaultModelId() {
        AiModelConfig defaultConfig = configMapper.selectDefault();
        if (defaultConfig != null) {
            return defaultConfig.getId();
        }

        // 如果没有默认模型，返回第一个启用的模型
        List<AiModelConfig> enabledConfigs = configMapper.selectEnabled();
        if (!enabledConfigs.isEmpty()) {
            return enabledConfigs.get(0).getId();
        }

        // Fallback to Spring Bean configuration
        log.info("No AI models configured in database, using Spring Bean fallback");
        return FALLBACK_MODEL_ID;
    }

    /**
     * 检查是否有数据库配置的模型
     */
    public boolean hasDatabaseModels() {
        List<AiModelConfig> enabledConfigs = configMapper.selectEnabled();
        return !enabledConfigs.isEmpty();
    }

    /**
     * 获取默认模型
     */
    public ChatModel getDefaultChatModel() {
        return getChatModel(getDefaultModelId());
    }

    /**
     * 获取默认流式模型
     */
    public StreamingChatModel getDefaultStreamingChatModel() {
        return getStreamingChatModel(getDefaultModelId());
    }

    /**
     * 刷新模型缓存
     */
    public void refreshModel(Long id) {
        chatModelCache.remove(id);
        streamingModelCache.remove(id);

        AiModelConfig config = configMapper.selectById(id);
        if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
            chatModelCache.put(id, createChatModel(config));
            streamingModelCache.put(id, createStreamingChatModel(config));
            log.info("Refreshed model: {}", config.getName());
        }
    }

    /**
     * 移除模型缓存
     */
    public void removeModel(Long id) {
        chatModelCache.remove(id);
        streamingModelCache.remove(id);
        log.info("Removed model cache: {}", id);
    }

    /**
     * 测试模型连接（传入的 apiKey 是原始密钥，不需要解密）
     */
    public String testConnection(AiModelConfig config) {
        // 直接使用传入的 API Key，不进行解密（测试连接时传入的是原始密钥）
        ChatModel testModel = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .timeout(Duration.ofSeconds(10))
                .maxRetries(0)
                .build();

        try {
            String response = testModel.chat("Hello, please respond with 'OK' to confirm connection.");
            return "连接成功: " + (response != null && response.length() > 50 ? response.substring(0, 50) + "..." : response);
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }
}
