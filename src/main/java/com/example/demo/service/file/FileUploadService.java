package com.example.demo.service.file;

import com.example.demo.dto.ContentAnalysis;
import com.example.demo.entity.Document;
import com.example.demo.mapper.DocumentMapper;
import com.example.demo.service.chat.ContentAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传服务
 * 处理文件上传、内容提取、AI分析、数据库存储
 */
@Slf4j
@Service
public class FileUploadService {

    @Value("${app.file.upload-dir:./data/documents}")
    private String uploadDir;

    @Value("${app.file.allowed-types:txt,md,pdf,doc,docx}")
    private String allowedTypes;

    private List<String> allowedTypeList;

    private final DocumentMapper documentMapper;
    private final ContentAnalysisService contentAnalysisService;

    public FileUploadService(DocumentMapper documentMapper, ContentAnalysisService contentAnalysisService) {
        this.documentMapper = documentMapper;
        this.contentAnalysisService = contentAnalysisService;
    }

    @PostConstruct
    public void init() {
        // 初始化允许的文件类型列表
        allowedTypeList = Arrays.asList(allowedTypes.toLowerCase().split(","));
        // 创建上传目录
        try {
            Path path = Paths.get(uploadDir);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("创建文件上传目录: {}", uploadDir);
            }
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", uploadDir, e);
        }
    }

    /**
     * 上传文件并进行AI分析
     *
     * @param file 上传的文件
     * @return 保存后的文档记录
     */
    public Document uploadAndAnalyze(MultipartFile file) throws IOException {
        // 1. 验证文件
        validateFile(file);

        // 2. 获取文件信息
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename);
        long fileSize = file.getSize();

        // 3. 生成唯一文件名并保存
        String uniqueFileName = UUID.randomUUID().toString() + "." + fileType;
        Path filePath = Paths.get(uploadDir, uniqueFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        log.info("文件保存成功: {}", filePath);

        // 4. 提取文件内容
        String content = extractContent(filePath, fileType);

        // 5. 创建文档记录(初始状态pending)
        Document document = Document.builder()
                .fileName(originalFilename)
                .filePath(filePath.toString())
                .fileType(fileType)
                .fileSize(fileSize)
                .content(content)
                .status("pending")
                .createTime(LocalDateTime.now())
                .build();

        // 6. 保存到数据库
        documentMapper.insert(document);
        log.info("文档记录已创建, ID: {}", document.getId());

        // 7. AI分析
        try {
            analyzeDocument(document);
        } catch (Exception e) {
            log.error("AI分析失败, 文档ID: {}", document.getId(), e);
            document.setStatus("failed");
            documentMapper.updateById(document);
        }

        return document;
    }

    /**
     * 验证上传的文件
     */
    private void validateFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            throw new IOException("文件名不能为空");
        }

        String fileType = getFileExtension(filename);
        if (!allowedTypeList.contains(fileType.toLowerCase())) {
            throw new IOException("不支持的文件类型: " + fileType + ", 支持的类型: " + allowedTypes);
        }

        // 检查文件大小(10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IOException("文件大小超过限制(最大10MB)");
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * 提取文件内容
     * 目前只支持文本文件(txt, md)
     */
    private String extractContent(Path filePath, String fileType) throws IOException {
        // 只处理文本类型文件
        if ("txt".equals(fileType) || "md".equals(fileType)) {
            return Files.readString(filePath);
        }

        // 其他类型暂不支持，返回提示信息
        log.warn("暂不支持{}类型文件的文本提取", fileType);
        return "[文件内容暂不支持提取: " + fileType + "类型]";
    }

    /**
     * AI分析文档内容
     */
    private void analyzeDocument(Document document) {
        String content = document.getContent();

        // 跳过无法提取内容的文件
        if (content == null || content.startsWith("[文件内容暂不支持提取")) {
            log.info("跳过无法提取内容的文件分析: {}", document.getFileName());
            document.setStatus("analyzed");
            document.setImportance(3); // 默认中等重要程度
            documentMapper.updateById(document);
            return;
        }

        // 内容过长时截取前2000字符进行分析
        String analyzeContent = content.length() > 2000 ? content.substring(0, 2000) : content;

        // 调用AI分析服务
        ContentAnalysis analysis = contentAnalysisService.analyze(analyzeContent);

        // 更新文档记录
        document.setImportance(analysis.getImportance());
        document.setTags(analysis.getTags() != null ? String.join(",", analysis.getTags()) : null);
        document.setSentiment(analysis.getSentiment() != null ? analysis.getSentiment().name() : "NEUTRAL");
        document.setSummary(analysis.getSummary());
        document.setStatus("analyzed");
        document.setUpdateTime(LocalDateTime.now());

        documentMapper.updateById(document);
        log.info("文档分析完成, ID: {}, 重要程度: {}, 标签: {}",
                document.getId(), document.getImportance(), document.getTags());
    }

    /**
     * 获取文档列表
     */
    public List<Document> listDocuments() {
        return documentMapper.selectList(null);
    }

    /**
     * 根据ID获取文档
     */
    public Document getDocument(Long id) {
        return documentMapper.selectById(id);
    }

    /**
     * 删除文档(同时删除文件)
     */
    public void deleteDocument(Long id) throws IOException {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new IOException("文档不存在: " + id);
        }

        // 删除文件
        if (document.getFilePath() != null) {
            Path filePath = Paths.get(document.getFilePath());
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("文件已删除: {}", filePath);
            }
        }

        // 删除数据库记录
        documentMapper.deleteById(id);
        log.info("文档记录已删除, ID: {}", id);
    }
}