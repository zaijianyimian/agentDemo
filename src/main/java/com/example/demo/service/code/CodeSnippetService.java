package com.example.demo.service.code;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.CodeSnippet;
import com.example.demo.mapper.CodeSnippetMapper;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 代码片段服务
 */
@Slf4j
@Service
public class CodeSnippetService {

    @Resource
    private CodeSnippetMapper codeSnippetMapper;

    @Resource
    private ChatModel chatModel;

    /**
     * 获取所有代码片段
     */
    public List<CodeSnippet> getAllSnippets() {
        return codeSnippetMapper.findAllOrderByTime();
    }

    /**
     * 获取代码片段详情
     */
    public CodeSnippet getSnippet(Long id) {
        return codeSnippetMapper.selectById(id);
    }

    /**
     * 创建代码片段
     */
    @Transactional
    public CodeSnippet createSnippet(CodeSnippet snippet) {
        snippet.setCreateTime(LocalDateTime.now());
        snippet.setUpdateTime(LocalDateTime.now());
        codeSnippetMapper.insert(snippet);
        log.info("创建代码片段: id={}, title={}", snippet.getId(), snippet.getTitle());
        return snippet;
    }

    /**
     * 更新代码片段
     */
    @Transactional
    public CodeSnippet updateSnippet(CodeSnippet snippet) {
        snippet.setUpdateTime(LocalDateTime.now());
        codeSnippetMapper.updateById(snippet);
        log.info("更新代码片段: id={}", snippet.getId());
        return snippet;
    }

    /**
     * 删除代码片段
     */
    @Transactional
    public boolean deleteSnippet(Long id) {
        int result = codeSnippetMapper.deleteById(id);
        log.info("删除代码片段: id={}, result={}", id, result > 0);
        return result > 0;
    }

    /**
     * 按语言查询
     */
    public List<CodeSnippet> getByLanguage(String language) {
        return codeSnippetMapper.findByLanguage(language);
    }

    /**
     * 搜索代码片段
     */
    public List<CodeSnippet> searchSnippets(String keyword) {
        LambdaQueryWrapper<CodeSnippet> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(CodeSnippet::getTitle, keyword)
                    .or().like(CodeSnippet::getCode, keyword)
                    .or().like(CodeSnippet::getTags, keyword);
        }
        wrapper.orderByDesc(CodeSnippet::getUpdateTime);
        return codeSnippetMapper.selectList(wrapper);
    }

    /**
     * AI 解释代码
     */
    public String explainCode(Long id) {
        CodeSnippet snippet = codeSnippetMapper.selectById(id);
        if (snippet == null || snippet.getCode() == null) {
            return null;
        }

        String prompt = String.format(
                "请解释以下 %s 代码的功能和用法，用简洁的中文说明：\n\n```%s\n%s\n```",
                snippet.getLanguage() != null ? snippet.getLanguage() : "",
                snippet.getLanguage() != null ? snippet.getLanguage() : "",
                snippet.getCode()
        );

        return chatModel.chat(prompt);
    }
}