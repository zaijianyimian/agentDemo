package com.example.demo.chat.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.chat.domain.ChatMessageEntity;
import com.example.demo.chat.domain.ChatSession;
import com.example.demo.chat.persistence.ChatMessageMapper;
import com.example.demo.chat.persistence.ChatSessionMapper;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 聊天历史服务。
 *
 * <p>会话表通过 MyBatis TenantLine 按 JWT {@code user_id} 自动隔离；所有消息操作在访问
 * {@code chat_message} 前再次校验会话归属，避免用户通过猜测 sessionId 越权读取或写入消息。</p>
 */
@Slf4j
@Service
public class ChatHistoryService {

    @Resource
    private ChatSessionMapper chatSessionMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private CurrentUserProvider currentUserProvider;

    /**
     * 创建当前用户的新会话。
     *
     * @param title 会话标题。
     * @return 已创建会话。
     */
    @Transactional
    public ChatSession createSession(String title) {
        long userId = currentUserProvider.requireUserId();
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .title(title != null ? title : "新会话")
                .messageCount(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        chatSessionMapper.insert(session);
        log.info("创建新会话: userId={}, id={}, title={}", userId, session.getId(), session.getTitle());
        return session;
    }

    /**
     * 获取当前用户全部会话。
     *
     * @return 当前用户会话列表。
     */
    public List<ChatSession> getAllSessions() {
        currentUserProvider.requireUserId();
        return chatSessionMapper.findAllOrderByLastMessageTime();
    }

    /**
     * 获取当前用户所有聊天消息。
     *
     * @return 当前用户消息列表。
     */
    public List<ChatMessageEntity> getAllMessages() {
        List<Long> sessionIds = getAllSessions().stream().map(ChatSession::getId).toList();
        if (sessionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessageEntity>()
                        .in(ChatMessageEntity::getSessionId, sessionIds)
                        .orderByAsc(ChatMessageEntity::getCreateTime));
    }

    /**
     * 获取当前用户会话详情。
     *
     * @param sessionId 会话 ID。
     * @return 会话，不存在或不属于当前用户时返回 null。
     */
    public ChatSession getSession(Long sessionId) {
        currentUserProvider.requireUserId();
        return chatSessionMapper.selectById(sessionId);
    }

    /**
     * 更新当前用户会话标题。
     *
     * @param sessionId 会话 ID。
     * @param title 新标题。
     * @return 更新后的会话；无权访问时返回 null。
     */
    @Transactional
    public ChatSession updateSessionTitle(Long sessionId, String title) {
        ChatSession session = getSession(sessionId);
        if (session != null) {
            session.setTitle(title);
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.updateById(session);
        }
        return session;
    }

    /**
     * 删除当前用户会话及其消息。
     *
     * @param sessionId 会话 ID。
     * @return 是否删除成功。
     */
    @Transactional
    public boolean deleteSession(Long sessionId) {
        ChatSession session = getSession(sessionId);
        if (session == null) {
            return false;
        }
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId));
        int result = chatSessionMapper.deleteById(sessionId);
        log.info("删除会话: userId={}, id={}, result={}", session.getUserId(), sessionId, result > 0);
        return result > 0;
    }

    /**
     * 添加消息到当前用户会话。
     *
     * @param sessionId 会话 ID。
     * @param role 消息角色。
     * @param content 消息内容。
     * @param model 模型标识。
     * @return 新消息。
     * @throws AccessDeniedException 会话不存在或不属于当前用户时抛出。
     */
    @Transactional
    public ChatMessageEntity addMessage(Long sessionId, String role, String content, String model) {
        ChatSession session = requireOwnedSession(sessionId);
        int estimatedTokenCount = estimateTokenCount(content);
        ChatMessageEntity message = ChatMessageEntity.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .model(model)
                .tokenCount(estimatedTokenCount)
                .createTime(LocalDateTime.now())
                .build();
        chatMessageMapper.insert(message);

        int currentCount = session.getMessageCount() == null ? 0 : session.getMessageCount();
        session.setMessageCount(currentCount + 1);
        session.setLastMessageTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        if (session.getMessageCount() == 1 && "user".equals(role)) {
            session.setTitle(generateAutoTitle(content));
        }
        chatSessionMapper.updateById(session);

        log.info("添加消息: userId={}, sessionId={}, role={}, contentLength={}",
                session.getUserId(), sessionId, role, content == null ? 0 : content.length());
        return message;
    }

    /**
     * 获取当前用户指定会话的消息。
     *
     * @param sessionId 会话 ID。
     * @return 消息列表。
     */
    public List<ChatMessageEntity> getSessionMessages(Long sessionId) {
        requireOwnedSession(sessionId);
        return chatMessageMapper.findBySessionIdOrderByCreateTime(sessionId);
    }

    /**
     * 清空当前用户指定会话的消息。
     *
     * @param sessionId 会话 ID。
     * @return 是否成功。
     */
    @Transactional
    public boolean clearSessionMessages(Long sessionId) {
        ChatSession session = requireOwnedSession(sessionId);
        chatMessageMapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getSessionId, sessionId));
        session.setMessageCount(0);
        session.setUpdateTime(LocalDateTime.now());
        chatSessionMapper.updateById(session);
        return true;
    }

    private ChatSession requireOwnedSession(Long sessionId) {
        ChatSession session = getSession(sessionId);
        if (session == null) {
            throw new AccessDeniedException("会话不存在或无权访问");
        }
        return session;
    }

    private String generateAutoTitle(String content) {
        if (content == null || content.isEmpty()) {
            return "新会话";
        }
        String title = content.trim();
        if (title.length() > 30) {
            title = title.substring(0, 30) + "...";
        }
        return title;
    }

    private int estimateTokenCount(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        int cjkCount = 0;
        int otherCount = 0;
        for (char c : content.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                cjkCount++;
            } else if (!Character.isWhitespace(c)) {
                otherCount++;
            }
        }
        int englishTokens = (int) Math.ceil(otherCount / 4.0);
        return Math.max(cjkCount + englishTokens, 1);
    }
}
