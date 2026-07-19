package com.example.demo.dispatch.application;

import com.example.demo.system.application.SystemSettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部执行器（claude-code / codex 等）的启用开关管理。
 *
 * <p>配置存储在 {@code system_settings} 表（category="dispatch", config_key="executor.<hint>.enabled"）。
 * 默认值：claude-code=true, codex=true。运行时 {@code Executor.isAvailable()} 调用
 * {@link #isExecutorEnabled(String)}，被禁用时调度器走 fallback（decision-layer-self LLM）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorToggleService {

    /**
     * 系统设置 category 与 key 前缀：settings 落在 {@code dispatch.executor.<hint>.enabled}。
     */
    public static final String CATEGORY = "dispatch";
    public static final String KEY_PREFIX = "executor.";

    /** 默认全启用。增删这里同时改 {@link #defaults()} 与前端的 executors 配置清单。 */
    private static final List<String> DEFAULT_HINTS = List.of(
            "claude-code",
            "codex"
    );

    private final SystemSettingsService systemSettingsService;

    /**
     * 启动时把默认 enabled=true 写入 DB（仅当用户从未配置过）。
     * 保证后续 {@link #isExecutorEnabled(String)} 在 DB 为空时也能返回合理结果。
     */
    @PostConstruct
    void initDefaults() {
        for (String hint : DEFAULT_HINTS) {
            String key = KEY_PREFIX + hint + ".enabled";
            String existing = systemSettingsService.getSetting(CATEGORY, key);
            if (existing == null) {
                systemSettingsService.setSetting(CATEGORY, key, "true");
                log.info("executor default enabled: {}.{}={}", CATEGORY, key, "true");
            }
        }
    }

    /**
     * 查询某个 executor hint 是否启用。未配置按默认 true 处理。
     */
    public boolean isExecutorEnabled(String hint) {
        if (hint == null || hint.isBlank()) {
            return false;
        }
        return systemSettingsService.getBooleanSetting(
                CATEGORY, KEY_PREFIX + hint + ".enabled", true);
    }

    /**
     * 启停某个 executor hint。设置为 false 时 {@link com.example.demo.dispatch.application.executor.Executor#isAvailable()} 返回 false，调度降级到 decision-layer-self。
     */
    public void setExecutorEnabled(String hint, boolean enabled) {
        systemSettingsService.setSetting(
                CATEGORY, KEY_PREFIX + hint + ".enabled", String.valueOf(enabled));
        log.info("executor toggle set: {}.{}{}={}",
                CATEGORY, KEY_PREFIX + hint, ".enabled", enabled);
    }

    /**
     * 一次性返回所有已知 executor 的启用状态快照，供前端 Settings 页面渲染。
     */
    public Map<String, Boolean> snapshot() {
        Map<String, Boolean> snap = new LinkedHashMap<>();
        for (String hint : DEFAULT_HINTS) {
            snap.put(hint, isExecutorEnabled(hint));
        }
        return snap;
    }

    /**
     * 前端一次性保存所有 toggle。
     */
    public void replaceAll(Map<String, Boolean> states) {
        if (states == null) {
            return;
        }
        for (Map.Entry<String, Boolean> e : states.entrySet()) {
            if (DEFAULT_HINTS.contains(e.getKey())) {
                setExecutorEnabled(e.getKey(), Boolean.TRUE.equals(e.getValue()));
            }
        }
    }
}