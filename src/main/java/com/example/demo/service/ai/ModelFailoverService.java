package com.example.demo.service.ai;

import com.example.demo.entity.AiModelConfig;
import com.example.demo.mapper.AiModelConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型故障转移服务
 * 当模型调用失败时，自动切换到下一个可用模型
 */
@Slf4j
@Service
public class ModelFailoverService {

    private final AiModelConfigMapper configMapper;
    private final ModelManager modelManager;

    // 模型健康状态缓存
    // key: modelId, value: HealthStatus
    private final Map<Long, HealthStatus> healthStatusMap = new ConcurrentHashMap<>();

    // 失败冷却时间（毫秒） - 失败后的模型在冷却时间内不会被优先选择
    private static final long FAILURE_COOLDOWN_MS = 60_000; // 1分钟

    // 最大失败计数阈值 - 达到后模型会被暂时禁用
    private static final int MAX_FAILURE_THRESHOLD = 3;

    public ModelFailoverService(AiModelConfigMapper configMapper, ModelManager modelManager) {
        this.configMapper = configMapper;
        this.modelManager = modelManager;
    }

    /**
     * 模型健康状态
     */
    public static class HealthStatus {
        private int failureCount;
        private Instant lastFailureTime;
        private Instant lastSuccessTime;
        private boolean temporarilyDisabled;

        public HealthStatus() {
            this.failureCount = 0;
            this.temporarilyDisabled = false;
        }

        public void recordFailure() {
            this.failureCount++;
            this.lastFailureTime = Instant.now();
            if (this.failureCount >= MAX_FAILURE_THRESHOLD) {
                this.temporarilyDisabled = true;
            }
        }

        public void recordSuccess() {
            this.failureCount = 0;
            this.lastSuccessTime = Instant.now();
            this.temporarilyDisabled = false;
        }

        public boolean isInCooldown() {
            if (lastFailureTime == null) return false;
            return Instant.now().toEpochMilli() - lastFailureTime.toEpochMilli() < FAILURE_COOLDOWN_MS;
        }

        public boolean isAvailable() {
            return !temporarilyDisabled || !isInCooldown();
        }

        public int getFailureCount() { return failureCount; }
        public Instant getLastFailureTime() { return lastFailureTime; }
        public Instant getLastSuccessTime() { return lastSuccessTime; }
        public boolean isTemporarilyDisabled() { return temporarilyDisabled; }
    }

    /**
     * 获取所有可用模型（按优先级排序）
     * 优先级：默认模型 > 最近成功的模型 > 冷却中的模型
     */
    public List<AiModelConfig> getAvailableModelsSorted() {
        List<AiModelConfig> enabledModels = configMapper.selectEnabled();
        AiModelConfig defaultModel = configMapper.selectDefault();

        // 排序：默认模型优先，然后按健康状态排序
        List<AiModelConfig> sortedModels = new ArrayList<>(enabledModels);

        sortedModels.sort((m1, m2) -> {
            // 默认模型最高优先级
            if (Boolean.TRUE.equals(m1.getIsDefault())) return -1;
            if (Boolean.TRUE.equals(m2.getIsDefault())) return 1;

            // 检查健康状态
            HealthStatus h1 = healthStatusMap.getOrDefault(m1.getId(), new HealthStatus());
            HealthStatus h2 = healthStatusMap.getOrDefault(m2.getId(), new HealthStatus());

            // 暂时禁用的模型最低优先级
            if (h1.isTemporarilyDisabled() && !h2.isTemporarilyDisabled()) return 1;
            if (!h1.isTemporarilyDisabled() && h2.isTemporarilyDisabled()) return -1;

            // 冷却中的模型次低优先级
            if (h1.isInCooldown() && !h2.isInCooldown()) return 1;
            if (!h1.isInCooldown() && h2.isInCooldown()) return -1;

            // 失败次数少的优先
            return Integer.compare(h1.getFailureCount(), h2.getFailureCount());
        });

        return sortedModels;
    }

    /**
     * 获取下一个可用的模型ID
     * @param currentModelId 当前失败的模型ID
     * @return 下一个可用模型ID，如果没有可用模型则返回null
     */
    public Long getNextAvailableModelId(Long currentModelId) {
        List<AiModelConfig> availableModels = getAvailableModelsSorted();

        // 过滤掉当前失败的模型和不可用的模型
        for (AiModelConfig model : availableModels) {
            if (model.getId().equals(currentModelId)) continue;

            HealthStatus status = healthStatusMap.getOrDefault(model.getId(), new HealthStatus());
            if (status.isAvailable()) {
                log.info("切换模型: 从 {} 切换到 {} ({})", currentModelId, model.getId(), model.getName());
                return model.getId();
            }
        }

        // 如果没有其他可用模型，检查是否可以使用冷却中的模型
        for (AiModelConfig model : availableModels) {
            if (model.getId().equals(currentModelId)) continue;
            log.warn("没有健康的备用模型，使用冷却中的模型: {} ({})", model.getId(), model.getName());
            return model.getId();
        }

        log.error("没有可用的备用模型");
        return null;
    }

    /**
     * 记录模型失败
     * @param modelId 模型ID
     * @param errorMessage 错误信息
     */
    public void recordFailure(Long modelId, String errorMessage) {
        HealthStatus status = healthStatusMap.computeIfAbsent(modelId, k -> new HealthStatus());
        status.recordFailure();
        log.warn("模型 {} 失败 (第{}次): {} - 是否暂时禁用: {}",
                modelId, status.getFailureCount(), errorMessage, status.isTemporarilyDisabled());

        // 清除该模型的缓存，下次使用时重新创建
        modelManager.removeModel(modelId);
    }

    /**
     * 记录模型成功
     * @param modelId 模型ID
     */
    public void recordSuccess(Long modelId) {
        HealthStatus status = healthStatusMap.computeIfAbsent(modelId, k -> new HealthStatus());
        status.recordSuccess();
        log.debug("模型 {} 成功", modelId);
    }

    /**
     * 获取模型健康状态
     * @param modelId 模型ID
     */
    public HealthStatus getHealthStatus(Long modelId) {
        return healthStatusMap.getOrDefault(modelId, new HealthStatus());
    }

    /**
     * 获取所有模型的健康状态摘要
     */
    public Map<Long, HealthStatus> getAllHealthStatus() {
        return new HashMap<>(healthStatusMap);
    }

    /**
     * 重置模型健康状态（手动恢复）
     * @param modelId 模型ID
     */
    public void resetHealthStatus(Long modelId) {
        healthStatusMap.remove(modelId);
        modelManager.refreshModel(modelId);
        log.info("重置模型 {} 的健康状态", modelId);
    }

    /**
     * 检查模型是否可用
     * @param modelId 模型ID
     */
    public boolean isModelAvailable(Long modelId) {
        HealthStatus status = healthStatusMap.getOrDefault(modelId, new HealthStatus());
        return status.isAvailable();
    }

    /**
     * 获取首选模型ID（考虑健康状态）
     */
    public Long getPreferredModelId() {
        List<AiModelConfig> availableModels = getAvailableModelsSorted();
        if (availableModels.isEmpty()) {
            return ModelManager.FALLBACK_MODEL_ID;
        }
        return availableModels.get(0).getId();
    }
}