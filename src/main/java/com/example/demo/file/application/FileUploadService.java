package com.example.demo.file.application;

import com.example.demo.file.domain.Document;
import com.example.demo.file.persistence.DocumentMapper;
import com.example.demo.infrastructure.storage.OwnedStorageResolver;
import com.example.demo.shared.application.FileContentExtractor;
import com.example.demo.shared.context.CurrentUserContext;
import com.example.demo.shared.web.UserResourceNotFoundException;
import com.example.demo.system.application.SystemSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传服务。
 *
 * <p>Java 负责文件安全校验、落盘、内容提取和元数据持久化。AI 分析、Embedding 与语义处理均由
 * Python Agent Engine 负责。</p>
 */
@Slf4j
@Service
public class FileUploadService {

    private static final long DEFAULT_MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> TEXT_TYPES = Set.of("txt", "md", "text", "markdown");
    private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
            "pdf", new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D},
            "zip", new byte[]{0x50, 0x4B, 0x03, 0x04});
    private static final byte[] OLD_OFFICE_MAGIC = new byte[]{
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    @Value("${app.file.allowed-types:txt,md,pdf,doc,docx}")
    private String allowedTypes;

    private final DocumentMapper documentMapper;
    private final SystemSettingsService settingsService;
    private final FileContentExtractor contentExtractor;
    private final OwnedStorageResolver storage;
    private final CurrentUserContext currentUser;

    public FileUploadService(
            DocumentMapper documentMapper,
            SystemSettingsService settingsService,
            FileContentExtractor contentExtractor,
            OwnedStorageResolver storage,
            CurrentUserContext currentUser) {
        this.documentMapper = documentMapper;
        this.settingsService = settingsService;
        this.contentExtractor = contentExtractor;
        this.storage = storage;
        this.currentUser = currentUser;
    }

    /**
     * 上传并提取文件内容。
     *
     * <p>保留旧方法名以兼容现有 Controller；该方法不再执行任何 AI 分析。</p>
     *
     * @param file 上传文件。
     * @return 已保存的文档记录。
     * @throws IOException 文件校验、保存或内容提取失败。
     */
    public Document uploadAndAnalyze(MultipartFile file) throws IOException {
        validateFile(file);
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename);
        long userId = currentUser.requireUserId();
        String storageKey = UUID.randomUUID() + "." + fileType;
        Path filePath = storage.resolveForCreate(
                userId, OwnedStorageResolver.Category.DOCUMENTS, storageKey);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        Document document = Document.builder()
                .userId(userId)
                .fileName(originalFilename)
                .storageKey(storageKey)
                .fileType(fileType)
                .fileSize(file.getSize())
                .content(contentExtractor.extractContent(filePath, fileType))
                .status("stored")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        documentMapper.insert(document);
        return document;
    }

    /** 查询全部文档。 */
    public List<Document> listDocuments() {
        currentUser.requireUserId();
        return documentMapper.selectList(null);
    }

    /** 根据 ID 查询文档。 */
    public Document getDocument(Long id) {
        return requireOwnedDocument(id);
    }

    public StoredDocumentContent readDocumentFile(Long id) throws IOException {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new UserResourceNotFoundException("文档不存在");
        }
        if (document.getStorageKey() == null || document.getStorageKey().isBlank()) {
            throw new IOException("文档尚未迁移到用户存储: " + id);
        }
        Path target = storage.resolveExisting(
                document.getUserId(), OwnedStorageResolver.Category.DOCUMENTS, document.getStorageKey());
        String contentType = Files.probeContentType(target);
        return new StoredDocumentContent(document.getFileName(), Files.readAllBytes(target),
                contentType == null ? "application/octet-stream" : contentType);
    }

    public StoredImage storeImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getContentType() == null
                || !file.getContentType().startsWith("image/")) {
            throw new IOException("只支持非空图片文件");
        }
        String originalName = file.getOriginalFilename();
        String extension = getFileExtension(originalName);
        if (extension.isBlank() || !Set.of("png", "jpg", "jpeg", "gif", "webp", "svg").contains(extension)) {
            throw new IOException("不支持的图片类型");
        }
        long userId = currentUser.requireUserId();
        String storageKey = "images/" + UUID.randomUUID() + "." + extension;
        Path target = storage.resolveForCreate(
                userId, OwnedStorageResolver.Category.DOCUMENTS, storageKey);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return new StoredImage(storageKey.substring("images/".length()), originalName,
                file.getSize(), file.getContentType());
    }

    public StoredImageContent readImage(String fileName) throws IOException {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            throw new IOException("非法图片文件名");
        }
        long userId = currentUser.requireUserId();
        Path target = storage.resolveExisting(
                userId, OwnedStorageResolver.Category.DOCUMENTS, "images/" + fileName);
        String contentType = Files.probeContentType(target);
        return new StoredImageContent(Files.readAllBytes(target),
                contentType == null ? "application/octet-stream" : contentType);
    }

    /** 删除文档及本地文件。 */
    public void deleteDocument(Long id) throws IOException {
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new UserResourceNotFoundException("文档不存在");
        }
        if (document.getStorageKey() == null || document.getStorageKey().isBlank()) {
            throw new IOException("文档尚未迁移到用户存储: " + id);
        }
        Path file = storage.resolveExisting(
                document.getUserId(), OwnedStorageResolver.Category.DOCUMENTS, document.getStorageKey());
        Files.deleteIfExists(file);
        documentMapper.deleteById(id);
    }

    private Document requireOwnedDocument(Long id) {
        currentUser.requireUserId();
        Document document = documentMapper.selectById(id);
        if (document == null) {
            throw new UserResourceNotFoundException("文档不存在");
        }
        return document;
    }

    private void validateFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new IOException("文件名不能为空");
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IOException("文件名包含非法字符");
        }
        String fileType = getFileExtension(filename);
        List<String> allowedTypeList = resolveAllowedTypeList();
        if (!allowedTypeList.contains(fileType)) {
            throw new IOException("不支持的文件类型: " + fileType);
        }
        if (!contentExtractor.getSupportedTypes().contains(fileType)) {
            throw new IOException("当前不支持该文件类型的内容提取: " + fileType);
        }
        if (file.getSize() > resolveMaxFileSizeBytes()) {
            throw new IOException("文件大小超过限制");
        }
        validateMagicBytes(file, fileType);
    }

    private void validateMagicBytes(MultipartFile file, String fileType) throws IOException {
        if (TEXT_TYPES.contains(fileType)) {
            return;
        }
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(8);
            if (header.length < 4) {
                return;
            }
            if ("pdf".equals(fileType) && !matches(header, MAGIC_BYTES.get("pdf"))) {
                throw new IOException("文件内容与 PDF 扩展名不匹配");
            }
            if (Set.of("docx", "xlsx", "pptx").contains(fileType)
                    && !matches(header, MAGIC_BYTES.get("zip"))) {
                throw new IOException("文件内容与 Office 扩展名不匹配");
            }
            if (Set.of("doc", "xls", "ppt").contains(fileType)
                    && !matches(header, OLD_OFFICE_MAGIC)) {
                throw new IOException("文件内容与旧版 Office 扩展名不匹配");
            }
        }
    }

    private boolean matches(byte[] header, byte[] expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (header[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private List<String> resolveAllowedTypeList() {
        String configured = settingsService.getSetting("file", "allowed_types", allowedTypes);
        return List.of(configured.toLowerCase().replace(" ", "").split(","));
    }

    private long resolveMaxFileSizeBytes() {
        String configured = settingsService.getSetting(
                "file", "max_size_bytes", String.valueOf(DEFAULT_MAX_FILE_SIZE));
        try {
            return Long.parseLong(configured);
        } catch (NumberFormatException error) {
            return DEFAULT_MAX_FILE_SIZE;
        }
    }

    public record StoredImage(String fileName, String originalName, long fileSize, String contentType) {
    }

    public record StoredImageContent(byte[] bytes, String contentType) {
    }

    public record StoredDocumentContent(String fileName, byte[] bytes, String contentType) {
    }
}
