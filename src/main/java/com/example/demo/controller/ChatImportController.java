package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ChatImportResult;
import com.example.demo.entity.ChatHistory;
import com.example.demo.entity.VirtualAssistant;
import com.example.demo.service.chatimport.ChatImportService;
import com.example.demo.service.chatimport.ChatHistoryVectorService;
import com.example.demo.service.chatimport.VirtualAssistantService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 外部聊天记录导入控制器
 * 支持导入微信、QQ、Telegram、WhatsApp等聊天记录
 * 并基于导入的聊天记录创建虚拟助手
 */
@RestController
@RequestMapping("/api/chatimport")
public class ChatImportController {

    @Resource
    private ChatImportService chatImportService;

    @Resource
    private ChatHistoryVectorService chatHistoryVectorService;

    @Resource
    private VirtualAssistantService virtualAssistantService;

    /**
     * 导入聊天记录文件
     * @param file 聊天记录文件（txt或json格式）
     * @param platform 平台类型：wechat, qq, telegram, whatsapp, auto
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ChatImportResult> importChatHistory(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "platform", defaultValue = "auto") String platform) throws IOException {

        ChatImportResult result = chatImportService.importChatHistory(file.getInputStream(), platform);
        if (result.isSuccess()) {
            return ApiResponse.success(result);
        }
        return ApiResponse.error(result.getErrorMessage());
    }

    /**
     * 获取所有导入的会话列表
     */
    @GetMapping("/sessions")
    public ApiResponse<List<String>> getImportedSessions(
            @RequestParam(value = "platform", required = false) String platform) {
        List<String> sessions = chatImportService.getAllSessionIds(platform);
        return ApiResponse.success(sessions);
    }

    /**
     * 获取指定会话的消息列表
     */
    @GetMapping("/session/{sessionId}/messages")
    public ApiResponse<List<ChatHistory>> getSessionMessages(@PathVariable String sessionId) {
        List<ChatHistory> messages = chatImportService.getSessionMessages(sessionId);
        return ApiResponse.success(messages);
    }

    /**
     * 删除导入的会话
     */
    @DeleteMapping("/session/{sessionId}")
    public ApiResponse<Void> deleteImportedSession(@PathVariable String sessionId) {
        chatImportService.deleteBySessionId(sessionId);
        return ApiResponse.success(null);
    }

    // ========== 虚拟助手相关接口 ==========

    /**
     * 获取所有虚拟助手
     */
    @GetMapping("/assistants")
    public ApiResponse<List<VirtualAssistant>> getAllAssistants() {
        List<VirtualAssistant> assistants = virtualAssistantService.getAllAssistants();
        return ApiResponse.success(assistants);
    }

    /**
     * 获取助手详情
     */
    @GetMapping("/assistant/{id}")
    public ApiResponse<VirtualAssistant> getAssistant(@PathVariable Long id) {
        VirtualAssistant assistant = virtualAssistantService.getAssistant(id);
        if (assistant == null) {
            return ApiResponse.error("助手不存在");
        }
        return ApiResponse.success(assistant);
    }

    /**
     * 创建虚拟助手
     * @param name 助手名称
     * @param description 助手描述
     * @param platform 来源平台
     * @param sessionIds 要训练的会话ID列表
     */
    @PostMapping("/assistant")
    public ApiResponse<VirtualAssistant> createAssistant(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam String platform,
            @RequestParam List<String> sessionIds) {

        VirtualAssistant assistant = chatHistoryVectorService.createAssistant(name, description, platform, sessionIds);
        return ApiResponse.success(assistant);
    }

    /**
     * 更新助手信息
     */
    @PutMapping("/assistant/{id}")
    public ApiResponse<VirtualAssistant> updateAssistant(
            @PathVariable Long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Boolean enabled) {

        VirtualAssistant assistant = virtualAssistantService.updateAssistant(id, name, description, enabled);
        if (assistant == null) {
            return ApiResponse.error("助手不存在");
        }
        return ApiResponse.success(assistant);
    }

    /**
     * 删除虚拟助手
     */
    @DeleteMapping("/assistant/{id}")
    public ApiResponse<Void> deleteAssistant(@PathVariable Long id) {
        boolean result = virtualAssistantService.deleteAssistant(id);
        if (!result) {
            return ApiResponse.error("删除失败");
        }
        return ApiResponse.success(null);
    }

    /**
     * 与虚拟助手对话
     * @param id 助手ID
     * @param message 用户消息
     * @param history 历史对话上下文（可选）
     */
    @PostMapping("/assistant/{id}/chat")
    public ApiResponse<String> chatWithAssistant(
            @PathVariable Long id,
            @RequestParam String message,
            @RequestBody(required = false) List<Map<String, String>> history) {

        String response = virtualAssistantService.chat(id, message, history);
        return ApiResponse.success(response);
    }

    /**
     * 分析助手人格特征
     */
    @PostMapping("/assistant/{id}/analyze")
    public ApiResponse<Void> analyzeAssistantPersonality(@PathVariable Long id) {
        virtualAssistantService.analyzePersonality(id);
        return ApiResponse.success(null);
    }

    /**
     * 重新向量化助手
     */
    @PostMapping("/assistant/{id}/revectorize")
    public ApiResponse<Integer> revectorizeAssistant(@PathVariable Long id) {
        int count = chatHistoryVectorService.vectorizeAssistantMessages(id);
        return ApiResponse.success(count);
    }
}