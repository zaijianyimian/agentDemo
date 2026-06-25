package com.example.demo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

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
                SETTINGS_BY_KEY
        ));
        cacheManager.setAllowNullValues(false);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .recordStats());
        return cacheManager;
    }
}
