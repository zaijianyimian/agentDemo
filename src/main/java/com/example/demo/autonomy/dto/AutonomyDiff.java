package com.example.demo.autonomy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表示最近两次自治扫描结果之间的差异。
 * <p>
 * 汇总新增、已解决和持续存在的发现项及其数量。
 */
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
