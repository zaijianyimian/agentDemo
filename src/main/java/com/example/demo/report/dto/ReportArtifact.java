package com.example.demo.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 报告归档条目数据传输对象,用于在列表接口中返回已生成的报告元信息。
 * <p>
 * 包含报告所属周期、名称、文件路径、生成时间以及预览片段,通常用于前端展示历史报告列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportArtifact {

    private String period;

    private String name;

    private String path;

    private LocalDateTime time;

    private String preview;
}
