package com.example.demo.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Ollama Embedding 模型配置属性
 * 绑定 langchain4j.ollama.embedding-model.* 配置项（Base URL、模型名）
 */
@Data
@Component
@ConfigurationProperties(prefix = "langchain4j.ollama.embedding-model")
public class OllamaEmbeddingProperties {
    private String baseUrl = "http://localhost:11434";
    private String modelName = "nomic-embed-text:latest";
}
