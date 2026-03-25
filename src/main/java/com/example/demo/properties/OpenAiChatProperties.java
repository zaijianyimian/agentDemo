package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
public class OpenAiChatProperties {

    private String apiKey;
    private String baseUrl;
    private String modelName;
}