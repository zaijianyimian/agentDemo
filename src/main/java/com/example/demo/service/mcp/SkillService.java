package com.example.demo.service.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.McpTool;
import com.example.demo.entity.Skill;
import com.example.demo.entity.SkillToolMapping;
import com.example.demo.mapper.McpToolMapper;
import com.example.demo.mapper.SkillMapper;
import com.example.demo.mapper.SkillToolMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 技能服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillMapper skillMapper;
    private final SkillToolMappingMapper skillToolMappingMapper;
    private final McpToolMapper mcpToolMapper;

    // ==================== 查询操作 ====================

    /**
     * 获取所有技能
     */
    public List<Skill> listAll() {
        return skillMapper.selectList(null);
    }

    /**
     * 获取启用的技能
     */
    public List<Skill> listEnabled() {
        return skillMapper.selectList(
                new LambdaQueryWrapper<Skill>().eq(Skill::getEnabled, true)
        );
    }

    /**
     * 获取内置技能
     */
    public List<Skill> listBuiltin() {
        return skillMapper.selectList(
                new LambdaQueryWrapper<Skill>().eq(Skill::getIsBuiltin, true)
        );
    }

    /**
     * 按分类获取技能
     */
    public List<Skill> listByCategory(String category) {
        return skillMapper.selectList(
                new LambdaQueryWrapper<Skill>()
                        .eq(Skill::getCategory, category)
                        .eq(Skill::getEnabled, true)
        );
    }

    /**
     * 获取所有分类
     */
    public List<String> listCategories() {
        List<Skill> skills = skillMapper.selectList(
                new LambdaQueryWrapper<Skill>()
                        .select(Skill::getCategory)
                        .isNotNull(Skill::getCategory)
                        .groupBy(Skill::getCategory)
        );
        return skills.stream()
                .map(Skill::getCategory)
                .collect(Collectors.toList());
    }

    /**
     * 根据 ID 获取技能
     */
    public Skill getById(Long id) {
        return skillMapper.selectById(id);
    }

    /**
     * 根据编码获取技能
     */
    public Skill getByCode(String code) {
        return skillMapper.selectOne(
                new LambdaQueryWrapper<Skill>().eq(Skill::getCode, code)
        );
    }

    // ==================== 增删改操作 ====================

    /**
     * 添加技能
     */
    @Transactional
    public void add(Skill skill) {
        // 检查编码是否已存在
        Skill existing = getByCode(skill.getCode());
        if (existing != null) {
            throw new IllegalArgumentException("技能编码已存在: " + skill.getCode());
        }

        // 设置默认值
        if (skill.getEnabled() == null) {
            skill.setEnabled(true);
        }
        if (skill.getIsBuiltin() == null) {
            skill.setIsBuiltin(false);
        }

        // 设置时间
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        skill.setCreateTime(now);
        skill.setUpdateTime(now);

        skillMapper.insert(skill);
        log.info("添加技能: {}", skill.getCode());
    }

    /**
     * 更新技能
     */
    @Transactional
    public void update(Skill skill) {
        Skill existing = skillMapper.selectById(skill.getId());
        if (existing == null) {
            throw new IllegalArgumentException("技能不存在: " + skill.getId());
        }

        // 内置技能不允许修改编码
        if (Boolean.TRUE.equals(existing.getIsBuiltin()) && !existing.getCode().equals(skill.getCode())) {
            throw new IllegalArgumentException("内置技能不允许修改编码");
        }

        skillMapper.updateById(skill);
        log.info("更新技能: {}", skill.getCode());
    }

    /**
     * 删除技能
     */
    @Transactional
    public void delete(Long id) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) {
            return;
        }

        // 内置技能不允许删除
        if (Boolean.TRUE.equals(skill.getIsBuiltin())) {
            throw new IllegalArgumentException("内置技能不允许删除: " + skill.getCode());
        }

        // 删除关联映射
        skillToolMappingMapper.delete(
                new LambdaQueryWrapper<SkillToolMapping>().eq(SkillToolMapping::getSkillId, id)
        );

        skillMapper.deleteById(id);
        log.info("删除技能: {}", skill.getCode());
    }

    /**
     * 切换启用状态
     */
    public void toggleEnabled(Long id) {
        Skill skill = skillMapper.selectById(id);
        if (skill == null) {
            throw new IllegalArgumentException("技能不存在: " + id);
        }

        skill.setEnabled(!skill.getEnabled());
        skillMapper.updateById(skill);
        log.info("切换技能状态: {} -> {}", skill.getCode(), skill.getEnabled());
    }

    // ==================== 技能-工具映射 ====================

    /**
     * 绑定工具到技能
     */
    @Transactional
    public void bindTool(Long skillId, Long toolId, Integer invokeOrder, Boolean isRequired) {
        // 检查技能和工具是否存在
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null) {
            throw new IllegalArgumentException("技能不存在: " + skillId);
        }

        McpTool tool = mcpToolMapper.selectById(toolId);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在: " + toolId);
        }

        // 检查映射是否已存在
        SkillToolMapping existing = skillToolMappingMapper.selectOne(
                new LambdaQueryWrapper<SkillToolMapping>()
                        .eq(SkillToolMapping::getSkillId, skillId)
                        .eq(SkillToolMapping::getToolId, toolId)
        );

        if (existing != null) {
            // 更新现有映射
            existing.setInvokeOrder(invokeOrder != null ? invokeOrder : 0);
            existing.setIsRequired(isRequired != null ? isRequired : true);
            skillToolMappingMapper.updateById(existing);
        } else {
            // 创建新映射
            SkillToolMapping mapping = SkillToolMapping.builder()
                    .skillId(skillId)
                    .toolId(toolId)
                    .invokeOrder(invokeOrder != null ? invokeOrder : 0)
                    .isRequired(isRequired != null ? isRequired : true)
                    .build();
            skillToolMappingMapper.insert(mapping);
        }

        log.info("绑定工具到技能: {} -> {}", tool.getName(), skill.getCode());
    }

    /**
     * 解绑工具
     */
    @Transactional
    public void unbindTool(Long skillId, Long toolId) {
        skillToolMappingMapper.delete(
                new LambdaQueryWrapper<SkillToolMapping>()
                        .eq(SkillToolMapping::getSkillId, skillId)
                        .eq(SkillToolMapping::getToolId, toolId)
        );
        log.info("解绑工具: {} <- {}", skillId, toolId);
    }

    /**
     * 获取技能关联的工具
     */
    public List<McpTool> getSkillTools(Long skillId) {
        // 获取映射关系
        List<SkillToolMapping> mappings = skillToolMappingMapper.selectList(
                new LambdaQueryWrapper<SkillToolMapping>()
                        .eq(SkillToolMapping::getSkillId, skillId)
                        .orderByAsc(SkillToolMapping::getInvokeOrder)
        );

        if (mappings.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取工具列表
        List<Long> toolIds = mappings.stream()
                .map(SkillToolMapping::getToolId)
                .collect(Collectors.toList());

        return mcpToolMapper.selectBatchIds(toolIds);
    }

    /**
     * 获取技能的工具映射
     */
    public List<SkillToolMapping> getSkillToolMappings(Long skillId) {
        return skillToolMappingMapper.selectList(
                new LambdaQueryWrapper<SkillToolMapping>()
                        .eq(SkillToolMapping::getSkillId, skillId)
                        .orderByAsc(SkillToolMapping::getInvokeOrder)
        );
    }
}