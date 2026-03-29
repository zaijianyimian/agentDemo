package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    @Select("SELECT * FROM knowledge_document WHERE base_id = #{baseId} ORDER BY create_time DESC")
    List<KnowledgeDocument> selectByBaseId(Long baseId);

    @Select("SELECT COUNT(*) FROM knowledge_document WHERE base_id = #{baseId} AND status = 'completed'")
    int countCompletedByBaseId(Long baseId);
}