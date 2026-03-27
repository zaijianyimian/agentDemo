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
 * AI 技能实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("skill")
public class Skill {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 技能编码，系统唯一标识
     */
    private String code;

    /**
     * 技能名称
     */
    private String name;

    /**
     * 技能描述，AI 调用依据
     */
    private String description;

    /**
     * 技能分类：search, data, system, ai, custom
     */
    private String category;

    /**
     * 图标名称
     */
    private String icon;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否内置技能
     */
    private Boolean isBuiltin;

    /**
     * 技能配置，JSON 格式
     */
    private String config;

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