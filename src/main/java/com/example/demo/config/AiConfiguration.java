package com.example.demo.config;

import com.example.demo.properties.AppMemoryProperties;
import com.example.demo.properties.OllamaEmbeddingProperties;
import com.example.demo.properties.OpenAiChatProperties;
import com.example.demo.properties.QdrantProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Slf4j
@Configuration
public class AiConfiguration {

    private final QdrantProperties qdrantProperties;
    private final AppMemoryProperties appMemoryProperties;

    public AiConfiguration(QdrantProperties qdrantProperties, AppMemoryProperties appMemoryProperties) {
        this.qdrantProperties = qdrantProperties;
        this.appMemoryProperties = appMemoryProperties;
    }

    @PostConstruct
    public void init() {
        String host = qdrantProperties.getHost();
        int port = qdrantProperties.getPort();
        String collectionName = appMemoryProperties.getCollectionName();

        log.info("Qdrant configured at {}:{}, collection: {}", host, port, collectionName);
        log.info("Note: Ensure Qdrant collection '{}' exists before storing embeddings", collectionName);
    }

    @Bean("embeddingModel")
    @Primary
    public EmbeddingModel embeddingModel(OllamaEmbeddingProperties properties){
        return OllamaEmbeddingModel.builder()
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModelName())
                .build();
    }

    @Bean
    public ChatModel chatModel(OpenAiChatProperties  properties){
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .baseUrl(properties.getBaseUrl())
                .timeout(Duration.ofSeconds(120))
                .maxRetries(3)
                .logRequests(true)
                .logResponses(true)
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

    @Bean("streamingChatModel")
    public StreamingChatModel streamingChatModel(OpenAiChatProperties properties){
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .baseUrl(properties.getBaseUrl())
                .timeout(Duration.ofSeconds(180))
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(QdrantProperties qdrantProperties, AppMemoryProperties appMemoryProperties){
        return QdrantEmbeddingStore.builder()
                .host(qdrantProperties.getHost())
                .port(qdrantProperties.getPort())
                .collectionName(appMemoryProperties.getCollectionName())
                .build();
    }
}
