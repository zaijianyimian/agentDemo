package com.example.demo.markdownskill.tools;

import com.example.demo.markdownskill.domain.MarkdownSkill;
import com.example.demo.markdownskill.registry.MarkdownSkillRegistry;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 暴露给 LLM（{@code QwenChatService}）的 markdown skill 工具集。
 * <p>
 * 该类实现 Claude Skills 思路的"显式调用"路径：当用户表达与某个 skill 相关、但又不确定是否要自动注入时，
 * LLM 可以主动调用 {@code list_markdown_skills} / {@code load_markdown_skill} 检索与加载。
 *
 * <p>所有方法均以 {@code String} 返回，便于 LLM 直接拼装到下一轮 prompt。</p>
 */
@Slf4j
@Component("markdownSkillTools")
@RequiredArgsConstructor
public class MarkdownSkillTools {

    /**
     * 注册表：负责实际匹配与读取。
     */
    private final MarkdownSkillRegistry registry;

    /**
     * 单个 skill 的 body 截断上限（防止 prompt 爆炸）。
     */
    private static final int BODY_TRUNCATE_LIMIT = 6000;

    @Tool("""
            列出当前所有可用的 markdown skill 名称与简短描述。
            使用场景：用户问'有哪些 skill'、'你现在能做什么'，或你不确定该调用哪个 skill 时先用此查询。
            返回格式：每行一个，'name | description'。
            """)
    public String list_markdown_skills() {
        List<MarkdownSkill> all = registry.listAll();
        if (all.isEmpty()) {
            return "当前没有任何 markdown skill。";
        }
        StringBuilder sb = new StringBuilder("当前共有 ").append(all.size()).append(" 个 markdown skill：\n");
        for (MarkdownSkill skill : all) {
            sb.append("- ").append(skill.getName())
                    .append(" | ").append(truncate(skill.getDescription(), 150))
                    .append("\n");
        }
        return sb.toString();
    }

    @Tool("""
            加载指定名称的 markdown skill 完整正文，返回 SKILL.md 中的所有指导内容。
            使用场景：用户明确说'用 X skill'、'按 X 的方式回答'，或 list_markdown_skills 返回了候选后想看具体内容。
            返回：skill 的正文（去掉 frontmatter 的 markdown 内容）；不存在时返回错误提示。
            """)
    public String load_markdown_skill(@P("skill 名称，例如 openspec-explore") String name) {
        Optional<MarkdownSkill> skill = registry.findByName(name);
        if (skill.isEmpty()) {
            return "未找到 markdown skill: " + name + "。可先用 list_markdown_skills 查看可用列表。";
        }
        MarkdownSkill s = skill.get();
        StringBuilder sb = new StringBuilder("# Skill: ").append(s.getName()).append("\n\n");
        sb.append("## 描述\n").append(s.getDescription()).append("\n\n");
        if (s.getFrontmatter() != null) {
            if (s.getFrontmatter().getLicense() != null) {
                sb.append("## 许可证\n").append(s.getFrontmatter().getLicense()).append("\n\n");
            }
            if (s.getFrontmatter().getCompatibility() != null) {
                sb.append("## 兼容性\n").append(s.getFrontmatter().getCompatibility()).append("\n\n");
            }
        }
        sb.append("## 正文\n").append(truncate(s.getBody(), BODY_TRUNCATE_LIMIT));
        return sb.toString();
    }

    @Tool("""
            根据用户问题找出最匹配的 markdown skill 名称与匹配分。
            使用场景：你不确定当前问题该走哪个 skill，又不想列全部时，先用此方法做语义检索。
            返回：最佳匹配 skill 的名称、描述、得分（0~1），无候选时返回空。
            """)
    public String find_markdown_skill_for_query(@P("用户的原始问题") String query) {
        Optional<MarkdownSkillRegistry.MatchResult> best = registry.matchBest(query);
        if (best.isEmpty()) {
            return "未找到与问题匹配的 markdown skill。";
        }
        MarkdownSkillRegistry.MatchResult r = best.get();
        return String.format("最佳匹配: %s (得分=%.2f)\n描述: %s",
                r.skill().getName(), r.score(), r.skill().getDescription());
    }

    /**
     * 文本截断辅助方法，避免超长文本返回影响 prompt。
     */
    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }
}