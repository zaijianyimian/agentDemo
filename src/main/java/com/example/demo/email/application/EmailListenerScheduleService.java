package com.example.demo.email.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.persistence.EmailConfigMapper;
import com.example.demo.infrastructure.security.CurrentUserProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 邮件监听调度服务。
 * 用时间轮统一管理多邮箱的监听开始/停止事件，EmailListenerService 只负责实际连接。
 */
@Slf4j
@Service
public class EmailListenerScheduleService {

    private static final long TICK_MILLIS = 1000L;
    private static final int WHEEL_SIZE = 3600;

    private final EmailConfigMapper emailConfigMapper;
    private final EmailAuthConfigService emailAuthConfigService;
    private final EmailListenerService emailListenerService;
    private final CurrentUserProvider currentUserProvider;
    private final TimeWheel timeWheel = new TimeWheel(TICK_MILLIS, WHEEL_SIZE);
    private final Map<Long, List<WheelTask>> scheduledTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @Value("${app.email.listener.enabled:true}")
    private boolean listenerEnabled;

    public EmailListenerScheduleService(
            EmailConfigMapper emailConfigMapper,
            EmailAuthConfigService emailAuthConfigService,
            EmailListenerService emailListenerService,
            CurrentUserProvider currentUserProvider) {
        this.emailConfigMapper = emailConfigMapper;
        this.emailAuthConfigService = emailAuthConfigService;
        this.emailListenerService = emailListenerService;
        this.currentUserProvider = currentUserProvider;
    }

    // ==================== 启动与重载 ====================

    /**
     * Spring 启动完成后启动时间轮并重载所有启用邮箱的监听。
     * 使用 {@link AtomicBoolean} 保证幂等，事件多次触发也只会执行一次。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!listenerEnabled) {
            log.info("邮件监听启动已通过 app.email.listener.enabled=false 禁用");
            return;
        }
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        timeWheel.start();
        reloadListeners();
    }

    @PreDestroy
    public void destroy() {
        timeWheel.shutdown();
    }

    /**
     * 重载邮箱监听。
     *
     * <p>系统启动线程没有登录态时执行全局重载；HTTP 用户请求有 JWT 时只停止、重建当前用户自己的
     * 邮箱任务，避免一个用户点击“重载”影响其他用户。</p>
     */
    public void reloadListeners() {
        if (currentUserProvider.currentUserId().isPresent()) {
            List<EmailConfig> userConfigs = emailConfigMapper.selectList(null);
            userConfigs.forEach(config -> stopAndUnschedule(config.getId()));
            List<EmailConfig> enabledConfigs = userConfigs.stream()
                    .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                    .toList();
            log.info("当前用户重载 {} 个启用邮箱配置", enabledConfigs.size());
            enabledConfigs.forEach(this::scheduleEnabledListener);
            return;
        }

        cancelAllSchedules();
        emailListenerService.stopAllListeners();
        List<EmailConfig> configs = emailConfigMapper.selectList(
                new LambdaQueryWrapper<EmailConfig>()
                        .eq(EmailConfig::getEnabled, true)
        );
        log.info("系统全局加载 {} 个启用邮箱配置，交给时间轮调度", configs.size());
        configs.forEach(this::scheduleEnabledListener);
    }

    /**
     * 单个邮箱的调度入口。
     *
     * <p>分支逻辑：</p>
     * <ul>
     *   <li>未启用 → 立即停止监听</li>
     *   <li>全天监听（{@code listenStartTime == listenEndTime} 或任一为 null）→ 立即启动</li>
     *   <li>当前在监听窗口内 → 立即启动并安排停止事件</li>
     *   <li>当前不在窗口内 → 停止监听并安排启动事件</li>
     * </ul>
     *
     * @param config 邮箱配置。
     */
    public void scheduleEnabledListener(EmailConfig config) {
        if (config == null || config.getId() == null) {
            return;
        }
        emailAuthConfigService.decodeTransientFields(config);
        cancelSchedule(config.getId());

        if (!Boolean.TRUE.equals(config.getEnabled())) {
            emailListenerService.stopListener(config.getId());
            return;
        }

        if (isAlwaysOn(config)) {
            emailListenerService.startListener(config);
            log.info("[{}] 已设置为全天监听", config.getEmail());
            return;
        }

        if (isWithinListeningWindow(config)) {
            emailListenerService.startListener(config);
            scheduleStop(config);
            log.info("[{}] 当前在监听时间段内，已启动并安排结束事件", config.getEmail());
        } else {
            emailListenerService.stopListener(config.getId());
            scheduleStart(config);
            log.info("[{}] 当前不在监听时间段内，已安排下一次开始事件", config.getEmail());
        }
    }

    /**
     * 立即停止监听并取消该邮箱所有未触发的调度任务。
     *
     * @param configId 邮箱配置 ID。
     */
    public void stopAndUnschedule(Long configId) {
        cancelSchedule(configId);
        emailListenerService.stopListener(configId);
    }

    /**
     * 获取当前调用方可见邮箱的监听状态。
     *
     * @return 状态映射。
     */
    public Map<Long, Map<String, Object>> getListenerStatus() {
        return emailListenerService.getListenerStatus();
    }

    // ==================== 调度逻辑 ====================

