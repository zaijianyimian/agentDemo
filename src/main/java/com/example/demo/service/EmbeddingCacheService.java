package com.example.demo.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class EmbeddingCacheService {

    private final EmbeddingModel embeddingModel;
    private final Cache<String, Embedding> cache;

    public EmbeddingCacheService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        // 配置缓存：最大 10000 条，过期时间 30 分钟
        this.cache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build();
    }

    /**
     * 获取文本的 Embedding，优先从缓存读取
     */
    public Embedding getEmbedding(String text) {
        return cache.get(text, this::generateEmbedding);
    }

    /**
     * 获取文本的 Embedding，带自定义缓存键
     */
    public Embedding getEmbedding(String text, String cacheKey) {
        return cache.get(cacheKey, k -> generateEmbedding(text));
    }

    private Embedding generateEmbedding(String text) {
        TextSegment textSegment = TextSegment.from(text);
        Response<Embedding> response = embeddingModel.embed(textSegment);
        return response.content();
    }

    /**
     * 手动清除缓存
     */
    public void invalidate(String key) {
        cache.invalidate(key);
    }

    /**
     * 清除所有缓存
     */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /**
     * 获取缓存统计信息
     */
    public long estimatedSize() {
        return cache.estimatedSize();
    }
}
