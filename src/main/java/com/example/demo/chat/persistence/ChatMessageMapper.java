package com.example.demo.chat.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.chat.domain.ChatMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天消息 Mapper
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
