package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.SystemSettings;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 系统设置 Mapper
 */
@Mapper
public interface SystemSettingsMapper extends BaseMapper<SystemSettings> {

    /**
     * 根据分类查询配置
     */
    @Select("SELECT * FROM system_settings WHERE category = #{category}")
    List<SystemSettings> selectByCategory(String category);

    /**
     * 根据分类和键查询配置
     */
    @Select("SELECT * FROM system_settings WHERE category = #{category} AND config_key = #{configKey}")
    SystemSettings selectByCategoryAndKey(String category, String configKey);

    /**
     * 删除指定分类的所有配置
     */
    @Delete("DELETE FROM system_settings WHERE category = #{category}")
    void deleteByCategory(String category);
}