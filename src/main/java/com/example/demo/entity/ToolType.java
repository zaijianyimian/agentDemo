package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
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
    LOCAL_SCRIPT("local_script", "本地脚本");

    /**
     * 数据库存储值
     */
    @EnumValue
    private final String code;

    /**
     * 显示名称
     */
    @JsonValue
    private final String displayName;

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
}