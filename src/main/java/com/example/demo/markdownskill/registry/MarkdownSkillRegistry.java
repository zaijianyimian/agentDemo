package com.example.demo.markdownskill.registry;

import com.example.demo.markdownskill.domain.MarkdownSkill;
import com.example.demo.markdownskill.loader.MarkdownSkillLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 对外暴露的 skill 注册表，统一封装查询与匹配能力。
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>从 {@link MarkdownSkillLoader} 拉取已加载 skill；</li>
 *   <li>提供按名称查询、按关键词匹配等 LLM 友好的接口；</li>
 *   <li>匹配算法使用 Jaccard 相似度 + tag 命中加权，无需引入额外 embedding 模型。</li>
 * </ul>
 *
 * <p>Jaccard 阈值由 {@code app.markdown-skills.match-threshold} 控制（默认 0.15）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkdownSkillRegistry {

    /**
     * 数据源：{@link MarkdownSkillLoader} 已完成文件 IO 与解析，本注册表只做匹配。
     */
    private final MarkdownSkillLoader loader;

    /**
     * 自动匹配的相似度阈值（0~1）。值越小越容易命中（容易误触），越大越严格。
     */
    @Value("${app.markdown-skills.match-threshold:0.15}")
    private double matchThreshold;

    /**
     * tag 完全命中时附加的额外权重，让 metadata.tags 显式标注的 skill 优先被命中。
     */
    @Value("${app.markdown-skills.tag-match-bonus:0.2}")
    private double tagMatchBonus;

    /**
     * 获取所有 skill。
     */
    public List<MarkdownSkill> listAll() {
        return loader.listAll();
    }

    /**
     * 按名称获取单个 skill。
     */
    public Optional<MarkdownSkill> findByName(String name) {
        return Optional.ofNullable(loader.getByName(name));
    }

    /**
     * 当前已加载的 skill 数量。
     */
    public int size() {
        return loader.size();
    }

    /**
     * 触发全量 reload（供运维或调试接口使用）。
     */
    public void reload() {
        loader.reload();
    }

    /**
     * 根据用户查询找出最匹配的 skill。
     *
     * <p>匹配策略：</p>
     * <ol>
     *   <li>把查询文本拆成 token（中文按字、英文按词）；</li>
     *   <li>对每个 skill 计算 Jaccard(queryTokens, skillTokens)；</li>
     *   <li>如果查询中包含 skill 的 tag，额外加分；</li>
     *   <li>得分超过阈值的最高分 skill 返回；无候选返回 {@link Optional#empty()}。</li>
     * </ol>
     *
     * @param query 用户原始输入
     * @return 命中的 skill 与得分（0~1）
     */
    public Optional<MatchResult> matchBest(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return Optional.empty();
        }

        MarkdownSkill bestSkill = null;
        double bestScore = matchThreshold;
        for (MarkdownSkill skill : loader.listAll()) {
            double score = scoreSkill(skill, queryTokens);
            if (score > bestScore) {
                bestScore = score;
                bestSkill = skill;
            }
        }
        return bestSkill == null ? Optional.empty() : Optional.of(new MatchResult(bestSkill, bestScore));
    }

    /**
     * 计算单个 skill 与查询 token 集合的匹配分。
     */
    private double scoreSkill(MarkdownSkill skill, Set<String> queryTokens) {
        // 把 skill 的 name + description 一起拆 token
        String searchable = (skill.getName() + " " + skill.getDescription()).toLowerCase(Locale.ROOT);
        Set<String> skillTokens = tokenize(searchable);
        if (skillTokens.isEmpty()) {
            return 0;
        }

        // Jaccard = |交集| / |并集|
        Set<String> union = new HashSet<>(queryTokens);
        union.addAll(skillTokens);
        Set<String> intersection = new HashSet<>(queryTokens);
        intersection.retainAll(skillTokens);
        double jaccard = union.isEmpty() ? 0 : (double) intersection.size() / union.size();

        // tag 命中加权：查询 token 包含任一 tag 即视为强命中
        boolean tagHit = false;
        if (skill.getFrontmatter() != null && skill.getFrontmatter().getTags() != null) {
            for (String tag : skill.getFrontmatter().getTags()) {
                if (queryTokens.contains(tag.toLowerCase(Locale.ROOT))) {
                    tagHit = true;
                    break;
                }
            }
        }

        // name 直接出现算最强命中：name 任一连续子串出现在查询里
        boolean nameHit = containsIgnoreCase(queryTokens, skill.getName().toLowerCase(Locale.ROOT));

        double score = jaccard;
        if (tagHit) {
            score += tagMatchBonus;
        }
        if (nameHit) {
            score += 0.3;
        }
        return Math.min(1.0, score);
    }

    /**
     * 文本分词：对中文按单字切分，对英文按单词切分，统一小写并过滤停用字符。
     * <p>这是简单实现，对短查询足够；后续若需要更精准可换成 HanLP / jieba。</p>
     */
    static Set<String> tokenize(String text) {
        if (text == null) {
            return Collections.emptySet();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        // 用正则按非字母数字中日韩文切分，剩下的中文按字符再拆
        String[] roughTokens = NON_TOKEN_CHARS.split(lower);
        Set<String> result = new HashSet<>();
        for (String token : roughTokens) {
            if (token.isEmpty()) {
                continue;
            }
            // 如果整段都是 CJK 字符，逐字放入；否则作为整体词
            if (CJK_PATTERN.matcher(token).matches()) {
                for (char c : token.toCharArray()) {
                    if (CJK_PATTERN.matcher(String.valueOf(c)).matches()) {
                        result.add(String.valueOf(c));
                    }
                }
            } else if (token.length() > 0) {
                result.add(token);
            }
        }
        return result;
    }

    /**
     * 判断 name 字符串能否在查询 tokens 里"按出现顺序"拼出来。
     * <p>简化做法：name 转 token 后只要所有 token 都在 queryTokens 里，就视为命中。</p>
     */
    private static boolean containsIgnoreCase(Set<String> queryTokens, String loweredName) {
        if (loweredName == null || loweredName.isBlank()) {
            return false;
        }
        Set<String> nameTokens = tokenize(loweredName);
        return queryTokens.containsAll(nameTokens) && !nameTokens.isEmpty();
    }

    /**
     * 非字母数字与 CJK 字符的分隔符正则。
     */
    private static final Pattern NON_TOKEN_CHARS = Pattern.compile("[^\\p{L}\\p{N}\\p{IsHan}\\p{IsBopo}\\p{IsHangul}]+");

    /**
     * 单个 CJK 字符匹配。
     */
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\p{IsHan}\\p{IsBopo}\\p{IsHangul}]");

    /**
     * 取匹配得分最高的 Top N 个 skill，用于前端展示"我可能用得上这些"。
     *
     * @param query 用户输入
     * @param limit 返回数量上限
     */
    public List<MatchResult> matchTopN(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }
        List<MatchResult> results = new ArrayList<>();
        for (MarkdownSkill skill : loader.listAll()) {
            double score = scoreSkill(skill, queryTokens);
            if (score > 0) {
                results.add(new MatchResult(skill, score));
            }
        }
        results.sort(Comparator.comparingDouble(MatchResult::score).reversed());
        if (results.size() > limit) {
            return results.subList(0, limit);
        }
        return results;
    }

    /**
     * 匹配结果 DTO。
     */
    public record MatchResult(MarkdownSkill skill, double score) {}
}