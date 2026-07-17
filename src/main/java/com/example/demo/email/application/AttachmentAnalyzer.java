package com.example.demo.email.application;

import java.nio.file.Path;

/**
 * 附件 AI 解析抽象。
 * <p>实现按附件类型分流：图片走多模态（image+text），文档走文本抽取后送 LLM。</p>
 */
public interface AttachmentAnalyzer {

    /**
     * 是否支持该 contentType。
     */
    boolean supports(String contentType);

    /**
     * 用 AI 对图片类附件生成摘要。
     *
     * @param imageFile  落盘后的图片文件
     * @param contentType MIME
     * @return 摘要文本；调用失败抛异常由调用方捕获
     */
    String analyzeImage(Path imageFile, String contentType) throws Exception;

    /**
     * 用 AI 对文档类附件（PDF/DOCX/TXT/MD）的文本内容生成摘要。
     *
     * @param extractedText 已抽取出的文本（可能很长，应由调用方截断）
     * @param contentType   MIME
     * @return 摘要文本
     */
    String analyzeText(String extractedText, String contentType) throws Exception;
}
