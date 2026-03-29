package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.SearchHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 搜索历史 Mapper
 */
@Mapper
public interface SearchHistoryMapper extends BaseMapper<SearchHistory> {

    /**
     * 获取最近的搜索历史
     */
    @Select("SELECT * FROM search_history ORDER BY create_time DESC LIMIT #{limit}")
    List<SearchHistory> selectRecent(int limit);

    /**
     * 获取热门搜索关键词
     */
    @Select("SELECT query, COUNT(*) as count FROM search_history " +
            "GROUP BY query ORDER BY count DESC LIMIT #{limit}")
    List<java.util.Map<String, Object>> selectHotQueries(int limit);

    /**
     * 搜索关键词统计
     */
    @Select("SELECT COUNT(*) FROM search_history WHERE query LIKE CONCAT('%', #{keyword}, '%')")
    int countByKeyword(String keyword);
}