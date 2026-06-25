package com.example.demo.service.file;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * 文件内容提取服务
 * 支持从多种文件类型提取文本内容
 */
@Slf4j
@Service
public class FileContentExtractor {

    /**
     * 支持的文件类型
     */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "txt", "md", "pdf", "docx", "doc"
    );

    /**
     * 检查是否支持该文件类型
     */
    public boolean isSupported(String fileType) {
        return SUPPORTED_TYPES.contains(fileType.toLowerCase());
    }

    /**
     * 获取支持的文件类型列表
     */
    public Set<String> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    /**
     * 从文件提取文本内容
     *
     * @param filePath 文件路径
     * @param fileType 文件类型（扩展名）
     * @return 提取的文本内容
     */
    public String extractContent(Path filePath, String fileType) throws IOException {
        fileType = fileType.toLowerCase();

        switch (fileType) {
            case "txt":
            case "md":
                return extractTextFile(filePath);

            case "pdf":
                return extractPdfContent(filePath);

            case "docx":
                return extractDocxContent(filePath);

            case "doc":
                // 旧版 doc 格式需要特殊处理，暂时提示用户转换
                throw new IOException("暂不支持 .doc 格式，请转换为 .docx 格式后上传");

            default:
                throw new IOException("不支持的文件类型: " + fileType);
        }
    }

    /**
     * 提取纯文本文件内容
     */
    private String extractTextFile(Path filePath) throws IOException {
        return Files.readString(filePath);
    }

    /**
     * 提取 PDF 文件内容
     * 使用 Apache PDFBox 库
     */
    private String extractPdfContent(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             PDDocument document = PDDocument.load(is)) {

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());

            String text = stripper.getText(document);
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            log.error("PDF 内容提取失败: {}", filePath, e);
            throw new IOException("PDF 内容提取失败: " + e.getMessage());
        }
    }

    /**
     * 提取 DOCX 文件内容
     * 使用 Apache POI 库
     */
    private String extractDocxContent(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            String text = extractor.getText();
            return text != null ? text.trim() : "";
        } catch (Exception e) {
            log.error("DOCX 内容提取失败: {}", filePath, e);
            throw new IOException("DOCX 内容提取失败: " + e.getMessage());
        }
    }
}