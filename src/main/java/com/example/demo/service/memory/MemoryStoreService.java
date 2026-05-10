package com.example.demo.service.memory;

import com.example.demo.memory.MemoryRecord;
import com.example.demo.properties.AppMemoryProperties;
import com.example.demo.properties.QdrantProperties;
import com.example.demo.service.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class MemoryStoreService {
    private final EmbeddingCacheService embeddingCacheService;
    private final AppMemoryProperties memoryProperties;
    private final QdrantProperties qdrantProperties;
    private final SystemSettingsService settingsService;
    private final ObjectMapper ObjectMapper;

    public MemoryStoreService(EmbeddingCacheService embeddingCacheService,
                              AppMemoryProperties memoryProperties,
                              QdrantProperties qdrantProperties,
                              SystemSettingsService settingsService,
                              ObjectMapper ObjectMapper) {
        this.embeddingCacheService = embeddingCacheService;
        this.memoryProperties = memoryProperties;
        this.qdrantProperties = qdrantProperties;
        this.settingsService = settingsService;
        this.ObjectMapper = ObjectMapper;
    }

    public boolean save(MemoryRecord record){
        if (record == null || Boolean.FALSE.equals(record.getShouldStore()) || record.getSummary() == null || record.getSummary().isBlank()){
            return false;
        }
        Metadata metadata = new Metadata();
        metadata.put("sessionId", record.getSessionId());
        metadata.put("category", record.getCategory());
        metadata.put("importance", String.valueOf(record.getImportance()));
        metadata.put("createdAt", String.valueOf(record.getCreateAt() == null ? new Date() : record.getCreateAt()));
        metadata.put("tags", record.getTags() == null ? "" : String.join(",", record.getTags()));

        try{
            metadata.put("metadata",ObjectMapper.writeValueAsString(record.getMetadata() == null ? Map.of() : record.getMetadata()));
        }catch (Exception e){
            metadata.put("metadata", "{}");
            e.printStackTrace();
        }

        TextSegment textSegment  =  TextSegment.from(record.getSummary(),metadata);
        Embedding embedding = embeddingCacheService.getEmbedding(record.getSummary());
        ensureCollection(embedding.vector().length);
        embeddingStore().add(embedding,textSegment);
        return true;
    }
    public List<Map<String, Object>> search(String query, int topK) {
        return search(query, topK, null, null);
    }

    public List<Map<String, Object>> search(String query, int topK, String sessionId, String category) {
        Embedding queryEmbedding = embeddingCacheService.getEmbedding(query);
        ensureCollection(queryEmbedding.vector().length);

        var filterBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK > 0 ? topK : settingsService.getIntSetting("qdrant", "top_k", memoryProperties.getTopK()))
                .minScore(settingsService.getDoubleSetting("qdrant", "min_score", memoryProperties.getMinScore()));

        // 添加过滤条件
        Filter combinedFilter = null;
        if (sessionId != null && !sessionId.isBlank()) {
            Filter filter = metadataKey("sessionId").isEqualTo(sessionId);
            combinedFilter = filter;
        }
        if (category != null && !category.isBlank()) {
            Filter filter = metadataKey("category").isEqualTo(category);
            combinedFilter = combinedFilter == null ? filter : combinedFilter.and(filter);
        }
        if (combinedFilter != null) {
            filterBuilder.filter(combinedFilter);
        }

        EmbeddingSearchResult<TextSegment> result = embeddingStore().search(filterBuilder.build());

        return result.matches().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMap(EmbeddingMatch<TextSegment> match) {
        Map<String, Object> map = new HashMap<>();
        map.put("score", match.score());
        map.put("text", match.embedded().text());

        Metadata metadata = match.embedded().metadata();
        if (metadata != null) {
            map.put("sessionId", metadata.getString("sessionId"));
            map.put("category", metadata.getString("category"));
            map.put("importance", metadata.getString("importance"));
            map.put("createdAt", metadata.getString("createdAt"));
            map.put("tags", metadata.getString("tags"));
        }
        return map;
    }

    private EmbeddingStore<TextSegment> embeddingStore() {
        return QdrantEmbeddingStore.builder()
                .host(normalizeGrpcHost(resolveQdrantHost()))
                .port(resolveQdrantGrpcPort())
                .collectionName(resolveCollectionName())
                .build();
    }

    private void ensureCollection(int vectorSize) {
        String host = normalizeHttpHost(resolveQdrantHost());
        int port = resolveQdrantRestPort();
        String collectionName = resolveCollectionName();

        try {
            HttpURLConnection getConn = (HttpURLConnection) new URL(host + ":" + port + "/collections/" + collectionName).openConnection();
            getConn.setRequestMethod("GET");
            getConn.setConnectTimeout(5000);
            getConn.setReadTimeout(5000);
            int status = getConn.getResponseCode();
            getConn.disconnect();
            if (status == 200) {
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            HttpURLConnection putConn = (HttpURLConnection) new URL(host + ":" + port + "/collections/" + collectionName).openConnection();
            putConn.setRequestMethod("PUT");
            putConn.setConnectTimeout(10000);
            putConn.setReadTimeout(10000);
            putConn.setDoOutput(true);
            putConn.setRequestProperty("Content-Type", "application/json");
            String json = """
            {
                "vectors": {
                    "size": %d,
                    "distance": "Cosine"
                }
            }
            """.formatted(vectorSize);
            try (OutputStream os = putConn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int status = putConn.getResponseCode();
            putConn.disconnect();
            if (status != 200) {
                throw new IllegalStateException("创建记忆集合失败，状态码: " + status);
            }
        } catch (Exception e) {
            throw new IllegalStateException("初始化记忆集合失败", e);
        }
    }

    private String resolveCollectionName() {
        return settingsService.getSetting("qdrant", "collection_name", memoryProperties.getCollectionName());
    }

    private String resolveQdrantHost() {
        return settingsService.getSetting("qdrant", "host", qdrantProperties.getHost());
    }

    private int resolveQdrantGrpcPort() {
        return settingsService.getIntSetting("qdrant", "port", qdrantProperties.getPort());
    }

    private int resolveQdrantRestPort() {
        int grpcPort = resolveQdrantGrpcPort();
        return settingsService.getIntSetting("qdrant", "rest_port", grpcPort == 6334 ? 6333 : grpcPort);
    }

    private String normalizeHttpHost(String host) {
        if (host == null || host.isBlank()) {
            return "http://localhost";
        }
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return host.replaceAll(":\\d+$", "");
        }
        return "http://" + host.replaceAll(":\\d+$", "");
    }

    private String normalizeGrpcHost(String host) {
        if (host == null || host.isBlank()) {
            return "localhost";
        }
        return host.replaceFirst("^https?://", "").replaceAll(":\\d+$", "");
    }
}
