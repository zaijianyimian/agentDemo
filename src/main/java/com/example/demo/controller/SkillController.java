package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.SkillExecutionResult;
import com.example.demo.config.CacheConfig;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.Skill;
import com.example.demo.service.skill.SkillExecutor;
import com.example.demo.service.skill.SkillLoaderService;
import com.example.demo.service.mcp.SkillService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/skill")
public class SkillController {

    @Resource
    private SkillService skillService;

    @Resource
    private SkillExecutor skillExecutor;

    @Resource
    private SkillLoaderService skillLoaderService;

    // ==================== 查询接口 ====================

    /**
     * 获取所有技能
     */
    @GetMapping("/list")
    public List<Skill> listAll() {
        return skillService.listAll();
    }

    /**
     * 获取启用的技能
     */
    @GetMapping("/enabled")
    public List<Skill> listEnabled() {
        return skillService.listEnabled();
    }

    /**
     * 获取内置技能
     */
    @GetMapping("/builtin")
    public List<Skill> listBuiltin() {
        return skillService.listBuiltin();
    }

    /**
     * 获取技能分类
     */
    @GetMapping("/categories")
    public List<String> listCategories() {
        return skillService.listCategories();
    }

    /**
     * 按分类获取技能
     */
    @GetMapping("/category/{category}")
    public List<Skill> listByCategory(@PathVariable String category) {
        return skillService.listByCategory(category);
    }

    /**
     * 获取技能详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Skill> getById(@PathVariable Long id) {
        Skill skill = skillService.getById(id);
        if (skill == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(skill);
    }

    /**
     * 根据编码获取技能
     */
    @GetMapping("/code/{code}")
    public ResponseEntity<Skill> getByCode(@PathVariable String code) {
        Skill skill = skillService.getByCode(code);
        if (skill == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(skill);
    }

    // ==================== 管理接口 ====================

    /**
     * 添加技能
     */
    @PostMapping
    public ResponseEntity<String> add(@RequestBody Skill skill) {
        try {
            log.info("收到添加技能请求: code={}, name={}", skill.getCode(), skill.getName());
            skillService.add(skill);
            return ResponseEntity.ok("添加成功");
        } catch (IllegalArgumentException e) {
            log.warn("添加技能参数错误: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("添加技能异常: ", e);
            return ResponseEntity.internalServerError().body("服务器错误: " + e.getMessage());
        }
    }

    /**
     * 更新技能
     */
    @PutMapping("/{id}")
    public ResponseEntity<String> update(@PathVariable Long id, @RequestBody Skill skill) {
        try {
            skill.setId(id);
            skillService.update(skill);
            return ResponseEntity.ok("更新成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 删除技能
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        try {
            skillService.delete(id);
            return ResponseEntity.ok("删除成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 切换启用状态
     */
    @PutMapping("/{id}/toggle")
    public ResponseEntity<String> toggleEnabled(@PathVariable Long id) {
        try {
            skillService.toggleEnabled(id);
            return ResponseEntity.ok("状态已切换");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // ==================== 技能-工具映射 ====================

    /**
     * 绑定工具到技能
     */
    @PostMapping("/{skillId}/tools/{toolId}")
    public ResponseEntity<String> bindTool(
            @PathVariable Long skillId,
            @PathVariable Long toolId,
            @RequestParam(required = false, defaultValue = "0") Integer order,
            @RequestParam(required = false, defaultValue = "true") Boolean required) {
        try {
            skillService.bindTool(skillId, toolId, order, required);
            return ResponseEntity.ok("绑定成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 解绑工具
     */
    @DeleteMapping("/{skillId}/tools/{toolId}")
    public ResponseEntity<String> unbindTool(
            @PathVariable Long skillId,
            @PathVariable Long toolId) {
        skillService.unbindTool(skillId, toolId);
        return ResponseEntity.ok("解绑成功");
    }

    /**
     * 获取技能关联的工具
     */
    @GetMapping("/{id}/tools")
    public ResponseEntity<List<McpTool>> getSkillTools(@PathVariable Long id) {
        List<McpTool> tools = skillService.getSkillTools(id);
        return ResponseEntity.ok(tools);
    }

    // ==================== 执行接口 ====================

    /**
     * 执行技能
     */
    @PostMapping("/{code}/execute")
    public ApiResponse<SkillExecutionResult> execute(
            @PathVariable String code,
            @RequestBody(required = false) Map<String, Object> params) {
        SkillExecutionResult result = skillExecutor.execute(code, params);
        return ApiResponse.success(result);
    }

    /**
     * 测试技能执行
     */
    @PostMapping("/{id}/test")
    public ApiResponse<SkillExecutionResult> test(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> params) {
        Skill skill = skillService.getById(id);
        if (skill == null) {
            return ApiResponse.error("技能不存在: " + id);
        }

        SkillExecutionResult result = skillExecutor.execute(skill, params);
        return ApiResponse.success(result);
    }

    // ==================== 导入导出接口 ====================

    /**
     * 从配置文件重新加载技能
     */
    @PostMapping("/reload")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.SKILL_LIST_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_LIST_ENABLED, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_LIST_BUILTIN, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_LIST_BY_CATEGORY, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_BY_ID, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_BY_CODE, allEntries = true)
    })
    public ResponseEntity<Map<String, Object>> reloadSkills() {
        int loaded = skillLoaderService.loadSkillsFromConfig();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "成功加载 " + loaded + " 个技能",
                "count", loaded
        ));
    }

    /**
     * 从 findskills 远程地址重新加载技能
     */
    @PostMapping("/reload-findskills")
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.SKILL_LIST_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_LIST_ENABLED, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_LIST_BUILTIN, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_CATEGORIES, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_LIST_BY_CATEGORY, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_BY_ID, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SKILL_BY_CODE, allEntries = true)
    })
    public ResponseEntity<Map<String, Object>> reloadSkillsFromFindskills() {
        int loaded = skillLoaderService.loadSkillsFromFindskills();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "成功从 findskills 加载 " + loaded + " 个技能",
                "count", loaded
        ));
    }

    /**
     * 导入技能(JSON格式)
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importSkill(@RequestBody Map<String, Object> skillData) {
        try {
            log.info("收到技能导入请求: {}", skillData);
            // 将Map转回JSON字符串
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(skillData);
            boolean success = skillLoaderService.importSkillFromJson(json);
            if (success) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "技能导入成功"
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "技能导入失败，可能技能已存在或格式错误"
                ));
            }
        } catch (Exception e) {
            log.error("导入技能异常: ", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "导入失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 导出技能(JSON格式)
     */
    @GetMapping("/{id}/export")
    public ResponseEntity<Map<String, Object>> exportSkill(@PathVariable Long id) {
        String json = skillLoaderService.exportSkillToJson(id);
        if (json == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", json
        ));
    }
}
