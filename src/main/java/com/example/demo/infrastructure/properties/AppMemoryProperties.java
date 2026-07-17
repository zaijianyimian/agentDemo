package com.example.demo.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 应用级记忆（向量）配置属性
 * 绑定 app.memory.* 配置项：集合名、向量维度、TopK、相似度阈值
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.memory")
public class AppMemoryProperties {
    private String collectionName = "app-memory";
    private int vectorSize = 768;
    private int topK = 5;
    private double minScore = 0.5;
}
