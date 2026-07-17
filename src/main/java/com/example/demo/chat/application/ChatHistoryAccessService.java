package com.example.demo.chat.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.chat.domain.ChatHistory;
import com.example.demo.chat.persistence.ChatHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 聊天历史数据访问服务
 * 封装 ChatHistory 的 CRUD 与批量向量状态更新等常用操作。
 */
@Service
@RequiredArgsConstructor
public class ChatHistoryAccessService {

    private final ChatHistoryMapper chatHistoryMapper;

    /**
     * 插入一条聊天消息。
     */
    public void save(ChatHistory message) {
        chatHistoryMapper.insert(message);
    }

    /**
     * 按会话 ID 获取该会话的全部消息。
     */
    public List<ChatHistory> findBySessionId(String sessionId) {
        return chatHistoryMapper.findBySessionId(sessionId);
    }

    /**
     * 获取会话 ID 列表，可按平台过滤。
     */
    public List<String> findSessionIds(String platform) {
        if (platform != null && !platform.isBlank()) {
            return chatHistoryMapper.findSessionIdsByPlatform(platform);
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
        return chatHistoryMapper.findUnvectorizedByAssistantId(assistantId, limit);
    }

    /**
     * 更新一条聊天消息。
     */
    public void update(ChatHistory message) {
        chatHistoryMapper.updateById(message);
    }

    /**
     * 批量标记消息已向量化完成。
     */
    public void markVectorized(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            chatHistoryMapper.batchUpdateVectorized(ids);
        }
    }

    /**
     * 统计指定助手的聊天消息数量。
     */
    public int countByAssistantId(Long assistantId) {
        return chatHistoryMapper.countByAssistantId(assistantId);
    }

    /**
     * 按助手 ID 删除全部聊天消息。
     */
    public void deleteByAssistantId(Long assistantId) {
        chatHistoryMapper.deleteByAssistantId(assistantId);
    }

    /**
     * 按会话 ID 删除全部聊天消息。
     */
    public int deleteBySessionId(String sessionId) {
        return chatHistoryMapper.delete(
                new LambdaQueryWrapper<ChatHistory>()
                        .eq(ChatHistory::getSessionId, sessionId)
        );
    }
}
