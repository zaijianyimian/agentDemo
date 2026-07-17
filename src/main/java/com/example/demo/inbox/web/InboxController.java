package com.example.demo.inbox.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.infrastructure.config.CacheConfig;
import com.example.demo.inbox.dto.InboxSummary;
import com.example.demo.inbox.application.UnifiedInboxService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收件箱控制器
 * 聚合多模块数据生成首页收件箱摘要
 */
@RestController
@RequestMapping("/api/inbox")
public class InboxController {

    private final UnifiedInboxService unifiedInboxService;

    /**
     * 构造时注入统一收件箱服务
     */
    public InboxController(UnifiedInboxService unifiedInboxService) {
        this.unifiedInboxService = unifiedInboxService;
    }

    /**
     * 获取收件箱摘要（包含日程、任务、笔记、邮件等聚合信息）。
     * 缓存 30 秒——首页首屏热点，DB 聚合查询开销大。
     */
    @GetMapping("/summary")
    @Cacheable(cacheNames = CacheConfig.INBOX_SUMMARY, key = "#limit", sync = true)
    public ApiResponse<InboxSummary> summary(@RequestParam(defaultValue = "18") int limit) {
        return ApiResponse.success(unifiedInboxService.buildSummary(limit));
    }
}
