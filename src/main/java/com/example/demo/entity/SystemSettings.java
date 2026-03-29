package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统设置实体
 * 使用键值对存储各类配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("system_settings")
public class SystemSettings {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 配置分类: system/database/search/mail/schedule/file
     */
    private String category;

    /**
     * 配置键
     */
    @TableField("config_key")
    private String configKey;

    /**
     * 配置值
     */
    @TableField("config_value")
    private String configValue;

    /**
     * 配置描述
     */
    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}