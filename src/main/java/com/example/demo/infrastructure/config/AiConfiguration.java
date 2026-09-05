package com.example.demo.infrastructure.config;

import com.example.demo.infrastructure.properties.OllamaEmbeddingProperties;
import com.example.demo.infrastructure.properties.OpenAiChatProperties;
import com.example.demo.infrastructure.properties.OpenAiEmbeddingProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * AI 模型与全局 Jackson 配置。
 *
 * <p>这些 Bean（embeddingModel、chatModel、structuredChatModel、streamingChatModel、
 * objectMapper）被多个模块复用，因此放到 OPEN 类型的 {@code infrastructure} 模块，
 * 任何模块都可以注入。</p>
 */
@Slf4j
@Configuration
public class AiConfiguration {

    @PostConstruct
    public void init() {
        log.info("AI models initialized");
    }

    /**
     * 主 Embedding 模型。按 {@link OpenAiEmbeddingProperties#isEnabled()} 选择
     * OpenAI 兼容实现或保留 Ollama；都不满足时回退内嵌 ONNX 模型，使应用仍可启动。
     */
    @Bean("embeddingModel")
    @Primary
    public EmbeddingModel embeddingModel(OpenAiEmbeddingProperties openAiEmbedding,
                                          OllamaEmbeddingProperties ollamaEmbedding) {
        if (openAiEmbedding.isEnabled()) {
            return openAiEmbeddingModel(openAiEmbedding);
        }
        if (ollamaEmbedding.getBaseUrl() == null || ollamaEmbedding.getBaseUrl().isBlank()) {
            throw new IllegalStateException(
                    "no embedding provider configured; set langchain4j.open-ai.embedding-model.enabled=true"
                            + " or langchain4j.ollama.embedding-model.base-url");
        }
        log.info("embedding provider: ollama baseUrl={} model={}",
                ollamaEmbedding.getBaseUrl(), ollamaEmbedding.getModelName());
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaEmbedding.getBaseUrl())
                .modelName(ollamaEmbedding.getModelName())
                .build();
    }

    private EmbeddingModel openAiEmbeddingModel(OpenAiEmbeddingProperties properties) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "langchain4j.open-ai.embedding-model.api-key is required when enabled=true");
        }
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .maxRetries(properties.getMaxRetries())
                .logRequests(properties.isLogRequests())
                .logResponses(properties.isLogResponses());
        if (properties.getDimensions() > 0) {
            builder.dimensions(properties.getDimensions());
        }
        log.info("embedding provider: openai-compatible baseUrl={} model={} dimensions={}",
                properties.getBaseUrl(), properties.getModelName(), properties.getDimensions());
        return builder.build();
    }

    /**
     * 主聊天模型（OpenAI 兼容协议）。
     */
    @Bean
    public ChatModel chatModel(OpenAiChatProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .baseUrl(properties.getBaseUrl())
                .timeout(Duration.ofSeconds(120))
                .maxRetries(3)
                .logRequests(properties.isLogRequests())
                .logResponses(properties.isLogResponses())
                .build();
    }

    /**
     * 用于结构化输出的 ChatModel - 强制返回 JSON 格式
     */
    @Bean("structuredChatModel")
    public ChatModel structuredChatModel(OpenAiChatProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .baseUrl(properties.getBaseUrl())
                .responseFormat("json_object")
                .timeout(Duration.ofSeconds(120))
                .maxRetries(3)
                .build();
    }

    /**
     * ObjectMapper Bean - 用于 JSON 序列化/反序列化
     * 包含 JavaTimeModule 支持 LocalDateTime 序列化
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        DateTimeFormatter flexibleDateTimeFormatter = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd")
                .optionalStart().appendLiteral(' ').optionalEnd()
                .optionalStart().appendLiteral('T').optionalEnd()
                .appendPattern("HH:mm:ss")
                .toFormatter();

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addDeserializer(
                LocalDateTime.class,
                new LocalDateTimeDeserializer(flexibleDateTimeFormatter)
        );

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(javaTimeModule);
        // 禁用日期时间作为时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * 流式聊天模型（OpenAI 兼容协议）。
     */
    @Bean("streamingChatModel")
    public StreamingChatModel streamingChatModel(OpenAiChatProperties properties) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .baseUrl(properties.getBaseUrl())
                .timeout(Duration.ofSeconds(180))
                .build();
    }

}
