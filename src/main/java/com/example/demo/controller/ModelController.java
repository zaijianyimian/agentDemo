package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.entity.AiModelConfig;
import com.example.demo.mapper.AiModelConfigMapper;
import com.example.demo.service.ai.EncodingService;
import com.example.demo.service.ai.ModelManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型配置控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/model")
public class ModelController {

    private final AiModelConfigMapper configMapper;
    private final EncodingService encodingService;
    private final ModelManager modelManager;

    public ModelController(AiModelConfigMapper configMapper,
                           EncodingService encodingService,
                           ModelManager modelManager) {
        this.configMapper = configMapper;
        this.encodingService = encodingService;
        this.modelManager = modelManager;
    }

    /**
     * 获取模型列表（不返回 API Key）
     */
    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<AiModelConfig> configs = configMapper.selectList(null);
        List<Map<String, Object>> result = configs.stream()
                .map(this::toSafeMap)
                .toList();
        return ApiResponse.success(result);
    }

    /**
     * 获取模型详情
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long id) {
        AiModelConfig config = configMapper.selectById(id);
        if (config == null) {
            return ApiResponse.error("模型不存在");
        }
        return ApiResponse.success(toSafeMap(config));
    }

    /**
     * 创建模型
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody AiModelConfig config) {
        try {
            // 编码 API Key
            config.setApiKey(encodingService.encode(config.getApiKey()));

            // 首个模型自动启用且设为默认，其余模型默认禁用
            boolean hasEnabledModel = configMapper.countEnabled() > 0;
            if (hasEnabledModel) {
                config.setEnabled(false);
                config.setIsDefault(false);
            } else {
                config.setEnabled(true);
                config.setIsDefault(true);
                configMapper.clearDefault();
            }

            configMapper.insert(config);

            // 创建 ChatModel 缓存
            if (Boolean.TRUE.equals(config.getEnabled())) {
                modelManager.refreshModel(config.getId());
            }

            log.info("Created AI model: {}", config.getName());
            return ApiResponse.success(toSafeMap(config));
        } catch (Exception e) {
            log.error("创建模型失败", e);
            return ApiResponse.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新模型
     */
    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody AiModelConfig config) {
        AiModelConfig existing = configMapper.selectById(id);
        if (existing == null) {
            return ApiResponse.error("模型不存在");
        }

        try {
            config.setId(id);

            // 如果更新了 API Key，需要编码
            if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
                // 检查是否已经编码
                if (!encodingService.isEncoded(config.getApiKey())) {
                    config.setApiKey(encodingService.encode(config.getApiKey()));
                }
            } else {
                config.setApiKey(existing.getApiKey());
            }

            // 如果设置为默认，先清除其他默认
            if (Boolean.TRUE.equals(config.getIsDefault())) {
                configMapper.clearDefault();
            }

            configMapper.updateById(config);

            // 刷新模型缓存
            modelManager.refreshModel(id);

            log.info("Updated AI model: {}", config.getName());
            return ApiResponse.success(toSafeMap(configMapper.selectById(id)));
        } catch (Exception e) {
            log.error("更新模型失败", e);
            return ApiResponse.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除模型
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        AiModelConfig existing = configMapper.selectById(id);
        if (existing == null) {
            return ApiResponse.error("模型不存在");
        }

        if (Boolean.TRUE.equals(existing.getEnabled()) && configMapper.countEnabled() <= 1) {
            return ApiResponse.error("至少需要保留一个启用模型，请先启用其他模型");
        }

        try {
            // 移除缓存
            modelManager.removeModel(id);
            configMapper.deleteById(id);
            log.info("Deleted AI model: {}", id);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("删除模型失败", e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 启用/禁用模型
     */
    @PutMapping("/{id}/toggle")
    public ApiResponse<Map<String, Object>> toggle(@PathVariable Long id) {
        AiModelConfig config = configMapper.selectById(id);
        if (config == null) {
            return ApiResponse.error("模型不存在");
        }

        // 单启用策略：启用某模型时，自动禁用其他模型并设为默认
        if (Boolean.TRUE.equals(config.getEnabled())) {
            if (configMapper.countEnabled() <= 1) {
                return ApiResponse.error("至少需要保留一个启用模型");
            }

            config.setEnabled(false);
            config.setIsDefault(false);
            configMapper.updateById(config);
            modelManager.removeModel(id);
        } else {
            // 记录旧的启用模型并清理缓存
            List<AiModelConfig> previouslyEnabled = configMapper.selectEnabled();
            for (AiModelConfig enabledConfig : previouslyEnabled) {
                modelManager.removeModel(enabledConfig.getId());
            }

            configMapper.clearEnabled();
            configMapper.clearDefault();

            config.setEnabled(true);
            config.setIsDefault(true);
            configMapper.updateById(config);
            modelManager.refreshModel(id);
        }

        AiModelConfig latest = configMapper.selectById(id);
        return ApiResponse.success(toSafeMap(latest));
    }

    /**
     * 设置为默认模型
     */
    @PutMapping("/{id}/default")
    public ApiResponse<Map<String, Object>> setDefault(@PathVariable Long id) {
        AiModelConfig config = configMapper.selectById(id);
        if (config == null) {
            return ApiResponse.error("模型不存在");
        }
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return ApiResponse.error("请先启用该模型");
        }

        // 清除其他默认
        configMapper.clearDefault();

        // 设置当前为默认
        config.setIsDefault(true);
        configMapper.updateById(config);

        return ApiResponse.success(toSafeMap(config));
    }

    /**
     * 测试模型连接
     */
    @PostMapping("/test")
    public ApiResponse<String> test(@RequestBody AiModelConfig config) {
        try {
            String result = modelManager.testConnection(config);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("测试连接失败", e);
            return ApiResponse.error("测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取支持的提供商列表
     */
    @GetMapping("/providers")
    public ApiResponse<List<Map<String, String>>> getProviders() {
        return ApiResponse.success(List.of(
                Map.of("value", "openai", "label", "OpenAI", "baseUrl", "https://api.openai.com/v1"),
                Map.of("value", "aliyun", "label", "阿里云（通义千问）", "baseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
                Map.of("value", "deepseek", "label", "DeepSeek", "baseUrl", "https://api.deepseek.com/v1"),
                Map.of("value", "anthropic", "label", "Anthropic（Claude）", "baseUrl", "https://api.anthropic.com/v1"),
                Map.of("value", "glm", "label", "智谱AI（GLM）", "baseUrl", "https://open.bigmodel.cn/api/paas/v4"),
                Map.of("value", "moonshot", "label", "Moonshot（Kimi）", "baseUrl", "https://api.moonshot.cn/v1"),
                Map.of("value", "other", "label", "自定义", "baseUrl", "")
        ));
    }

    /**
     * 转换为安全的 Map（不包含 API Key）
     */
    private Map<String, Object> toSafeMap(AiModelConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", config.getId());
        map.put("name", config.getName());
        map.put("provider", config.getProvider());
        map.put("baseUrl", config.getBaseUrl());
        map.put("modelName", config.getModelName());
        map.put("isDefault", config.getIsDefault());
        map.put("enabled", config.getEnabled());
        map.put("createTime", config.getCreateTime());
        map.put("updateTime", config.getUpdateTime());
        // API Key 只返回前4位和后4位
        if (config.getApiKey() != null && config.getApiKey().length() > 16) {
            map.put("apiKeyPreview", config.getApiKey().substring(0, 8) + "****");
        } else {
            map.put("apiKeyPreview", "****");
        }
        return map;
    }
}
