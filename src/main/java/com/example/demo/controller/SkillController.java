package com.example.demo.controller;

import com.example.demo.dto.SkillExecutionResult;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.Skill;
import com.example.demo.service.SkillExecutor;
import com.example.demo.service.SkillService;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能管理控制器
 */
@RestController
@RequestMapping("/api/skill")
public class SkillController {

    @Resource
    private SkillService skillService;

    @Resource
    private SkillExecutor skillExecutor;

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
            skillService.add(skill);
            return ResponseEntity.ok("添加成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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
    public ResponseEntity<SkillExecutionResult> execute(
            @PathVariable String code,
            @RequestBody(required = false) Map<String, Object> params) {
        SkillExecutionResult result = skillExecutor.execute(code, params);
        return ResponseEntity.ok(result);
    }

    /**
     * 测试技能执行
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<SkillExecutionResult> test(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> params) {
        Skill skill = skillService.getById(id);
        if (skill == null) {
            return ResponseEntity.badRequest().body(
                    SkillExecutionResult.failure(null, "技能不存在: " + id)
            );
        }

        SkillExecutionResult result = skillExecutor.execute(skill, params);
        return ResponseEntity.ok(result);
    }
}