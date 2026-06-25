package com.example.demo.memory;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class MemoryExtractor {

    public MemoryRecord extract(String sessonId, List<String> recentMessages) {
        String merged = String.join("\n", recentMessages).trim();
        MemoryRecord record = new MemoryRecord();
        record.setSessionId(sessonId);
        record.setCreateAt(new Date());
        if (merged.isBlank()) {
            record.setSummary("");
            record.setCategory("empty");
            record.setTags(List.of());
            record.setImportance(0);
            record.setShouldStore(false);
            record.setMetadata(Map.of());
            return record;
        }

        String summary = summarize(merged);
        int importance = calculateImportance(merged);
        String category = classify(merged);
        List<String> tags = tagsOf(merged, category);

        record.setSummary(summary);
        record.setCategory(category);
        record.setTags(tags);
        record.setImportance(importance);
        record.setShouldStore(importance >= 60 || isLongTermCategory(category));
        record.setMetadata(Map.of("score", "rule-based-extarctor",
                "messageCount", recentMessages.size()));
        return record;

    }

    private boolean isLongTermCategory(String category) {
        return "project_context".equals(category)
                || "preference".equals(category)
                || "decision".equals(category);
    }

    private List<String> tagsOf(String text, String category) {
        List<String> tags = new ArrayList<>();
        String lower = text.toLowerCase();

        if (lower.contains("spring boot")) tags.add("spring-boot");
        if (lower.contains("langchain4j")) tags.add("langchain4j");
        if (lower.contains("qdrant")) tags.add("qdrant");
        if (lower.contains("向量")) tags.add("vector-db");
        tags.add(category);

        return tags;

    }

    private String classify(String text) {
        String lower = text.toLowerCase();

        if (lower.contains("spring boot") || lower.contains("langchain4j") || lower.contains("qdrant")) {
            return "project_context";
        }
        if (lower.contains("偏好") || lower.contains("习惯")) {
            return "preference";
        }
        if (lower.contains("决定") || lower.contains("确定") || lower.contains("采用")) {
            return "decision";
        }
        return "general";
    }

    private int calculateImportance(String text) {
        int score = 20;
        String lower = text.toLowerCase();

        if (lower.contains("项目") || lower.contains("技术选型") || lower.contains("方案")) score += 25;
        if (lower.contains("以后") || lower.contains("长期") || lower.contains("后续")) score += 20;
        if (lower.contains("偏好") || lower.contains("习惯") || lower.contains("使用")) score += 15;
        if (lower.contains("spring boot") || lower.contains("langchain4j") || lower.contains("qdrant")) score += 20;

        return Math.min(score, 100);
    }

    private String summarize(String merged) {
        merged = merged.replaceAll("\\s", " ").trim();
        return merged.length() <= 300 ? merged : merged.substring(0, 300);
    }
}
