package com.example.demo.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 封装已生成报告的结果数据传输对象。
 * <p>
 * 包含报告对应的时间周期、生成时间、存储路径、正文内容以及可选的指标集合,用于在报告服务与控制器之间传递生成的报告内容。
 */
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
