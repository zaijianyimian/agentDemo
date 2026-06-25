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
 * 技能-工具映射实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("skill_tool_mapping")
public class SkillToolMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 技能 ID
     */
    private Long skillId;

    /**
     * MCP 工具 ID
     */
    private Long toolId;

    /**
     * 调用顺序，数字越小越先执行
     */
    private Integer invokeOrder;

    /**
     * 是否必须
     */
    private Boolean isRequired;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}