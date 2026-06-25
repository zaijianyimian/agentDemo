package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 聊天会话 Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {

    /**
     * 查询所有会话，按最后消息时间倒序
     */
    @Select("SELECT * FROM chat_session ORDER BY last_message_time DESC, create_time DESC")
    List<ChatSession> findAllOrderByLastMessageTime();
}