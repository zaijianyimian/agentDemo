package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.ScheduledTask;
import com.example.demo.service.task.ScheduledTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时任务控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final ScheduledTaskService taskService;

    public TaskController(ScheduledTaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 获取任务列表
     */
    @GetMapping("/list")
    public ApiResponse<List<ScheduledTask>> list() {
        return ApiResponse.success(taskService.listTasks());
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ScheduledTask> get(@PathVariable Long id) {
        ScheduledTask task = taskService.getTask(id);
        if (task == null) {
            return ApiResponse.error("任务不存在");
        }
        return ApiResponse.success(task);
    }

    /**
     * 创建任务
     */
    @PostMapping
    public ApiResponse<ScheduledTask> create(@RequestBody ScheduledTask task) {
        try {
            return ApiResponse.success(taskService.createTask(task));
        } catch (Exception e) {
            log.error("创建任务失败", e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新任务
     */
    @PutMapping("/{id}")
    public ApiResponse<ScheduledTask> update(@PathVariable Long id, @RequestBody ScheduledTask task) {
        task.setId(id);
        try {
            return ApiResponse.success(taskService.updateTask(task));
        } catch (Exception e) {
            log.error("更新任务失败", e);
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        try {
            taskService.deleteTask(id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除任务失败", e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用任务
     */
    @PutMapping("/{id}/toggle")
    public ApiResponse<ScheduledTask> toggle(@PathVariable Long id) {
        try {
            return ApiResponse.success(taskService.toggleTask(id));
        } catch (Exception e) {
            log.error("切换任务状态失败", e);
            return ApiResponse.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * 手动执行任务
     */
    @PostMapping("/{id}/execute")
    public ApiResponse<String> execute(@PathVariable Long id) {
        try {
            String result = taskService.executeTask(id);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("执行任务失败", e);
            return ApiResponse.error("执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务类型列表
     */
    @GetMapping("/types")
    public ApiResponse<List<String>> getTypes() {
        return ApiResponse.success(List.of("SKILL", "CHAT", "REMINDER"));
    }
}