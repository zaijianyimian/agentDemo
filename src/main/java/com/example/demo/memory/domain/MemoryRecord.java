package com.example.demo.memory.domain;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
/**
 * 表示从会话内容中提取的记忆记录，承载摘要、分类、标签和重要度等信息。
 * <p>
 * 供记忆提取、持久化与语义检索流程共同使用。
 */
public class MemoryRecord {
    private String sessionId;
    private String summary;
    private String category;
    private List<String> tags;
    private Integer importance;
    private Boolean shouldStore;
    private Date createAt;
    private Map<String,Object> metadata;
}
