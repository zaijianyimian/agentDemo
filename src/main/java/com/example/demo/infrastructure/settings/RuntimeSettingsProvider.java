package com.example.demo.infrastructure.settings;

/**
 * 运行时动态设置提供者接口
 * <p>
 * 定义 Java 业务运行时配置的统一读取入口。
 */
public interface RuntimeSettingsProvider {

    String getSetting(String category, String key, String defaultValue);

    int getIntSetting(String category, String key, int defaultValue);
}
