package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.DataArchiveService;
import com.example.demo.service.SystemSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Map;

/**
 * 系统设置控制器
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
     * 获取所有配置
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
     * 获取指定分类的配置
     */
    @GetMapping("/{category}")
    public ApiResponse<Map<String, String>> getSettingsByCategory(@PathVariable String category) {
        return ApiResponse.success(settingsService.getSettingsByCategory(category));
    }

    /**
     * 获取单个配置
     */
    @GetMapping("/{category}/{key}")
    public ApiResponse<String> getSetting(
            @PathVariable String category,
            @PathVariable String key) {
        String value = settingsService.getSetting(category, key);
        return ApiResponse.success(value);
    }

    /**
     * 设置单个配置
     */
    @PutMapping("/{category}/{key}")
    public ApiResponse<Void> setSetting(
            @PathVariable String category,
            @PathVariable String key,
            @RequestBody String value) {
        settingsService.setSetting(category, key, value);
        return ApiResponse.success(null);
    }

    /**
     * 批量设置配置
     */
    @PutMapping("/{category}")
    public ApiResponse<Void> setSettings(
            @PathVariable String category,
            @RequestBody Map<String, String> settings) {
        settingsService.setSettings(category, settings);
        return ApiResponse.success(null);
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{category}/{key}")
    public ApiResponse<Void> deleteSetting(
            @PathVariable String category,
            @PathVariable String key) {
        settingsService.deleteSetting(category, key);
        return ApiResponse.success(null);
    }

    // ==================== 便捷接口 ====================

    /**
     * 获取系统设置
     */
    @GetMapping("/system")
    public ApiResponse<Map<String, String>> getSystemSettings() {
        return ApiResponse.success(settingsService.getSettingsByCategory("system"));
    }

    /**
     * 更新系统设置
     */
    @PutMapping("/system")
    public ApiResponse<Void> updateSystemSettings(@RequestBody Map<String, String> settings) {
        settingsService.setSettings("system", settings);
        return ApiResponse.success(null);
    }

    /**
     * 获取向量数据库配置
     */
    @GetMapping("/qdrant")
    public ApiResponse<Map<String, String>> getQdrantSettings() {
        return ApiResponse.success(settingsService.getSettingsByCategory("qdrant"));
    }

    /**
     * 更新向量数据库配置
     */
    @PutMapping("/qdrant")
    public ApiResponse<Void> updateQdrantSettings(@RequestBody Map<String, String> settings) {
        settingsService.setSettings("qdrant", settings);
        return ApiResponse.success(null);
    }

    /**
     * 获取搜索配置
     */
    @GetMapping("/search")
    public ApiResponse<Map<String, String>> getSearchSettings() {
        Map<String, String> settings = settingsService.getSettingsByCategory("search");
        // 隐藏敏感信息
        if (settings.containsKey("api_key") && settings.get("api_key") != null) {
            String apiKey = settings.get("api_key");
            if (apiKey.length() > 8) {
                settings.put("api_key", apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
            }
        }
        return ApiResponse.success(settings);
    }

    /**
     * 更新搜索配置
     */
    @PutMapping("/search")
    public ApiResponse<Void> updateSearchSettings(@RequestBody Map<String, String> settings) {
        // 如果 API Key 没有变化（包含 ****），则不更新
        if (settings.containsKey("api_key") && settings.get("api_key") != null) {
            String apiKey = settings.get("api_key");
            if (apiKey.contains("****")) {
                settings.remove("api_key");
            }
        }
        settingsService.setSettings("search", settings);
        return ApiResponse.success(null);
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
     * 获取代理配置
     */
    @GetMapping("/proxy")
    public ApiResponse<Map<String, String>> getProxySettings() {
        return ApiResponse.success(settingsService.getProxySettings());
    }

    /**
     * 更新代理配置
     */
    @PutMapping("/proxy")
    public ApiResponse<Void> updateProxySettings(@RequestBody Map<String, String> settings) {
        settingsService.setProxySettings(settings);
        return ApiResponse.success(null);
    }

    /**
     * 导出全量数据（ZIP）
     */
    @GetMapping("/data/export")
    public ResponseEntity<byte[]> exportAllDataZip() {
        byte[] archive = dataArchiveService.exportAllDataAsZip();
        String filename = dataArchiveService.buildArchiveFileName();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded)
                .body(archive);
    }

    /**
     * 导入全量数据（ZIP）
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
