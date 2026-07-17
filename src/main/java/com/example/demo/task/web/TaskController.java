package com.example.demo.task.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.infrastructure.config.CacheConfig;
import com.example.demo.task.application.ScheduledTaskService;
import com.example.demo.task.domain.JobLog;
import com.example.demo.task.domain.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务控制器。
 * <p>既负责 {@link ScheduledTask} 的 CRUD，也提供 {@link JobLog} 的查询端点
 * （前端调度管理页的"执行日志"面板使用）。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final ScheduledTaskService taskService;

    // ------------------------------------------------------------------
    // 任务 CRUD
    // ------------------------------------------------------------------

    @GetMapping("/list")
    @Cacheable(cacheNames = CacheConfig.TASK_LIST, sync = true)
    public ApiResponse<List<ScheduledTask>> list() {
        return ApiResponse.success(taskService.listTasks());
    }

    @GetMapping("/{id}")
    public ApiResponse<ScheduledTask> get(@PathVariable Long id) {
        ScheduledTask task = taskService.getTask(id);
        if (task == null) {
            return ApiResponse.error("任务不存在");
        }
        return ApiResponse.success(task);
    }

    @PostMapping
    @CacheEvict(cacheNames = CacheConfig.TASK_LIST, allEntries = true)
    public ApiResponse<ScheduledTask> create(@RequestBody ScheduledTask task) {
        try {
            return ApiResponse.success(taskService.createTask(task));
        } catch (Exception e) {
            log.error("创建任务失败", e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @CacheEvict(cacheNames = CacheConfig.TASK_LIST, allEntries = true)
    public ApiResponse<ScheduledTask> update(@PathVariable Long id, @RequestBody ScheduledTask task) {
        task.setId(id);
        try {
            return ApiResponse.success(taskService.updateTask(task));
        } catch (Exception e) {
            log.error("更新任务失败", e);
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @CacheEvict(cacheNames = CacheConfig.TASK_LIST, allEntries = true)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            taskService.deleteTask(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除任务失败", e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/toggle")
    @CacheEvict(cacheNames = CacheConfig.TASK_LIST, allEntries = true)
    public ApiResponse<ScheduledTask> toggle(@PathVariable Long id) {
        try {
            return ApiResponse.success(taskService.toggleTask(id));
        } catch (Exception e) {
            log.error("切换任务状态失败", e);
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/execute")
    @CacheEvict(cacheNames = CacheConfig.TASK_LIST, allEntries = true)
    public ApiResponse<String> execute(@PathVariable Long id) {
        try {
            String result = taskService.executeTask(id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("执行任务失败", e);
            return ApiResponse.error("执行失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 执行日志（前端调度管理页用）
    // ------------------------------------------------------------------

    /** 分页查询某任务的执行日志。 */
    @GetMapping("/{id}/logs")
    public ApiResponse<Map<String, Object>> pageLogs(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        IPage<JobLog> p = taskService.pageLogs(id, page, size);
        Map<String, Object> body = new HashMap<>();
        body.put("records", p.getRecords());
        body.put("total", p.getTotal());
        body.put("page", p.getCurrent());
        body.put("size", p.getSize());
        return ApiResponse.success(body);
    }

    /** 最近 N 条日志（用于 LLM @Tool 与前端详情侧栏）。 */
    @GetMapping("/{id}/logs/recent")
    public ApiResponse<List<JobLog>> recentLogs(@PathVariable Long id,
                                                @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(taskService.recentLogs(id, limit));
    }
}