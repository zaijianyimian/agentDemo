package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AiModelConfigMapper extends BaseMapper<AiModelConfig> {

    @Select("SELECT * FROM ai_model_config WHERE enabled = 1")
    List<AiModelConfig> selectEnabled();

    @Select("SELECT * FROM ai_model_config WHERE is_default = 1 AND enabled = 1 LIMIT 1")
    AiModelConfig selectDefault();

    @Update("UPDATE ai_model_config SET is_default = 0")
    int clearDefault();

    @Update("UPDATE ai_model_config SET enabled = 0")
    int clearEnabled();

    @Select("SELECT COUNT(*) FROM ai_model_config WHERE enabled = 1")
    int countEnabled();
}
