package com.example.demo.email.domain.listener;

import com.example.demo.email.domain.EmailConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * provider 扩展配置只读视图。
 * <p>
 * 把 {@link EmailConfig#getProviderSettings()} 里的 JSON 解析为键值对，供 provider 适配器读取
 * 主题名、通知 URL、folderId 等非敏感参数；解析失败时安全降级为空配置。
 */
public record ProviderSettings(Map<String, Object> values) {

    public static ProviderSettings empty() {
        return new ProviderSettings(Collections.emptyMap());
    }

    public static ProviderSettings fromConfig(EmailConfig config, ObjectMapper objectMapper) {
        String raw = config == null ? null : config.getProviderSettings();
        if (raw == null || raw.isBlank()) {
            return empty();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(raw, new TypeReference<>() {});
            return new ProviderSettings(parsed == null ? Collections.emptyMap() : parsed);
        } catch (Exception e) {
            return empty();
        }
    }

    public String string(String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public String stringOrDefault(String key, String fallback) {
        String value = string(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public Map<String, Object> mutableCopy() {
        return new LinkedHashMap<>(values);
    }
}
