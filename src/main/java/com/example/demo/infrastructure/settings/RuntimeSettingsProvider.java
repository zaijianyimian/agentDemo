package com.example.demo.infrastructure.settings;

/**
 * 运行时动态设置提供者接口
 * <p>
 * 定义在数据库中存储的应用运行时配置（如 Qdrant 连接参数）的统一读取入口，
 * 供基础设施组件在配置优先于环境变量时使用。
 */
public interface RuntimeSettingsProvider {

    String getSetting(String category, String key, String defaultValue);

    int getIntSetting(String category, String key, int defaultValue);
}
