package com.example.demo.note.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.note.domain.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 笔记 Mapper
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /**
     * 查询所有笔记，置顶优先，按更新时间倒序。
     * LIMIT 200 防止累积数据后全表扫描导致 RT 飙升。
     */
    @Select("SELECT * FROM note ORDER BY is_pinned DESC, update_time DESC LIMIT 200")
    List<Note> findAllOrderByPinnedAndTime();
}