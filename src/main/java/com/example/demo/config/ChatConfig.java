package com.example.demo.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatConfig {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String dashScopeApiKey;

    @Value("${langchain4j.ollama.embedding-model.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${langchain4j.ollama.embedding-model.model-name:nomic-embed-text:latest}")
    private String ollamaEmbeddingModelName;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String openAiModelName;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String openAiBaseUrl;

    /**
     * 通义千问流式聊天模型
     */
    @Bean(name = "streamQwen")
    public StreamingChatModel streamingChatModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(openAiModelName)
                .baseUrl(openAiBaseUrl)
                .build();
    }

    /**
     * 通义千问普通聊天模型（可选）
     */
    @Bean(name = "qwen")
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(dashScopeApiKey)
                .modelName(openAiModelName)
                .baseUrl(openAiBaseUrl)
                .build();
    }

    /**
     * Ollama Embedding 模型
     */
    @Bean("embedding")
    @Primary
    public EmbeddingModel embeddingModel() {
        return dev.langchain4j.model.ollama.OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaEmbeddingModelName)
                .build();
    }


}
