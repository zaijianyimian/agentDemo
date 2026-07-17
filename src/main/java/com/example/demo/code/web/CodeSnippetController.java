package com.example.demo.code.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.infrastructure.config.CacheConfig;
import com.example.demo.code.domain.CodeSnippet;
import com.example.demo.code.application.CodeSnippetService;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 代码片段控制器
 */
@RestController
@RequestMapping("/api/snippet")
public class CodeSnippetController {

    @Resource
    private CodeSnippetService codeSnippetService;

    /**
     * 获取所有代码片段。30 秒 Caffeine 缓存，sync=true 让并发同 key 请求只查一次 DB。
     */
    @GetMapping("/list")
    @Cacheable(cacheNames = CacheConfig.SNIPPET_LIST, sync = true)
    public ApiResponse<List<CodeSnippet>> getAllSnippets() {
        List<CodeSnippet> snippets = codeSnippetService.getAllSnippets();
        return ApiResponse.success(snippets);
    }

    /**
     * 获取代码片段详情
     */
    @GetMapping("/{id}")
    public ApiResponse<CodeSnippet> getSnippet(@PathVariable Long id) {
        CodeSnippet snippet = codeSnippetService.getSnippet(id);
        if (snippet == null) {
            return ApiResponse.error("代码片段不存在");
        }
        return ApiResponse.success(snippet);
    }

    /**
     * 创建代码片段
     */
    @PostMapping
    @CacheEvict(cacheNames = CacheConfig.SNIPPET_LIST, allEntries = true)
    public ApiResponse<CodeSnippet> createSnippet(@RequestBody CodeSnippet snippet) {
        CodeSnippet created = codeSnippetService.createSnippet(snippet);
        return ApiResponse.success(created);
    }

    /**
     * 更新代码片段
     */
    @PutMapping("/{id}")
    @CacheEvict(cacheNames = CacheConfig.SNIPPET_LIST, allEntries = true)
    public ApiResponse<CodeSnippet> updateSnippet(@PathVariable Long id, @RequestBody CodeSnippet snippet) {
        snippet.setId(id);
        CodeSnippet updated = codeSnippetService.updateSnippet(snippet);
        return ApiResponse.success(updated);
    }

    /**
     * 删除代码片段
     */
    @DeleteMapping("/{id}")
    @CacheEvict(cacheNames = CacheConfig.SNIPPET_LIST, allEntries = true)
    public ApiResponse<Void> deleteSnippet(@PathVariable Long id) {
        boolean result = codeSnippetService.deleteSnippet(id);
        if (!result) {
            return ApiResponse.error("删除失败");
        }
        return ApiResponse.success(null);
    }

    /**
     * 按语言查询
     */
    @GetMapping("/language/{language}")
    public ApiResponse<List<CodeSnippet>> getByLanguage(@PathVariable String language) {
        List<CodeSnippet> snippets = codeSnippetService.getByLanguage(language);
        return ApiResponse.success(snippets);
    }

    /**
     * 搜索代码片段
     */
    @GetMapping("/search")
    public ApiResponse<List<CodeSnippet>> searchSnippets(@RequestParam(required = false) String keyword) {
        List<CodeSnippet> snippets = codeSnippetService.searchSnippets(keyword);
        return ApiResponse.success(snippets);
    }

    /**
     * AI 解释代码
     */
    @PostMapping("/{id}/explain")
    public ApiResponse<String> explainCode(@PathVariable Long id) {
        String explanation = codeSnippetService.explainCode(id);
        if (explanation == null) {
            return ApiResponse.error("代码片段不存在");
        }
        return ApiResponse.success(explanation);
    }
}