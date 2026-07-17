package com.example.demo.file.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.file.domain.Document;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件上传记录Mapper
 */
@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
}