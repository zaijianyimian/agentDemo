package com.example.demo.email.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.email.domain.EmailConfig;
import com.example.demo.email.persistence.EmailConfigMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
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
    private final TimeWheel timeWheel = new TimeWheel(TICK_MILLIS, WHEEL_SIZE);
    private final Map<Long, List<WheelTask>> scheduledTasks = new ConcurrentHashMap<>();
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    @Value("${app.email.listener.enabled:true}")
    private boolean listenerEnabled;

    public EmailListenerScheduleService(
            EmailConfigMapper emailConfigMapper,
            EmailAuthConfigService emailAuthConfigService,
            EmailListenerService emailListenerService) {
        this.emailConfigMapper = emailConfigMapper;
        this.emailAuthConfigService = emailAuthConfigService;
        this.emailListenerService = emailListenerService;
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
     * 清空所有时间轮任务并停止现有监听，然后从数据库重新拉取启用的配置重新调度。
     * 适用于配置热更新或批量改完后的全量重载。
     */
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
     * 立即停止监听并取消该邮箱所有未触发的调度任务（启动/停止事件）。
     * 删除配置时使用，避免遗留任务指向已删除的邮箱。
     */
    public void stopAndUnschedule(Long configId) {
        cancelSchedule(configId);
        emailListenerService.stopListener(configId);
    }

    /**
     * 透传各邮箱的连接状态；具体字段含义见 {@link EmailListenerService#getListenerStatus}。
     */
    public Map<Long, Map<String, Object>> getListenerStatus() {
        return emailListenerService.getListenerStatus();
    }

    // ==================== 调度逻辑 ====================

    /**
     * 安排一次「启动监听」事件，距离 {@code listenStartTime} 还有多少时间就多久触发。
     * 触发时会重新加载最新配置，避免使用已变更的过期对象。
     */
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

    /**
     * 安排一次「停止监听」事件，到 {@code listenEndTime} 时触发。
     * 触发后立即安排下一次启动事件，形成循环。
     */
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

    /**
     * 重新加载最新配置；配置已删除或被禁用时返回 null，调用方应取消后续调度。
     */
    private EmailConfig loadEnabledConfig(Long configId) {
        EmailConfig latest = emailConfigMapper.selectById(configId);
        if (latest == null || !Boolean.TRUE.equals(latest.getEnabled())) {
            return null;
        }
        return latest;
    }

    /**
     * 把任务挂到指定 configId 的任务列表中；已 done 的旧任务自动清理，避免内存泄漏。
     *
     * <p>列表本身用 {@link CopyOnWriteArrayList} 包装，多线程并发 track 不会破坏内部结构；
     * {@link #cancelSchedule(Long)} 删除 map entry 仍由 ConcurrentHashMap 保证原子。</p>
     */
    private void trackTask(Long configId, WheelTask task) {
        List<WheelTask> tasks = scheduledTasks.computeIfAbsent(configId,
                ignored -> new CopyOnWriteArrayList<>());
        tasks.removeIf(WheelTask::isDone);
        tasks.add(task);
    }

    /**
     * 取消指定邮箱在时间轮上挂着的所有未触发任务并清空任务列表。
     */
    private void cancelSchedule(Long configId) {
        List<WheelTask> tasks = scheduledTasks.remove(configId);
        if (tasks == null) {
            return;
        }
        tasks.forEach(WheelTask::cancel);
    }

    /**
     * 取消所有邮箱的调度任务；在重载监听前调用，避免新旧任务重叠。
     */
    private void cancelAllSchedules() {
        List<Long> configIds = new ArrayList<>(scheduledTasks.keySet());
        configIds.forEach(this::cancelSchedule);
    }

    // ==================== 时间计算与判定 ====================

    /**
     * 是否为全天监听（开始/结束时间为空或两者相等）。
     */
    private boolean isAlwaysOn(EmailConfig config) {
        return config.getListenStartTime() == null
                || config.getListenEndTime() == null
                || config.getListenStartTime().equals(config.getListenEndTime());
    }

    /**
     * 当前时间是否落在监听窗口内；支持跨夜窗口（{@code start > end}）。
     */
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

    /**
     * 计算距离目标时间（今日或次日）的延迟，target 已过则推迟到明日同一时刻。
     */
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

    /**
     * 单线程时间轮。
     * <p>刻度 1 秒、轮盘 3600 槽，可调度最长约 1 小时内的任务；超过 1 小时的任务通过
     * {@link WheelTask#remainingRounds} 多轮回绕实现。每个槽位用 {@link java.util.concurrent.ConcurrentHashMap#newKeySet()}
     * 保证任务增删的线程安全。</p>
     */
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

        /**
         * 启动时间轮工作线程（守护线程），幂等。
         */
        void start() {
            if (running.compareAndSet(false, true)) {
                worker.start();
            }
        }

        /**
         * 注册一个延迟任务到时间轮；最小延迟为 1 个 tick。
         * @return 可取消的任务句柄
         */
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

        /**
         * 停止工作线程；调用后不会再触发任何已注册任务。
         */
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

        /**
         * 推进一个 tick，触发到期任务。
         * 同步块内只做「摘取到期任务」工作，Runnable 执行放在锁外避免长任务阻塞其他邮箱的调度。
         */
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

    /**
     * 时间轮任务句柄。线程安全：{@link #cancel} 可在任意线程调用。
     */
    private static class WheelTask {
        private final Runnable action;
        private volatile boolean cancelled = false;
        private int slotIndex;
        private long remainingRounds;

        WheelTask(Runnable action) {
            this.action = action;
        }

        /**
         * 取消任务。已触发或已取消的任务再次调用无副作用。
         */
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
