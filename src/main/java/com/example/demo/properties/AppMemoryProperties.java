package com.example.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.memory")
public class AppMemoryProperties {
    private String collectionName = "app-memory";
    private int vectorSize = 768;
    private int topK = 5;
    private double minScore = 0.5;
}
