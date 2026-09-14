package com.example.demo.task.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.task.application.ScheduledTaskService;
import com.example.demo.task.domain.JobLog;
import com.example.demo.task.domain.ScheduledTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public ApiResponse<List<ScheduledTask>> list() {
        return ApiResponse.success(taskService.listTasks());
    }

    @GetMapping("/{id}")
    public ApiResponse<ScheduledTask> get(@PathVariable Long id) {
        ScheduledTask task = taskService.getTask(id);
        return ApiResponse.success(task);
    }

    @PostMapping
    public ApiResponse<ScheduledTask> create(@RequestBody ScheduledTask task) {
        try {
            return ApiResponse.success(taskService.createTask(task));
        } catch (Exception e) {
            log.error("创建任务失败", e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ApiResponse<ScheduledTask> update(@PathVariable Long id, @RequestBody ScheduledTask task) {
        task.setId(id);
        return ApiResponse.success(taskService.updateTask(task));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/toggle")
    public ApiResponse<ScheduledTask> toggle(@PathVariable Long id) {
        return ApiResponse.success(taskService.toggleTask(id));
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<String> execute(@PathVariable Long id) {
        return ApiResponse.success(taskService.executeTask(id));
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
