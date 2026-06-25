package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 自治扫描报告
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutonomyScanReport {

    private LocalDateTime scanTime;

    private String workspaceRoot;

    private Map<String, Object> metrics;

    private List<AutonomyFinding> findings;

    private String reportPath;

    private String summaryPath;
}
