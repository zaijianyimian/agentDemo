package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.service.code.CodeGenService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 代码生成控制器
 * AI 驱动的代码生成功能
 */
@RestController
@RequestMapping("/api/code")
public class CodeGenController {

    @Resource
    private CodeGenService codeGenService;

    /**
     * 生成代码请求
     */
    public record GenerateRequest(
            String type,        // entity/service/controller/mapper/dto/vue/typescript/sql
            String name,        // 类名/文件名
            List<String> fields, // 字段列表
            String packageName,  // 包名
            String description,  // 描述
            Map<String, Object> options // 额外选项
    ) {}

    /**
     * 保存代码请求
     */
    public record SaveRequest(
            String code,
            String fileName,
            String subDir
    ) {}

    /**
     * 代码审查请求
     */
    public record ReviewRequest(
            String code,
            String language
    ) {}

    /**
     * 代码转换请求
     */
    public record ConvertRequest(
            String code,
            String fromLanguage,
            String toLanguage
    ) {}

    /**
     * 生成代码
     */
    @PostMapping("/generate")
    public ApiResponse<CodeGenService.GenerateResponse> generateCode(@RequestBody GenerateRequest request) {
        try {
            // 参数校验
            if (request == null || request.type() == null || request.name() == null) {
                return ApiResponse.error("参数错误: type 和 name 不能为空");
            }

            CodeGenService.CodeType type = CodeGenService.CodeType.valueOf(request.type().toUpperCase());

            CodeGenService.GenerateRequest genRequest = new CodeGenService.GenerateRequest(
                    type,
                    request.name(),
                    request.fields(),
                    request.packageName(),
                    request.description(),
                    request.options()
            );

            CodeGenService.GenerateResponse response = codeGenService.generateCode(genRequest);

            if (response.success()) {
                return ApiResponse.success(response);
            } else {
                return ApiResponse.error(response.message());
            }
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("不支持的代码类型: " + (request != null ? request.type() : "null"));
        } catch (Exception e) {
            return ApiResponse.error("代码生成失败: " + e.getMessage());
        }
    }

    /**
     * 保存代码到文件
     */
    @PostMapping("/save")
    public ApiResponse<CodeGenService.GenerateResponse> saveCode(@RequestBody SaveRequest request) {
        CodeGenService.GenerateResponse response = codeGenService.saveCode(
                request.code(),
                request.fileName(),
                request.subDir()
        );

        if (response.success()) {
            return ApiResponse.success(response);
        } else {
            return ApiResponse.error(response.message());
        }
    }

    /**
     * 代码审查
     */
    @PostMapping("/review")
    public ApiResponse<String> reviewCode(@RequestBody ReviewRequest request) {
        try {
            String review = codeGenService.reviewCode(request.code(), request.language());
            return ApiResponse.success(review);
        } catch (Exception e) {
            return ApiResponse.error("代码审查失败: " + e.getMessage());
        }
    }

    /**
     * 代码转换
     */
    @PostMapping("/convert")
    public ApiResponse<String> convertCode(@RequestBody ConvertRequest request) {
        try {
            String converted = codeGenService.convertCode(
                    request.code(),
                    request.fromLanguage(),
                    request.toLanguage()
            );
            return ApiResponse.success(converted);
        } catch (Exception e) {
            return ApiResponse.error("代码转换失败: " + e.getMessage());
        }
    }

    /**
     * 分析项目结构
     */
    @GetMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyzeProject(
            @RequestParam(required = false, defaultValue = ".") String path) {
        Map<String, Object> result = codeGenService.analyzeProject(path);
        return ApiResponse.success(result);
    }

    /**
     * 获取支持的代码类型
     */
    @GetMapping("/types")
    public ApiResponse<List<String>> getSupportedTypes() {
        return ApiResponse.success(List.of(
                "entity", "service", "controller", "mapper", "dto",
                "vue", "typescript", "sql"
        ));
    }
}