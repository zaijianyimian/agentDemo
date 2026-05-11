package com.example.demo.service.email;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.entity.EmailConfig;
import com.example.demo.mapper.EmailConfigMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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
    private final TimeWheel timeWheel = new TimeWheel(TICK_MILLIS, WHEEL_SIZE);
    private final Map<Long, List<WheelTask>> scheduledTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public EmailListenerScheduleService(
            EmailConfigMapper emailConfigMapper,
            EmailAuthConfigService emailAuthConfigService,
            EmailListenerService emailListenerService) {
        this.emailConfigMapper = emailConfigMapper;
        this.emailAuthConfigService = emailAuthConfigService;
        this.emailListenerService = emailListenerService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
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

    public void reloadListeners() {
        cancelAllSchedules();
        emailListenerService.stopAllListeners();

        List<EmailConfig> configs = emailConfigMapper.selectList(
                new LambdaQueryWrapper<EmailConfig>()
                        .eq(EmailConfig::getEnabled, true)
        );
        log.info("找到 {} 个启用的邮箱配置，交给时间轮调度", configs.size());
        configs.forEach(this::scheduleEnabledListener);
    }

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

    public void stopAndUnschedule(Long configId) {
        cancelSchedule(configId);
        emailListenerService.stopListener(configId);
    }

    public Map<Long, Map<String, Object>> getListenerStatus() {
        return emailListenerService.getListenerStatus();
    }

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
        List<WheelTask> tasks = scheduledTasks.computeIfAbsent(configId, ignored -> new ArrayList<>());
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
