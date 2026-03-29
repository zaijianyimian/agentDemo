package com.example.demo.controller;

import com.example.demo.entity.Document;
import com.example.demo.service.file.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传控制器
 * 处理文件上传、AI分析、查询等接口
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileUploadService fileUploadService;
    private final String uploadDir = "./data/uploads";

    public FileController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
        // 确保上传目录存在
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            log.warn("无法创建上传目录: {}", e.getMessage());
        }
    }

    /**
     * 图片上传接口（用于Logo等）
     * 只存储文件，不进行AI分析
     */
    @PostMapping("/upload/image")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            // 检查文件类型
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "只支持图片文件"
                ));
            }

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".png";
            String newFilename = UUID.randomUUID().toString() + extension;

            // 保存文件
            Path filePath = Paths.get(uploadDir, newFilename);
            Files.copy(file.getInputStream(), filePath);

            log.info("图片上传成功: {}", newFilename);

            // 返回可访问的URL
            String fileUrl = "/api/file/image/" + newFilename;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "图片上传成功",
                    "data", Map.of(
                            "url", fileUrl,
                            "fileName", newFilename,
                            "originalName", originalFilename,
                            "fileSize", file.getSize()
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
            Path filePath = Paths.get(uploadDir, filename);
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] content = Files.readAllBytes(filePath);
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "image/png";
            }

            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Cache-Control", "max-age=31536000")
                    .body(content);
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