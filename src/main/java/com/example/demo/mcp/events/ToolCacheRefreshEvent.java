package com.example.demo.mcp.events;

import org.springframework.context.ApplicationEvent;

/**
 * 工具缓存刷新事件
 */
public class ToolCacheRefreshEvent extends ApplicationEvent {

    public ToolCacheRefreshEvent(Object source) {
        super(source);
    }
}