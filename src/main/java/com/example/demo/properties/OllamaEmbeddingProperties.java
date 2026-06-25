package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "langchain4j.ollama.embedding-model")
public class OllamaEmbeddingProperties {
    private String baseUrl = "http://localhost:11434";
    private String modelName = "nomic-embed-text:latest";
}
