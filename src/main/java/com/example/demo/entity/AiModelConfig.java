package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 模型配置实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_model_config")
public class AiModelConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模型名称（显示用）
     */
    private String name;

    /**
     * 提供商: openai/aliyun/deepseek/anthropic/glm
     */
    private String provider;

    /**
     * API 请求地址
     */
    private String baseUrl;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * API Key（加密存储）
     */
    private String apiKey;

    /**
     * 是否为默认模型
     */
    private Boolean isDefault;

    /**
     * 是否启用
     */
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public void setName(String name) {
        this.name = trim(name);
    }

    public void setProvider(String provider) {
        this.provider = trim(provider);
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = trim(baseUrl);
    }

    public void setModelName(String modelName) {
        this.modelName = trim(modelName);
    }

    public void setApiKey(String apiKey) {
        this.apiKey = trim(apiKey);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
