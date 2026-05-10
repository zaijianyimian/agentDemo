package com.example.demo.service.file;

import com.example.demo.dto.ContentAnalysis;
import com.example.demo.entity.Document;
import com.example.demo.mapper.DocumentMapper;
import com.example.demo.service.SystemSettingsService;
import com.example.demo.service.chat.ContentAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传服务
 * 处理文件上传、内容提取、AI分析、数据库存储
 * 包含安全验证措施防止恶意文件上传
 */
@Slf4j
@Service
public class FileUploadService {

    private static final long DEFAULT_MAX_FILE_SIZE = 10L * 1024 * 1024;

    /**
     * 文件魔术字节（Magic Bytes）映射
     * 用于验证文件真实类型，防止扩展名伪装攻击
     */
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            // PDF 文件: %PDF-
            "pdf", new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D},
            // ZIP/DOCX/XLSX/PPTX 文件
            "zip", new byte[]{0x50, 0x4B, 0x03, 0x04},
            "docx", new byte[]{0x50, 0x4B, 0x03, 0x04},
            "xlsx", new byte[]{0x50, 0x4B, 0x03, 0x04},
            "pptx", new byte[]{0x50, 0x4B, 0x03, 0x04}
    );

    /**
     * 允许通过魔术字节验证的扩展名类型
     * TXT 和 MD 是纯文本文件，没有特定的魔术字节
     */
    private static final Set<String> TEXT_TYPES = Set.of("txt", "md", "text", "markdown");

    /**
     * Office 旧版文件类型（DOC, XLS, PPT）
     * OLE 复合文档格式：D0 CF 11 E0 A1 B1 1A E1
     */
    private static final Map<String, byte[]> OLD_OFFICE_MAGIC = Map.of(
            "doc", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1},
            "xls", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1},
            "ppt", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1}
    );

    @Value("${app.file.upload-dir:./data/documents}")
    private String uploadDir;

    @Value("${app.file.allowed-types:txt,md,pdf,doc,docx}")
    private String allowedTypes;

    private final DocumentMapper documentMapper;
    private final ContentAnalysisService contentAnalysisService;
    private final SystemSettingsService settingsService;
    private final FileContentExtractor contentExtractor;

    public FileUploadService(DocumentMapper documentMapper,
                             ContentAnalysisService contentAnalysisService,
                             SystemSettingsService settingsService,
                             FileContentExtractor contentExtractor) {
        this.documentMapper = documentMapper;
        this.contentAnalysisService = contentAnalysisService;
        this.settingsService = settingsService;
        this.contentExtractor = contentExtractor;
    }

    @PostConstruct
    public void init() {
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
        String resolvedUploadDir = resolveUploadDir();

        // 3. 生成唯一文件名并保存
        String uniqueFileName = UUID.randomUUID().toString() + "." + fileType;
        Path filePath = Paths.get(resolvedUploadDir, uniqueFileName);
        Files.createDirectories(filePath.getParent());
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
     * 包含扩展名验证、魔术字节验证、大小限制
     */
    private void validateFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty()) {
            throw new IOException("文件名不能为空");
        }

        // 安全检查：文件名不能包含路径遍历字符
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IOException("文件名包含非法字符");
        }

        String fileType = getFileExtension(filename);
        List<String> allowedTypeList = resolveAllowedTypeList();
        if (!allowedTypeList.contains(fileType.toLowerCase())) {
            throw new IOException("不支持的文件类型: " + fileType + ", 支持的类型: " + String.join(",", allowedTypeList));
        }

        // 检查是否支持内容提取
        Set<String> supportedTypes = contentExtractor.getSupportedTypes();
        if (!supportedTypes.contains(fileType.toLowerCase())) {
            throw new IOException("当前不支持 " + fileType + " 类型文件的内容提取，支持的类型: " + String.join(",", supportedTypes));
        }

        long maxFileSize = resolveMaxFileSizeBytes();
        if (file.getSize() > maxFileSize) {
            throw new IOException("文件大小超过限制(最大" + formatSize(maxFileSize) + ")");
        }

        // 安全检查：魔术字节验证（防止扩展名伪装攻击）
        validateMagicBytes(file, fileType);
    }

    /**
     * 验证文件魔术字节
     * 检查文件内容是否与声明的扩展名匹配，防止恶意文件伪装
     */
    private void validateMagicBytes(MultipartFile file, String declaredType) throws IOException {
        // 纯文本文件没有固定的魔术字节，跳过验证
        if (TEXT_TYPES.contains(declaredType.toLowerCase())) {
            return;
        }

        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int bytesRead = is.read(header);

            if (bytesRead < 4) {
                // 文件太小，无法验证魔术字节
                return;
            }

            // 检查 PDF 文件
            if ("pdf".equalsIgnoreCase(declaredType)) {
                byte[] pdfMagic = MAGIC_BYTES.get("pdf");
                if (!matchesMagicBytes(header, pdfMagic)) {
                    throw new IOException("文件内容与声明的PDF类型不匹配，可能是伪装文件");
                }
                return;
            }

            // 检查 Office 新版文件 (DOCX, XLSX, PPTX)
            if ("docx".equalsIgnoreCase(declaredType) ||
                "xlsx".equalsIgnoreCase(declaredType) ||
                "pptx".equalsIgnoreCase(declaredType)) {
                byte[] zipMagic = MAGIC_BYTES.get("zip");
                if (!matchesMagicBytes(header, zipMagic)) {
                    throw new IOException("文件内容与声明的" + declaredType + "类型不匹配，可能是伪装文件");
                }
                return;
            }

            // 检查 Office 旧版文件 (DOC, XLS, PPT)
            if ("doc".equalsIgnoreCase(declaredType) ||
                "xls".equalsIgnoreCase(declaredType) ||
                "ppt".equalsIgnoreCase(declaredType)) {
                byte[] oleMagic = OLD_OFFICE_MAGIC.get("doc");
                if (!matchesMagicBytes(header, oleMagic)) {
                    throw new IOException("文件内容与声明的" + declaredType + "类型不匹配，可能是伪装文件");
                }
                return;
            }
        }
    }

    /**
     * 检查文件头是否匹配魔术字节
     */
    private boolean matchesMagicBytes(byte[] header, byte[] magic) {
        if (magic == null || header == null) {
            return false;
        }
        for (int i = 0; i < magic.length && i < header.length; i++) {
            if (header[i] != magic[i]) {
                return false;
            }
        }
        return true;
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
     * 使用 FileContentExtractor 支持多种文件类型
     */
    private String extractContent(Path filePath, String fileType) throws IOException {
        return contentExtractor.extractContent(filePath, fileType);
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

    private String resolveUploadDir() {
        return settingsService.getSetting("file", "upload_dir", uploadDir);
    }

    private List<String> resolveAllowedTypeList() {
        String configured = settingsService.getSetting("file", "allowed_types", allowedTypes);
        return List.of(configured.toLowerCase().replace(" ", "").split(","));
    }

    private long resolveMaxFileSizeBytes() {
        String configured = settingsService.getSetting("file", "max_file_size", "10MB");
        return parseSizeToBytes(configured, DEFAULT_MAX_FILE_SIZE);
    }

    private long parseSizeToBytes(String raw, long defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String normalized = raw.trim().toUpperCase();
        long multiplier = 1L;
        if (normalized.endsWith("KB")) {
            multiplier = 1024L;
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        } else if (normalized.endsWith("MB")) {
            multiplier = 1024L * 1024L;
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        } else if (normalized.endsWith("GB")) {
            multiplier = 1024L * 1024L * 1024L;
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        } else if (normalized.endsWith("B")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        try {
            return Math.max(1L, Long.parseLong(normalized) * multiplier);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return (bytes / (1024L * 1024L * 1024L)) + "GB";
        }
        if (bytes >= 1024L * 1024L) {
            return (bytes / (1024L * 1024L)) + "MB";
        }
        if (bytes >= 1024L) {
            return (bytes / 1024L) + "KB";
        }
        return bytes + "B";
    }
}
