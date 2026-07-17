package com.example.demo.inbox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 收件箱条目数据传输对象。
 * <p>
 * 描述首页收件箱中单一聚合项（如日程、任务、笔记、邮件、自治发现等）的展示属性，
 * 包括分类、标题、状态、跳转路由及扩展元数据，供前端统一渲染。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxItem {

    private String category;

    private String title;

    private String summary;

    private String status;

    private String route;

    private String accent;

    private LocalDateTime time;

    private Map<String, Object> meta;
}
