package com.example.demo.chat.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.chat.domain.ChatHistory;
import com.example.demo.chat.persistence.ChatHistoryMapper;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.web.UserResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.List;

/**
 * 聊天历史数据访问服务
 * 封装 ChatHistory 的 CRUD 与批量向量状态更新等常用操作。
 */
@Service
@RequiredArgsConstructor
public class ChatHistoryAccessService {

    private final ChatHistoryMapper chatHistoryMapper;
    private final CurrentUserContext currentUser;

    /**
     * 插入一条聊天消息。
     */
    public void save(ChatHistory message) {
        message.setUserId(currentUser.requireUserId());
        chatHistoryMapper.insert(message);
    }

    /**
     * 按会话 ID 获取该会话的全部消息。
     */
    public List<ChatHistory> findBySessionId(String sessionId) {
        currentUser.requireUserId();
        return chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                .eq(ChatHistory::getSessionId, sessionId)
                .orderByAsc(ChatHistory::getMessageTime));
    }

    /**
     * 获取会话 ID 列表，可按平台过滤。
     */
    public List<String> findSessionIds(String platform) {
        if (platform != null && !platform.isBlank()) {
            return chatHistoryMapper.selectList(
                    new LambdaQueryWrapper<ChatHistory>()
                            .select(ChatHistory::getSessionId)
                            .eq(ChatHistory::getPlatform, platform)
                            .groupBy(ChatHistory::getSessionId)
                            .orderByAsc(ChatHistory::getSessionId)
            ).stream().map(ChatHistory::getSessionId).toList();
        }
        return chatHistoryMapper.selectList(
                new LambdaQueryWrapper<ChatHistory>()
                        .select(ChatHistory::getSessionId)
                        .groupBy(ChatHistory::getSessionId)
        ).stream().map(ChatHistory::getSessionId).toList();
    }

    /**
     * 获取指定助手未向量化的消息批次。
     */
    public List<ChatHistory> findUnvectorizedByAssistantId(Long assistantId, int limit) {
        currentUser.requireUserId();
        return chatHistoryMapper.selectList(new LambdaQueryWrapper<ChatHistory>()
                .eq(ChatHistory::getVectorized, false)
                .eq(ChatHistory::getAssistantId, assistantId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 1000))));
    }

    /**
     * 更新一条聊天消息。
     */
    public void update(ChatHistory message) {
        ChatHistory existing = requireOwnedMessage(message == null ? null : message.getId());
        message.setUserId(existing.getUserId());
        chatHistoryMapper.updateById(message);
    }

    /**
     * 批量标记消息已向量化完成。
     */
    public void markVectorized(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            currentUser.requireUserId();
            List<ChatHistory> owned = chatHistoryMapper.selectBatchIds(ids);
            if (owned.size() != ids.stream().distinct().count()) {
                throw new UserResourceNotFoundException("聊天消息不存在");
            }
            chatHistoryMapper.update(null, new LambdaUpdateWrapper<ChatHistory>()
                    .in(ChatHistory::getId, ids)
                    .set(ChatHistory::getVectorized, true)
                    .set(ChatHistory::getUpdateTime, java.time.LocalDateTime.now()));
        }
    }

    /**
     * 统计指定助手的聊天消息数量。
     */
    public int countByAssistantId(Long assistantId) {
        currentUser.requireUserId();
        return Math.toIntExact(chatHistoryMapper.selectCount(new LambdaQueryWrapper<ChatHistory>()
                .eq(ChatHistory::getAssistantId, assistantId)));
    }

    /**
     * 按助手 ID 删除全部聊天消息。
     */
    public void deleteByAssistantId(Long assistantId) {
        currentUser.requireUserId();
        chatHistoryMapper.delete(new LambdaQueryWrapper<ChatHistory>()
                .eq(ChatHistory::getAssistantId, assistantId));
    }

    /**
     * 按会话 ID 删除全部聊天消息。
     */
    public int deleteBySessionId(String sessionId) {
        currentUser.requireUserId();
        return chatHistoryMapper.delete(
                new LambdaQueryWrapper<ChatHistory>()
                        .eq(ChatHistory::getSessionId, sessionId)
        );
    }

    private ChatHistory requireOwnedMessage(Long id) {
        currentUser.requireUserId();
        if (id == null) {
            throw new UserResourceNotFoundException("聊天消息不存在");
        }
        ChatHistory existing = chatHistoryMapper.selectById(id);
        if (existing == null) {
            throw new UserResourceNotFoundException("聊天消息不存在");
        }
        return existing;
    }
}
