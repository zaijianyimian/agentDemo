package com.example.demo.knowledge.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.knowledge.domain.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 知识库文档数据访问接口，负责文档记录的基础持久化操作。
 * <p>
 * 支持按知识库查询文档及统计已完成处理的文档数量。
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    @Select("SELECT * FROM knowledge_document WHERE base_id = #{baseId} ORDER BY create_time DESC")
    List<KnowledgeDocument> selectByBaseId(Long baseId);

    @Select("SELECT COUNT(*) FROM knowledge_document WHERE base_id = #{baseId} AND status = 'completed'")
    int countCompletedByBaseId(Long baseId);
}