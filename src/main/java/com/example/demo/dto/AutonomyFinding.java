package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自治扫描发现项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutonomyFinding {

    private String severity;

    private String title;

    private String detail;

    private String suggestion;
}
