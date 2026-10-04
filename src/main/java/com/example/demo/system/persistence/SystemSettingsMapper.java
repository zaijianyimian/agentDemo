package com.example.demo.system.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.system.domain.SystemSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 系统设置 Mapper。
 *
 * <p>管理接口下线后仅保留按分类+键读取的单条查询，写入与批量读取请直接走数据库或迁移脚本。</p>
 */
@Mapper
public interface SystemSettingsMapper extends BaseMapper<SystemSettings> {

    /**
     * 根据分类和键查询配置
     */
    @Select("SELECT * FROM system_settings WHERE category = #{category} AND config_key = #{configKey}")
    SystemSettings selectByCategoryAndKey(String category, String configKey);
}
