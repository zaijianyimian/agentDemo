package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutonomyDiff {

    private LocalDateTime latestScanTime;

    private LocalDateTime previousScanTime;

    private int newCount;

    private int resolvedCount;

    private int persistentCount;

    private List<AutonomyFinding> newFindings;

    private List<AutonomyFinding> resolvedFindings;

    private List<AutonomyFinding> persistentFindings;
}
