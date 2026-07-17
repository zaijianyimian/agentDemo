package com.example.demo.markdownskill.domain;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.nio.file.Path;
import java.time.Instant;

/**
 * 加载到内存中的 markdown skill 完整描述。
 * <p>
 * 一个 {@code MarkdownSkill} 对应磁盘上的一个 {@code <skill-name>/SKILL.md} 文件，
 * 由 {@code MarkdownSkillLoader} 在启动时或文件变更时构造。
 *
 * <p>该对象是不可变快照：每次文件变更会生成新的实例替换注册表中的旧实例，
 * 因此多线程访问安全。</p>
 */
@Data
@Builder
public class MarkdownSkill {

    /**
     * skill 名，与 frontmatter.name 一致，全局唯一。
     * <p>作为 {@code @Tool} 调用参数与自动匹配 key。</p>
     */
    private final String name;

    /**
     * skill 描述，用于 @Tool 描述展示和关键词匹配。
     */
    private final String description;

    /**
     * frontmatter 完整解析结果，备用扩展。
     */
    private final SkillFrontmatter frontmatter;

    /**
     * SKILL.md 去除 frontmatter 后的正文（markdown body）。
     * <p>自动注入或显式加载时作为 prompt 上下文使用。</p>
     */
    private final String body;

    /**
     * SKILL.md 的绝对路径，便于排错与日志追踪。
     */
    private final Path filePath;

    /**
     * 文件最后修改时间，用于判断是否需要重新加载。
     */
    private final Instant loadedAt;

    /**
     * 文件字节数，便于监控单个 skill 的体量。
     */
    @Getter
    private final long sizeBytes;
}