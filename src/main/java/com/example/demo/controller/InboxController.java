package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.InboxSummary;
import com.example.demo.service.inbox.UnifiedInboxService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    private final UnifiedInboxService unifiedInboxService;

    public InboxController(UnifiedInboxService unifiedInboxService) {
        this.unifiedInboxService = unifiedInboxService;
    }

    @GetMapping("/summary")
    public ApiResponse<InboxSummary> summary(@RequestParam(defaultValue = "18") int limit) {
        return ApiResponse.success(unifiedInboxService.buildSummary(limit));
    }
}
