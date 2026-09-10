package com.example.demo.mcp.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.mcp.domain.McpTool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * MCP 工具 Mapper。
 *
 * <p>用户可见性由 McpToolService 统一处理。保留的直接名称查询只允许读取系统工具，避免其他模块
 * 绕过服务层后跨用户读取私有工具。</p>
 */
@Mapper
public interface McpToolMapper extends BaseMapper<McpTool> {

    @Select("SELECT * FROM mcp_tool WHERE name = #{name} AND user_id IS NULL LIMIT 1")
    McpTool selectByName(String name);
}
