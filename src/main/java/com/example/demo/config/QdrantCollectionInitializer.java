package com.example.demo.config;

import com.example.demo.properties.AppMemoryProperties;
import com.example.demo.properties.QdrantProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Qdrant 集合初始化器
 * 使用 HTTP REST API 检查和创建集合
 */
@Slf4j
@Component
public class QdrantCollectionInitializer {

    private final QdrantProperties qdrantProperties;
    private final AppMemoryProperties appMemoryProperties;

    public QdrantCollectionInitializer(QdrantProperties qdrantProperties,
                                       AppMemoryProperties appMemoryProperties) {
        this.qdrantProperties = qdrantProperties;
        this.appMemoryProperties = appMemoryProperties;
    }

    @PostConstruct
    public void init() {
        String host = qdrantProperties.getHost();
        int port = qdrantProperties.getPort();
        String collectionName = appMemoryProperties.getCollectionName();

        log.info("Checking Qdrant collection: {} at {}:{}", collectionName, host, port);

        try {
            if (!collectionExists(host, port, collectionName)) {
                log.info("Collection not found, creating: {}", collectionName);
                createCollection(host, port, collectionName);
            } else {
                log.info("Collection already exists: {}", collectionName);
            }
        } catch (Exception e) {
            log.warn("Qdrant 集合初始化失败 {}..", e.getMessage());
            log.warn("请确保Qdrant在 {}:{} 上正常运行", host, port);
        }
    }

    private boolean collectionExists(String host, int port, String collectionName) throws Exception {
        String url = "http://" + host + ":" + port + "/collections/" + collectionName;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        try {
            int status = conn.getResponseCode();
            return status == 200;
        } finally {
            conn.disconnect();
        }
    }

    private void createCollection(String host, int port, String collectionName) throws Exception {
        String url = "http://" + host + ":" + port + "/collections/" + collectionName;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PUT");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        // nomic-embed-text 生成 768 维向量
        String json = """
        {
            "vectors": {
                "size": 768,
                "distance": "Cosine"
            }
        }
        """;

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = json.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int status = conn.getResponseCode();
        if (status == 200) {
            log.info("Collection '{}' created successfully", collectionName);
        } else {
            log.error("Failed to create collection, status: {}", status);
            throw new RuntimeException("Failed to create collection, status: " + status);
        }

        conn.disconnect();
    }
}
