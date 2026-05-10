package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.VirtualAssistant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 虚拟助手 Mapper
 */
@Mapper
public interface VirtualAssistantMapper extends BaseMapper<VirtualAssistant> {

    /**
     * 查询所有启用的助手
     */
    @Select("SELECT * FROM virtual_assistant WHERE enabled = 1 ORDER BY update_time DESC")
    List<VirtualAssistant> findAllEnabled();

    /**
     * 查询指定平台的助手
     */
    @Select("SELECT * FROM virtual_assistant WHERE source_platform = #{platform} AND enabled = 1")
    List<VirtualAssistant> findByPlatform(@Param("platform") String platform);

    /**
     * 根据集合名称查询助手
     */
    @Select("SELECT * FROM virtual_assistant WHERE collection_name = #{collectionName}")
    VirtualAssistant findByCollectionName(@Param("collectionName") String collectionName);
}