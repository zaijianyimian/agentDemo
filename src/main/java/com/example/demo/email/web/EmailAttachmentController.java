package com.example.demo.email.web;

import com.example.demo.email.application.EmailAttachmentListener;
import com.example.demo.email.domain.EmailAttachmentAnalysis;
import com.example.demo.email.persistence.EmailAttachmentAnalysisMapper;
import com.example.demo.infrastructure.properties.EmailAttachmentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件附件 / 解析结果查询接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailAttachmentController {

    private final EmailAttachmentAnalysisMapper analysisMapper;
    private final EmailAttachmentListener attachmentListener;
    private final EmailAttachmentProperties properties;

    /**
     * 查询指定邮件的所有附件解析结果。
     */
    @GetMapping("/messages/{messageId}/analyses")
    public List<EmailAttachmentAnalysis> listAnalyses(@PathVariable("messageId") String messageId) {
        return analysisMapper.selectByMessageId(messageId);
    }

    /**
     * 下载/预览附件原始文件。
     *
     * <p>安全要点：仅当数据库里的 {@code filePath} 解析后位于 {@code app.email.attachment-dir}
     * 之内时才返回文件，避免数据库被污染后读取任意系统文件。</p>
     */
    @GetMapping("/analyses/{id}/file")
    public ResponseEntity<?> downloadAttachment(@PathVariable("id") Long id) {
        EmailAttachmentAnalysis analysis = analysisMapper.selectById(id);
        if (analysis == null) {
            return ResponseEntity.notFound().build();
        }
        if (analysis.getFilePath() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "文件路径为空"));
        }
        Path file;
        Path attachmentRoot;
        try {
            file = Paths.get(analysis.getFilePath()).toAbsolutePath().normalize();
            attachmentRoot = Paths.get(properties.getAttachmentDir())
                    .toAbsolutePath().normalize();
            // 防止 file 越过根目录：必须以 attachmentRoot 为前缀
            if (!file.startsWith(attachmentRoot) || !Files.isRegularFile(file)) {
                log.warn("附件路径越界或不存在: id={}, file={}, root={}", id, file, attachmentRoot);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("附件路径解析失败: id={}", id, e);
            return ResponseEntity.badRequest().body(Map.of("message", "附件路径非法"));
        }
        try {
            String contentType = analysis.getContentType() == null
                    ? "application/octet-stream"
                    : analysis.getContentType();
            MediaType mediaType = MediaType.parseMediaType(contentType);

            String encoded = URLEncoder.encode(analysis.getFileName() == null ? "file" : analysis.getFileName(),
                    StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(Files.size(file))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                    .body(new FileSystemResource(file));
        } catch (Exception e) {
            log.error("下载附件失败: id={}", id, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "下载失败"));
        }
    }

    /**
     * 手动重试某条解析记录。
     */
    @PostMapping("/analyses/{id}/retry")
    public ResponseEntity<Map<String, Object>> retry(@PathVariable("id") Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            attachmentListener.retry(id);
            result.put("success", true);
            result.put("message", "已重新加入解析队列");
        } catch (Exception e) {
            log.error("重试解析失败: id={}", id, e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}
