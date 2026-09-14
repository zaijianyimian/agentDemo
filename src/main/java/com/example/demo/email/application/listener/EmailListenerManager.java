package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailAuthConfigService;
import com.example.demo.email.application.EmailListenerConfigSupport;
import com.example.demo.email.application.EmailListenerStateService;
import com.example.demo.email.application.listener.strategy.ListenStrategy;
import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailListenerState;
import com.example.demo.email.domain.OwnedEmailConfigRef;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.ListenerStatus;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.persistence.EmailConfigMapper;
import com.example.demo.shared.context.CurrentUserContext;
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
    private final EmailConfigMapper emailConfigMapper;
    private final CurrentUserContext currentUser;

    private final Map<OwnedEmailConfigRef, ListenStrategy> runningStrategies = new ConcurrentHashMap<>();
    private final Map<OwnedEmailConfigRef, EmailConfig> runningConfigs = new ConcurrentHashMap<>();

    /**
     * 启动指定邮箱的监听：选择 provider 适配器 + 监听策略并替换已有的同邮箱会话。
     *
     * @param config 邮箱配置。
     */
    public void start(EmailConfig config) {
        OwnedEmailConfigRef reference = requireCurrentOwner(config);
        authConfigService.decodeTransientFields(config);
        configSupport.applyDefaults(config);
        stop(reference);

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
        runningStrategies.put(reference, strategy);
        runningConfigs.put(reference, config);
    }

    /**
     * 停止指定邮箱监听并更新状态。
     *
     * @param configId 邮箱配置 ID。
     */
    public void stop(EmailConfig config) {
        stop(requireCurrentOwner(config));
    }

    private void stop(OwnedEmailConfigRef reference) {
        ListenStrategy strategy = runningStrategies.remove(reference);
        if (strategy != null) {
            strategy.stop(reference.configId());
        }
        EmailConfig config = runningConfigs.remove(reference);
        if (config != null) {
            stateService.markStatus(config, ListenerStatus.STOPPED, null);
        }
    }

    /** Lifecycle-only global stop; never expose this method through a user controller. */
    public void stopAllForLifecycleInternal() {
        for (OwnedEmailConfigRef reference : List.copyOf(runningStrategies.keySet())) {
            stop(reference);
        }
    }

    public void stopForLifecycleInternal(OwnedEmailConfigRef reference) {
        stop(reference);
    }

    /**
     * 汇总当前请求可见邮箱的运行状态。
     *
     * <p>状态表本身没有 user_id，因此不能直接把 {@link EmailListenerStateService#listAll()} 的结果返回。
     * 这里重新通过 {@code email_config} 查询配置；HTTP 请求场景会被 MyBatis TenantLine 自动按当前 JWT
     * 用户过滤，后台线程无认证上下文时则仍可看到全部状态。</p>
     *
     * @return 当前调用方可见的邮箱状态。
     */
    public Map<Long, Map<String, Object>> status() {
        long ownerId = currentUser.requireUserId();
        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (EmailListenerState state : stateService.listAll()) {
            EmailConfig config = emailConfigMapper.selectById(state.getConfigId());
            if (config == null || !Long.valueOf(ownerId).equals(config.getUserId())) {
                continue;
            }

            OwnedEmailConfigRef reference = new OwnedEmailConfigRef(ownerId, state.getConfigId());

            Map<String, Object> item = new HashMap<>();
            item.put("connected", runningStrategies.containsKey(reference));
            item.put("status", state.getStatus());
            item.put("provider", state.getProvider());
            item.put("listenMode", state.getListenMode());
            item.put("fallbackListenMode", state.getFallbackListenMode());
            item.put("lastSuccessTime", state.getLastSuccessTime());
            item.put("lastErrorTime", state.getLastErrorTime());
            item.put("lastError", state.getLastError());
            item.put("email", config.getEmail());
            item.put("host", config.getHost());
            result.put(state.getConfigId(), item);
        }
        return result;
    }

    /**
     * 把 provider webhook 回调转交给对应邮箱的 webhook 策略处理。
     *
     * @param configId 邮箱配置 ID。
     * @param payload Webhook 请求体。
     */
    public void handleWebhook(EmailConfig requestedConfig, Map<String, Object> payload) {
        OwnedEmailConfigRef reference = requireCurrentOwner(requestedConfig);
        EmailConfig config = runningConfigs.get(reference);
        if (config == null) {
            throw new IllegalArgumentException("邮箱监听未运行: " + reference.configId());
        }
        MailSourceAdapter adapter = adapterRegistry.get(MailProvider.fromConfig(config));
        ListenStrategy strategy = strategyRegistry.get(ListenMode.WEBHOOK);
        strategy.handleWebhook(config, adapter, payload);
    }

    private OwnedEmailConfigRef requireCurrentOwner(EmailConfig config) {
        if (config == null || config.getId() == null || config.getUserId() == null) {
            throw new IllegalArgumentException("邮箱配置缺少可信 owner");
        }
        long ownerId = currentUser.requireUserId();
        if (ownerId != config.getUserId()) {
            throw new IllegalArgumentException("邮箱配置 owner 与执行上下文不匹配");
        }
        return new OwnedEmailConfigRef(ownerId, config.getId());
    }
}
