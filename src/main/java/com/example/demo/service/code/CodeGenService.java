package com.example.demo.service.code;

import dev.langchain4j.model.chat.ChatModel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 代码生成服务
 * AI 驱动的代码生成功能
 */
@Slf4j
@Service
public class CodeGenService {

    @Resource
    private ChatModel chatModel;

    @Value("${app.code.gen.output-dir:./generated}")
    private String outputDir;

    /**
     * 代码类型
     */
    public enum CodeType {
        ENTITY,       // JPA/MyBatis 实体类
        SERVICE,      // 服务类
        CONTROLLER,   // 控制器类
        MAPPER,       // Mapper 接口
        DTO,          // DTO 类
        VUE,          // Vue 组件
        TYPESCRIPT,   // TypeScript 类型
        SQL           // SQL 脚本
    }

    /**
     * 生成代码请求
     */
    public record GenerateRequest(
            CodeType type,
            String name,
            List<String> fields,
            String packageName,
            String description,
            Map<String, Object> options
    ) {}

    /**
     * 生成代码响应
     */
    public record GenerateResponse(
            boolean success,
            String code,
            String fileName,
            String filePath,
            String message
    ) {}

    /**
     * 生成代码
     */
    public GenerateResponse generateCode(GenerateRequest request) {
        try {
            String prompt = buildPrompt(request);
            String generatedCode = chatModel.chat(prompt);

            // 清理代码块标记
            generatedCode = cleanCodeBlock(generatedCode);

            // 生成文件名
            String fileName = generateFileName(request);

            return new GenerateResponse(
                    true,
                    generatedCode,
                    fileName,
                    null,
                    "代码生成成功"
            );
        } catch (Exception e) {
            log.error("代码生成失败", e);
            return new GenerateResponse(
                    false,
                    null,
                    null,
                    null,
                    "代码生成失败: " + e.getMessage()
            );
        }
    }

    /**
     * 保存生成的代码到文件
     */
    public GenerateResponse saveCode(String code, String fileName, String subDir) {
        try {
            // 确保输出目录存在
            Path dirPath = Paths.get(outputDir, subDir != null ? subDir : "");
            Files.createDirectories(dirPath);

            // 写入文件
            Path filePath = dirPath.resolve(fileName);
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(code);
            }

            log.info("代码已保存: {}", filePath.toAbsolutePath());

            return new GenerateResponse(
                    true,
                    code,
                    fileName,
                    filePath.toAbsolutePath().toString(),
                    "代码已保存到: " + filePath.toAbsolutePath()
            );
        } catch (IOException e) {
            log.error("保存代码失败", e);
            return new GenerateResponse(
                    false,
                    code,
                    fileName,
                    null,
                    "保存失败: " + e.getMessage()
            );
        }
    }

    /**
     * 代码审查
     */
    public String reviewCode(String code, String language) {
        String prompt = String.format("""
                请对以下 %s 代码进行审查，指出潜在问题和改进建议：

                ```%s
                %s
                ```

                请从以下几个方面进行审查：
                1. 代码质量和最佳实践
                2. 潜在的 bug 或安全漏洞
                3. 性能优化建议
                4. 可读性和维护性

                请用中文回答。
                """,
                language != null ? language : "",
                language != null ? language : "",
                code
        );

        return chatModel.chat(prompt);
    }

    /**
     * 代码转换
     */
    public String convertCode(String code, String fromLanguage, String toLanguage) {
        String prompt = String.format("""
                请将以下 %s 代码转换为 %s 代码，保持功能一致：

                ```%s
                %s
                ```

                请只输出转换后的代码，不要添加解释。
                """,
                fromLanguage,
                toLanguage,
                fromLanguage,
                code
        );

        String result = chatModel.chat(prompt);
        return cleanCodeBlock(result);
    }

    /**
     * 分析项目结构
     */
    public Map<String, Object> analyzeProject(String projectPath) {
        Map<String, Object> result = new HashMap<>();

        try {
            Path rootPath = Paths.get(projectPath);

            // 统计文件
            Map<String, Long> fileCounts = new HashMap<>();
            Files.walk(rootPath)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String ext = fileName.contains(".") ?
                                fileName.substring(fileName.lastIndexOf(".")) : "other";
                        fileCounts.merge(ext, 1L, Long::sum);
                    });

            result.put("fileCounts", fileCounts);
            result.put("totalFiles", fileCounts.values().stream().mapToLong(Long::longValue).sum());

            // 检测项目类型
            boolean isJavaProject = Files.exists(rootPath.resolve("pom.xml")) ||
                    Files.exists(rootPath.resolve("build.gradle"));
            boolean isVueProject = Files.exists(rootPath.resolve("package.json")) &&
                    Files.exists(rootPath.resolve("src"));

            result.put("isJavaProject", isJavaProject);
            result.put("isVueProject", isVueProject);

        } catch (IOException e) {
            log.error("分析项目失败", e);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 构建生成提示词
     */
    private String buildPrompt(GenerateRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请生成一个").append(getTypeDescription(request.type())).append("。\n\n");
        prompt.append("名称: ").append(request.name()).append("\n");

        if (request.fields() != null && !request.fields().isEmpty()) {
            prompt.append("字段:\n");
            for (String field : request.fields()) {
                prompt.append("- ").append(field).append("\n");
            }
        }

        if (request.packageName() != null) {
            prompt.append("包名: ").append(request.packageName()).append("\n");
        }

        if (request.description() != null) {
            prompt.append("描述: ").append(request.description()).append("\n");
        }

        prompt.append("\n请只输出代码，使用以下格式：\n");
        prompt.append("```").append(getLanguageTag(request.type())).append("\n");
        prompt.append("// 代码内容\n");
        prompt.append("```\n");

        prompt.append("\n要求：\n");
        prompt.append("1. 代码规范、注释清晰\n");
        prompt.append("2. 使用中文注释\n");
        prompt.append("3. 符合最佳实践\n");

        return prompt.toString();
    }

    private String getTypeDescription(CodeType type) {
        return switch (type) {
            case ENTITY -> "Java JPA/MyBatis Plus 实体类";
            case SERVICE -> "Java Spring 服务类";
            case CONTROLLER -> "Java Spring REST 控制器";
            case MAPPER -> "Java MyBatis Plus Mapper 接口";
            case DTO -> "Java DTO 数据传输对象";
            case VUE -> "Vue 3 组合式 API 组件";
            case TYPESCRIPT -> "TypeScript 类型定义";
            case SQL -> "MySQL 建表 SQL 脚本";
        };
    }

    private String getLanguageTag(CodeType type) {
        return switch (type) {
            case ENTITY, SERVICE, CONTROLLER, MAPPER, DTO -> "java";
            case VUE -> "vue";
            case TYPESCRIPT -> "typescript";
            case SQL -> "sql";
        };
    }

    private String generateFileName(GenerateRequest request) {
        String name = request.name();
        return switch (request.type()) {
            case ENTITY, SERVICE, CONTROLLER, MAPPER, DTO -> name + ".java";
            case VUE -> name + ".vue";
            case TYPESCRIPT -> name + ".ts";
            case SQL -> name + ".sql";
        };
    }

    private String cleanCodeBlock(String code) {
        if (code == null) return null;

        // 移除代码块标记
        code = code.trim();
        if (code.startsWith("```")) {
            int firstNewline = code.indexOf('\n');
            if (firstNewline > 0) {
                code = code.substring(firstNewline + 1);
            }
        }
        if (code.endsWith("```")) {
            code = code.substring(0, code.length() - 3);
        }

        return code.trim();
    }
}