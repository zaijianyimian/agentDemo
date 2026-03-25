package com.example.demo.config;

import com.example.demo.properties.AppMemoryProperties;
import com.example.demo.properties.OllamaEmbeddingProperties;
import com.example.demo.properties.OpenAiChatProperties;
import com.example.demo.properties.QdrantProperties;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
                .build();
    }

    @Bean("streamingChatModel")
    public StreamingChatModel streamingChatModel(OpenAiChatProperties properties){
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .baseUrl(properties.getBaseUrl())
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
