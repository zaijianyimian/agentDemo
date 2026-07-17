package com.example.demo.markdownskill.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SKILL.md 文件 YAML frontmatter 的强类型映射。
 * <p>
 * 参考 Anthropic Claude Skills 规范，frontmatter 至少包含 {@code name} 与 {@code description} 两个字段；
 * 其余字段（{@code license}、{@code compatibility}、{@code metadata} 等）作为可选扩展透传，
 * 不参与匹配与执行逻辑。
 *
 * <p>典型示例：</p>
 * <pre>{@code
 * ---
 * name: openspec-explore
 * description: Enter explore mode - a thinking partner...
 * license: MIT
 * compatibility: Requires openspec CLI.
 * metadata:
 *   author: openspec
 *   version: "1.0"
 * ---
 * }</pre>
 */
@Data
@Builder
public class SkillFrontmatter {

    /**
     * skill 名称，必须与目录名一致且全局唯一。
     * <p>作为 {@link MarkdownSkill#getName()} 的来源，也是 LLM 调 @Tool 的参数。</p>
     */
    private String name;

    /**
     * skill 描述，用于自动匹配与 @Tool 描述展示。
     * <p>长度建议 50~500 字符，描述越清晰，匹配命中率越高。</p>
     */
    private String description;

    /**
     * 许可证声明，例如 MIT / Apache-2.0。
     */
    private String license;

    /**
     * 兼容性说明，例如依赖哪些外部 CLI / 环境变量。
     */
    private String compatibility;

    /**
     * 其它自定义元数据（如 author / version / tags），保留透传以便未来扩展。
     */
    private Map<String, Object> metadata;

    /**
     * 可选标签列表，用于精细化匹配（如果用户描述里包含任一标签则视为命中）。
     * <p>不在标准 frontmatter 规范中，本系统从 {@code metadata.tags} 自动提取。</p>
     */
    private List<String> tags;
}