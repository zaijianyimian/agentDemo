package com.example.demo.markdownskill.loader;

import com.example.demo.markdownskill.domain.MarkdownSkill;
import com.example.demo.markdownskill.domain.SkillFrontmatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * 扫描指定目录下所有 {@code <skill-name>/SKILL.md} 文件并解析为 {@link MarkdownSkill} 列表。
 *
 * <p>扫描规则：</p>
 * <ul>
 *   <li>目录由 {@code app.markdown-skills.dir} 配置（默认 {@code ./skills}）；</li>
 *   <li>只匹配直接子目录下的 {@code SKILL.md}（不限大小写）；</li>
 *   <li>frontmatter 解析失败的 skill 会被跳过并记录 WARN，不中断整体加载；</li>
 *   <li>同名 skill（name 冲突）保留最后一次出现的实例，便于覆盖式开发。</li>
 * </ul>
 *
 * <p>热刷新：通过 {@link SkillFileWatcher} 监听文件系统事件，单文件变更触发全量 reload。</p>
 *
 * <p>关闭方式：{@code app.markdown-skills.enabled=false} 时整个模块不装配。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.markdown-skills", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MarkdownSkillLoader {

    private final SkillFrontmatterParser parser;

    /**
     * skills 根目录，支持相对路径（相对 JVM 工作目录）。
     */
    @Value("${app.markdown-skills.dir:./skills}")
    private String skillsDir;

    /**
     * SKILL.md 文件名（不区分大小写匹配）。
     */
    private static final String SKILL_FILE_NAME = "SKILL.md";

    /**
     * 内存中已加载的 skill 快照，key 为 skill name。
     */
    private final Map<String, MarkdownSkill> loaded = new ConcurrentHashMap<>();

    /**
     * 文件监听器，启动后异步扫描目录。
     */
    private SkillFileWatcher watcher;

    /**
     * 启动时做一次全量加载，并启动文件监听器。
     */
    @PostConstruct
    public void init() {
        Path root = resolveRoot();
        if (root == null) {
            log.warn("skills 目录不存在或无法访问，跳过加载: {}", skillsDir);
            return;
        }
        reload();
        try {
            watcher = new SkillFileWatcher(root, this::onFileEvent);
            watcher.start();
            log.info("skills 文件监听器启动: {}", root.toAbsolutePath());
        } catch (IOException e) {
            log.warn("启动 skills 文件监听器失败: {}", e.getMessage());
        }
    }

    /**
     * 优雅停机，关闭文件监听线程。
     */
    @PreDestroy
    public void destroy() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    /**
     * 全量重新扫描目录并刷新内存中的 skill 快照。
     * <p>实现上：先清空再重建，保证同名 skill 一定反映最新文件内容。</p>
     */
    public synchronized void reload() {
        Path root = resolveRoot();
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        Map<String, MarkdownSkill> next = new ConcurrentHashMap<>();
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory).forEach(dir -> {
                Path skillFile = findSkillFile(dir);
                if (skillFile == null) {
                    return;
                }
                MarkdownSkill skill = parseSkillFile(dir, skillFile);
                if (skill != null) {
                    next.put(skill.getName(), skill);
                }
            });
        } catch (IOException e) {
            log.warn("扫描 skills 目录失败: {}", e.getMessage());
        }
        loaded.clear();
        loaded.putAll(next);
        log.info("markdown skills 已加载: 共 {} 个 skill", loaded.size());
        if (log.isDebugEnabled()) {
            loaded.keySet().forEach(name -> log.debug("  - {}", name));
        }
    }

    /**
     * 返回当前内存中所有已加载 skill 的快照。
     */
    public List<MarkdownSkill> listAll() {
        return new ArrayList<>(loaded.values());
    }

    /**
     * 按名称获取单个 skill。
     *
     * @param name skill 名
     * @return skill 实例，未找到返回 null
     */
    public MarkdownSkill getByName(String name) {
        if (name == null) {
            return null;
        }
        return loaded.get(name);
    }

    /**
     * 当前已加载 skill 的数量。
     */
    public int size() {
        return loaded.size();
    }

    /**
     * 解析单个 skill 目录。
     * <p>出错时打印 WARN 并返回 null，不抛异常中断整体加载。</p>
     */
    private MarkdownSkill parseSkillFile(Path skillDir, Path skillFile) {
        String raw;
        try {
            raw = Files.readString(skillFile);
        } catch (IOException e) {
            log.warn("读取 SKILL.md 失败: {}", skillFile, e);
            return null;
        }

        SkillFrontmatterParser.ParsedResult parsed;
        try {
            parsed = parser.parseFile(raw);
        } catch (Exception e) {
            log.warn("解析 SKILL.md 失败 [{}]: {}", skillFile, e.getMessage());
            return null;
        }

        // 防御：frontmatter.name 与目录名不一致时记录 WARN（仍允许加载）
        if (!skillDir.getFileName().toString().equals(parsed.frontmatter().getName())) {
            log.warn("skill 目录名 '{}' 与 frontmatter.name '{}' 不一致，建议对齐",
                    skillDir.getFileName(), parsed.frontmatter().getName());
        }

        long size;
        try {
            size = Files.size(skillFile);
        } catch (IOException e) {
            size = raw.getBytes().length;
        }

        return MarkdownSkill.builder()
                .name(parsed.frontmatter().getName())
                .description(parsed.frontmatter().getDescription())
                .frontmatter(parsed.frontmatter())
                .body(parsed.body())
                .filePath(skillFile.toAbsolutePath())
                .loadedAt(Instant.now())
                .sizeBytes(size)
                .build();
    }

    /**
     * 在 skill 目录下递归查找 {@code SKILL.md} 文件。
     * <p>为兼容嵌套结构，最多向下查 2 层；优先匹配直接子文件。</p>
     */
    private Path findSkillFile(Path skillDir) {
        // 直接子文件
        try (Stream<Path> files = Files.list(skillDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(SKILL_FILE_NAME))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 解析配置中的 skills 目录路径。目录不存在时返回 null（不抛异常）。
     */
    private Path resolveRoot() {
        Path path = Paths.get(skillsDir).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                log.info("skills 目录不存在，已自动创建: {}", path);
            } catch (IOException e) {
                log.warn("无法创建 skills 目录 {}: {}", path, e.getMessage());
                return null;
            }
        }
        return Files.isDirectory(path) ? path : null;
    }

    /**
     * WatchService 事件回调：任何变更都触发全量 reload。
     * <p>SKILL.md 数量通常 < 50，全量 reload 成本可忽略。</p>
     */
    private void onFileEvent(Path changed) {
        log.info("检测到 skills 目录变更: {}，触发重新加载", changed);
        reload();
    }

    /**
     * 默认空目录占位，避免 IDE 编译期 NPE。
     */
    @SuppressWarnings("unused")
    private static final List<MarkdownSkill> EMPTY = Collections.emptyList();
}