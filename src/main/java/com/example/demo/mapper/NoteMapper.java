package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 笔记 Mapper
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /**
     * 查询所有笔记，置顶优先，按更新时间倒序
     */
    @Select("SELECT * FROM note ORDER BY is_pinned DESC, update_time DESC")
    List<Note> findAllOrderByPinnedAndTime();
}