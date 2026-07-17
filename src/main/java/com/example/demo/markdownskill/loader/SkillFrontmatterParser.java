package com.example.demo.markdownskill.loader;

import com.example.demo.markdownskill.domain.SkillFrontmatter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SKILL.md 文件 YAML frontmatter 解析器。
 * <p>
 * 把 {@code ---} 包裹的 YAML 片段解析为 {@link SkillFrontmatter} 结构，
 * 并做基础的字段补全与容错。
 *
 * <p>设计取舍：</p>
 * <ul>
 *   <li>使用 Jackson YAMLFactory（项目已有 jackson-dataformat-yaml 依赖），不再额外引入 SnakeYAML；</li>
 *   <li>缺失必填字段（{@code name} / {@code description}）抛异常，让上层 loader 跳过并告警；</li>
 *   <li>未知字段忽略，仅在 {@link SkillFrontmatter#getMetadata()} 中保留 {@code metadata} 子树。</li>
 * </ul>
 */
@Slf4j
@Component
public class SkillFrontmatterParser {

    /**
     * frontmatter 起止分隔符，必须成对出现。
     */
    private static final String DELIMITER = "---";

    /**
     * Jackson YAML 解析器，单例复用。
     */
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * 拆分 frontmatter 与 markdown 正文。
     *
     * @param raw 原始文件全文
     * @return {@link Split} 包含 frontmatter 文本（YAML）和 markdown 正文
     * @throws IllegalArgumentException 文件不以 {@code ---} 开头，无法解析
     */
    public Split split(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("文件内容为空");
        }
        // 统一换行符，避免 Windows / Linux 风格差异
        String content = raw.replace("\r\n", "\n");
        if (!content.startsWith(DELIMITER + "\n") && !content.startsWith(DELIMITER + " ")) {
            throw new IllegalArgumentException("文件缺少 frontmatter 起止分隔符 '---'");
        }
        // 从第二个分隔符开始切分（跳过第一个 --- 起始符）
        int bodyStart = content.indexOf(DELIMITER, DELIMITER.length() + 1);
        if (bodyStart < 0) {
            throw new IllegalArgumentException("frontmatter 缺少结束分隔符 '---'");
        }
        // 调整到分隔符之后第一个换行符
        int afterDelimiter = bodyStart + DELIMITER.length();
        if (afterDelimiter < content.length() && content.charAt(afterDelimiter) == '\n') {
            afterDelimiter++;
        }
        String frontmatterText = content.substring(DELIMITER.length(), bodyStart).trim();
        String body = content.substring(afterDelimiter).strip();
        return new Split(frontmatterText, body);
    }

    /**
     * 把 frontmatter YAML 文本解析为强类型对象。
     *
     * @param yamlText YAML 文本（不含外层 {@code ---}）
     * @return 解析结果
     * @throws IllegalArgumentException 缺少 name 或 description 字段
     */
    public SkillFrontmatter parse(String yamlText) {
        if (yamlText == null || yamlText.isBlank()) {
            throw new IllegalArgumentException("frontmatter 内容为空");
        }
        Map<String, Object> map;
        try {
            map = yamlMapper.readValue(yamlText, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("frontmatter YAML 解析失败: " + e.getMessage(), e);
        }

        String name = stringValue(map.get("name"));
        String description = stringValue(map.get("description"));
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("frontmatter 缺少必填字段 'name'");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("frontmatter 缺少必填字段 'description'");
        }

        // 提取可选字段
        String license = stringValue(map.get("license"));
        String compatibility = stringValue(map.get("compatibility"));

        // metadata 子树整体透传
        Object metadataRaw = map.get("metadata");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = metadataRaw instanceof Map ? (Map<String, Object>) metadataRaw : null;

        // 从 metadata.tags 提取可选标签列表
        List<String> tags = Collections.emptyList();
        if (metadata != null) {
            Object tagsRaw = metadata.get("tags");
            if (tagsRaw instanceof List<?> list) {
                tags = list.stream().map(String::valueOf).toList();
            }
        }

        return SkillFrontmatter.builder()
                .name(name.trim())
                .description(description.trim())
                .license(license)
                .compatibility(compatibility)
                .metadata(metadata)
                .tags(tags)
                .build();
    }

    /**
     * 一站式解析：拆分 + 解析 frontmatter。
     *
     * @param raw 原始文件全文
     * @return {@link ParsedResult} 同时包含 frontmatter 对象与 markdown 正文
     */
    public ParsedResult parseFile(String raw) {
        Split split = split(raw);
        SkillFrontmatter frontmatter = parse(split.frontmatterText());
        return new ParsedResult(frontmatter, split.body());
    }

    /**
     * 把任意对象安全转换为字符串（处理 String / 数字 / 布尔）。
     */
    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return String.valueOf(value);
    }

    /**
     * frontmatter 与 body 拆分结果。
     */
    public record Split(String frontmatterText, String body) {}

    /**
     * 完整解析结果。
     */
    public record ParsedResult(SkillFrontmatter frontmatter, String body) {}
}