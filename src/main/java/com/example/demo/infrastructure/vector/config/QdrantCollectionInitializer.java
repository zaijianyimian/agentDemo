package com.example.demo.infrastructure.vector.config;

import com.example.demo.infrastructure.properties.AppMemoryProperties;
import com.example.demo.infrastructure.vector.QdrantConnectionSupport;
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

    private final AppMemoryProperties appMemoryProperties;
    private final QdrantConnectionSupport qdrantConnectionSupport;

    /**
     * 构造时注入应用记忆属性和 Qdrant 连接辅助
     */
    public QdrantCollectionInitializer(AppMemoryProperties appMemoryProperties,
                                       QdrantConnectionSupport qdrantConnectionSupport) {
        this.appMemoryProperties = appMemoryProperties;
        this.qdrantConnectionSupport = qdrantConnectionSupport;
    }

    /**
     * 启动时检查并按需创建 Qdrant 集合
     */
    @PostConstruct
    public void init() {
        String host = resolveHttpHost();
        int grpcPort = resolveGrpcPort();
        int restPort = resolveRestPort();
        String collectionName = resolveCollectionName();

        log.info("Checking Qdrant collection: {} at {} (grpc={}, rest={})", collectionName, host, grpcPort, restPort);

        try {
            if (!collectionExists(host, restPort, collectionName)) {
                log.info("Collection not found, creating: {}", collectionName);
                createCollection(host, restPort, collectionName);
            } else {
                log.info("Collection already exists: {}", collectionName);
            }
        } catch (Exception e) {
            log.warn("Qdrant 集合初始化失败 {}..", e.getMessage());
            log.warn("请确保Qdrant在 {} 上正常运行，REST 端口为 {}", host, restPort);
        }
    }

    private boolean collectionExists(String host, int port, String collectionName) throws Exception {
        String url = host + ":" + port + "/collections/" + collectionName;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        qdrantConnectionSupport.applyApiKey(conn);

        try {
            int status = conn.getResponseCode();
            return status == 200;
        } finally {
            conn.disconnect();
        }
    }

    private void createCollection(String host, int port, String collectionName) throws Exception {
        String url = host + ":" + port + "/collections/" + collectionName;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PUT");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        qdrantConnectionSupport.applyApiKey(conn);

        int vectorSize = qdrantConnectionSupport.vectorSize(appMemoryProperties.getVectorSize());
        String json = """
        {
            "vectors": {
                "size": %d,
                "distance": "Cosine"
            }
        }
        """.formatted(vectorSize);

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

    private String resolveHttpHost() {
        return qdrantConnectionSupport.httpHost();
    }

    private int resolveGrpcPort() {
        return qdrantConnectionSupport.grpcPort();
    }

    private int resolveRestPort() {
        return qdrantConnectionSupport.restPort();
    }

    private String resolveCollectionName() {
        return qdrantConnectionSupport.collectionName(appMemoryProperties.getCollectionName());
    }
}
