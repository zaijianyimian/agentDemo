package com.example.demo.report.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.report.dto.GeneratedReport;
import com.example.demo.report.dto.ReportArtifact;
import com.example.demo.report.application.ReportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 报告控制器
 * 提供报告生成、历史查询与报告内容查看接口
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    /**
     * 构造时注入报告服务
     */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 生成报告（支持 daily / weekly 周期）
     */
    @PostMapping("/generate")
    public ApiResponse<GeneratedReport> generate(@RequestParam(defaultValue = "daily") String period) {
        return ApiResponse.success(reportService.generate(period));
    }

    /**
     * 获取历史报告列表
     */
    @GetMapping("/history")
    public ApiResponse<List<ReportArtifact>> history(@RequestParam(defaultValue = "12") int limit) {
        return ApiResponse.success(reportService.list(limit));
    }

    /**
     * 读取指定路径的报告内容
     */
    @GetMapping("/artifact")
    public ApiResponse<String> artifact(@RequestParam String path) {
        return ApiResponse.success(reportService.read(path));
    }
}
