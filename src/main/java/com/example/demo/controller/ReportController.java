package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.GeneratedReport;
import com.example.demo.dto.ReportArtifact;
import com.example.demo.service.report.ReportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/generate")
    public ApiResponse<GeneratedReport> generate(@RequestParam(defaultValue = "daily") String period) {
        return ApiResponse.success(reportService.generate(period));
    }

    @GetMapping("/history")
    public ApiResponse<List<ReportArtifact>> history(@RequestParam(defaultValue = "12") int limit) {
        return ApiResponse.success(reportService.list(limit));
    }

    @GetMapping("/artifact")
    public ApiResponse<String> artifact(@RequestParam String path) {
        return ApiResponse.success(reportService.read(path));
    }
}
