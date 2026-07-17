package com.example.demo.autonomy.web;

import com.example.demo.shared.dto.ApiResponse;
import com.example.demo.autonomy.dto.AutonomyArtifact;
import com.example.demo.autonomy.dto.AutonomyDiff;
import com.example.demo.autonomy.dto.AutonomyDraftResponse;
import com.example.demo.autonomy.dto.AutonomyScanReport;
import com.example.demo.autonomy.dto.AutonomyVerificationResult;
import com.example.demo.autonomy.application.ProjectAutonomyService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

/**
 * 受控自治控制器
 * 项目扫描、验证等敏感操作需要管理员权限
 */
@RestController
@RequestMapping("/api/autonomy")
public class AutonomyController {

    private final ProjectAutonomyService autonomyService;

    public AutonomyController(ProjectAutonomyService autonomyService) {
        this.autonomyService = autonomyService;
    }

    /**
     * 获取自治能力 - 普通用户可查看
     */
    @GetMapping("/capabilities")
    public ApiResponse<Map<String, Object>> getCapabilities() {
        return ApiResponse.success(autonomyService.getCapabilities());
    }

    /**
     * 查看历史记录 - 需要管理员权限
     */
    @GetMapping("/history")
        public ApiResponse<List<AutonomyArtifact>> history(@RequestParam(defaultValue = "12") int limit) {
        return ApiResponse.success(autonomyService.listArtifacts(limit));
    }

    /**
     * 查看差异对比 - 需要管理员权限
     */
    @GetMapping("/diff")
        public ApiResponse<AutonomyDiff> diff() {
        return ApiResponse.success(autonomyService.compareLatestScans());
    }

    /**
     * 读取产物文件 - 需要管理员权限
     */
    @GetMapping("/artifact")
        public ApiResponse<String> readArtifact(@RequestParam String path) {
        return ApiResponse.success(autonomyService.readArtifact(path));
    }

    /**
     * 扫描项目 - 需要管理员权限
     */
    @PostMapping("/scan")
        public ApiResponse<AutonomyScanReport> scanProject() {
        return ApiResponse.success(autonomyService.scanProject());
    }

    public record VerifyRequest(Boolean backend, Boolean frontend) {}

    /**
     * 验证项目 - 需要管理员权限
     */
    @PostMapping("/verify")
        public ApiResponse<AutonomyVerificationResult> verify(@RequestBody(required = false) VerifyRequest request) {
        boolean backend = request == null || request.backend() == null || request.backend();
        boolean frontend = request == null || request.frontend() == null || request.frontend();
        return ApiResponse.success(autonomyService.verifyProject(backend, frontend));
    }

    public record DraftRequest(String target, Boolean includeVerification) {}

    /**
     * 生成补全草稿 - 需要管理员权限。生成前会先做扫描，结果以 Markdown 落盘。
     */
    @PostMapping("/draft")
    public ApiResponse<AutonomyDraftResponse> generateDraft(@RequestBody(required = false) DraftRequest request) {
        String target = request != null ? request.target() : null;
        boolean includeVerification = request != null && Boolean.TRUE.equals(request.includeVerification());
        return ApiResponse.success(autonomyService.generateCompletionDraft(target, includeVerification));
    }
}
