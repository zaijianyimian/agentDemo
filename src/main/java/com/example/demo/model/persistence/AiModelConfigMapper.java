package com.example.demo.model.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.model.domain.AiModelConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * AI 模型配置的数据访问接口，负责配置记录的基础持久化操作。
 * <p>
 * 提供启用模型、默认模型查询以及状态批量更新能力。
 */
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

    @Select("SELECT * FROM ai_model_config WHERE enabled = 1 AND purpose = #{purpose} ORDER BY id ASC")
    List<AiModelConfig> selectEnabledByPurpose(String purpose);
}
