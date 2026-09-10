package com.example.demo.email.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.EmailListenerState;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.ListenerStatus;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailMessageKey;
import com.example.demo.email.domain.listener.MailProvider;
import com.example.demo.email.persistence.EmailListenerStateMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱监听状态服务。
 * 维护每个邮箱的运行状态、增量游标、订阅信息以及最近处理过的消息 key，用于断点续传与去重。
 */
@Service
@RequiredArgsConstructor
public class EmailListenerStateService {

    private static final int MAX_RECENT_MESSAGE_KEYS = 500;

    private final EmailListenerStateMapper mapper;
    private final ObjectMapper objectMapper;
    private final Map<Long, Object> configLocks = new ConcurrentHashMap<>();
    private static final Object NO_LOCK = new Object();

    /**
     * 获取或新建对应邮箱的监听状态记录。
     *
     * @param config 邮箱配置。
     * @return 监听状态。
     */
    public EmailListenerState getOrCreate(EmailConfig config) {
        EmailListenerState existing = findByConfigId(config.getId());
        if (existing != null) {
            return existing;
        }
        EmailListenerState created = EmailListenerState.builder()
                .configId(config.getId())
                .provider(MailProvider.fromConfig(config).name())
                .listenMode(ListenMode.fromConfig(config).name())
                .fallbackListenMode(config.getFallbackListenMode())
                .status(ListenerStatus.STOPPED.name())
                .build();
        mapper.insert(created);
        return created;
    }

    /**
     * 根据邮箱配置 ID 查找监听状态；不存在或参数为空时返回 null。
     *
     * @param configId 邮箱配置 ID。
     * @return 监听状态或 null。
     */
    public EmailListenerState findByConfigId(Long configId) {
        if (configId == null) {
            return null;
        }
        return mapper.selectOne(new LambdaQueryWrapper<EmailListenerState>()
                .eq(EmailListenerState::getConfigId, configId)
                .last("LIMIT 1"));
    }

    /**
     * 读取当前邮箱的增量游标，必要时自动创建状态记录。
     *
     * @param config 邮箱配置。
     * @return 当前游标。
     */
    public MailCursor cursorFor(EmailConfig config) {
        EmailListenerState state = getOrCreate(config);
        return new MailCursor(state.getCursorType(), state.getCursorValue());
    }

    /**
     * 更新邮箱的增量游标，同时同步 provider / listenMode 等派生字段并写入最后成功时间。
     *
     * @param config 邮箱配置。
     * @param cursor 新游标。
     */
    public void updateCursor(EmailConfig config, MailCursor cursor) {
        if (config == null || cursor == null) {
            return;
        }
        EmailListenerState state = getOrCreate(config);
        state.setProvider(MailProvider.fromConfig(config).name());
        state.setListenMode(ListenMode.fromConfig(config).name());
        state.setFallbackListenMode(config.getFallbackListenMode());
        state.setCursorType(cursor.type());
        state.setCursorValue(cursor.value());
        state.setStatus(ListenerStatus.RUNNING.name());
        state.setLastSuccessTime(LocalDateTime.now());
        mapper.updateById(state);
    }

    /**
     * 更新 Webhook 订阅信息。
     *
     * @param config 邮箱配置。
     * @param subscriptionId 订阅 ID。
     * @param expireTime 过期时间。
     * @param webhookResource Webhook 资源。
     */
    public void updateSubscription(
            EmailConfig config,
            String subscriptionId,
            LocalDateTime expireTime,
            String webhookResource) {
        EmailListenerState state = getOrCreate(config);
        state.setSubscriptionId(subscriptionId);
        state.setSubscriptionExpireTime(expireTime);
        state.setWebhookResource(webhookResource);
        state.setLastSuccessTime(LocalDateTime.now());
        mapper.updateById(state);
    }

    /**
     * 记录邮箱运行状态。
     *
     * @param config 邮箱配置。
     * @param status 监听状态。
     * @param error 错误信息。
     */
    public void markStatus(EmailConfig config, ListenerStatus status, String error) {
        if (config == null) {
            return;
        }
        EmailListenerState state = getOrCreate(config);
        state.setProvider(MailProvider.fromConfig(config).name());
        state.setListenMode(ListenMode.fromConfig(config).name());
        state.setFallbackListenMode(config.getFallbackListenMode());
        state.setStatus(status.name());
        if (status == ListenerStatus.ERROR || StringUtils.hasText(error)) {
            state.setLastErrorTime(LocalDateTime.now());
            state.setLastError(error);
        } else {
            state.setLastSuccessTime(LocalDateTime.now());
            state.setLastError(null);
        }
        mapper.updateById(state);
    }

    /**
     * 把已处理的邮件 key 记入最近窗口，用于跨重启去重。
     *
     * <p>并发安全：每个 {@code configId} 在同一时刻只有一个线程能进入临界区，避免并发覆盖。</p>
     *
     * @param config 邮箱配置。
     * @param key 邮件去重键。
     * @return true 表示首次见到该 key；false 表示重复。
     */
    public boolean rememberMessageKey(EmailConfig config, MailMessageKey key) {
        if (config == null || key == null || !StringUtils.hasText(key.stableKey())) {
            return false;
        }
        synchronized (lockFor(config.getId())) {
            EmailListenerState state = getOrCreate(config);
            Set<String> keys = readRecentKeys(state.getRecentMessageKeys());
            boolean added = keys.add(key.stableKey());
            if (!added) {
                return false;
            }
            while (keys.size() > MAX_RECENT_MESSAGE_KEYS) {
                String first = keys.iterator().next();
                keys.remove(first);
            }
            state.setRecentMessageKeys(writeRecentKeys(keys));
            mapper.updateById(state);
            return true;
        }
    }

    /**
     * 撤销一个已记录的邮件 key。
     *
     * <p>用于 Graph 投递失败补偿：邮件在进入事件总线前已经记录去重 key，如果 Python 暂时不可用，
     * 必须删除该 key 才能让下一次轮询重新尝试。</p>
     *
     * @param configId 邮箱配置 ID。
     * @param stableKey 已记录的稳定 key。
     */
    public void forgetMessageKey(Long configId, String stableKey) {
        if (configId == null || !StringUtils.hasText(stableKey)) {
            return;
        }
        synchronized (lockFor(configId)) {
            EmailListenerState state = findByConfigId(configId);
            if (state == null) {
                return;
            }
            Set<String> keys = readRecentKeys(state.getRecentMessageKeys());
            if (!keys.remove(stableKey)) {
                return;
            }
            state.setRecentMessageKeys(writeRecentKeys(keys));
            mapper.updateById(state);
        }
    }

    /**
     * 列出全部邮箱监听状态。
     *
     * @return 监听状态列表。
     */
    public List<EmailListenerState> listAll() {
        return mapper.selectList(null);
    }

    private Object lockFor(Long configId) {
        if (configId == null) {
            return NO_LOCK;
        }
        return configLocks.computeIfAbsent(configId, ignored -> new Object());
    }

    private Set<String> readRecentKeys(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> values = objectMapper.readValue(raw, new TypeReference<>() {
            });
            return new LinkedHashSet<>(values);
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    private String writeRecentKeys(Set<String> keys) {
        try {
            return objectMapper.writeValueAsString(keys);
        } catch (Exception e) {
            throw new IllegalStateException("保存邮件去重状态失败", e);
        }
    }
}
