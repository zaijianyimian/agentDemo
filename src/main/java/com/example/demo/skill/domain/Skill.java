package com.example.demo.skill.domain;

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
 * AI 技能实体。
 *
 * <p>内置技能为系统全局记录（userId=null, isBuiltin=true）；用户创建的技能必须带 owner 且
 * isBuiltin=false。普通用户可以使用内置技能，但不能修改/删除它们。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("skill")
public class Skill {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户；null 表示系统全局技能。 */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long userId;

    private String code;
    private String name;
    private String description;
    private String category;
    private String icon;
    private Boolean enabled;
    private Boolean isBuiltin;
    private String config;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
