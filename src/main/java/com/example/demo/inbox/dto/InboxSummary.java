package com.example.demo.inbox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 收件箱摘要数据传输对象。
 * <p>
 * 聚合生成时间、各模块统计计数（如今日日程、启用任务、置顶笔记等）以及收件箱条目列表，
 * 同时携带读取过程中的警告信息，为前端首页提供统一的摘要视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxSummary {

    private LocalDateTime generatedAt;

    private Map<String, Object> counts;

    private List<InboxItem> items;

    private List<String> warnings;
}
