package com.example.demo.markdownskill.web;

import com.example.demo.markdownskill.domain.MarkdownSkill;
import com.example.demo.markdownskill.registry.MarkdownSkillRegistry;
import com.example.demo.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * markdown skill 管理端点。
 * <p>
 * 给前端 Chat.vue / Settings.vue 用的查询与重载入口，
 * 与 {@code MarkdownSkillTools}（给 LLM 用的 @Tool）解耦。
 */
@RestController
@RequestMapping("/api/markdown-skill")
@RequiredArgsConstructor
public class MarkdownSkillController {

    /**
     * skill 注册表。
     */
    private final MarkdownSkillRegistry registry;

    /**
     * 列出所有 skill（仅元数据，不含 body）。
     */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> result = registry.listAll().stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("name", s.getName());
            m.put("description", s.getDescription());
            m.put("license", s.getFrontmatter() != null ? s.getFrontmatter().getLicense() : null);
            m.put("compatibility", s.getFrontmatter() != null ? s.getFrontmatter().getCompatibility() : null);
            m.put("tags", s.getFrontmatter() != null ? s.getFrontmatter().getTags() : null);
            m.put("filePath", s.getFilePath() == null ? null : s.getFilePath().toString());
            m.put("sizeBytes", s.getSizeBytes());
            m.put("loadedAt", s.getLoadedAt());
            return m;
        }).toList();
        return ApiResponse.success(result);
    }

    /**
     * 按名称加载 skill 完整正文。
     */
    @GetMapping("/{name}")
    public ApiResponse<Map<String, Object>> get(@PathVariable String name) {
        MarkdownSkill skill = registry.findByName(name).orElse(null);
        if (skill == null) {
            return ApiResponse.error("skill 不存在: " + name);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("name", skill.getName());
        body.put("description", skill.getDescription());
        body.put("license", skill.getFrontmatter() != null ? skill.getFrontmatter().getLicense() : null);
        body.put("compatibility", skill.getFrontmatter() != null ? skill.getFrontmatter().getCompatibility() : null);
        body.put("tags", skill.getFrontmatter() != null ? skill.getFrontmatter().getTags() : null);
        body.put("body", skill.getBody());
        body.put("filePath", skill.getFilePath() == null ? null : skill.getFilePath().toString());
        body.put("sizeBytes", skill.getSizeBytes());
        return ApiResponse.success(body);
    }

    /**
     * 给定查询返回匹配得分最高的 skill。
     */
    @GetMapping("/match")
    public ApiResponse<Map<String, Object>> match(@RequestParam String query) {
        return registry.matchBest(query)
                .<ApiResponse<Map<String, Object>>>map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", r.skill().getName());
                    m.put("description", r.skill().getDescription());
                    m.put("score", r.score());
                    return ApiResponse.success(m);
                })
                .orElse(ApiResponse.success(null));
    }

    /**
     * 触发全量 reload（一般在调试或新增 skill 后手动调用）。
     */
    @PostMapping("/reload")
    public ApiResponse<Integer> reload() {
        registry.reload();
        return ApiResponse.success(registry.size());
    }
}