package com.example.demo.infrastructure.config;

import com.example.demo.infrastructure.security.CurrentUserProvider;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Caffeine 缓存配置。
 *
 * <p>默认 KeyGenerator 会把当前 userId 放进缓存键，避免模型、Skill、知识库、Note 等用户数据
 * 因相同方法参数命中另一个用户的缓存。系统后台无用户上下文时使用 {@code system} scope。</p>
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

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

    private final CurrentUserProvider currentUserProvider;

    public CacheConfig(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    /** 配置 Caffeine 缓存管理器。 */
    @Bean
    @Override
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

    /**
     * 默认缓存键增加用户 scope。
     *
     * @return 租户感知 KeyGenerator。
     */
    @Bean
    @Override
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            String scope = currentUserProvider.currentUserId()
                    .map(id -> "user:" + id)
                    .orElse("system");
            Object methodKey = SimpleKeyGenerator.generateKey(params);
            return scope + ":" + method.getDeclaringClass().getName() + ":" + method.getName() + ":" + methodKey;
        };
    }
}
