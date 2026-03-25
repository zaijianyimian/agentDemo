package com.example.demo.memory;

import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
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
