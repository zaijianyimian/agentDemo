package com.example.demo.chat.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.chat.dto.ChatActionRequest;
import com.example.demo.chat.dto.ChatActionResult;
import com.example.demo.chat.application.ChatActionService;
import org.springframework.web.bind.annotation.*;

/**
 * 聊天动作控制器
 * 把聊天内容沉淀为笔记、任务、日程或长期记忆的统一入口。
 */
@RestController
@RequestMapping("/api/chat/action")
public class ChatActionController {

    private final ChatActionService chatActionService;

    public ChatActionController(ChatActionService chatActionService) {
        this.chatActionService = chatActionService;
    }

    /**
     * 从聊天内容生成笔记。
     */
    @PostMapping("/note")
    public ApiResponse<ChatActionResult> createNote(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.createNote(request));
    }

    /**
     * 从聊天内容生成定时提醒任务。
     */
    @PostMapping("/task")
    public ApiResponse<ChatActionResult> createTask(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.createTask(request));
    }

    /**
     * 从聊天内容生成日程事件。
     */
    @PostMapping("/schedule")
    public ApiResponse<ChatActionResult> createSchedule(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.createSchedule(request));
    }

    /**
     * 从聊天内容提取并存储长期记忆。
     */
    @PostMapping("/memory")
    public ApiResponse<ChatActionResult> storeMemory(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.storeMemory(request));
    }
}
