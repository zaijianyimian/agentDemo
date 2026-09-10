package com.example.demo.mcp.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MCP 工具配置实体。
 *
 * <p>{@code userId == null} 表示系统工具；非空表示用户私有工具。普通用户可以读取系统工具和自己的
 * 工具，但只能修改自己的记录。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("mcp_tool")
public class McpTool {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户；null 表示系统全局工具。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long userId;

    private String name;
    private String displayName;
    private String description;
    private ToolType toolType;
    private String config;
    private String inputSchema;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
