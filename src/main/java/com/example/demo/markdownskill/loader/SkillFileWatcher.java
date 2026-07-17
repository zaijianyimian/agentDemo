package com.example.demo.markdownskill.loader;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * 基于 JDK {@link WatchService} 的 skills 目录监听器。
 *
 * <p>行为约定：</p>
 * <ul>
 *   <li>监听根目录的 {@code ENTRY_CREATE} / {@code ENTRY_MODIFY} / {@code ENTRY_DELETE}；</li>
 *   <li>事件触发后调用回调函数，由 loader 决定如何处理（全量 reload）；</li>
 *   <li>文件频繁修改时做去抖（{@code OVERFLOW} 事件立即触发兜底）；</li>
 *   <li>后台守护线程运行，{@link #stop()} 时优雅关闭。</li>
 * </ul>
 */
@Slf4j
class SkillFileWatcher {

    /**
     * 监听根目录。
     */
    private final Path root;

    /**
     * 文件变更回调（接收被变更的路径）。
     */
    private final Consumer<Path> onChange;

    /**
     * JDK WatchService 实例。
     */
    private WatchService watchService;

    /**
     * 后台监听线程。
     */
    private Thread thread;

    /**
     * 运行标志位，用于优雅停止。
     */
    private volatile boolean running = true;

    /**
     * 构造监听器。
     *
     * @param root     要监听的目录（必须存在）
     * @param onChange 文件变更回调
     * @throws IOException 注册 WatchKey 失败时抛出
     */
    SkillFileWatcher(Path root, Consumer<Path> onChange) throws IOException {
        this.root = root;
        this.onChange = onChange;
        this.watchService = root.getFileSystem().newWatchService();
        // 注册三种基本事件；OVERFLOW 单独在事件循环里处理
        root.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
    }

    /**
     * 启动后台监听线程。
     */
    void start() {
        thread = new Thread(this::watchLoop, "markdown-skill-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 停止监听器，关闭 WatchService 与线程。
     */
    void stop() {
        running = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("关闭 WatchService 失败: {}", e.getMessage());
            }
        }
        if (thread != null) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 监听主循环：阻塞等待 WatchKey，处理事件后重新入队。
     */
    private void watchLoop() {
        log.debug("skills 文件监听循环启动: {}", root);
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }

            // 收集本次触发涉及的所有路径
            Path lastChanged = root;
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    log.warn("WatchService 事件溢出，强制全量 reload");
                    safeCallback(root);
                    continue;
                }
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path filename = ev.context();
                Path fullPath = root.resolve(filename);
                // 创建/删除/修改三类事件都触发回调
                if (kind == ENTRY_CREATE || kind == ENTRY_DELETE || kind == ENTRY_MODIFY) {
                    lastChanged = fullPath;
                    safeCallback(fullPath);
                }
            }
            // 重置 key 以继续接收后续事件
            if (!key.reset()) {
                log.warn("WatchKey 已失效，停止监听: {}", key.watchable());
                break;
            }
            log.debug("skills 文件事件处理完成，最后变更: {}", lastChanged);
        }
        log.debug("skills 文件监听循环退出");
    }

    /**
     * 安全调用回调，单次失败不影响整体监听。
     */
    private void safeCallback(Path changed) {
        try {
            onChange.accept(changed);
        } catch (Exception e) {
            log.error("skills 变更回调失败: {}", e.getMessage(), e);
        }
    }
}