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

    /**
     * 获取或新建对应邮箱的监听状态记录。
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
     */
    public MailCursor cursorFor(EmailConfig config) {
        EmailListenerState state = getOrCreate(config);
        return new MailCursor(state.getCursorType(), state.getCursorValue());
    }

    /**
     * 更新邮箱的增量游标，同时同步 provider / listenMode 等派生字段并写入最后成功时间。
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
     * 更新 Webhook 订阅信息：订阅 ID、过期时间与资源路径。
     */
    public void updateSubscription(EmailConfig config, String subscriptionId, LocalDateTime expireTime, String webhookResource) {
        EmailListenerState state = getOrCreate(config);
        state.setSubscriptionId(subscriptionId);
        state.setSubscriptionExpireTime(expireTime);
        state.setWebhookResource(webhookResource);
        state.setLastSuccessTime(LocalDateTime.now());
        mapper.updateById(state);
    }

    /**
     * 记录邮箱的运行状态；异常时会刷新最后错误时间与错误信息。
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
     * <p>并发安全：每个 {@code configId} 在同一时刻只有一个线程能进入临界区，
     * 避免两个 listener 同时读旧 state、各自 add key 后互相覆盖导致去重逻辑被绕过。</p>
     *
     * @return true 表示首次见到该 key；false 表示重复
     */
    public boolean rememberMessageKey(EmailConfig config, MailMessageKey key) {
        if (config == null || key == null || !StringUtils.hasText(key.stableKey())) {
            return false;
        }
        // 按 configId 串行化，确保并发 listener 不会读到同一个旧 state 后互相覆盖
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

    private final Map<Long, Object> configLocks = new ConcurrentHashMap<>();
    private static final Object NO_LOCK = new Object();

    /**
     * 为指定 configId 获取（或惰性创建）一个进程内锁对象，
     * 让同邮箱的并发 listener 在同一时刻只有一个能进入临界区。
     */
    private Object lockFor(Long configId) {
        if (configId == null) {
            return NO_LOCK;
        }
        return configLocks.computeIfAbsent(configId, k -> new Object());
    }

    /**
     * 列出全部邮箱的监听状态，供状态面板展示使用。
     */
    public List<EmailListenerState> listAll() {
        return mapper.selectList(null);
    }

    private Set<String> readRecentKeys(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> values = objectMapper.readValue(raw, new TypeReference<>() {});
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
