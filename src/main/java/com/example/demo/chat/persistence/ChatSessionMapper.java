package com.example.demo.chat.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.chat.domain.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话 Mapper
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
