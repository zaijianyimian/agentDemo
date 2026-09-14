package com.example.demo.personal.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.task.domain.ScheduledTask;
import com.example.demo.personal.application.PersonalProductivityService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 个人生产力控制器
 * 提供个人洞察、任务模板、备份导入导出等接口
 */
@RestController
@RequestMapping("/api/personal")
public class PersonalProductivityController {

    private final PersonalProductivityService personalProductivityService;

    /**
     * 构造时注入个人生产力服务
     */
    public PersonalProductivityController(PersonalProductivityService personalProductivityService) {
        this.personalProductivityService = personalProductivityService;
    }

    /**
     * 获取个人生产力洞察统计。首页面板频繁读取，缓存 30 秒。
     */
    @GetMapping("/insights")
    public ApiResponse<Map<String, Object>> insights() {
        return ApiResponse.success(personalProductivityService.insights());
    }

    /**
     * 获取预置的任务模板列表。模板不常变，缓存 30 秒足够。
     */
    @GetMapping("/task-templates")
    public ApiResponse<Object> taskTemplates() {
        return ApiResponse.success(personalProductivityService.taskTemplates());
    }

    /**
     * 基于模板 ID 创建一个新的调度任务。
     * 命中 taskList 缓存会自动 evict（由 taskId 路径触发）。
     */
    @PostMapping("/task-templates/{templateId}/create")
    public ApiResponse<ScheduledTask> createFromTemplate(@PathVariable String templateId) {
        return ApiResponse.success(personalProductivityService.createTaskFromTemplate(templateId));
    }

}
