package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.KnowledgeBase;
import com.example.demo.entity.KnowledgeDocument;
import com.example.demo.service.knowledge.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * RAG 知识库控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final RagService ragService;

    public KnowledgeController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 获取知识库列表
     */
    @GetMapping("/list")
    public ApiResponse<List<KnowledgeBase>> list() {
        return ApiResponse.success(ragService.listKnowledgeBases());
    }

    /**
     * 获取知识库详情
     */
    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBase> get(@PathVariable Long id) {
        KnowledgeBase kb = ragService.getKnowledgeBase(id);
        if (kb == null) {
            return ApiResponse.error("知识库不存在");
        }
        return ApiResponse.success(kb);
    }

    /**
     * 创建知识库
     */
    @PostMapping
    public ApiResponse<KnowledgeBase> create(@RequestBody KnowledgeBase kb) {
        try {
            return ApiResponse.success(ragService.createKnowledgeBase(kb));
        } catch (Exception e) {
            log.error("创建知识库失败", e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新知识库
     */
    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBase> update(@PathVariable Long id, @RequestBody KnowledgeBase kb) {
        kb.setId(id);
        return ApiResponse.success(ragService.updateKnowledgeBase(kb));
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            ragService.deleteKnowledgeBase(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除知识库失败", e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用知识库
     */
    @PutMapping("/{id}/toggle")
    public ApiResponse<KnowledgeBase> toggle(@PathVariable Long id) {
        KnowledgeBase kb = ragService.getKnowledgeBase(id);
        if (kb == null) {
            return ApiResponse.error("知识库不存在");
        }
        kb.setEnabled(!kb.getEnabled());
        return ApiResponse.success(ragService.updateKnowledgeBase(kb));
    }

    /**
     * 上传文档到知识库
     */
    @PostMapping(value = "/{baseId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeDocument> upload(
            @PathVariable Long baseId,
            @RequestParam("file") MultipartFile file) {
        try {
            return ApiResponse.success(ragService.uploadDocument(baseId, file));
        } catch (Exception e) {
            log.error("上传文档失败", e);
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取知识库下的文档列表
     */
    @GetMapping("/{baseId}/documents")
    public ApiResponse<List<KnowledgeDocument>> listDocuments(@PathVariable Long baseId) {
        return ApiResponse.success(ragService.listDocuments(baseId));
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/document/{docId}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long docId) {
        try {
            ragService.deleteDocument(docId);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除文档失败", e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * RAG 问答 - 返回相关上下文
     */
    @GetMapping("/{baseId}/query")
    public ApiResponse<String> query(
            @PathVariable Long baseId,
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int topK) {
        return ApiResponse.success(ragService.query(baseId, question, topK));
    }

    /**
     * 搜索相关文档片段
     */
    @GetMapping("/{baseId}/search")
    public ApiResponse<List<Map<String, Object>>> search(
            @PathVariable Long baseId,
            @RequestParam String query,
            @RequestParam(defaultValue = "10") int topK) {
        return ApiResponse.success(ragService.searchChunks(baseId, query, topK));
    }
}