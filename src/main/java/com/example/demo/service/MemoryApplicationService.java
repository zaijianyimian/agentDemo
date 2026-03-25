package com.example.demo.service;

import com.example.demo.memory.MemoryExtractor;
import com.example.demo.memory.MemoryRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MemoryApplicationService {
    private final MemoryExtractor memoryExtractor;
    private final MemoryStoreService memoryStoreService;

    public MemoryApplicationService(MemoryExtractor memoryExtractor,
                                    MemoryStoreService memoryStoreService) {
        this.memoryExtractor = memoryExtractor;
        this.memoryStoreService = memoryStoreService;
    }

    public MemoryRecord extractAndStore(String sessionId, List<String> recentMessages) {
        MemoryRecord record = memoryExtractor.extract(sessionId, recentMessages);
        memoryStoreService.save(record);
        return record;
    }

    public List<Map<String, Object>> recall(String query, int topK) {
        return memoryStoreService.search(query, topK, null, null);
    }

    public List<Map<String, Object>> recall(String query, int topK, String sessionId, String category) {
        return memoryStoreService.search(query, topK, sessionId, category);
    }
}
