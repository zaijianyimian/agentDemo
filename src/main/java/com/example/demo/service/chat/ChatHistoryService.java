package com.example.demo.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.ChatMessageEntity;
import com.example.demo.entity.ChatSession;
import com.example.demo.mapper.ChatMessageMapper;
import com.example.demo.mapper.ChatSessionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天历史服务
 * 管理会话和消息的持久化
 */
@Slf4j
@Service
public class ChatHistoryService {

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    /**
     * 创建新会话
     */
    @Transactional
    public ChatSession createSession(String title) {
        ChatSession session = ChatSession.builder()
                .title(title != null ? title : "新会话")
                .messageCount(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        chatSessionMapper.insert(session);
        log.info("创建新会话: id={}, title={}", session.getId(), session.getTitle());
        return session;
    }

    /**
     * 获取所有会话列表
     */
    public List<ChatSession> getAllSessions() {
        return chatSessionMapper.findAllOrderByLastMessageTime();
    }

    /**
     * 获取会话详情
     */
    public ChatSession getSession(Long sessionId) {
        return chatSessionMapper.selectById(sessionId);
    }

    /**
     * 更新会话标题
     */
    @Transactional
    public ChatSession updateSessionTitle(Long sessionId, String title) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setTitle(title);
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
        return session;
    }

    /**
     * 删除会话（会自动删除关联消息）
     */
    @Transactional
    public boolean deleteSession(Long sessionId) {
        // 先删除消息
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessageEntity::getSessionId, sessionId);
        chatMessageMapper.delete(wrapper);
        // 再删除会话
        int result = chatSessionMapper.deleteById(sessionId);
        log.info("删除会话: id={}, result={}", sessionId, result > 0);
        return result > 0;
    }

    /**
     * 添加消息到会话
     */
    @Transactional
    public ChatMessageEntity addMessage(Long sessionId, String role, String content, String model) {
        // 创建消息
        ChatMessageEntity message = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .model(model)
                .createTime(LocalDateTime.now())
                .build();
        chatMessageMapper.insert(message);

        // 更新会话统计
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setMessageCount(session.getMessageCount() + 1);
            session.setLastMessageTime(LocalDateTime.now());
            session.setUpdateTime(LocalDateTime.now());
            // 如果是第一条消息，自动生成标题
            if (session.getMessageCount() == 1 && "user".equals(role)) {
                String autoTitle = generateAutoTitle(content);
                session.setTitle(autoTitle);
            }
            chatSessionMapper.updateById(session);
        }

        log.info("添加消息: sessionId={}, role={}, contentLength={}", sessionId, role, content.length());
        return message;
    }

    /**
     * 获取会话的所有消息
     */
    public List<ChatMessageEntity> getSessionMessages(Long sessionId) {
        return chatMessageMapper.findBySessionIdOrderByCreateTime(sessionId);
    }

    /**
     * 清空会话消息
     */
    @Transactional
    public boolean clearSessionMessages(Long sessionId) {
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessageEntity::getSessionId, sessionId);
        chatMessageMapper.delete(wrapper);

        // 重置会话统计
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session != null) {
            session.setMessageCount(0);
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
        return true;
    }

    /**
     * 自动生成会话标题
     */
    private String generateAutoTitle(String content) {
        // 截取前30个字符作为标题
        if (content == null || content.isEmpty()) {
            return "新会话";
        }
        String title = content.trim();
        if (title.length() > 30) {
            title = title.substring(0, 30) + "...";
        }
        return title;
    }
}