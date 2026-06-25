package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * MCP 工具类型枚举
 */
@Getter
@RequiredArgsConstructor
public enum ToolType {

    /**
     * HTTP API 类型 - 调用外部 HTTP 接口
     */
    HTTP_API("http_api", "HTTP API"),

    /**
     * 本地脚本类型 - 执行本地脚本或命令
     */
    LOCAL_SCRIPT("local_script", "本地脚本"),

    /**
     * MCP 客户端 - 连接外部 MCP Server 并调用工具
     */
    MCP_CLIENT("mcp_client", "MCP 客户端");

    /**
     * 数据库存储值
     */
    @EnumValue
    private final String code;

    /**
     * 显示名称
     */
    private final String displayName;

    /**
     * JSON 序列化时返回枚举名称
     */
    @JsonValue
    public String toJson() {
        return this.name();
    }

    /**
     * 根据代码获取枚举
     */
    public static ToolType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ToolType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * JSON 反序列化时支持多种格式
     * 支持：枚举名称（HTTP_API）、code（http_api）、displayName（HTTP API）
     */
    @JsonCreator
    public static ToolType fromJson(String value) {
        if (value == null) {
            return null;
        }

        // 尝试按枚举名称匹配（优先）
        try {
            return ToolType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
        }

        // 尝试按 code 匹配
        for (ToolType type : values()) {
            if (type.getCode().equalsIgnoreCase(value)) {
                return type;
            }
        }

        // 尝试按 displayName 匹配
        for (ToolType type : values()) {
            if (type.getDisplayName().equalsIgnoreCase(value)) {
                return type;
            }
        }

        return null;
    }
}