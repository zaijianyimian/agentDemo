package com.example.demo.autonomy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 表示自治模块生成的单个产物及其基本元数据。
 * <p>
 * 用于展示扫描、验证或草稿文件的类型、路径、生成时间和内容预览。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutonomyArtifact {

    private String type;

    private String name;

    private String path;

    private LocalDateTime time;

    private String preview;
}
