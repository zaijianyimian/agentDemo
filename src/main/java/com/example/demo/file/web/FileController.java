package com.example.demo.file.web;

import com.example.demo.file.domain.Document;
import com.example.demo.file.application.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 文件上传控制器
 * 处理文件上传、AI分析、查询等接口
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileUploadService fileUploadService;

    /**
     * 构造时注入上传服务并确保本地图片目录存在
     */
    public FileController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    /**
     * 图片上传接口（用于Logo等）
     * 只存储文件，不进行AI分析
     */
    @PostMapping("/upload/image")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            FileUploadService.StoredImage stored = fileUploadService.storeImage(file);
            log.info("图片上传成功: {}", stored.fileName());

            // 返回可访问的URL
            String fileUrl = "/api/file/image/" + stored.fileName();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "图片上传成功",
                    "data", Map.of(
                            "url", fileUrl,
                            "fileName", stored.fileName(),
                            "originalName", stored.originalName(),
                            "fileSize", stored.fileSize()
                    )
            ));
        } catch (IOException e) {
            log.error("图片上传失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "图片上传失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取图片文件
     */
    @GetMapping("/image/{filename}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        try {
            FileUploadService.StoredImageContent content = fileUploadService.readImage(filename);

            return ResponseEntity.ok()
                    .header("Content-Type", content.contentType())
                    .header("Cache-Control", "max-age=31536000")
                    .body(content.bytes());
        } catch (IOException e) {
            log.error("读取图片失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 文件上传接口
     * 上传文件后自动进行AI分析并存储
     *
     * @param file 上传的文件
     * @return 上传结果，包含文档ID、分析结果等
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            log.info("收到文件上传请求: {}, 大小: {} bytes", file.getOriginalFilename(), file.getSize());

            Document document = fileUploadService.uploadAndAnalyze(file);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "文件上传并分析成功",
                    "data", Map.of(
                            "id", document.getId(),
                            "fileName", document.getFileName(),
                            "fileType", document.getFileType(),
                            "fileSize", document.getFileSize(),
                            "importance", document.getImportance(),
                            "tags", document.getTags(),
                            "sentiment", document.getSentiment(),
                            "summary", document.getSummary(),
                            "status", document.getStatus(),
                            "createTime", document.getCreateTime()
                    )
            ));
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "文件上传失败: " + e.getMessage()
            ));
        } catch (Exception e) {
            log.error("文件处理异常", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "文件处理异常: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取文档列表
     *
     * @return 所有文档列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listDocuments() {
        List<Document> documents = fileUploadService.listDocuments();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", documents,
                "total", documents.size()
        ));
    }

    /**
     * 获取文档详情
     *
     * @param id 文档ID
     * @return 文档详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getDocument(@PathVariable Long id) {
        Document document = fileUploadService.getDocument(id);
        if (document == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", document
        ));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id) {
        try {
            FileUploadService.StoredDocumentContent content = fileUploadService.readDocumentFile(id);
            return ResponseEntity.ok()
                    .header("Content-Type", content.contentType())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + content.fileName().replace("\"", "") + "\"")
                    .body(content.bytes());
        } catch (IOException error) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 删除文档
     *
     * @param id 文档ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        try {
            fileUploadService.deleteDocument(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "文档删除成功"
            ));
        } catch (IOException e) {
            log.error("文档删除失败", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "文档删除失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 按重要程度查询文档
     *
     * @param minImportance 最小重要程度
     * @param maxImportance 最大重要程度
     * @return 符合条件的文档列表
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchByImportance(
            @RequestParam(required = false) Integer minImportance,
            @RequestParam(required = false) Integer maxImportance) {

        List<Document> allDocuments = fileUploadService.listDocuments();

        // 过滤重要程度
        List<Document> filtered = allDocuments.stream()
                .filter(doc -> {
                    if (minImportance != null && doc.getImportance() < minImportance) {
                        return false;
                    }
                    if (maxImportance != null && doc.getImportance() > maxImportance) {
                        return false;
                    }
                    return true;
                })
                .toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", filtered,
                "total", filtered.size()
        ));
    }
}
