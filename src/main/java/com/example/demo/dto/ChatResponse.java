package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 聊天响应结构化数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 响应内容
     */
    private String content;

    /**
     * 重要程度 (1-5, 5最重要)
     */
    private Integer importance;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 情感倾向 (positive/negative/neutral)
     */
    private String sentiment;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 是否流式响应的结束标记
     */
    private Boolean isComplete;

    /**
     * 创建流式内容块（不含元数据）
     */
    public static ChatResponse contentChunk(String content) {
        return ChatResponse.builder()
                .content(content)
                .isComplete(false)
                .build();
    }

    /**
     * 创建完整响应（含元数据）
     */
    public static ChatResponse complete(String content, Integer importance, List<String> tags, String sentiment, String summary) {
        return ChatResponse.builder()
                .content(content)
                .importance(importance)
                .tags(tags)
                .sentiment(sentiment)
                .summary(summary)
                .isComplete(true)
                .build();
    }
}