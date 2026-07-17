package com.example.demo.dispatch.web;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.dispatch.application.Dispatcher;
import com.example.demo.dispatch.application.DispatchedTaskService;
import com.example.demo.dispatch.application.executor.ExecutorRouter;
import com.example.demo.dispatch.domain.DispatchedTask;
import com.example.demo.shared.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 派发任务 REST API。
 */
@RestController
@RequestMapping("/api/dispatched")
@RequiredArgsConstructor
public class DispatchController {

    private final DispatchedTaskService taskService;
    private final Dispatcher dispatcher;
    private final ExecutorRouter executorRouter;

    @GetMapping
    /**
     * 分页查询已派发任务，按创建时间倒序。
     */
    public ApiResponse<Page<DispatchedTask>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(taskService.list(page, size));
    }

    @GetMapping("/{id}")
    /**
     * 获取单个派发任务详情。
     */
    public ApiResponse<DispatchedTask> get(@PathVariable Long id) {
        DispatchedTask t = taskService.getById(id);
        if (t == null) {
            return ApiResponse.error("task not found");
        }
        return ApiResponse.success(t);
    }

    @PostMapping("/{id}/cancel")
    /**
     * 取消一个尚未开始的任务（只能从 PENDING 进入 CANCELLED）。
     */
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        taskService.cancel(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/rerun")
    /**
     * 将已 DONE 或 FAILED 的任务重置回 PENDING 重新派发。
     */
    public ApiResponse<Void> rerun(@PathVariable Long id) {
        taskService.rerun(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/executors/availability")
    /**
     * 返回当前各执行器是否在本机 PATH 上可用，供前端运维面板展示。
     */
    public ApiResponse<Map<String, Boolean>> executorAvailability() {
        return ApiResponse.success(executorRouter.availabilitySnapshot());
    }
}
