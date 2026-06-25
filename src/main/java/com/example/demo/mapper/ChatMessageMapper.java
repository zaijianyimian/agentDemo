package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 聊天消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {

    /**
     * 查询会话的所有消息，按时间正序
     */
    @Select("SELECT * FROM chat_message WHERE session_id = #{sessionId} ORDER BY create_time ASC")
    List<ChatMessageEntity> findBySessionIdOrderByCreateTime(@Param("sessionId") Long sessionId);
}