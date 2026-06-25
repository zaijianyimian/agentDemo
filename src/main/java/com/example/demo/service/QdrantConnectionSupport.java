package com.example.demo.service;

import com.example.demo.properties.QdrantProperties;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;

@Component
public class QdrantConnectionSupport {

    private final QdrantProperties qdrantProperties;
    private final SystemSettingsService settingsService;

    public QdrantConnectionSupport(QdrantProperties qdrantProperties,
                                   SystemSettingsService settingsService) {
        this.qdrantProperties = qdrantProperties;
        this.settingsService = settingsService;
    }

    public String rawHost() {
        return setting("host", qdrantProperties.getHost());
    }

    public String httpHost() {
        String host = rawHost();
        if (host == null || host.isBlank()) {
            return "http://localhost";
        }
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return stripPort(host);
        }
        return (useTls() ? "https://" : "http://") + stripPort(host);
    }

    public String grpcHost() {
        String host = rawHost();
        if (host == null || host.isBlank()) {
            return "localhost";
        }
        return stripPort(host.replaceFirst("^https?://", ""));
    }

    public int grpcPort() {
        return intSetting("port", qdrantProperties.getPort());
    }

    public int restPort() {
        int grpcPort = grpcPort();
        return intSetting(
                "rest_port",
                qdrantProperties.getRestPort() > 0 ? qdrantProperties.getRestPort() : (grpcPort == 6334 ? 6333 : grpcPort)
        );
    }

    public String collectionName(String fallback) {
        return setting("collection_name", fallback);
    }

    public int vectorSize(int fallback) {
        return intSetting("vector_size", fallback);
    }

    public String apiKey() {
        String apiKey = setting("api_key", qdrantProperties.getApiKey());
        return apiKey == null ? "" : apiKey.trim();
    }

    public boolean useTls() {
        String configured = setting("use_tls", String.valueOf(qdrantProperties.isUseTls()));
        if ("true".equalsIgnoreCase(configured) || "1".equals(configured)) {
            return true;
        }
        String host = rawHost();
        return host != null && host.startsWith("https://");
    }

    public String restUrl(String path) {
        return httpHost() + ":" + restPort() + path;
    }

    public void applyApiKey(HttpURLConnection connection) {
        String apiKey = apiKey();
        if (!apiKey.isBlank()) {
            connection.setRequestProperty("api-key", apiKey);
        }
    }

    public EmbeddingStore<TextSegment> embeddingStore(String collectionName) {
        String apiKey = apiKey();
        QdrantEmbeddingStore.Builder builder = QdrantEmbeddingStore.builder()
                .host(grpcHost())
                .port(grpcPort())
                .useTls(useTls())
                .collectionName(collectionName);
        if (!apiKey.isBlank()) {
            builder.apiKey(apiKey);
        }
        return builder.build();
    }

    private String stripPort(String host) {
        return host.replaceAll(":\\d+$", "");
    }

    private String setting(String key, String fallback) {
        if (qdrantProperties.isPreferEnv() && fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return settingsService.getSetting("qdrant", key, fallback);
    }

    private int intSetting(String key, int fallback) {
        if (qdrantProperties.isPreferEnv()) {
            return fallback;
        }
        return settingsService.getIntSetting("qdrant", key, fallback);
    }
}
