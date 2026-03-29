package com.example.demo.service.memory;

import com.example.demo.memory.MemoryRecord;
import com.example.demo.properties.AppMemoryProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class MemoryStoreService {
    private final EmbeddingCacheService embeddingCacheService;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final AppMemoryProperties memoryProperties;
    private final ObjectMapper ObjectMapper;

    public MemoryStoreService(EmbeddingCacheService embeddingCacheService,
                              EmbeddingStore<TextSegment> embeddingStore,
                              AppMemoryProperties memoryProperties,
                              ObjectMapper ObjectMapper) {
        this.embeddingCacheService = embeddingCacheService;
        this.embeddingStore = embeddingStore;
        this.memoryProperties = memoryProperties;
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
        metadata.put("tags", String.join(",", record.getTags()));

        try{
            metadata.put("metadata",ObjectMapper.writeValueAsString(record.getMetadata()));
        }catch (Exception e){
            metadata.put("metadata", "{}");
            e.printStackTrace();
        }

        TextSegment textSegment  =  TextSegment.from(record.getSummary(),metadata);
        Embedding embedding = embeddingCacheService.getEmbedding(record.getSummary());
        embeddingStore.add(embedding,textSegment);
        return true;
    }
    public List<Map<String, Object>> search(String query, int topK) {
        return search(query, topK, null, null);
    }

    public List<Map<String, Object>> search(String query, int topK, String sessionId, String category) {
        Embedding queryEmbedding = embeddingCacheService.getEmbedding(query);

        var filterBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK > 0 ? topK : memoryProperties.getTopK())
                .minScore(memoryProperties.getMinScore());

        // 添加过滤条件
        if (sessionId != null && !sessionId.isBlank()) {
            Filter filter = metadataKey("sessionId").isEqualTo(sessionId);
            filterBuilder.filter(filter);
        }
        if (category != null && !category.isBlank()) {
            Filter filter = metadataKey("category").isEqualTo(category);
            filterBuilder.filter(filter);
        }

        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(filterBuilder.build());

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

}
