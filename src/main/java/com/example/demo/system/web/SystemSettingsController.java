package com.example.demo.system.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.system.application.DataArchiveService;
import com.example.demo.system.application.SystemSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Java 业务平台系统设置控制器。
 *
 * <p>仅管理 Java 业务配置与数据归档。模型、向量库、语义检索等 Agent 配置已迁移到 Python，
 * 不再通过 Java 设置接口维护。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/settings")
public class SystemSettingsController {

    private static final Set<String> PROTECTED_CATEGORIES = Set.of(
            "security", "auth", "internal", "system_secret");
    private static final Set<String> AGENT_CATEGORIES = Set.of(
            "model", "qdrant", "search", "memory", "mcp", "skill", "autonomy", "embedding");
    private static final Set<String> SENSITIVE_KEYWORDS = Set.of(
            "password", "secret", "token", "api_key", "apikey", "private", "credential", "jwt");

    private final SystemSettingsService settingsService;
    private final DataArchiveService dataArchiveService;

    public SystemSettingsController(
            SystemSettingsService settingsService,
            DataArchiveService dataArchiveService) {
        this.settingsService = settingsService;
        this.dataArchiveService = dataArchiveService;
    }

    /** 获取所有 Java 业务配置。 */
    @GetMapping
    public ApiResponse<Map<String, Map<String, String>>> getAllSettings() {
        try {
            Map<String, Map<String, String>> settings = settingsService.getAllSettings();
            AGENT_CATEGORIES.forEach(settings::remove);
            return ApiResponse.success(settings);
        } catch (Exception error) {
            log.error("获取设置失败", error);
            return ApiResponse.error("获取设置失败: " + error.getMessage());
        }
    }

    /** 获取指定 Java 业务配置分类。 */
    @GetMapping("/{category}")
    public ApiResponse<Map<String, String>> getSettingsByCategory(@PathVariable String category) {
        if (isAgentCategory(category)) {
            return ApiResponse.error("Agent 配置已迁移到 Python 服务");
        }
        return ApiResponse.success(settingsService.getSettingsByCategory(category));
    }

    /** 获取单个 Java 业务配置。 */
    @GetMapping("/{category}/{key}")
    public ApiResponse<String> getSetting(
            @PathVariable String category,
            @PathVariable String key) {
        if (isAgentCategory(category)) {
            return ApiResponse.error("Agent 配置已迁移到 Python 服务");
        }
        String value = settingsService.getSetting(category, key);
        if (value != null && isSensitiveKey(key)) {
            return ApiResponse.success(maskSecret(value));
        }
        return ApiResponse.success(value);
    }

    /** 设置单个 Java 业务配置。 */
    @PutMapping("/{category}/{key}")
    public ApiResponse<Void> setSetting(
            @PathVariable String category,
            @PathVariable String key,
            @RequestBody String value) {
        if (isProtectedCategory(category)) {
            return ApiResponse.error("禁止直接修改受保护的 category: " + category);
        }
        if (isAgentCategory(category)) {
            return ApiResponse.error("Agent 配置已迁移到 Python 服务");
        }
        settingsService.setSetting(category, key, value);
        return ApiResponse.success(null);
    }

    /** 批量更新 Java 业务配置。 */
    @PutMapping("/{category}")
    public ApiResponse<Void> setSettings(
            @PathVariable String category,
            @RequestBody Map<String, String> settings) {
        if (isProtectedCategory(category)) {
            return ApiResponse.error("禁止直接修改受保护的 category: " + category);
        }
        if (isAgentCategory(category)) {
            return ApiResponse.error("Agent 配置已迁移到 Python 服务");
        }
        settingsService.setSettings(category, settings);
        return ApiResponse.success(null);
    }

    /** 删除 Java 业务配置。 */
    @DeleteMapping("/{category}/{key}")
    public ApiResponse<Void> deleteSetting(
            @PathVariable String category,
            @PathVariable String key) {
        if (isProtectedCategory(category)) {
            return ApiResponse.error("禁止删除受保护的 category: " + category);
        }
        if (isAgentCategory(category)) {
            return ApiResponse.error("Agent 配置已迁移到 Python 服务");
        }
        settingsService.deleteSetting(category, key);
        return ApiResponse.success(null);
    }

    /** 获取系统配置。 */
    @GetMapping("/system")
    public ApiResponse<Map<String, String>> getSystemSettings() {
        return ApiResponse.success(settingsService.getSettingsByCategory("system"));
    }

    /** 更新系统配置。 */
    @PutMapping("/system")
    public ApiResponse<Void> updateSystemSettings(@RequestBody Map<String, String> settings) {
        settingsService.setSettings("system", settings);
        return ApiResponse.success(null);
    }

    /** 获取日程业务配置。 */
    @GetMapping("/schedule")
    public ApiResponse<Map<String, String>> getScheduleSettings() {
        return ApiResponse.success(settingsService.getSettingsByCategory("schedule"));
    }

    /** 更新日程业务配置。 */
    @PutMapping("/schedule")
    public ApiResponse<Void> updateScheduleSettings(@RequestBody Map<String, String> settings) {
        settingsService.setSettings("schedule", settings);
        return ApiResponse.success(null);
    }

    /** 获取文件业务配置。 */
    @GetMapping("/file")
    public ApiResponse<Map<String, String>> getFileSettings() {
        return ApiResponse.success(settingsService.getSettingsByCategory("file"));
    }

    /** 更新文件业务配置。 */
    @PutMapping("/file")
    public ApiResponse<Void> updateFileSettings(@RequestBody Map<String, String> settings) {
        settingsService.setSettings("file", settings);
        return ApiResponse.success(null);
    }

    /** 导出 Java 业务数据 ZIP。 */
    @GetMapping("/data/export")
    public ResponseEntity<StreamingResponseBody> exportAllDataZip() {
        String filename = dataArchiveService.buildArchiveFileName();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        StreamingResponseBody body = dataArchiveService::writeArchiveTo;
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .body(body);
    }

    /** 导入 Java 业务数据 ZIP。 */
    @PostMapping(value = "/data/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> importAllDataZip(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean replaceExisting) {
        try {
            Map<String, Object> result =
                    dataArchiveService.importAllDataFromZip(file, replaceExisting);
            return ApiResponse.success(result, "Java 业务数据导入成功");
        } catch (Exception error) {
            log.error("导入 Java 业务数据失败", error);
            return ApiResponse.error("导入失败: " + error.getMessage());
        }
    }

    private boolean isProtectedCategory(String category) {
        return category != null && PROTECTED_CATEGORIES.contains(category.toLowerCase());
    }

    private boolean isAgentCategory(String category) {
        return category != null && AGENT_CATEGORIES.contains(category.toLowerCase());
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase();
        return SENSITIVE_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private String maskSecret(String value) {
        return value == null || value.isEmpty() ? value : "***" + value.length() + " chars";
    }
}
