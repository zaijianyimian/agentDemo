package com.example.demo.system.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.system.application.DataArchiveService;
import com.example.demo.system.application.SystemSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Map;

/**
 * 系统设置控制器
 * 敏感操作需要管理员权限
 */
@Slf4j
@RestController
@RequestMapping("/api/settings")
public class SystemSettingsController {

    private final SystemSettingsService settingsService;
    private final DataArchiveService dataArchiveService;

    public SystemSettingsController(SystemSettingsService settingsService,
                                    DataArchiveService dataArchiveService) {
        this.settingsService = settingsService;
        this.dataArchiveService = dataArchiveService;
    }

    /**
     * 获取所有配置 - 需要管理员权限
     */
    @GetMapping
        public ApiResponse<Map<String, Map<String, String>>> getAllSettings() {
        try {
            Map<String, Map<String, String>> settings = settingsService.getAllSettings();
            return ApiResponse.success(settings);
        } catch (Exception e) {
            log.error("获取设置失败", e);
            return ApiResponse.error("获取设置失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定分类的配置 - 需要管理员权限
     */
    @GetMapping("/{category}")
        public ApiResponse<Map<String, String>> getSettingsByCategory(@PathVariable String category) {
        return ApiResponse.success(settingsService.getSettingsByCategory(category));
    }

    /**
     * 获取单个配置 - 需要管理员权限（敏感配置如API密钥）。
     *
     * <p>敏感 key（密码、secret、token、key 等）会被脱敏返回明文长度 + 末 4 位，
     * 避免通过此接口读取完整密钥。</p>
     */
    @GetMapping("/{category}/{key}")
        public ApiResponse<String> getSetting(
            @PathVariable String category,
            @PathVariable String key) {
        String value = settingsService.getSetting(category, key);
        if (value != null && isSensitiveKey(key)) {
            return ApiResponse.success(maskSecret(value));
        }
        return ApiResponse.success(value);
    }

    /**
     * 设置单个配置 - 需要管理员权限。
     *
     * <p>禁止写入黑名单 category（{@code security}、{@code auth}、{@code internal}），
     * 防止已登录用户覆盖 JWT 密钥、数据密钥等系统级凭据。</p>
     */
    @PutMapping("/{category}/{key}")
        public ApiResponse<Void> setSetting(
            @PathVariable String category,
            @PathVariable String key,
            @RequestBody String value) {
        if (isProtectedCategory(category)) {
            return ApiResponse.error("禁止直接修改受保护的 category: " + category);
        }
        settingsService.setSetting(category, key, value);
        return ApiResponse.success(null);
    }

    /**
     * 批量设置配置 - 需要管理员权限。同样禁止写入受保护 category。
     */
    @PutMapping("/{category}")
        public ApiResponse<Void> setSettings(
            @PathVariable String category,
            @RequestBody Map<String, String> settings) {
        if (isProtectedCategory(category)) {
            return ApiResponse.error("禁止直接修改受保护的 category: " + category);
        }
        settingsService.setSettings(category, settings);
        return ApiResponse.success(null);
    }

    private static final java.util.Set<String> PROTECTED_CATEGORIES = java.util.Set.of(
            "security", "auth", "internal", "system_secret");

    private static boolean isProtectedCategory(String category) {
        if (category == null) {
            return false;
        }
        return PROTECTED_CATEGORIES.contains(category.toLowerCase());
    }

    private static final java.util.Set<String> SENSITIVE_KEYWORDS = java.util.Set.of(
            "password", "secret", "token", "api_key", "apikey",
            "private", "credential", "jwt");

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String maskSecret(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        // 只保留长度，避免 base64 padding、末 4 位泄漏原始字节范围
        return "***" + value.length() + " chars";
    }

    /**
     * 删除配置 - 需要管理员权限。同样禁止删除受保护 category 下的配置。
     */
    @DeleteMapping("/{category}/{key}")
        public ApiResponse<Void> deleteSetting(
            @PathVariable String category,
            @PathVariable String key) {
        if (isProtectedCategory(category)) {
            return ApiResponse.error("禁止删除受保护的 category: " + category);
        }
        settingsService.deleteSetting(category, key);
        return ApiResponse.success(null);
    }

    // ==================== 便捷接口 ====================

    /**
     * 获取系统设置 - 需要管理员权限
     */
    @GetMapping("/system")
        public ApiResponse<Map<String, String>> getSystemSettings() {
        return ApiResponse.success(settingsService.getSettingsByCategory("system"));
    }

    /**
     * 更新系统设置 - 需要管理员权限
     */
    @PutMapping("/system")
        public ApiResponse<Void> updateSystemSettings(@RequestBody Map<String, String> settings) {
        settingsService.setSettings("system", settings);
        return ApiResponse.success(null);
    }

    /**
     * 获取向量数据库配置 - 需要管理员权限
     */
    @GetMapping("/qdrant")
        public ApiResponse<Map<String, String>> getQdrantSettings() {
        Map<String, String> settings = settingsService.getSettingsByCategory("qdrant");
        maskApiKey(settings);
        return ApiResponse.success(settings);
    }

    /**
     * 更新向量数据库配置 - 需要管理员权限
     */
    @PutMapping("/qdrant")
        public ApiResponse<Void> updateQdrantSettings(@RequestBody Map<String, String> settings) {
        removeMaskedApiKey(settings);
        settingsService.setSettings("qdrant", settings);
        return ApiResponse.success(null);
    }

    /**
     * 获取搜索配置 - 需要管理员权限
     */
    @GetMapping("/search")
        public ApiResponse<Map<String, String>> getSearchSettings() {
        Map<String, String> settings = settingsService.getSettingsByCategory("search");
        // 隐藏敏感信息
        maskApiKey(settings);
        return ApiResponse.success(settings);
    }

    /**
     * 更新搜索配置 - 需要管理员权限
     */
    @PutMapping("/search")
        public ApiResponse<Void> updateSearchSettings(@RequestBody Map<String, String> settings) {
        // 如果 API Key 没有变化（包含 ****），则不更新
        removeMaskedApiKey(settings);
        settingsService.setSettings("search", settings);
        return ApiResponse.success(null);
    }

    private void maskApiKey(Map<String, String> settings) {
        if (settings.containsKey("api_key") && settings.get("api_key") != null) {
            String apiKey = settings.get("api_key");
            if (apiKey.length() > 8) {
                settings.put("api_key", apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
            }
        }
    }

    private void removeMaskedApiKey(Map<String, String> settings) {
        if (settings.containsKey("api_key") && settings.get("api_key") != null) {
            String apiKey = settings.get("api_key");
            if (apiKey.contains("****")) {
                settings.remove("api_key");
            }
        }
    }

    /**
     * 获取日程配置
     */
    @GetMapping("/schedule")
    public ApiResponse<Map<String, String>> getScheduleSettings() {
        return ApiResponse.success(settingsService.getSettingsByCategory("schedule"));
    }

    /**
     * 更新日程配置
     */
    @PutMapping("/schedule")
    public ApiResponse<Void> updateScheduleSettings(@RequestBody Map<String, String> settings) {
        settingsService.setSettings("schedule", settings);
        return ApiResponse.success(null);
    }

    /**
     * 获取文件上传配置
     */
    @GetMapping("/file")
    public ApiResponse<Map<String, String>> getFileSettings() {
        return ApiResponse.success(settingsService.getSettingsByCategory("file"));
    }

    /**
     * 更新文件上传配置
     */
    @PutMapping("/file")
    public ApiResponse<Void> updateFileSettings(@RequestBody Map<String, String> settings) {
        settingsService.setSettings("file", settings);
        return ApiResponse.success(null);
    }

    /**
     * 获取模型参数配置
     */
    @GetMapping("/model")
    public ApiResponse<Map<String, String>> getModelSettings() {
        return ApiResponse.success(settingsService.getSettingsByCategory("model"));
    }

    /**
     * 更新模型参数配置
     */
    @PutMapping("/model")
    public ApiResponse<Void> updateModelSettings(@RequestBody Map<String, String> settings) {
        settingsService.setSettings("model", settings);
        return ApiResponse.success(null);
    }

    /**
     * 导出全量数据（ZIP） - 需要管理员权限
     */
    @GetMapping("/data/export")
        public ResponseEntity<StreamingResponseBody> exportAllDataZip() {
        String filename = dataArchiveService.buildArchiveFileName();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        StreamingResponseBody body = outputStream -> dataArchiveService.writeArchiveTo(outputStream);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .body(body);
    }

    /**
     * 导入全量数据（ZIP） - 需要管理员权限
     */
    @PostMapping(value = "/data/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public ApiResponse<Map<String, Object>> importAllDataZip(@RequestParam("file") MultipartFile file,
                                                             @RequestParam(defaultValue = "true") boolean replaceExisting) {
        try {
            Map<String, Object> result = dataArchiveService.importAllDataFromZip(file, replaceExisting);
            return ApiResponse.success(result, "数据导入成功");
        } catch (Exception e) {
            log.error("导入数据失败", e);
            return ApiResponse.error("导入失败: " + e.getMessage());
        }
    }
}
