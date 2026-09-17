package com.example.demo.chat.web;

import com.example.demo.chat.application.ChatHistoryService;
import com.example.demo.chat.domain.ChatMessageEntity;
import com.example.demo.chat.domain.ChatSession;
import com.example.demo.shared.dto.ApiResponse;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 聊天历史控制器。
 *
 * <p>管理当前用户的 UUID 会话及消息 REST API。</p>
 */
@RestController
@RequestMapping("/api/chat/history")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * 创建新会话。
     *
     * @param title 可选会话标题。
     * @return 新建会话。
     */
    @PostMapping("/session")
    public ApiResponse<ChatSession> createSession(@RequestParam(required = false) String title) {
        ChatSession session = chatHistoryService.createSession(title);
        return ApiResponse.success(session);
    }

    /** 获取所有会话列表。 */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> getAllSessions() {
        List<ChatSession> sessions = chatHistoryService.getAllSessions();
        return ApiResponse.success(sessions);
    }

    /** 获取会话详情。 */
    @GetMapping("/session/{id}")
    public ApiResponse<ChatSession> getSession(@PathVariable String id) {
        return ApiResponse.success(chatHistoryService.getSession(id));
    }

    /** 更新会话标题。 */
    @PutMapping("/session/{id}/title")
    public ApiResponse<ChatSession> updateSessionTitle(
            @PathVariable String id,
            @RequestParam String title) {
        return ApiResponse.success(chatHistoryService.updateSessionTitle(id, title));
    }

    /** 删除会话。 */
    @DeleteMapping("/session/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable String id) {
        boolean result = chatHistoryService.deleteSession(id);
        if (!result) {
            return ApiResponse.error("删除失败");
        }
        return ApiResponse.success(null);
    }

    /** 获取会话的所有消息。 */
    @GetMapping("/session/{sessionId}/messages")
    public ApiResponse<List<ChatMessageEntity>> getSessionMessages(
            @PathVariable String sessionId) {
        return ApiResponse.success(chatHistoryService.getSessionMessages(sessionId));
    }

    /** 添加消息到会话。 */
    @PostMapping("/session/{sessionId}/message")
    public ApiResponse<ChatMessageEntity> addMessage(
            @PathVariable String sessionId,
            @RequestParam String role,
            @RequestParam String content,
            @RequestParam(required = false) String model) {
        return ApiResponse.success(chatHistoryService.addMessage(sessionId, role, content, model));
    }

    /** 清空会话消息。 */
    @DeleteMapping("/session/{sessionId}/messages")
    public ApiResponse<Void> clearSessionMessages(@PathVariable String sessionId) {
        chatHistoryService.clearSessionMessages(sessionId);
        return ApiResponse.success(null);
    }
}
