package com.example.demo.email.application.listener;

import com.example.demo.email.application.EmailListenerStateService;
import com.example.demo.email.application.listener.strategy.ListenStrategy;
import com.example.demo.email.application.listener.strategy.MailSourceAdapter;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.domain.listener.ListenMode;
import com.example.demo.email.domain.listener.ListenerStatus;
import com.sun.mail.imap.IMAPFolder;
import jakarta.mail.Folder;
import jakarta.mail.Store;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * IMAP IDLE 监听策略。
 * 与 IMAP 服务器建立长连接并使用 IDLE 命令等待新邮件通知；服务器不支持时自动降级为 {@link PollingStrategy}。
 */
@Slf4j
@Component
public class ImapIdleStrategy implements ListenStrategy {

    private final JavaMailSupport javaMailSupport;
    private final EmailListenerStateService stateService;
    private final PollingStrategy pollingStrategy;
    private final ExecutorService executorService;
    private final Map<Long, Future<?>> tasks = new ConcurrentHashMap<>();

    public ImapIdleStrategy(
            JavaMailSupport javaMailSupport,
            EmailListenerStateService stateService,
            PollingStrategy pollingStrategy,
            @Qualifier("emailProcessingExecutor") ExecutorService executorService) {
        this.javaMailSupport = javaMailSupport;
        this.stateService = stateService;
        this.pollingStrategy = pollingStrategy;
        this.executorService = executorService;
    }

    @Override
    public ListenMode mode() {
        return ListenMode.IMAP_IDLE;
    }

    @Override
    public void start(EmailConfig config, MailSourceAdapter adapter) {
        stop(config.getId());
        Future<?> task = executorService.submit(() -> idleLoop(config, adapter));
        tasks.put(config.getId(), task);
    }

    @Override
    public void stop(Long configId) {
        Future<?> task = tasks.remove(configId);
        if (task != null) {
            task.cancel(true);
        }
    }

    private void idleLoop(EmailConfig config, MailSourceAdapter adapter) {
        Store store = null;
        Folder folder = null;
        try {
            store = javaMailSupport.connectStore(config);
            folder = javaMailSupport.openFolder(store, config, Folder.READ_WRITE);
            if (!(folder instanceof IMAPFolder imapFolder)) {
                fallback(config, adapter, "服务器 Folder 不支持 IMAP IDLE");
                return;
            }
            stateService.markStatus(config, ListenerStatus.RUNNING, null);
            while (!Thread.currentThread().isInterrupted()) {
                pollingStrategy.pollOnce(config, adapter, "imap-idle-before-wait");
                imapFolder.idle();
                pollingStrategy.pollOnce(config, adapter, "imap-idle");
            }
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                fallback(config, adapter, e.getMessage());
            }
        } finally {
            javaMailSupport.closeQuietly(folder, false);
            javaMailSupport.closeQuietly(store);
        }
    }

    private void fallback(EmailConfig config, MailSourceAdapter adapter, String reason) {
        log.warn("[{}] IMAP IDLE 不可用，降级为轮询: {}", config.getEmail(), reason);
        config.setFallbackListenMode(ListenMode.POLLING.name());
        stateService.markStatus(config, ListenerStatus.FALLBACK, reason);
        pollingStrategy.start(config, adapter);
    }
}
