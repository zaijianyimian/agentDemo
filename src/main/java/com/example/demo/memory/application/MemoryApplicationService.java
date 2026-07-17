package com.example.demo.memory.application;

import com.example.demo.memory.domain.MemoryExtractor;
import com.example.demo.memory.domain.MemoryRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 记忆应用服务
 * 编排记忆的提取、存储与检索流程。
 */
@Service
public class MemoryApplicationService {
    private final MemoryExtractor memoryExtractor;
    private final MemoryStoreService memoryStoreService;

    public MemoryApplicationService(MemoryExtractor memoryExtractor,
                                    MemoryStoreService memoryStoreService) {
        this.memoryExtractor = memoryExtractor;
        this.memoryStoreService = memoryStoreService;
    }

    /**
     * 从最近消息中提取记忆并写入存储。
     */
    public MemoryRecord extractAndStore(String sessionId, List<String> recentMessages) {
        MemoryRecord record = memoryExtractor.extract(sessionId, recentMessages);
        memoryStoreService.save(record);
        return record;
    }

    /**
     * 直接写入一条已构造好的记忆记录。
     */
    public boolean store(MemoryRecord record) {
        return memoryStoreService.save(record);
    }

    /**
     * 语义召回相关记忆（不带过滤）。
     */
    public List<Map<String, Object>> recall(String query, int topK) {
        return memoryStoreService.search(query, topK, null, null);
    }

    /**
     * 语义召回相关记忆，支持按会话 ID 和分类过滤。
     */
    public List<Map<String, Object>> recall(String query, int topK, String sessionId, String category) {
        return memoryStoreService.search(query, topK, sessionId, category);
    }
}
