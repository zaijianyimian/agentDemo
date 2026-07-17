package com.example.demo.skill.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.mcp.domain.McpTool;
import com.example.demo.skill.domain.SkillToolMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 技能-工具映射 Mapper
 */
@Mapper
public interface SkillToolMappingMapper extends BaseMapper<SkillToolMapping> {

    @Select("SELECT t.* FROM mcp_tool t " +
            "INNER JOIN skill_tool_mapping stm ON t.id = stm.tool_id " +
            "WHERE stm.skill_id = #{skillId} " +
            "ORDER BY stm.invoke_order")
    List<McpTool> selectToolsBySkillId(Long skillId);
}