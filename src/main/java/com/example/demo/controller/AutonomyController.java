package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.AutonomyArtifact;
import com.example.demo.dto.AutonomyDiff;
import com.example.demo.dto.AutonomyDraftResponse;
import com.example.demo.dto.AutonomyScanReport;
import com.example.demo.dto.AutonomyVerificationResult;
import com.example.demo.service.autonomy.ProjectAutonomyService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * 受控自治控制器
 */
@RestController
@RequestMapping("/api/autonomy")
public class AutonomyController {

    private final ProjectAutonomyService autonomyService;

    public AutonomyController(ProjectAutonomyService autonomyService) {
        this.autonomyService = autonomyService;
    }

    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Object>> getCapabilities() {
        return ApiResponse.success(autonomyService.getCapabilities());
    }

    @GetMapping("/history")
    public ApiResponse<List<AutonomyArtifact>> history(@RequestParam(defaultValue = "12") int limit) {
        return ApiResponse.success(autonomyService.listArtifacts(limit));
    }

    @GetMapping("/diff")
    public ApiResponse<AutonomyDiff> diff() {
        return ApiResponse.success(autonomyService.compareLatestScans());
    }

    @GetMapping("/artifact")
    public ApiResponse<String> readArtifact(@RequestParam String path) {
        return ApiResponse.success(autonomyService.readArtifact(path));
    }

    @PostMapping("/scan")
    public ApiResponse<AutonomyScanReport> scanProject() {
        return ApiResponse.success(autonomyService.scanProject());
    }

    public record VerifyRequest(Boolean backend, Boolean frontend) {}

    @PostMapping("/verify")
    public ApiResponse<AutonomyVerificationResult> verify(@RequestBody(required = false) VerifyRequest request) {
        boolean backend = request == null || request.backend() == null || request.backend();
        boolean frontend = request == null || request.frontend() == null || request.frontend();
        return ApiResponse.success(autonomyService.verifyProject(backend, frontend));
    }

    public record DraftRequest(String target, Boolean includeVerification) {}

    @PostMapping("/draft")
    public ApiResponse<AutonomyDraftResponse> generateDraft(@RequestBody(required = false) DraftRequest request) {
        String target = request != null ? request.target() : null;
        boolean includeVerification = request != null && Boolean.TRUE.equals(request.includeVerification());
        return ApiResponse.success(autonomyService.generateCompletionDraft(target, includeVerification));
    }
}