    private void scheduleStart(EmailConfig config) {
        Duration delay = delayUntil(config.getListenStartTime());
        WheelTask task = timeWheel.schedule(delay, () -> {
            EmailConfig latest = loadEnabledConfig(config.getId());
            if (latest == null) {
                cancelSchedule(config.getId());
                return;
            }
            emailAuthConfigService.decodeTransientFields(latest);
            emailListenerService.startListener(latest);
            scheduleStop(latest);
            log.info("[{}] 时间轮触发开始监听", latest.getEmail());
        });
        trackTask(config.getId(), task);
    }

    private void scheduleStop(EmailConfig config) {
        Duration delay = delayUntil(config.getListenEndTime());
        WheelTask task = timeWheel.schedule(delay, () -> {
            emailListenerService.stopListener(config.getId());
            EmailConfig latest = loadEnabledConfig(config.getId());
            if (latest == null) {
                cancelSchedule(config.getId());
                return;
            }
            scheduleStart(latest);
            log.info("[{}] 时间轮触发停止监听", latest.getEmail());
        });
        trackTask(config.getId(), task);
    }

    private EmailConfig loadEnabledConfig(Long configId) {
        EmailConfig latest = emailConfigMapper.selectById(configId);
        if (latest == null || !Boolean.TRUE.equals(latest.getEnabled())) {
            return null;
        }
        return latest;
    }

    private void trackTask(Long configId, WheelTask task) {
        List<WheelTask> tasks = scheduledTasks.computeIfAbsent(configId,
                ignored -> new CopyOnWriteArrayList<>());
        tasks.removeIf(WheelTask::isDone);
        tasks.add(task);
    }

    private void cancelSchedule(Long configId) {
        List<WheelTask> tasks = scheduledTasks.remove(configId);
        if (tasks == null) {
            return;
        }
        tasks.forEach(WheelTask::cancel);
    }

    private void cancelAllSchedules() {
        List<Long> configIds = new ArrayList<>(scheduledTasks.keySet());
        configIds.forEach(this::cancelSchedule);
    }

    // ==================== 时间计算与判定 ====================

    private boolean isAlwaysOn(EmailConfig config) {
        return config.getListenStartTime() == null
                || config.getListenEndTime() == null
                || config.getListenStartTime().equals(config.getListenEndTime());
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

    private Duration delayUntil(LocalTime target) {
        if (target == null) {
            return Duration.ZERO;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = LocalDateTime.of(LocalDate.now(), target);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Duration.between(now, next);
    }

    // ==================== 内部类 ====================

    /** 单线程时间轮。 */
    private static class TimeWheel {
        private final long tickMillis;
        private final int wheelSize;
        private final List<Set<WheelTask>> slots;
        private final Thread worker;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private long tick = 0L;

        TimeWheel(long tickMillis, int wheelSize) {
            this.tickMillis = tickMillis;
            this.wheelSize = wheelSize;
            this.slots = new ArrayList<>(wheelSize);
            for (int i = 0; i < wheelSize; i++) {
                this.slots.add(ConcurrentHashMap.newKeySet());
            }
            this.worker = new Thread(this::run, "email-listener-time-wheel");
            this.worker.setDaemon(true);
        }

        void start() {
            if (running.compareAndSet(false, true)) {
                worker.start();
            }
        }

        WheelTask schedule(Duration delay, Runnable action) {
            long delayMillis = Math.max(delay.toMillis(), tickMillis);
            long ticks = Math.max(1L, (delayMillis + tickMillis - 1) / tickMillis);
            WheelTask task = new WheelTask(action);
            synchronized (this) {
                long targetTick = tick + ticks;
                task.remainingRounds = (ticks - 1) / wheelSize;
                task.slotIndex = (int) (targetTick % wheelSize);
                slots.get(task.slotIndex).add(task);
            }
            return task;
        }

        void shutdown() {
            running.set(false);
            worker.interrupt();
        }

        private void run() {
            while (running.get()) {
                try {
                    Thread.sleep(tickMillis);
                    advance();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("邮件监听时间轮执行异常: {}", e.getMessage());
                }
            }
        }

        private void advance() {
            Set<WheelTask> dueTasks = new HashSet<>();
            synchronized (this) {
                tick++;
                int slotIndex = (int) (tick % wheelSize);
                Set<WheelTask> slot = slots.get(slotIndex);
                for (WheelTask task : new ArrayList<>(slot)) {
                    if (task.cancelled) {
                        slot.remove(task);
                    } else if (task.remainingRounds > 0) {
                        task.remainingRounds--;
                    } else {
                        slot.remove(task);
                        dueTasks.add(task);
                    }
                }
            }
            dueTasks.forEach(WheelTask::run);
        }
    }

    /** 时间轮任务句柄。 */
    private static class WheelTask {
        private final Runnable action;
        private volatile boolean cancelled = false;
        private int slotIndex;
        private long remainingRounds;

        WheelTask(Runnable action) {
            this.action = action;
        }

        void cancel() {
            this.cancelled = true;
        }

        void run() {
            if (!cancelled) {
                try {
                    action.run();
                } finally {
                    cancelled = true;
                }
            }
        }

        boolean isDone() {
            return cancelled;
        }
    }
}
