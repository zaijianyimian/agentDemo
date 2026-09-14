package com.example.demo.chat.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.chat.domain.ChatHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天记录 Mapper
 */
@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
}
