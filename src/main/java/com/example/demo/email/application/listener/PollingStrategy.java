package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailListenerStateService;
import com.example.demo.email.application.listener.strategy.ListenStrategy;
import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.ListenerStatus;
import com.example.demo.email.domain.listener.MailCursor;
import com.example.demo.email.domain.listener.MailboxMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 轮询监听策略。
 * 按 {@code pollInterval} 周期从 provider 适配器拉取新邮件，使用 {@link EmailListenerStateService} 维护游标与去重。
 */
@Slf4j
@Component
public class PollingStrategy implements ListenStrategy {

    private final EmailListenerStateService stateService;
    private final EmailMessagePublisher publisher;
    private final ExecutorService executorService;
    private final Map<Long, Future<?>> tasks = new ConcurrentHashMap<>();

    public PollingStrategy(
            EmailListenerStateService stateService,
            EmailMessagePublisher publisher,
            @Qualifier("emailProcessingExecutor") ExecutorService executorService) {
        this.stateService = stateService;
        this.publisher = publisher;
        this.executorService = executorService;
    }

    @Override
    public ListenMode mode() {
        return ListenMode.POLLING;
    }

    @Override
    public void start(EmailConfig config, MailSourceAdapter adapter) {
        stop(config.getId());
        Future<?> task = executorService.submit(() -> pollLoop(config, adapter));
        tasks.put(config.getId(), task);
    }

    @Override
    public void stop(Long configId) {
        Future<?> task = tasks.remove(configId);
        if (task != null) {
            task.cancel(true);
        }
    }

    private void pollLoop(EmailConfig config, MailSourceAdapter adapter) {
        int interval = config.getPollInterval() == null ? 600 : Math.max(600, config.getPollInterval());
        stateService.markStatus(config, ListenerStatus.RUNNING, null);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                pollOnce(config, adapter, "poll");
                Thread.sleep(interval * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("[{}] 邮箱轮询失败: {}", config.getEmail(), e.getMessage());
                stateService.markStatus(config, ListenerStatus.ERROR, e.getMessage());
                try {
                    Thread.sleep(Math.min(interval, 30) * 1000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        stateService.markStatus(config, ListenerStatus.STOPPED, null);
    }

    /**
     * 执行一次增量拉取并发布邮件事件。
     *
     * @param config 邮箱配置。
     * @param adapter 邮箱来源适配器。
     * @param trigger 触发来源。
     */
    public void pollOnce(EmailConfig config, MailSourceAdapter adapter, String trigger) {
        if (!isWithinListeningWindow(config)) {
            return;
        }
        MailCursor cursor = stateService.cursorFor(config);
        List<MailboxMessage> messages = adapter.fetchNewMessages(config, cursor);
        MailCursor latestCursor = cursor;
        for (MailboxMessage message : messages) {
            if (message.emailMessage() == null) {
                latestCursor = message.cursorAfter();
                continue;
            }
            if (!stateService.rememberMessageKey(config, message.key())) {
                latestCursor = message.cursorAfter();
                continue;
            }

            // 用户归属和来源信息都来自服务端配置/Adapter，不能由客户端或 Python 自行猜测。
            message.emailMessage().setUserId(config.getUserId());
            message.emailMessage().setEmailConfigId(config.getId());
            message.emailMessage().setProvider(config.getProvider());
            message.emailMessage().setExternalId(message.key().stableKey());
            publisher.publish(message.emailMessage(), trigger);
            adapter.acknowledge(config, message);
            latestCursor = message.cursorAfter();
        }
        if (latestCursor != null) {
            stateService.updateCursor(config, latestCursor);
        } else {
            stateService.markStatus(config, ListenerStatus.RUNNING, null);
        }
    }

    private boolean isWithinListeningWindow(EmailConfig config) {
        LocalTime start = config.getListenStartTime();
        LocalTime end = config.getListenEndTime();
        if (start == null || end == null || start.equals(end)) {
            return true;
        }
        LocalTime now = LocalTime.now();
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }
}
