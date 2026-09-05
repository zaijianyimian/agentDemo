package com.example.demo.infrastructure.vector;

import com.example.demo.infrastructure.properties.QdrantProperties;
import com.example.demo.infrastructure.settings.RuntimeSettingsProvider;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qdrant 连接辅助类
 * 集中处理 host/端口、TLS、API Key、集合名等配置解析，并构造 REST URL 与 LangChain4j EmbeddingStore。
 * 配置来源优先 RuntimeSettingsProvider（数据库动态配置），回退到 QdrantProperties。
 */
@Component
public class QdrantConnectionSupport {

    private final QdrantProperties qdrantProperties;
    private final ObjectProvider<RuntimeSettingsProvider> settingsProvider;
    private final Map<String, QdrantEmbeddingStore> embeddingStores = new ConcurrentHashMap<>();

    /**
     * 构造时注入 Qdrant 属性和动态设置提供者
     */
    public QdrantConnectionSupport(QdrantProperties qdrantProperties,
                                   ObjectProvider<RuntimeSettingsProvider> settingsProvider) {
        this.qdrantProperties = qdrantProperties;
        this.settingsProvider = settingsProvider;
    }

    /**
     * 获取原始 host 字符串（不附加协议或端口）
     */
    public String rawHost() {
        return setting("host", qdrantProperties.getHost());
    }

    /**
     * 获取带协议前缀的 HTTP host，未配置时默认 http://localhost
     */
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

    /**
     * 获取 gRPC 用的 host（去掉协议前缀）
     */
    public String grpcHost() {
        String host = rawHost();
        if (host == null || host.isBlank()) {
            return "localhost";
        }
        return stripPort(host.replaceFirst("^https?://", ""));
    }

    /**
     * 获取 gRPC 端口
     */
    public int grpcPort() {
        return intSetting("port", qdrantProperties.getPort());
    }

    /**
     * 获取 REST 端口，未配置时根据 gRPC 端口推断
     */
    public int restPort() {
        int grpcPort = grpcPort();
        return intSetting(
                "rest_port",
                qdrantProperties.getRestPort() > 0 ? qdrantProperties.getRestPort() : (grpcPort == 6334 ? 6333 : grpcPort)
        );
    }

    /**
     * 获取集合名称，未配置时使用 fallback
     */
    public String collectionName(String fallback) {
        return setting("collection_name", fallback);
    }

    /**
     * 获取向量维度，未配置时使用 fallback
     */
    public int vectorSize(int fallback) {
        return intSetting("vector_size", fallback);
    }

    /**
     * 获取 API Key（已 trim，未配置返回空串）
     */
    public String apiKey() {
        String apiKey = setting("api_key", qdrantProperties.getApiKey());
        return apiKey == null ? "" : apiKey.trim();
    }

    /**
     * 是否使用 TLS 连接到 Qdrant
     */
    public boolean useTls() {
        String configured = setting("use_tls", String.valueOf(qdrantProperties.isUseTls()));
        if ("true".equalsIgnoreCase(configured) || "1".equals(configured)) {
            return true;
        }
        String host = rawHost();
        return host != null && host.startsWith("https://");
    }

    /**
     * 构造 Qdrant REST API 的完整 URL
     *
     * @param path 接口路径，例如 /collections/foo
     */
    public String restUrl(String path) {
        return httpHost() + ":" + restPort() + path;
    }

    /**
     * 将 API Key 写入 HttpURLConnection 请求头（如已配置）
     */
    public void applyApiKey(HttpURLConnection connection) {
        String apiKey = apiKey();
        if (!apiKey.isBlank()) {
            connection.setRequestProperty("api-key", apiKey);
        }
    }

    /**
     * 构造 LangChain4j 使用的 Qdrant EmbeddingStore
     */
    public EmbeddingStore<TextSegment> embeddingStore(String collectionName) {
        String apiKey = apiKey();
        String host = grpcHost();
        int port = grpcPort();
        boolean tls = useTls();
        String cacheKey = host + ':' + port + ':' + tls + ':' + collectionName + ':' + apiKey;

        return embeddingStores.computeIfAbsent(cacheKey, ignored -> {
            QdrantEmbeddingStore.Builder builder = QdrantEmbeddingStore.builder()
                    .host(host)
                    .port(port)
                    .useTls(tls)
                    .collectionName(collectionName);
            if (!apiKey.isBlank()) {
                builder.apiKey(apiKey);
            }
            return builder.build();
        });
    }

    /** 应用退出时关闭所有复用的 gRPC channel。 */
    @PreDestroy
    public void closeEmbeddingStores() {
        embeddingStores.values().forEach(QdrantEmbeddingStore::close);
        embeddingStores.clear();
    }

    private String stripPort(String host) {
        return host.replaceAll(":\\d+$", "");
    }

    private String setting(String key, String fallback) {
        if (qdrantProperties.isPreferEnv() && fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        RuntimeSettingsProvider provider = settingsProvider.getIfAvailable();
        return provider == null ? fallback : provider.getSetting("qdrant", key, fallback);
    }

    private int intSetting(String key, int fallback) {
        if (qdrantProperties.isPreferEnv()) {
            return fallback;
        }
        RuntimeSettingsProvider provider = settingsProvider.getIfAvailable();
        return provider == null ? fallback : provider.getIntSetting("qdrant", key, fallback);
    }
}
