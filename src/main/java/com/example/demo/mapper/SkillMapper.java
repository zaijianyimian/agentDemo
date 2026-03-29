package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Skill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 技能 Mapper
 */
@Mapper
public interface SkillMapper extends BaseMapper<Skill> {

    @Select("SELECT * FROM skill WHERE code = #{code}")
    Skill selectByCode(String code);
}