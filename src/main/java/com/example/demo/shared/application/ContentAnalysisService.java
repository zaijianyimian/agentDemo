package com.example.demo.shared.application;

import com.example.demo.shared.dto.ContentAnalysis;
import dev.langchain4j.service.spring.AiService;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

/**
 * 内容分析服务 - 用于提取结构化信息。
 *
 * <p>使用 {@code structuredChatModel} 强制返回 JSON 格式。
 * 作为跨模块复用的能力，下沉到 {@code shared} 命名空间以避免模块循环依赖。</p>
 */
@AiService(
        wiringMode = EXPLICIT,
        chatModel = "structuredChatModel")
public interface ContentAnalysisService {

    /**
     * 分析内容，提取重要程度、标签、情感等信息
     *
     * @param content 需要分析的内容
     * @return 结构化的分析结果
     */
    ContentAnalysis analyze(String content);
}