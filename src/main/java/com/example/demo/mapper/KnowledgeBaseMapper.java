package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    @Select("SELECT * FROM knowledge_base WHERE enabled = 1")
    List<KnowledgeBase> selectEnabled();

    @Select("SELECT collection_name FROM knowledge_base WHERE id = #{id}")
    String selectCollectionNameById(Long id);
}