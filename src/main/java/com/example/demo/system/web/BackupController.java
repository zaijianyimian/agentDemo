package com.example.demo.system.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.system.application.BackupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 数据备份管理接口。
 *
 * <p>基于 {@link BackupService} 提供服务端 ZIP 快照的创建、列表、下载、删除与清理。
 * 前端 {@code frontend/src/services/api/backup.ts} 期望的接口。
 * 下载用 {@link InputStreamResource} 流式返回，避免把整个 ZIP 一次性读到堆上。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    @PostMapping("/create")
    public ApiResponse<BackupService.BackupResult> create() {
        return ApiResponse.success(backupService.createBackup());
    }

    /**
     * 直接流式下载刚创建的备份文件，供"创建并下载"场景使用。
     */
    @PostMapping("/download")
    public ResponseEntity<InputStreamResource> createAndDownload() {
        BackupService.BackupResult result = backupService.createBackup();
        return streamResponse(fileName -> backupService.openBackupStream(fileName), result.getFileName());
    }

    @GetMapping("/list")
    public ApiResponse<List<BackupService.BackupFileInfo>> list() {
        return ApiResponse.success(backupService.listBackups());
    }

    @GetMapping("/info/{fileName}")
    public ApiResponse<BackupService.BackupFileInfo> info(@PathVariable("fileName") String fileName) {
        return backupService.listBackups().stream()
                .filter(info -> info.getFileName().equals(fileName))
                .findFirst()
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error("备份文件不存在: " + fileName));
    }

    @GetMapping("/download/{fileName}")
    public ResponseEntity<InputStreamResource> download(@PathVariable("fileName") String fileName) {
        return streamResponse(backupService::openBackupStream, fileName);
    }

    @DeleteMapping("/{fileName}")
    public ApiResponse<String> delete(@PathVariable("fileName") String fileName) {
        backupService.deleteBackup(fileName);
        return ApiResponse.success("已删除 " + fileName);
    }

    @PostMapping("/cleanup")
    public ApiResponse<String> cleanup() {
        int removed = backupService.cleanupOldBackups();
        return ApiResponse.success("已清理 " + removed + " 个过期备份文件");
    }

    /**
     * 流式返回备份文件：Spring 会按块把 InputStream 写给 response，不需要把整个 ZIP 装到堆里。
     */
    private static ResponseEntity<InputStreamResource> streamResponse(
            java.util.function.Function<String, BackupService.BackupStream> opener,
            String fileName) {
        BackupService.BackupStream handle = opener.apply(fileName);
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        if (handle.size() >= 0) {
            builder.contentLength(handle.size());
        }
        return builder.body(new InputStreamResource(handle.stream()));
    }
}