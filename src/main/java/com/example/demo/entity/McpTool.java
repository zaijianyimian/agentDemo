package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MCP 工具配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("mcp_tool")
public class McpTool {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工具名称，唯一标识
     */
    private String name;

    /**
     * 工具显示名称
     */
    private String displayName;

    /**
     * 工具描述，AI 会根据此描述决定是否调用
     */
    private String description;

    /**
     * 工具类型
     */
    private ToolType toolType;

    /**
     * 工具配置，JSON 格式
     * HTTP API: {"url": "...", "method": "GET/POST", "headers": {...}, "timeout": 30}
     * 本地脚本: {"scriptPath": "...", "timeout": 10, "workingDir": "."}
     */
    private String config;

    /**
     * 输入参数 Schema，JSON Schema 格式
     * {"type": "object", "properties": {...}, "required": [...]}
     */
    private String inputSchema;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}