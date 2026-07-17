package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailAuthConfigService;
import com.example.demo.email.application.EmailListenerConfigSupport;
import com.example.demo.email.application.EmailListenerStateService;
import com.example.demo.email.application.listener.strategy.ListenStrategy;
import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailListenerState;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.ListenerStatus;
import com.example.demo.email.domain.listener.MailProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱监听管理器。
 * 通过 {@link MailSourceAdapterRegistry} 选 provider 适配器、通过 {@link ListenStrategyRegistry} 选监听策略，
 * 统一管理每个邮箱的启动、停止、状态以及 webhook 入口。
 */
@Service
@RequiredArgsConstructor
public class EmailListenerManager {

    private final EmailAuthConfigService authConfigService;
    private final EmailListenerConfigSupport configSupport;
    private final EmailListenerStateService stateService;
    private final MailSourceAdapterRegistry adapterRegistry;
    private final ListenStrategyRegistry strategyRegistry;

    private final Map<Long, ListenStrategy> runningStrategies = new ConcurrentHashMap<>();
    private final Map<Long, EmailConfig> runningConfigs = new ConcurrentHashMap<>();

    /**
     * 启动指定邮箱的监听：选择 provider 适配器 + 监听策略并替换已有的同邮箱会话。
     */
    public void start(EmailConfig config) {
        authConfigService.decodeTransientFields(config);
        configSupport.applyDefaults(config);
        stop(config.getId());

        MailProvider provider = MailProvider.fromConfig(config);
        ListenMode mode = ListenMode.fromConfig(config);
        MailSourceAdapter adapter = adapterRegistry.get(provider);
        if (!adapter.supportsListenMode(mode)) {
            String message = "邮箱提供商 " + provider + " 不支持监听模式 " + mode;
            stateService.markStatus(config, ListenerStatus.ERROR, message);
            throw new IllegalArgumentException(message);
        }

        ListenStrategy strategy = strategyRegistry.get(mode);
        stateService.markStatus(config, ListenerStatus.STARTING, null);
        strategy.start(config, adapter);
        runningStrategies.put(config.getId(), strategy);
        runningConfigs.put(config.getId(), config);
    }

    /**
     * 停止指定邮箱的监听并把状态置为已停止。
     */
    public void stop(Long configId) {
        ListenStrategy strategy = runningStrategies.remove(configId);
        if (strategy != null) {
            strategy.stop(configId);
        }
        EmailConfig config = runningConfigs.remove(configId);
        if (config != null) {
            stateService.markStatus(config, ListenerStatus.STOPPED, null);
        }
    }

    /**
     * 停止所有邮箱的监听。
     */
    public void stopAll() {
        for (Long configId : List.copyOf(runningStrategies.keySet())) {
            stop(configId);
        }
    }

    /**
     * 汇总所有邮箱的连接状态、运行状态、最近成功/错误时间等，供管理面板展示。
     */
    public Map<Long, Map<String, Object>> status() {
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (EmailListenerState state : stateService.listAll()) {
            Map<String, Object> item = new HashMap<>();
            EmailConfig config = runningConfigs.get(state.getConfigId());
            item.put("connected", runningStrategies.containsKey(state.getConfigId()));
            item.put("status", state.getStatus());
            item.put("provider", state.getProvider());
            item.put("listenMode", state.getListenMode());
            item.put("fallbackListenMode", state.getFallbackListenMode());
            item.put("lastSuccessTime", state.getLastSuccessTime());
            item.put("lastErrorTime", state.getLastErrorTime());
            item.put("lastError", state.getLastError());
            item.put("email", config == null ? "" : config.getEmail());
            item.put("host", config == null ? "" : config.getHost());
            result.put(state.getConfigId(), item);
        }
        return result;
    }

    /**
     * 把 provider webhook 回调转交给对应邮箱的 webhook 策略处理。
     */
    public void handleWebhook(Long configId, Map<String, Object> payload) {
        EmailConfig config = runningConfigs.get(configId);
        if (config == null) {
            throw new IllegalArgumentException("邮箱监听未运行: " + configId);
        }
        MailSourceAdapter adapter = adapterRegistry.get(MailProvider.fromConfig(config));
        ListenStrategy strategy = strategyRegistry.get(ListenMode.WEBHOOK);
        strategy.handleWebhook(config, adapter, payload);
    }
}
