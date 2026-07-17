package com.example.demo.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 内容分析结果 - 用于AI结构化输出。
 *
 * <p>供多个模块复用（chat、file、search 等），因此放在 {@code shared} 命名空间。
 * 结构与 LangChain4j {@code @AiService} 的结构化输出对应。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentAnalysis {

    /**
     * 重要程度 (1-5)
     * 1: 不重要 - 闲聊、寒暄
     * 2: 较低 - 一般信息
     * 3: 中等 - 有一定价值的信息
     * 4: 较高 - 重要信息
     * 5: 非常重要 - 关键信息、紧急事项
     */
    private Integer importance;

    /**
     * 内容标签 (如: 技术、编程、AI、问答、建议等)
     */
    private List<String> tags;

    /**
     * 情感倾向
     */
    private Sentiment sentiment;

    /**
     * 一句话摘要
     */
    private String summary;

    /**
     * 情感枚举
     */
    public enum Sentiment {
        POSITIVE,   // 积极正面
        NEGATIVE,   // 消极负面
        NEUTRAL     // 中性
    }
}