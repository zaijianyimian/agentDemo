package com.example.demo.service.chat;

import com.example.demo.dto.ContentAnalysis;
import dev.langchain4j.service.spring.AiService;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

/**
 * 内容分析服务 - 用于提取结构化信息
 * 使用 structuredChatModel 强制返回 JSON 格式
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