package com.example.demo.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qdrant 向量数据库连接配置属性
 * 绑定 app.qdrant.* 配置项（host、gRPC/REST 端口、API Key、是否 TLS、是否优先使用环境变量）
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.qdrant")
public class QdrantProperties {

    private String host = "http://localhost";
    private int port = 6334;
    private int restPort = 6333;
    private String apiKey = "";
    private boolean useTls = false;
    private boolean preferEnv = false;

}
