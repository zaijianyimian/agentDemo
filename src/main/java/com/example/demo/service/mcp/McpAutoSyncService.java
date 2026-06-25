package com.example.demo.service.mcp;

import com.example.demo.entity.McpTool;
import com.example.demo.entity.ToolType;
import com.example.demo.mapper.McpToolMapper;
import com.example.demo.properties.McpAutoSyncProperties;
import com.example.demo.service.ToolCacheRefreshEvent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpAutoSyncService {

    private static final String META_MARKER = "[AUTO_SYNC_META]";

    private final McpToolMapper mcpToolMapper;
    private final ObjectMapper objectMapper;
    private final McpAutoSyncProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    private final AtomicReference<SyncResult> lastSyncResult = new AtomicReference<>();

    @Scheduled(cron = "${app.mcp.auto-sync.cron:0 */5 * * * ?}")
    public void scheduledSync() {
        if (!properties.isEnabled()) {
            return;
        }
        syncNow(false);
    }

    public SyncResult getLastSyncResult() {
        return lastSyncResult.get();
    }

    @Transactional
    public SyncResult syncNow(boolean dryRun) {
        LocalDateTime start = LocalDateTime.now();
        SyncStats stats = new SyncStats();
        List<String> warnings = new ArrayList<>();

        try {
            String url = safe(properties.getUrl());
            if (url.isBlank()) {
                SyncResult result = SyncResult.builder()
                        .success(false)
                        .startTime(start)
                        .endTime(LocalDateTime.now())
                        .stats(stats)
                        .warnings(List.of("未配置 app.mcp.auto-sync.url"))
                        .message("同步失败：未配置远端地址")
                        .build();
                lastSyncResult.set(result);
                return result;
            }

            List<RemoteToolPayload> remoteTools = fetchRemoteTools(url);
            stats.setRemoteCount(remoteTools.size());
            Set<String> seenManagedNames = new HashSet<>();
            boolean changed = false;

            for (RemoteToolPayload payload : remoteTools) {
                String toolName = safe(payload.getName());
                if (toolName.isBlank()) {
                    stats.setSkipped(stats.getSkipped() + 1);
                    warnings.add("发现缺失 name 的远端工具，已跳过");
                    continue;
                }

                ToolType toolType;
                try {
                    toolType = parseToolType(payload.getToolType());
                } catch (Exception ex) {
                    stats.setSkipped(stats.getSkipped() + 1);
                    warnings.add("工具 " + toolName + " 的 toolType 非法，已跳过");
                    continue;
                }

                McpTool existing = mcpToolMapper.selectByName(toolName);
                if (existing == null) {
                    stats.setCreated(stats.getCreated() + 1);
                    changed = true;
                    if (!dryRun) {
                        McpTool tool = McpTool.builder()
                                .name(toolName)
                                .displayName(nonBlankOrDefault(payload.getDisplayName(), toolName))
                                .description(nonBlankOrDefault(payload.getDescription(), "Auto synced MCP tool"))
                                .toolType(toolType)
                                .config(toJson(payload.getConfig()))
                                .inputSchema(toJson(payload.getInputSchema()))
                                .enabled(payload.getEnabled() != null ? payload.getEnabled() : false)
                                .remark(withMeta(payload.getRemark(), AutoSyncMeta.newMeta(url)))
                                .createTime(LocalDateTime.now())
                                .updateTime(LocalDateTime.now())
                                .build();
                        mcpToolMapper.insert(tool);
                    }
                    seenManagedNames.add(toolName);
                    continue;
                }

                AutoSyncMeta meta = parseMeta(existing.getRemark());
                if (meta == null) {
                    // 不是自动同步托管的本地工具，避免覆盖用户手工工具
                    stats.setSkipped(stats.getSkipped() + 1);
                    warnings.add("工具 " + toolName + " 为手工配置，已跳过自动覆盖");
                    continue;
                }

                seenManagedNames.add(toolName);
                McpTool merged = merge(existing, payload, toolType, url);
                boolean rowChanged = hasChanged(existing, merged);
                if (rowChanged) {
                    stats.setUpdated(stats.getUpdated() + 1);
                    changed = true;
                    if (!dryRun) {
                        mcpToolMapper.updateById(merged);
                    }
                } else {
                    // 即使内容没变，也要刷新 lastSeen / missingCycles
                    changed = true;
                    if (!dryRun) {
                        merged.setUpdateTime(LocalDateTime.now());
                        mcpToolMapper.updateById(merged);
                    }
                }
            }

            if (properties.isDisableMissing()) {
                List<McpTool> all = mcpToolMapper.selectList(null);
                for (McpTool tool : all) {
                    AutoSyncMeta meta = parseMeta(tool.getRemark());
                    if (meta == null) {
                        continue;
                    }
                    if (seenManagedNames.contains(tool.getName())) {
                        continue;
                    }

                    int nextMissing = meta.getMissingCycles() + 1;
                    meta.setMissingCycles(nextMissing);
                    meta.setLastSeenAt(meta.getLastSeenAt() == null ? "" : meta.getLastSeenAt());

                    boolean needDisable = nextMissing >= Math.max(1, properties.getMissingThreshold());
                    if (needDisable && Boolean.TRUE.equals(tool.getEnabled())) {
                        stats.setDisabled(stats.getDisabled() + 1);
                        changed = true;
                        if (!dryRun) {
                            tool.setEnabled(false);
                            tool.setRemark(withMeta(stripMeta(tool.getRemark()), meta));
                            tool.setUpdateTime(LocalDateTime.now());
                            mcpToolMapper.updateById(tool);
                        }
                        continue;
                    }

                    if (!dryRun) {
                        tool.setRemark(withMeta(stripMeta(tool.getRemark()), meta));
                        tool.setUpdateTime(LocalDateTime.now());
                        mcpToolMapper.updateById(tool);
                    }
                }
            }

            if (changed && !dryRun) {
                eventPublisher.publishEvent(new ToolCacheRefreshEvent(this));
            }

            SyncResult result = SyncResult.builder()
                    .success(true)
                    .startTime(start)
                    .endTime(LocalDateTime.now())
                    .stats(stats)
                    .warnings(warnings)
                    .message("MCP 自动同步完成")
                    .build();
            lastSyncResult.set(result);
            return result;
        } catch (Exception e) {
            log.error("MCP 自动同步失败", e);
            SyncResult result = SyncResult.builder()
                    .success(false)
                    .startTime(start)
                    .endTime(LocalDateTime.now())
                    .stats(stats)
                    .warnings(warnings)
                    .message("同步失败: " + e.getMessage())
                    .build();
            lastSyncResult.set(result);
            return result;
        }
    }

    private List<RemoteToolPayload> fetchRemoteTools(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(2, properties.getConnectTimeoutSeconds())))
                .build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(3, properties.getReadTimeoutSeconds())))
                .header("Accept", "application/json")
                .GET();

        String token = safe(properties.getToken());
        if (!token.isBlank()) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("远端返回状态码: " + response.statusCode());
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            return List.of();
        }

        String trimmed = body.trim();
        if (trimmed.startsWith("[")) {
            return objectMapper.readValue(trimmed, new TypeReference<List<RemoteToolPayload>>() {
            });
        }

        SyncEnvelope envelope = objectMapper.readValue(trimmed, SyncEnvelope.class);
        if (envelope.getTools() == null) {
            return List.of();
        }
        return envelope.getTools();
    }

    private McpTool merge(McpTool existing, RemoteToolPayload payload, ToolType toolType, String sourceUrl) {
        AutoSyncMeta meta = parseMeta(existing.getRemark());
        if (meta == null) {
            meta = AutoSyncMeta.newMeta(sourceUrl);
        }
        meta.setLastSeenAt(LocalDateTime.now().toString());
        meta.setMissingCycles(0);
        meta.setSource(sourceUrl);

        McpTool merged = McpTool.builder()
                .id(existing.getId())
                .name(existing.getName())
                .displayName(nonBlankOrDefault(payload.getDisplayName(), existing.getDisplayName()))
                .description(nonBlankOrDefault(payload.getDescription(), existing.getDescription()))
                .toolType(toolType)
                .config(toJson(payload.getConfig(), existing.getConfig()))
                .inputSchema(toJson(payload.getInputSchema(), existing.getInputSchema()))
                .enabled(properties.isSyncEnabledState()
                        ? (payload.getEnabled() != null ? payload.getEnabled() : existing.getEnabled())
                        : existing.getEnabled())
                .remark(withMeta(nonBlankOrDefault(payload.getRemark(), stripMeta(existing.getRemark())), meta))
                .createTime(existing.getCreateTime())
                .updateTime(LocalDateTime.now())
                .build();
        return merged;
    }

    private boolean hasChanged(McpTool oldRow, McpTool newRow) {
        return !Objects.equals(oldRow.getDisplayName(), newRow.getDisplayName())
                || !Objects.equals(oldRow.getDescription(), newRow.getDescription())
                || !Objects.equals(oldRow.getToolType(), newRow.getToolType())
                || !Objects.equals(oldRow.getConfig(), newRow.getConfig())
                || !Objects.equals(oldRow.getInputSchema(), newRow.getInputSchema())
                || !Objects.equals(oldRow.getEnabled(), newRow.getEnabled())
                || !Objects.equals(oldRow.getRemark(), newRow.getRemark());
    }

    private ToolType parseToolType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("toolType 不能为空");
        }
        return ToolType.valueOf(value.trim().toUpperCase());
    }

    private String toJson(Object value) {
        return toJson(value, "{}");
    }

    private String toJson(Object value, String fallback) {
        try {
            if (value == null) {
                return fallback;
            }
            if (value instanceof String s) {
                return s;
            }
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String nonBlankOrDefault(String value, String fallback) {
        String safeValue = safe(value);
        if (!safeValue.isBlank()) {
            return safeValue;
        }
        return fallback;
    }

    private AutoSyncMeta parseMeta(String remark) {
        if (remark == null || remark.isBlank()) {
            return null;
        }
        int index = remark.lastIndexOf(META_MARKER);
        if (index < 0) {
            return null;
        }
        String json = remark.substring(index + META_MARKER.length()).trim();
        if (json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AutoSyncMeta.class);
        } catch (Exception e) {
            log.debug("解析 MCP 自动同步元数据失败: {}", e.getMessage());
            return null;
        }
    }

    private String stripMeta(String remark) {
        if (remark == null || remark.isBlank()) {
            return "";
        }
        int index = remark.lastIndexOf(META_MARKER);
        if (index < 0) {
            return remark.trim();
        }
        return remark.substring(0, index).trim();
    }

    private String withMeta(String humanRemark, AutoSyncMeta meta) {
        try {
            String base = safe(humanRemark);
            String metaJson = objectMapper.writeValueAsString(meta);
            if (base.isBlank()) {
                return META_MARKER + " " + metaJson;
            }
            return base + "\n" + META_MARKER + " " + metaJson;
        } catch (Exception e) {
            return humanRemark;
        }
    }

    @Data
    @Builder
    public static class SyncResult {
        private boolean success;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private SyncStats stats;
        private List<String> warnings;
        private String message;
    }

    @Data
    public static class SyncStats {
        private int remoteCount;
        private int created;
        private int updated;
        private int disabled;
        private int skipped;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SyncEnvelope {
        private List<RemoteToolPayload> tools = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RemoteToolPayload {
        private String name;
        private String displayName;
        private String description;
        private String toolType;
        private Object config;
        private Object inputSchema;
        private Boolean enabled;
        private String remark;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AutoSyncMeta {
        private String source;
        private String lastSeenAt;
        private int missingCycles;

        public static AutoSyncMeta newMeta(String source) {
            AutoSyncMeta meta = new AutoSyncMeta();
            meta.setSource(source);
            meta.setLastSeenAt(LocalDateTime.now().toString());
            meta.setMissingCycles(0);
            return meta;
        }
    }
}
