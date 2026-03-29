package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.McpTool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * MCP 工具 Mapper
 */
@Mapper
public interface McpToolMapper extends BaseMapper<McpTool> {

    @Select("SELECT * FROM mcp_tool WHERE name = #{name}")
    McpTool selectByName(String name);
}