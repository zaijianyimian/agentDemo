package com.example.demo.service.autonomy;

import com.example.demo.dto.AutonomyDraftResponse;
import com.example.demo.dto.AutonomyFinding;
import com.example.demo.dto.AutonomyDiff;
import com.example.demo.dto.AutonomyArtifact;
import com.example.demo.dto.AutonomyScanReport;
import com.example.demo.dto.AutonomyVerificationResult;
import com.example.demo.dto.AutonomyVerificationStep;
import com.example.demo.properties.AutonomyProperties;
import com.example.demo.service.chat.QwenChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 项目受控自治服务
 */
@Slf4j
@Service
public class ProjectAutonomyService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final AutonomyProperties autonomyProperties;
    private final ObjectMapper objectMapper;
    private final QwenChatService qwenChatService;

    public ProjectAutonomyService(AutonomyProperties autonomyProperties,
                                  ObjectMapper objectMapper,
                                  QwenChatService qwenChatService) {
        this.autonomyProperties = autonomyProperties;
        this.objectMapper = objectMapper;
        this.qwenChatService = qwenChatService;
    }

    public Map<String, Object> getCapabilities() {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("enabled", autonomyProperties.getEnabled());
        capabilities.put("workspaceRoot", resolveWorkspaceRoot().toString());
        capabilities.put("outputDir", resolveOutputDir().toString());
        capabilities.put("canScan", true);
        capabilities.put("canGenerateDraft", true);
        capabilities.put("canVerifyBackend", autonomyProperties.getAllowBackendVerify());
        capabilities.put("canVerifyFrontend", autonomyProperties.getAllowFrontendVerify());
        capabilities.put("allowSourceWrite", autonomyProperties.getAllowSourceWrite());
        capabilities.put("allowRemoteUpdate", autonomyProperties.getAllowRemoteUpdate());
        capabilities.put("policy", "默认只扫描、生成补全草稿、执行构建验证；不直接改源码，不做远程自更新。");
        return capabilities;
    }

    public AutonomyScanReport scanProject() {
        Path root = resolveWorkspaceRoot();
        Path outputDir = ensureOutputDir();
        LocalDateTime now = LocalDateTime.now();

        List<AutonomyFinding> findings = new ArrayList<>();
        Map<String, Object> metrics = new LinkedHashMap<>();

        Path readme = root.resolve("README.md");
        Path apiDoc = root.resolve("API.md");
        Path backendControllerDir = root.resolve("src/main/java/com/example/demo/controller");
        Path frontendViewsDir = root.resolve("frontend/src/views");
        Path frontendRouter = root.resolve("frontend/src/router/index.ts");
        Path frontendApi = root.resolve("frontend/src/services/api.ts");

        metrics.put("controllerCount", countFiles(backendControllerDir, "*.java"));
        metrics.put("viewCount", countFiles(frontendViewsDir, "*.vue"));
        metrics.put("routeCount", countRoutes(frontendRouter));
        metrics.put("apiServiceExports", countApiServices(frontendApi));
        metrics.put("readmeExists", Files.exists(readme));
        metrics.put("apiDocExists", Files.exists(apiDoc));
        metrics.put("gitClean", isGitClean(root));

        if (!Files.exists(readme)) {
            findings.add(finding("high", "README 缺失", "项目根目录未找到 README.md。", "补充项目概览、启动说明和模块说明。"));
        }
        if (!Files.exists(apiDoc)) {
            findings.add(finding("high", "API 文档缺失", "项目根目录未找到 API.md。", "补充接口文档并与当前实现保持一致。"));
        }
        if (!Files.exists(frontendApi)) {
            findings.add(finding("high", "前端 API 封装缺失", "未找到 frontend/src/services/api.ts。", "建立统一的前端服务层，避免页面内散落直连请求。"));
        }

        List<String> routeViews = extractRouteViews(frontendRouter);
        List<String> actualViews = listFileNames(frontendViewsDir, "*.vue");
        List<String> unroutedViews = actualViews.stream()
                .filter(name -> !routeViews.contains(name))
                .collect(Collectors.toList());
        if (!unroutedViews.isEmpty()) {
            findings.add(finding("medium", "存在未挂载页面", "以下页面文件未在路由中暴露: " + String.join(", ", unroutedViews), "检查这些页面是否应挂到导航或移除废弃页面。"));
        }

        List<String> missingViews = routeViews.stream()
                .filter(name -> !actualViews.contains(name))
                .collect(Collectors.toList());
        if (!missingViews.isEmpty()) {
            findings.add(finding("high", "路由引用缺失页面", "以下路由引用的页面文件不存在: " + String.join(", ", missingViews), "补齐页面文件或移除无效路由。"));
        }

        if (!Boolean.TRUE.equals(metrics.get("gitClean"))) {
            findings.add(finding("low", "工作区非干净状态", "检测到 git 工作区存在未提交改动。", "执行自动补全前，建议先提交或备份当前变更。"));
        }

        if (findings.isEmpty()) {
            findings.add(finding("info", "未发现结构性阻塞问题", "项目主要结构完整，可继续做增量补全和验证。", "优先针对功能缺口、测试覆盖和文档一致性继续演进。"));
        }

        String timestamp = now.format(TS);
        Path jsonPath = outputDir.resolve("scan-" + timestamp + ".json");
        Path mdPath = outputDir.resolve("scan-" + timestamp + ".md");

        AutonomyScanReport report = AutonomyScanReport.builder()
                .scanTime(now)
                .workspaceRoot(root.toString())
                .metrics(metrics)
                .findings(findings)
                .reportPath(jsonPath.toString())
                .summaryPath(mdPath.toString())
                .build();

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), report);
            Files.writeString(mdPath, buildMarkdownSummary(report), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("写入自治扫描报告失败", e);
        }

        return report;
    }

    public AutonomyVerificationResult verifyProject(boolean verifyBackend, boolean verifyFrontend) {
        List<AutonomyVerificationStep> steps = new ArrayList<>();
        Path root = resolveWorkspaceRoot();

        if (verifyBackend && Boolean.TRUE.equals(autonomyProperties.getAllowBackendVerify())) {
            steps.add(runCommand("backend-test", buildBackendCommand(root), root));
        }
        if (verifyFrontend && Boolean.TRUE.equals(autonomyProperties.getAllowFrontendVerify())) {
            Path frontendDir = root.resolve("frontend");
            steps.add(runCommand("frontend-build", buildFrontendCommand(), frontendDir));
        }

        boolean success = steps.stream().allMatch(AutonomyVerificationStep::isSuccess);
        AutonomyVerificationResult result = AutonomyVerificationResult.builder()
                .verifyTime(LocalDateTime.now())
                .success(success)
                .steps(steps)
                .build();
        writeJsonArtifact("verify", result);
        return result;
    }

    public AutonomyDraftResponse generateCompletionDraft(String target, boolean includeVerification) {
        AutonomyScanReport report = scanProject();
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个单用户 AI Agent 项目的受控自治补全助手。")
                .append("请根据以下扫描结果，输出一份中文 Markdown 补全方案。")
                .append("目标范围: ").append(target == null || target.isBlank() ? "general" : target).append("。")
                .append("要求：1. 先列缺口 2. 再列建议修改文件 3. 再列实施顺序 4. 不要要求开放无限自更新权限。")
                .append("\n扫描指标: ").append(report.getMetrics())
                .append("\n发现项:\n");
        for (AutonomyFinding finding : report.getFindings()) {
            prompt.append("- [").append(finding.getSeverity()).append("] ")
                    .append(finding.getTitle()).append(": ")
                    .append(finding.getDetail()).append("；建议：")
                    .append(finding.getSuggestion()).append("\n");
        }
        if (includeVerification) {
            prompt.append("\n另外请补充验证步骤，至少包含后端测试和前端构建。");
        }

        String content = qwenChatService.complete(prompt.toString());
        if (content == null || content.isBlank()) {
            content = "未生成到补全草稿，请稍后重试。";
        }

        Path outputDir = ensureOutputDir();
        Path draftPath = outputDir.resolve("draft-" + LocalDateTime.now().format(TS) + ".md");
        try {
            Files.writeString(draftPath, content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("写入自治补全草稿失败", e);
        }

        return AutonomyDraftResponse.builder()
                .generateTime(LocalDateTime.now())
                .target(target == null || target.isBlank() ? "general" : target)
                .draftPath(draftPath.toString())
                .content(content)
                .policyNote("当前实现默认只生成补全草稿与验证结果，不直接改写源码，也不执行远程自更新。")
                .build();
    }

    public List<AutonomyArtifact> listArtifacts(int limit) {
        Path outputDir = ensureOutputDir();
        if (!Files.exists(outputDir)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(outputDir)) {
            return stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .limit(Math.max(1, limit))
                    .map(this::toArtifact)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("读取自治产物列表失败", e);
            return List.of();
        }
    }

    public String readArtifact(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            Path outputDir = ensureOutputDir().toAbsolutePath().normalize();
            Path target = Paths.get(path).toAbsolutePath().normalize();
            if (!target.startsWith(outputDir)) {
                throw new IllegalArgumentException("只能读取自治输出目录内的文件");
            }
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取自治产物失败: " + e.getMessage(), e);
        }
    }

    public AutonomyDiff compareLatestScans() {
        List<Path> scanJsons = listScanJsons();
        if (scanJsons.size() < 2) {
            return AutonomyDiff.builder()
                    .newCount(0)
                    .resolvedCount(0)
                    .persistentCount(0)
                    .newFindings(List.of())
                    .resolvedFindings(List.of())
                    .persistentFindings(List.of())
                    .build();
        }

        AutonomyScanReport latest = readScan(scanJsons.get(0));
        AutonomyScanReport previous = readScan(scanJsons.get(1));
        if (latest == null || previous == null) {
            return AutonomyDiff.builder()
                    .newCount(0)
                    .resolvedCount(0)
                    .persistentCount(0)
                    .newFindings(List.of())
                    .resolvedFindings(List.of())
                    .persistentFindings(List.of())
                    .build();
        }

        Map<String, AutonomyFinding> latestMap = latest.getFindings().stream()
                .collect(Collectors.toMap(this::findingKey, item -> item, (a, b) -> a, LinkedHashMap::new));
        Map<String, AutonomyFinding> previousMap = previous.getFindings().stream()
                .collect(Collectors.toMap(this::findingKey, item -> item, (a, b) -> a, LinkedHashMap::new));

        List<AutonomyFinding> newFindings = latestMap.entrySet().stream()
                .filter(entry -> !previousMap.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        List<AutonomyFinding> resolvedFindings = previousMap.entrySet().stream()
                .filter(entry -> !latestMap.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        List<AutonomyFinding> persistentFindings = latestMap.entrySet().stream()
                .filter(entry -> previousMap.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        return AutonomyDiff.builder()
                .latestScanTime(latest.getScanTime())
                .previousScanTime(previous.getScanTime())
                .newCount(newFindings.size())
                .resolvedCount(resolvedFindings.size())
                .persistentCount(persistentFindings.size())
                .newFindings(newFindings)
                .resolvedFindings(resolvedFindings)
                .persistentFindings(persistentFindings)
                .build();
    }

    private AutonomyVerificationStep runCommand(String name, List<String> command, Path workDir) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);

        int exitCode = -1;
        String output;
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(autonomyProperties.getCommandTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                output = "命令执行超时，已终止: " + String.join(" ", command);
                return AutonomyVerificationStep.builder()
                        .name(name)
                        .success(false)
                        .exitCode(-1)
                        .workingDirectory(workDir.toString())
                        .output(output)
                        .build();
            }
            exitCode = process.exitValue();
            output = readProcessOutput(process.getInputStream());
        } catch (Exception e) {
            output = "命令执行失败: " + e.getMessage();
        }

        return AutonomyVerificationStep.builder()
                .name(name)
                .success(exitCode == 0)
                .exitCode(exitCode)
                .workingDirectory(workDir.toString())
                .output(trimOutput(output))
                .build();
    }

    private List<String> buildBackendCommand(Path root) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            return List.of("cmd.exe", "/c", "gradlew.bat test");
        }
        return List.of("sh", "-lc", "./gradlew test");
    }

    private List<String> buildFrontendCommand() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            return List.of("cmd.exe", "/c", "npm run build");
        }
        return List.of("sh", "-lc", "npm run build");
    }

    private long countFiles(Path dir, String glob) {
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            String suffix = glob.replace("*", "");
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .count();
        } catch (Exception e) {
            return 0;
        }
    }

    private List<String> listFileNames(Path dir, String glob) {
        if (!Files.exists(dir)) {
            return List.of();
        }
        String suffix = glob.replace("*", "");
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(suffix))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private int countRoutes(Path routerFile) {
        if (!Files.exists(routerFile)) {
            return 0;
        }
        try {
            String content = Files.readString(routerFile);
            Matcher matcher = Pattern.compile("name:\\s*'[^']+'").matcher(content);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private int countApiServices(Path apiFile) {
        if (!Files.exists(apiFile)) {
            return 0;
        }
        try {
            String content = Files.readString(apiFile);
            Matcher matcher = Pattern.compile("export const\\s+\\w+\\s*=").matcher(content);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<String> extractRouteViews(Path routerFile) {
        if (!Files.exists(routerFile)) {
            return List.of();
        }
        try {
            String content = Files.readString(routerFile);
            Matcher matcher = Pattern.compile("import\\('\\@/views/([^']+\\.vue)'\\)").matcher(content);
            List<String> result = new ArrayList<>();
            while (matcher.find()) {
                result.add(matcher.group(1));
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean isGitClean(Path root) {
        try {
            ProcessBuilder builder = new ProcessBuilder("git", "status", "--porcelain");
            builder.directory(root.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            boolean finished = process.waitFor(20, TimeUnit.SECONDS);
            if (!finished || process.exitValue() != 0) {
                return false;
            }
            String output = readProcessOutput(process.getInputStream()).trim();
            return output.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String buildMarkdownSummary(AutonomyScanReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 项目自治扫描报告\n\n");
        sb.append("- 扫描时间: ").append(report.getScanTime()).append("\n");
        sb.append("- 工作区: ").append(report.getWorkspaceRoot()).append("\n\n");
        sb.append("## 指标\n\n");
        for (Map.Entry<String, Object> entry : report.getMetrics().entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        sb.append("\n## 发现项\n\n");
        for (AutonomyFinding finding : report.getFindings()) {
            sb.append("- [").append(finding.getSeverity()).append("] ").append(finding.getTitle()).append("\n");
            sb.append("  - 说明: ").append(finding.getDetail()).append("\n");
            sb.append("  - 建议: ").append(finding.getSuggestion()).append("\n");
        }
        return sb.toString();
    }

    private AutonomyFinding finding(String severity, String title, String detail, String suggestion) {
        return AutonomyFinding.builder()
                .severity(severity)
                .title(title)
                .detail(detail)
                .suggestion(suggestion)
                .build();
    }

    private String readProcessOutput(InputStream inputStream) throws Exception {
        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private String trimOutput(String output) {
        if (output == null) {
            return "";
        }
        return output.length() > 8000 ? output.substring(0, 8000) + "\n...[truncated]" : output;
    }

    private Path resolveWorkspaceRoot() {
        return Paths.get(autonomyProperties.getWorkspaceRoot()).toAbsolutePath().normalize();
    }

    private Path resolveOutputDir() {
        return Paths.get(autonomyProperties.getOutputDir()).toAbsolutePath().normalize();
    }

    private Path ensureOutputDir() {
        Path outputDir = resolveOutputDir();
        try {
            Files.createDirectories(outputDir);
        } catch (Exception e) {
            log.warn("创建自治输出目录失败: {}", outputDir, e);
        }
        return outputDir;
    }

    private List<Path> listScanJsons() {
        Path outputDir = ensureOutputDir();
        try (Stream<Path> stream = Files.list(outputDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("scan-"))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private AutonomyScanReport readScan(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), AutonomyScanReport.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String findingKey(AutonomyFinding finding) {
        return finding.getSeverity() + "|" + finding.getTitle() + "|" + finding.getDetail();
    }

    private void writeJsonArtifact(String prefix, Object value) {
        Path outputDir = ensureOutputDir();
        Path path = outputDir.resolve(prefix + "-" + LocalDateTime.now().format(TS) + ".json");
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
        } catch (Exception e) {
            log.warn("写入自治产物失败: {}", path, e);
        }
    }

    private AutonomyArtifact toArtifact(Path path) {
        String name = path.getFileName().toString();
        String type = "artifact";
        if (name.startsWith("scan-")) {
            type = "scan";
        } else if (name.startsWith("verify-")) {
            type = "verify";
        } else if (name.startsWith("draft-")) {
            type = "draft";
        }

        String preview = "";
        try {
            preview = trimOutput(Files.readString(path, StandardCharsets.UTF_8).replaceAll("\\s+", " "));
        } catch (Exception ignored) {
        }

        LocalDateTime time = null;
        try {
            String base = name.substring(name.indexOf('-') + 1, name.lastIndexOf('.'));
            time = LocalDateTime.parse(base, TS);
        } catch (Exception ignored) {
        }

        return AutonomyArtifact.builder()
                .type(type)
                .name(name)
                .path(path.toAbsolutePath().toString())
                .time(time)
                .preview(preview)
                .build();
    }
}
