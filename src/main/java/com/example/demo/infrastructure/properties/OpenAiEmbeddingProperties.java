package com.example.demo.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容协议 Embedding 模型配置属性。
 *
 * <p>绑定 {@code langchain4j.open-ai.embedding-model.*} 配置项；启用时替换默认
 * {@link OllamaEmbeddingProperties} 提供的 Ollama 实现。可指向 DashScope、
 * OpenAI、Azure OpenAI 或其他 OpenAI 兼容 embedding 服务。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "langchain4j.open-ai.embedding-model")
public class OpenAiEmbeddingProperties {

    /** 启用开关；false 时保持 Ollama Embedding。 */
    private boolean enabled = false;

    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String modelName = "text-embedding-3-small";
    private int dimensions = 0;
    private int timeoutSeconds = 30;
    private int maxRetries = 3;
    private boolean logRequests = false;
    private boolean logResponses = false;
}
