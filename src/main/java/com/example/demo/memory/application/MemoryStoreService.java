package com.example.demo.memory.application;

import com.example.demo.memory.domain.MemoryRecord;
import com.example.demo.infrastructure.properties.AppMemoryProperties;
import com.example.demo.infrastructure.vector.QdrantConnectionSupport;
import com.example.demo.system.application.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 记忆存储服务
 * 负责向 Qdrant 写入和检索长期记忆，支持会话与分类过滤及语义/重要度/时间衰减的综合打分排序。
 */
@Service
@Slf4j
public class MemoryStoreService {
    private static final double MIN_RECALL_SCORE = 0.6;
    private static final int MIN_CANDIDATE_LIMIT = 20;
    private static final double SEMANTIC_WEIGHT = 0.70;
    private static final double IMPORTANCE_WEIGHT = 0.20;
    private static final double TIME_DECAY_WEIGHT = 0.10;
    private static final double TIME_DECAY_FLOOR = 0.20;
    private static final double HALF_LIFE_DAYS = 30.0;

    private final EmbeddingCacheService embeddingCacheService;
    private final AppMemoryProperties memoryProperties;
    private final SystemSettingsService settingsService;
    private final QdrantConnectionSupport qdrantConnectionSupport;
    private final ObjectMapper ObjectMapper;

    public MemoryStoreService(EmbeddingCacheService embeddingCacheService,
                              AppMemoryProperties memoryProperties,
                              SystemSettingsService settingsService,
                              QdrantConnectionSupport qdrantConnectionSupport,
                              ObjectMapper ObjectMapper) {
        this.embeddingCacheService = embeddingCacheService;
        this.memoryProperties = memoryProperties;
        this.settingsService = settingsService;
        this.qdrantConnectionSupport = qdrantConnectionSupport;
        this.ObjectMapper = ObjectMapper;
    }

    /**
     * 将记忆记录写入向量库；空摘要或 shouldStore=false 会跳过。
     */
    public boolean save(MemoryRecord record){
        if (record == null || Boolean.FALSE.equals(record.getShouldStore()) || record.getSummary() == null || record.getSummary().isBlank()){
            return false;
        }
        Metadata metadata = new Metadata();
        metadata.put("sessionId", defaultString(record.getSessionId(), "default"));
        metadata.put("category", defaultString(record.getCategory(), "general"));
        metadata.put("importance", String.valueOf(clamp(record.getImportance() == null ? 0 : record.getImportance(), 0, 100)));
        metadata.put("createdAt", (record.getCreateAt() == null ? Instant.now() : record.getCreateAt().toInstant()).toString());
        metadata.put("tags", record.getTags() == null ? "" : String.join(",", record.getTags()));

        try{
            metadata.put("metadata",ObjectMapper.writeValueAsString(record.getMetadata() == null ? Map.of() : record.getMetadata()));
        }catch (Exception e){
            metadata.put("metadata", "{}");
            log.warn("序列化记忆元数据失败，已使用空对象回退: {}", e.getMessage());
        }

        TextSegment textSegment  =  TextSegment.from(record.getSummary(),metadata);
        Embedding embedding = embeddingCacheService.getEmbedding(record.getSummary());
        ensureCollection(embedding.vector().length);
        embeddingStore().add(embedding,textSegment);
        return true;
    }
    /**
     * 语义检索记忆（不带过滤）。
     */
    public List<Map<String, Object>> search(String query, int topK) {
        return search(query, topK, null, null);
    }

    /**
     * 语义检索记忆并按综合打分排序，支持按会话 ID 与分类过滤。
     */
    public List<Map<String, Object>> search(String query, int topK, String sessionId, String category) {
        Embedding queryEmbedding = embeddingCacheService.getEmbedding(query);
        ensureCollection(queryEmbedding.vector().length);
        int resolvedTopK = topK > 0 ? topK : settingsService.getIntSetting("qdrant", "top_k", memoryProperties.getTopK());
        int candidateLimit = Math.max(MIN_CANDIDATE_LIMIT, resolvedTopK * 5);
        double minScore = Math.max(
                MIN_RECALL_SCORE,
                settingsService.getDoubleSetting("qdrant", "min_score", memoryProperties.getMinScore())
        );

        var filterBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(candidateLimit)
                .minScore(minScore);

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
        Instant now = Instant.now();

        return result.matches().stream()
                .map(match -> toScoredMap(match, now))
                .sorted((left, right) -> Double.compare(
                        doubleValue(right.get("finalScore")),
                        doubleValue(left.get("finalScore"))
                ))
                .limit(resolvedTopK)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toScoredMap(EmbeddingMatch<TextSegment> match, Instant now) {
        Map<String, Object> map = toMap(match);
        double semanticScore = match.score();
        double importanceScore = clamp(parseInteger(map.get("importance"), 0), 0, 100) / 100.0;
        double timeDecayScore = calculateTimeDecay(parseCreatedAt(map.get("createdAt")), now);
        double finalScore = SEMANTIC_WEIGHT * semanticScore
                + IMPORTANCE_WEIGHT * importanceScore
                + TIME_DECAY_WEIGHT * timeDecayScore;

        map.put("semanticScore", semanticScore);
        map.put("importanceScore", importanceScore);
        map.put("timeDecayScore", timeDecayScore);
        map.put("finalScore", finalScore);
        return map;
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

    private double calculateTimeDecay(Instant createdAt, Instant now) {
        if (createdAt == null || now == null || createdAt.isAfter(now)) {
            return 1.0;
        }
        double ageDays = Duration.between(createdAt, now).toMillis() / 86_400_000.0;
        return TIME_DECAY_FLOOR + (1.0 - TIME_DECAY_FLOOR)
                * Math.exp(-Math.log(2.0) * ageDays / HALF_LIFE_DAYS);
    }

    private Instant parseCreatedAt(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignored) {
        }
        try {
            return new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.US)
                    .parse(text)
                    .toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    private int parseInteger(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private EmbeddingStore<TextSegment> embeddingStore() {
        return qdrantConnectionSupport.embeddingStore(resolveCollectionName());
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
            qdrantConnectionSupport.applyApiKey(getConn);
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
            qdrantConnectionSupport.applyApiKey(putConn);
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
        return qdrantConnectionSupport.collectionName(memoryProperties.getCollectionName());
    }

    private String resolveQdrantHost() {
        return qdrantConnectionSupport.rawHost();
    }

    private int resolveQdrantRestPort() {
        return qdrantConnectionSupport.restPort();
    }

    private String normalizeHttpHost(String host) {
        return qdrantConnectionSupport.httpHost();
    }

}
