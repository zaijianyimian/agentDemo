package com.example.demo.knowledge.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.knowledge.domain.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库数据访问接口，提供知识库实体的基础持久化操作。
 * <p>
 * 支持查询已启用知识库及其向量集合名称。
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    @Select("SELECT * FROM knowledge_base WHERE enabled = 1")
    List<KnowledgeBase> selectEnabled();

    @Select("SELECT collection_name FROM knowledge_base WHERE id = #{id}")
    String selectCollectionNameById(Long id);
}