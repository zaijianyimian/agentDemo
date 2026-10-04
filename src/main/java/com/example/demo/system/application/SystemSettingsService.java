package com.example.demo.system.application;

import com.example.demo.infrastructure.config.CacheConfig;
import com.example.demo.infrastructure.settings.RuntimeSettingsProvider;
import com.example.demo.system.domain.SystemSettings;
import com.example.demo.system.persistence.SystemSettingsMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 系统设置服务。
 *
 * <p>管理接口（{@code /api/settings/**}）已下线，这里只保留后端业务读取配置的入口。
 * 目前仅 {@link com.example.demo.file.application.FileUploadService} 使用
 * {@code file.allowed_types} 等配置项，因此只实现只读能力；写入请直接走数据库或迁移脚本。</p>
 */
@Slf4j
@Service
public class SystemSettingsService implements RuntimeSettingsProvider {

    private final SystemSettingsMapper settingsMapper;

    public SystemSettingsService(SystemSettingsMapper settingsMapper) {
        this.settingsMapper = settingsMapper;
    }

    /**
     * 获取单个配置值，带默认值。配置缺失或读取失败时返回 {@code defaultValue}。
     */
    @Override
    @Cacheable(cacheNames = CacheConfig.SETTINGS_BY_KEY, key = "#category + ':' + #key")
    public String getSetting(String category, String key, String defaultValue) {
        try {
            SystemSettings setting = settingsMapper.selectByCategoryAndKey(category, key);
            String value = setting != null ? setting.getConfigValue() : null;
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            log.error("获取设置失败: category={}, key={}", category, key, e);
            return defaultValue;
        }
    }

    @Override
    public int getIntSetting(String category, String key, int defaultValue) {
        try {
            return Integer.parseInt(getSetting(category, key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
