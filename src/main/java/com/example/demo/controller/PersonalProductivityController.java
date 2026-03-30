package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.ScheduledTask;
import com.example.demo.service.personal.PersonalProductivityService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/personal")
public class PersonalProductivityController {

    private final PersonalProductivityService personalProductivityService;

    public PersonalProductivityController(PersonalProductivityService personalProductivityService) {
        this.personalProductivityService = personalProductivityService;
    }

    @GetMapping("/insights")
    public ApiResponse<Map<String, Object>> insights() {
        return ApiResponse.success(personalProductivityService.insights());
    }

    @GetMapping("/task-templates")
    public ApiResponse<Object> taskTemplates() {
        return ApiResponse.success(personalProductivityService.taskTemplates());
    }

    @PostMapping("/task-templates/{templateId}/create")
    public ApiResponse<ScheduledTask> createFromTemplate(@PathVariable String templateId) {
        return ApiResponse.success(personalProductivityService.createTaskFromTemplate(templateId));
    }

    @GetMapping("/backup/export")
    public ApiResponse<Map<String, Object>> exportBackup() {
        return ApiResponse.success(personalProductivityService.exportBackup());
    }

    @PostMapping("/backup/import")
    public ApiResponse<Map<String, Object>> importBackup(@RequestBody Map<String, Object> payload,
                                                         @RequestParam(defaultValue = "false") boolean replaceExisting) {
        return ApiResponse.success(personalProductivityService.importBackup(payload, replaceExisting));
    }
}
