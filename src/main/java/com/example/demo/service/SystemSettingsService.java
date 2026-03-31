package com.example.demo.service;

import com.example.demo.config.CacheConfig;
import com.example.demo.entity.SystemSettings;
import com.example.demo.mapper.SystemSettingsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统设置服务
 */
@Slf4j
@Service
public class SystemSettingsService {

    private final SystemSettingsMapper settingsMapper;

    public SystemSettingsService(SystemSettingsMapper settingsMapper) {
        this.settingsMapper = settingsMapper;
    }

    /**
     * 获取所有配置
     */
    @Cacheable(cacheNames = CacheConfig.SETTINGS_ALL)
    public Map<String, Map<String, String>> getAllSettings() {
        Map<String, Map<String, String>> result = new HashMap<>();

        try {
            List<SystemSettings> allSettings = settingsMapper.selectList(null);

            for (SystemSettings setting : allSettings) {
                result.computeIfAbsent(setting.getCategory(), k -> new HashMap<>())
                        .put(setting.getConfigKey(), setting.getConfigValue());
            }
        } catch (Exception e) {
            log.error("获取设置失败", e);
        }

        return result;
    }

    /**
     * 获取指定分类的配置
     */
    @Cacheable(cacheNames = CacheConfig.SETTINGS_BY_CATEGORY, key = "#category")
    public Map<String, String> getSettingsByCategory(String category) {
        Map<String, String> result = new HashMap<>();

        try {
            List<SystemSettings> settings = settingsMapper.selectByCategory(category);

            for (SystemSettings setting : settings) {
                result.put(setting.getConfigKey(), setting.getConfigValue());
            }
        } catch (Exception e) {
            log.error("获取设置失败: category={}", category, e);
        }

        return result;
    }

    /**
     * 获取单个配置值
     */
    @Cacheable(cacheNames = CacheConfig.SETTINGS_BY_KEY, key = "#category + ':' + #key")
    public String getSetting(String category, String key) {
        try {
            SystemSettings setting = settingsMapper.selectByCategoryAndKey(category, key);
            return setting != null ? setting.getConfigValue() : null;
        } catch (Exception e) {
            log.error("获取设置失败: category={}, key={}", category, key, e);
            return null;
        }
    }

    /**
     * 获取单个配置值，带默认值
     */
    public String getSetting(String category, String key, String defaultValue) {
        String value = getSetting(category, key);
        return value != null ? value : defaultValue;
    }

    /**
     * 设置单个配置
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.SETTINGS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SETTINGS_BY_CATEGORY, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SETTINGS_BY_KEY, allEntries = true)
    })
    public void setSetting(String category, String key, String value) {
        try {
            SystemSettings setting = settingsMapper.selectByCategoryAndKey(category, key);

            if (setting == null) {
                setting = SystemSettings.builder()
                        .category(category)
                        .configKey(key)
                        .configValue(value)
                        .build();
                settingsMapper.insert(setting);
            } else {
                setting.setConfigValue(value);
                settingsMapper.updateById(setting);
            }

            log.info("Updated setting: {}.{} = {}", category, key, value);
        } catch (Exception e) {
            log.error("保存设置失败: category={}, key={}", category, key, e);
            throw new RuntimeException("保存设置失败: " + e.getMessage());
        }
    }

    /**
     * 批量设置配置
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.SETTINGS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SETTINGS_BY_CATEGORY, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SETTINGS_BY_KEY, allEntries = true)
    })
    public void setSettings(String category, Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            setSetting(category, entry.getKey(), entry.getValue());
        }
    }

    /**
     * 删除配置
     */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheConfig.SETTINGS_ALL, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SETTINGS_BY_CATEGORY, allEntries = true),
            @CacheEvict(cacheNames = CacheConfig.SETTINGS_BY_KEY, allEntries = true)
    })
    public void deleteSetting(String category, String key) {
        try {
            SystemSettings setting = settingsMapper.selectByCategoryAndKey(category, key);
            if (setting != null) {
                settingsMapper.deleteById(setting.getId());
                log.info("Deleted setting: {}.{}", category, key);
            }
        } catch (Exception e) {
            log.error("删除设置失败: category={}, key={}", category, key, e);
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取系统名称
     */
    public String getSiteName() {
        return getSetting("system", "site_name", "AI Agent");
    }

    /**
     * 获取默认主题
     */
    public String getDefaultTheme() {
        return getSetting("system", "default_theme", "light");
    }

    /**
     * 检查搜索是否启用
     */
    public boolean isSearchEnabled() {
        return "true".equalsIgnoreCase(getSetting("search", "enabled", "true"));
    }

    /**
     * 获取搜索引擎
     */
    public String getSearchEngine() {
        return getSetting("search", "engine", "serper");
    }

    /**
     * 获取搜索 API Key
     */
    public String getSearchApiKey() {
        return getSetting("search", "api_key", "");
    }
}
