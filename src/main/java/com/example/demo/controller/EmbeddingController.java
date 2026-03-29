package com.example.demo.controller;

import com.example.demo.dto.ContentAnalysis;
import com.example.demo.service.chat.ContentAnalysisService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 向量化与分析控制器
 * 处理文本向量化和内容分析接口
 */
@RestController
@RequestMapping("/api")
public class EmbeddingController {

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private ContentAnalysisService contentAnalysisService;

    /**
     * 文本向量化接口 - 返回向量数组
     */
    @GetMapping("/embedding")
    public float[] embedding(@RequestParam("text") String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        return response.content().vector();
    }

    /**
     * 文本向量化接口 - 返回完整响应对象
     */
    @GetMapping("/embedding/full")
    public Response<Embedding> embeddingFull(@RequestParam("text") String text) {
        return embeddingModel.embed(text);
    }

    /**
     * 内容分析接口 - 分析文本的重要程度、标签等
     */
    @GetMapping("/analyze")
    public ContentAnalysis analyzeContent(@RequestParam("content") String content) {
        return contentAnalysisService.analyze(content);
    }
}