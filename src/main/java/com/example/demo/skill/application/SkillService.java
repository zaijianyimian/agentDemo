package com.example.demo.skill.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.infrastructure.config.CacheConfig;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import com.example.demo.mcp.application.McpToolService;
import com.example.demo.mcp.domain.McpTool;
import com.example.demo.skill.domain.Skill;
import com.example.demo.skill.domain.SkillToolMapping;
import com.example.demo.skill.persistence.SkillMapper;
import com.example.demo.skill.persistence.SkillToolMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 多用户 Skill 服务。
 *
 * <p>系统内置 Skill：{@code user_id IS NULL && is_builtin=1}；用户自定义 Skill：
 * {@code user_id=currentUser && is_builtin=0}。普通用户查询时两者可见，但只有自己的自定义 Skill
 * 可以修改、删除或调整工具映射。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private static final List<String> SKILL_CACHE_NAMES = List.of(
            CacheConfig.SKILL_LIST_ALL,
            CacheConfig.SKILL_LIST_ENABLED,
            CacheConfig.SKILL_LIST_BUILTIN,
            CacheConfig.SKILL_CATEGORIES,
            CacheConfig.SKILL_LIST_BY_CATEGORY,
            CacheConfig.SKILL_BY_ID,
            CacheConfig.SKILL_BY_CODE
    );

    private final SkillMapper skillMapper;
    private final SkillToolMappingMapper skillToolMappingMapper;
    private final McpToolService mcpToolService;
    private final CurrentUserProvider currentUserProvider;
    private final CacheManager cacheManager;

    @Cacheable(cacheNames = CacheConfig.SKILL_LIST_ALL)
    public List<Skill> listAll() {
        return skillMapper.selectList(visibleWrapper());
    }

    @Cacheable(cacheNames = CacheConfig.SKILL_LIST_ENABLED)
    public List<Skill> listEnabled() {
        return skillMapper.selectList(visibleWrapper().eq(Skill::getEnabled, true));
    }

    @Cacheable(cacheNames = CacheConfig.SKILL_LIST_BUILTIN)
    public List<Skill> listBuiltin() {
        return skillMapper.selectList(new LambdaQueryWrapper<Skill>()
                .isNull(Skill::getUserId)
                .eq(Skill::getIsBuiltin, true));
    }

    @Cacheable(cacheNames = CacheConfig.SKILL_LIST_BY_CATEGORY, key = "#category")
    public List<Skill> listByCategory(String category) {
        return skillMapper.selectList(visibleWrapper()
                .eq(Skill::getCategory, category)
                .eq(Skill::getEnabled, true));
    }

    @Cacheable(cacheNames = CacheConfig.SKILL_CATEGORIES)
    public List<String> listCategories() {
        return listAll().stream()
                .map(Skill::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    @Cacheable(cacheNames = CacheConfig.SKILL_BY_ID, key = "#id", unless = "#result == null")
    public Skill getById(Long id) {
        if (id == null) {
            return null;
        }
        return skillMapper.selectOne(visibleWrapper().eq(Skill::getId, id).last("LIMIT 1"));
    }

    @Cacheable(cacheNames = CacheConfig.SKILL_BY_CODE, key = "#code", unless = "#result == null")
    public Skill getByCode(String code) {
        List<Skill> matches = skillMapper.selectList(
                visibleWrapper().eq(Skill::getCode, code).orderByAsc(Skill::getId));
        Long userId = currentUserProvider.currentUserId().orElse(null);
        if (userId != null) {
            for (Skill skill : matches) {
                if (Objects.equals(userId, skill.getUserId())) {
                    return skill;
                }
            }
        }
        return matches.stream().filter(skill -> skill.getUserId() == null).findFirst().orElse(null);
    }

    @Transactional
    public void add(Skill skill) {
        Long owner = currentUserProvider.currentUserId().orElse(null);
        if (owner != null) {
            if (findGlobalByCode(skill.getCode()) != null || getOwnedByCode(skill.getCode(), owner) != null) {
                throw new IllegalArgumentException("技能编码已被占用: " + skill.getCode());
            }
            skill.setUserId(owner);
            skill.setIsBuiltin(false);
        } else {
            if (getOwnedByCode(skill.getCode(), null) != null) {
                throw new IllegalArgumentException("系统技能编码已存在: " + skill.getCode());
            }
            skill.setUserId(null);
            if (skill.getIsBuiltin() == null) {
                skill.setIsBuiltin(true);
            }
        }
        if (skill.getEnabled() == null) {
            skill.setEnabled(true);
        }
        LocalDateTime now = LocalDateTime.now();
        skill.setCreateTime(now);
        skill.setUpdateTime(now);
        skillMapper.insert(skill);
        evictSkillCaches();
    }

    @Transactional
    public void update(Skill skill) {
        Long owner = currentUserProvider.currentUserId().orElse(null);
        Skill existing = getOwnedById(skill.getId(), owner);
        if (existing == null) {
            throw new IllegalArgumentException("技能不存在或无权修改: " + skill.getId());
        }
        if (owner != null && Boolean.TRUE.equals(existing.getIsBuiltin())) {
            throw new IllegalArgumentException("内置技能不允许修改");
        }
        if (!Objects.equals(existing.getCode(), skill.getCode())) {
            if (findGlobalByCode(skill.getCode()) != null || getOwnedByCode(skill.getCode(), owner) != null) {
                throw new IllegalArgumentException("技能编码已被占用: " + skill.getCode());
            }
        }
        skill.setUserId(owner);
        skill.setIsBuiltin(owner == null && Boolean.TRUE.equals(existing.getIsBuiltin()));
        skill.setCreateTime(existing.getCreateTime());
        skill.setUpdateTime(LocalDateTime.now());
        skillMapper.updateById(skill);
        evictSkillCaches();
    }

    @Transactional
    public void delete(Long id) {
        Long owner = currentUserProvider.currentUserId().orElse(null);
        Skill skill = getOwnedById(id, owner);
        if (skill == null) {
            return;
        }
        if (Boolean.TRUE.equals(skill.getIsBuiltin())) {
            throw new IllegalArgumentException("内置技能不允许删除: " + skill.getCode());
        }
        skillToolMappingMapper.delete(
                new LambdaQueryWrapper<SkillToolMapping>().eq(SkillToolMapping::getSkillId, id));
        skillMapper.deleteById(id);
        evictSkillCaches();
    }

    public void toggleEnabled(Long id) {
        Long owner = currentUserProvider.currentUserId().orElse(null);
        Skill skill = getOwnedById(id, owner);
        if (skill == null) {
            throw new IllegalArgumentException("技能不存在或无权修改: " + id);
        }
        if (owner != null && Boolean.TRUE.equals(skill.getIsBuiltin())) {
            throw new IllegalArgumentException("内置技能不允许修改");
        }
        skill.setEnabled(!Boolean.TRUE.equals(skill.getEnabled()));
        skill.setUpdateTime(LocalDateTime.now());
        skillMapper.updateById(skill);
        evictSkillCaches();
    }

    @Transactional
    public void bindTool(Long skillId, Long toolId, Integer invokeOrder, Boolean isRequired) {
        Skill skill = requireMutableSkill(skillId);
        McpTool tool = mcpToolService.getById(toolId);
        if (tool == null) {
            throw new IllegalArgumentException("工具不存在或无权访问: " + toolId);
        }
        SkillToolMapping existing = skillToolMappingMapper.selectOne(
                new LambdaQueryWrapper<SkillToolMapping>()
                        .eq(SkillToolMapping::getSkillId, skillId)
                        .eq(SkillToolMapping::getToolId, toolId));
        if (existing != null) {
            existing.setInvokeOrder(invokeOrder != null ? invokeOrder : 0);
            existing.setIsRequired(isRequired != null ? isRequired : true);
            skillToolMappingMapper.updateById(existing);
        } else {
            skillToolMappingMapper.insert(SkillToolMapping.builder()
                    .skillId(skill.getId())
                    .toolId(toolId)
                    .invokeOrder(invokeOrder != null ? invokeOrder : 0)
                    .isRequired(isRequired != null ? isRequired : true)
                    .build());
        }
        evictSkillCaches();
    }

    @Transactional
    public void unbindTool(Long skillId, Long toolId) {
        requireMutableSkill(skillId);
        skillToolMappingMapper.delete(
                new LambdaQueryWrapper<SkillToolMapping>()
                        .eq(SkillToolMapping::getSkillId, skillId)
                        .eq(SkillToolMapping::getToolId, toolId));
        evictSkillCaches();
    }

    public List<McpTool> getSkillTools(Long skillId) {
        if (getById(skillId) == null) {
            return new ArrayList<>();
        }
        List<SkillToolMapping> mappings = getSkillToolMappings(skillId);
        if (mappings.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> toolIds = mappings.stream().map(SkillToolMapping::getToolId).toList();
        List<McpTool> tools = mcpToolService.listByIds(toolIds);
        java.util.Map<Long, McpTool> toolMap = tools.stream()
                .collect(Collectors.toMap(McpTool::getId, tool -> tool, (a, b) -> a));
        return toolIds.stream().map(toolMap::get).filter(Objects::nonNull).toList();
    }

    public List<SkillToolMapping> getSkillToolMappings(Long skillId) {
        if (getById(skillId) == null) {
            return List.of();
        }
        return skillToolMappingMapper.selectList(
                new LambdaQueryWrapper<SkillToolMapping>()
                        .eq(SkillToolMapping::getSkillId, skillId)
                        .orderByAsc(SkillToolMapping::getInvokeOrder));
    }

    private Skill requireMutableSkill(Long id) {
        Long owner = currentUserProvider.currentUserId().orElse(null);
        Skill skill = getOwnedById(id, owner);
        if (skill == null || (owner != null && Boolean.TRUE.equals(skill.getIsBuiltin()))) {
            throw new IllegalArgumentException("技能不存在或无权修改: " + id);
        }
        return skill;
    }

    private LambdaQueryWrapper<Skill> visibleWrapper() {
        Long userId = currentUserProvider.currentUserId().orElse(null);
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<>();
        if (userId == null) {
            return wrapper.isNull(Skill::getUserId);
        }
        return wrapper.and(scope -> scope.isNull(Skill::getUserId).or().eq(Skill::getUserId, userId));
    }

    private Skill getOwnedById(Long id, Long owner) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<Skill>().eq(Skill::getId, id);
        if (owner == null) {
            wrapper.isNull(Skill::getUserId);
        } else {
            wrapper.eq(Skill::getUserId, owner);
        }
        return skillMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    private Skill getOwnedByCode(String code, Long owner) {
        LambdaQueryWrapper<Skill> wrapper = new LambdaQueryWrapper<Skill>().eq(Skill::getCode, code);
        if (owner == null) {
            wrapper.isNull(Skill::getUserId);
        } else {
            wrapper.eq(Skill::getUserId, owner);
        }
        return skillMapper.selectOne(wrapper.last("LIMIT 1"));
    }

    private Skill findGlobalByCode(String code) {
        return skillMapper.selectOne(new LambdaQueryWrapper<Skill>()
                .eq(Skill::getCode, code)
                .isNull(Skill::getUserId)
                .last("LIMIT 1"));
    }

    private void evictSkillCaches() {
        for (String cacheName : SKILL_CACHE_NAMES) {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
