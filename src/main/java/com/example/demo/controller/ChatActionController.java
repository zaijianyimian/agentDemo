package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.ChatActionRequest;
import com.example.demo.dto.ChatActionResult;
import com.example.demo.service.chat.ChatActionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat/action")
public class ChatActionController {

    private final ChatActionService chatActionService;

    public ChatActionController(ChatActionService chatActionService) {
        this.chatActionService = chatActionService;
    }

    @PostMapping("/note")
    public ApiResponse<ChatActionResult> createNote(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.createNote(request));
    }

    @PostMapping("/task")
    public ApiResponse<ChatActionResult> createTask(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.createTask(request));
    }

    @PostMapping("/schedule")
    public ApiResponse<ChatActionResult> createSchedule(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.createSchedule(request));
    }

    @PostMapping("/memory")
    public ApiResponse<ChatActionResult> storeMemory(@RequestBody ChatActionRequest request) {
        return ApiResponse.success(chatActionService.storeMemory(request));
    }
}
