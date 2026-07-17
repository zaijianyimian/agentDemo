package com.example.demo.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * 缓存配置
 * 使用 Caffeine 作为 Spring Cache 后端，统一管理用户、模型、技能、设置等缓存名称与过期策略
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_ACCOUNT_BY_ID = "userAccountById";
    public static final String USER_ACCOUNT_BY_USERNAME = "userAccountByUsername";
    public static final String USER_ACCOUNT_BY_EMAIL = "userAccountByEmail";
    public static final String USER_ACCOUNT_BY_LOGIN = "userAccountByLogin";
    public static final String MODEL_LIST = "modelList";
    public static final String MODEL_DETAIL = "modelDetail";
    public static final String MODEL_PROVIDERS = "modelProviders";
    public static final String SKILL_LIST_ALL = "skillListAll";
    public static final String SKILL_LIST_ENABLED = "skillListEnabled";
    public static final String SKILL_LIST_BUILTIN = "skillListBuiltin";
    public static final String SKILL_CATEGORIES = "skillCategories";
    public static final String SKILL_LIST_BY_CATEGORY = "skillListByCategory";
    public static final String SKILL_BY_ID = "skillById";
    public static final String SKILL_BY_CODE = "skillByCode";
    public static final String SETTINGS_ALL = "settingsAll";
    public static final String SETTINGS_BY_CATEGORY = "settingsByCategory";
    public static final String SETTINGS_BY_KEY = "settingsByKey";
    public static final String KNOWLEDGE_LIST = "knowledgeList";
    public static final String KNOWLEDGE_DETAIL = "knowledgeDetail";
    public static final String NOTE_LIST = "noteList";
    public static final String NOTE_DETAIL = "noteDetail";
    public static final String SNIPPET_LIST = "snippetList";
    public static final String TASK_LIST = "taskList";
    public static final String INBOX_SUMMARY = "inboxSummary";
    public static final String PERSONAL_INSIGHTS = "personalInsights";
    public static final String EMAIL_CONFIG_LIST = "emailConfigList";

    /**
     * 配置 Caffeine 缓存管理器
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(List.of(
                USER_ACCOUNT_BY_ID,
                USER_ACCOUNT_BY_USERNAME,
                USER_ACCOUNT_BY_EMAIL,
                USER_ACCOUNT_BY_LOGIN,
                MODEL_LIST,
                MODEL_DETAIL,
                MODEL_PROVIDERS,
                SKILL_LIST_ALL,
                SKILL_LIST_ENABLED,
                SKILL_LIST_BUILTIN,
                SKILL_CATEGORIES,
                SKILL_LIST_BY_CATEGORY,
                SKILL_BY_ID,
                SKILL_BY_CODE,
                SETTINGS_ALL,
                SETTINGS_BY_CATEGORY,
                SETTINGS_BY_KEY,
                KNOWLEDGE_LIST,
                KNOWLEDGE_DETAIL,
                NOTE_LIST,
                NOTE_DETAIL,
                SNIPPET_LIST,
                TASK_LIST,
                INBOX_SUMMARY,
                PERSONAL_INSIGHTS,
                EMAIL_CONFIG_LIST
        ));
        cacheManager.setAllowNullValues(false);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(30))
                .recordStats());
        return cacheManager;
    }
}
