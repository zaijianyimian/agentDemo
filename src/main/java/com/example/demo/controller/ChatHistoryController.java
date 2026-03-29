package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.ChatMessageEntity;
import com.example.demo.entity.ChatSession;
import com.example.demo.service.chat.ChatHistoryService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 聊天历史控制器
 * 管理会话历史和消息的 REST API
 */
@RestController
@RequestMapping("/api/chat/history")
public class ChatHistoryController {

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     * 创建新会话
     */
    @PostMapping("/session")
    public ApiResponse<ChatSession> createSession(@RequestParam(required = false) String title) {
        ChatSession session = chatHistoryService.createSession(title);
        return ApiResponse.success(session);
    }

    /**
     * 获取所有会话列表
     */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSession>> getAllSessions() {
        List<ChatSession> sessions = chatHistoryService.getAllSessions();
        return ApiResponse.success(sessions);
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/session/{id}")
    public ApiResponse<ChatSession> getSession(@PathVariable Long id) {
        ChatSession session = chatHistoryService.getSession(id);
        if (session == null) {
            return ApiResponse.error("会话不存在");
        }
        return ApiResponse.success(session);
    }

    /**
     * 更新会话标题
     */
    @PutMapping("/session/{id}/title")
    public ApiResponse<ChatSession> updateSessionTitle(
            @PathVariable Long id,
            @RequestParam String title) {
        ChatSession session = chatHistoryService.updateSessionTitle(id, title);
        if (session == null) {
            return ApiResponse.error("会话不存在");
        }
        return ApiResponse.success(session);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/session/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id) {
        boolean result = chatHistoryService.deleteSession(id);
        if (!result) {
            return ApiResponse.error("删除失败");
        }
        return ApiResponse.success(null);
    }

    /**
     * 获取会话的所有消息
     */
    @GetMapping("/session/{sessionId}/messages")
    public ApiResponse<List<ChatMessageEntity>> getSessionMessages(@PathVariable Long sessionId) {
        List<ChatMessageEntity> messages = chatHistoryService.getSessionMessages(sessionId);
        return ApiResponse.success(messages);
    }

    /**
     * 添加消息到会话
     */
    @PostMapping("/session/{sessionId}/message")
    public ApiResponse<ChatMessageEntity> addMessage(
            @PathVariable Long sessionId,
            @RequestParam String role,
            @RequestParam String content,
            @RequestParam(required = false) String model) {
        ChatMessageEntity message = chatHistoryService.addMessage(sessionId, role, content, model);
        return ApiResponse.success(message);
    }

    /**
     * 清空会话消息
     */
    @DeleteMapping("/session/{sessionId}/messages")
    public ApiResponse<Void> clearSessionMessages(@PathVariable Long sessionId) {
        chatHistoryService.clearSessionMessages(sessionId);
        return ApiResponse.success(null);
    }
}