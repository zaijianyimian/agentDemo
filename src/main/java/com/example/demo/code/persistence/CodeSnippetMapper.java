package com.example.demo.code.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.code.domain.CodeSnippet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 代码片段 Mapper
 */
@Mapper
public interface CodeSnippetMapper extends BaseMapper<CodeSnippet> {

    /**
     * 查询所有代码片段，按更新时间倒序。
     * 带 LIMIT 200 上限防止全表扫描在累积数据后 RT 飙升。
     */
    @Select("SELECT * FROM code_snippet ORDER BY update_time DESC LIMIT 200")
    List<CodeSnippet> findAllOrderByTime();

    /**
     * 按语言查询
     */
    @Select("SELECT * FROM code_snippet WHERE language = #{language} ORDER BY update_time DESC")
    List<CodeSnippet> findByLanguage(String language);
}