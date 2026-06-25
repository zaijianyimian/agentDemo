package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxSummary {

    private LocalDateTime generatedAt;

    private Map<String, Object> counts;

    private List<InboxItem> items;

    private List<String> warnings;
}
