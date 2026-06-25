package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedReport {

    private String period;

    private LocalDateTime generatedAt;

    private String path;

    private String content;

    private Map<String, Object> metrics;
}
