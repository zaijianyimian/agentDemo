package com.example.demo.chat.web;

import com.example.demo.chat.application.ChatActionService;
import com.example.demo.chat.dto.ChatActionRequest;
import com.example.demo.chat.dto.ChatActionResult;
import com.example.demo.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天业务动作控制器。
 *
 * <p>仅保留 Java 业务能力入口。Agent Memory 已迁移到 Python，不再提供 Java 侧记忆写入接口。</p>
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
     *
     * @param request 聊天动作请求。
     * @return 创建结果。
     */
    @PostMapping("/note")
    public ApiResponse<ChatActionResult> createNote(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.createNote(request));
    }

    /**
     * 从聊天内容生成定时提醒任务。
     *
     * @param request 聊天动作请求。
     * @return 创建结果。
     */
    @PostMapping("/task")
    public ApiResponse<ChatActionResult> createTask(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.createTask(request));
    }

    /**
     * 从聊天内容生成日程事件。
     *
     * @param request 聊天动作请求。
     * @return 创建结果。
     */
    @PostMapping("/schedule")
    public ApiResponse<ChatActionResult> createSchedule(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.createSchedule(request));
    }
}
