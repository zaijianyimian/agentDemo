package com.example.demo.service.chatimport;

import com.example.demo.entity.ChatHistory;
import com.example.demo.entity.VirtualAssistant;
import com.example.demo.mapper.ChatHistoryMapper;
import com.example.demo.mapper.VirtualAssistantMapper;
import com.example.demo.properties.QdrantProperties;
import com.example.demo.service.SystemSettingsService;
import com.example.demo.service.memory.EmbeddingCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 聊天记录向量化服务
 * 将导入的聊天记录向量化并存储到Qdrant
 */
@Slf4j
@Service
public class ChatHistoryVectorService {

    private final ChatHistoryMapper chatHistoryMapper;
    private final VirtualAssistantMapper virtualAssistantMapper;
    private final EmbeddingCacheService embeddingCacheService;
    private final SystemSettingsService settingsService;
    private final QdrantProperties qdrantProperties;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 50;

    public ChatHistoryVectorService(ChatHistoryMapper chatHistoryMapper,
                                    VirtualAssistantMapper virtualAssistantMapper,
                                    EmbeddingCacheService embeddingCacheService,
                                    SystemSettingsService settingsService,
                                    QdrantProperties qdrantProperties,
                                    ObjectMapper objectMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.virtualAssistantMapper = virtualAssistantMapper;
        this.embeddingCacheService = embeddingCacheService;
        this.settingsService = settingsService;
        this.qdrantProperties = qdrantProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建虚拟助手并开始向量化训练
     * @param name 助手名称
     * @param description 助手描述
     * @param platform 来源平台
     * @param sessionIds 要训练的会话ID列表
     * @return 创建的虚拟助手
     */
    public VirtualAssistant createAssistant(String name, String description, String platform, List<String> sessionIds) {
        // 生成唯一的集合名称
        String collectionName = "assistant_" + System.currentTimeMillis();

        VirtualAssistant assistant = VirtualAssistant.builder()
                .name(name)
                .description(description)
                .sourcePlatform(platform)
                .trainedMessages(0)
                .collectionName(collectionName)
                .enabled(true)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        virtualAssistantMapper.insert(assistant);

        // 将聊天记录关联到助手
        for (String sessionId : sessionIds) {
            List<ChatHistory> messages = chatHistoryMapper.findBySessionId(sessionId);
            for (ChatHistory message : messages) {
                message.setAssistantId(assistant.getId());
                chatHistoryMapper.updateById(message);
            }
        }

        // 异步开始向量化
        startVectorizationAsync(assistant.getId());

        return assistant;
    }

    /**
     * 异步向量化聊天记录
     */
    @Async
    public void startVectorizationAsync(Long assistantId) {
        try {
            vectorizeAssistantMessages(assistantId);
        } catch (Exception e) {
            log.error("向量化助手消息失败: assistantId={}", assistantId, e);
        }
    }

    /**
     * 向量化指定助手的所有消息
     */
    public int vectorizeAssistantMessages(Long assistantId) {
        VirtualAssistant assistant = virtualAssistantMapper.selectById(assistantId);
        if (assistant == null) {
            log.warn("助手不存在: id={}", assistantId);
            return 0;
        }

        int totalVectorized = 0;
        List<ChatHistory> batch;

        while (true) {
            // 获取未向量化的消息批次
            batch = chatHistoryMapper.findUnvectorizedByAssistantId(assistantId, BATCH_SIZE);
            if (batch.isEmpty()) {
                break;
            }

            // 批量向量化
            List<Map<String, Object>> points = new ArrayList<>();
            for (ChatHistory message : batch) {
                try {
                    String text = buildVectorText(message);
                    if (text.isBlank()) {
                        continue;
                    }

                    Embedding embedding = embeddingCacheService.getEmbedding(text, "chat:" + message.getId());
                    float[] vector = embedding.vector();

                    // 确保集合存在
                    ensureCollection(assistant.getCollectionName(), vector.length);

                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("messageId", message.getId());
                    payload.put("sessionId", message.getSessionId());
                    payload.put("sender", message.getSender());
                    payload.put("senderType", message.getSenderType());
                    payload.put("content", truncate(message.getContent(), 500));
                    payload.put("messageTime", message.getMessageTime() != null ? message.getMessageTime().toString() : "");

                    points.add(Map.of(
                            "id", message.getId(),
                            "vector", toList(vector),
                            "payload", payload
                    ));
                } catch (Exception e) {
                    log.warn("向量化消息失败: messageId={}", message.getId(), e);
                }
            }

            if (!points.isEmpty()) {
                // 批量写入向量数据库
                try {
                    Map<String, Object> body = Map.of("points", points);
                    sendJson("PUT", "/collections/" + assistant.getCollectionName() + "/points?wait=true", body);

                    // 更新向量化状态
                    List<Long> ids = batch.stream()
                            .map(ChatHistory::getId)
                            .collect(Collectors.toList());
                    chatHistoryMapper.batchUpdateVectorized(ids);
                    totalVectorized += points.size();
                } catch (Exception e) {
                    log.error("批量写入向量失败: assistantId={}", assistantId, e);
                    break;
                }
            }
        }

        // 更新助手训练数量
        assistant.setTrainedMessages(chatHistoryMapper.countByAssistantId(assistantId));
        assistant.setUpdateTime(LocalDateTime.now());
        virtualAssistantMapper.updateById(assistant);

        log.info("助手向量化完成: assistantId={}, vectorized={}", assistantId, totalVectorized);
        return totalVectorized;
    }

    /**
     * 搜索相似消息
     */
    public List<Map<String, Object>> searchSimilarMessages(Long assistantId, String query, int topK) {
        VirtualAssistant assistant = virtualAssistantMapper.selectById(assistantId);
        if (assistant == null || !Boolean.TRUE.equals(assistant.getEnabled())) {
            return List.of();
        }

        try {
            Embedding embedding = embeddingCacheService.getEmbedding(query);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", toList(embedding.vector()));
            body.put("limit", Math.max(1, topK));
            body.put("with_payload", true);
            body.put("score_threshold", 0.4);

            var response = sendJson("POST", "/collections/" + assistant.getCollectionName() + "/points/search", body);
            var result = response.path("result");

            if (!result.isArray()) {
                return List.of();
            }

            List<Map<String, Object>> hits = new ArrayList<>();
            for (var item : result) {
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("score", item.path("score").asDouble());
                hit.put("messageId", item.path("id").asLong());
                var payload = item.path("payload");
                hit.put("sender", payload.path("sender").asText(""));
                hit.put("senderType", payload.path("senderType").asText(""));
                hit.put("content", payload.path("content").asText(""));
                hit.put("messageTime", payload.path("messageTime").asText(""));
                hits.add(hit);
            }
            return hits;
        } catch (Exception e) {
            log.warn("搜索相似消息失败: assistantId={}", assistantId, e);
            return List.of();
        }
    }

    /**
     * 删除助手的向量集合
     */
    public void deleteAssistantVectors(Long assistantId) {
        VirtualAssistant assistant = virtualAssistantMapper.selectById(assistantId);
        if (assistant == null) {
            return;
        }

        try {
            sendJson("DELETE", "/collections/" + assistant.getCollectionName(), null);
        } catch (Exception e) {
            log.warn("删除向量集合失败: collectionName={}", assistant.getCollectionName(), e);
        }

        // 删除关联的聊天记录
        chatHistoryMapper.deleteByAssistantId(assistantId);
    }

    /**
     * 构建用于向量化的文本
     */
    private String buildVectorText(ChatHistory message) {
        StringBuilder sb = new StringBuilder();
        if (message.getSender() != null && !message.getSender().isBlank()) {
            sb.append(message.getSender()).append(": ");
        }
        sb.append(safeText(message.getContent()));
        return sb.toString().trim();
    }

    /**
     * 确保向量集合存在
     */
    private void ensureCollection(String collectionName, int vectorSize) {
        try {
            sendRaw("GET", "/collections/" + collectionName, null, false);
        } catch (Exception ignored) {
            try {
                Map<String, Object> body = Map.of(
                        "vectors", Map.of(
                                "size", vectorSize,
                                "distance", "Cosine"
                        )
                );
                sendJson("PUT", "/collections/" + collectionName, body);
            } catch (Exception e) {
                throw new IllegalStateException("创建向量集合失败: " + collectionName, e);
            }
        }
    }

    private com.fasterxml.jackson.databind.JsonNode sendJson(String method, String path, Object body) throws Exception {
        String response = sendRaw(method, path, body != null ? objectMapper.writeValueAsString(body) : null, true);
        return objectMapper.readTree(response);
    }

    private String sendRaw(String method, String path, String body, boolean tolerateDelete404) throws Exception {
        String host = normalizeHttpHost(settingsService.getSetting("qdrant", "host", qdrantProperties.getHost()));
        int port = resolveRestPort();
        URL url = new URL(host + ":" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Content-Type", "application/json");

        if (body != null) {
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = conn.getResponseCode();
        if ((status >= 200 && status < 300) || (tolerateDelete404 && status == 404)) {
            try (InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
                if (is == null) {
                    return "{}";
                }
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } finally {
                conn.disconnect();
            }
        }

        String error = "";
        try (InputStream is = conn.getErrorStream()) {
            if (is != null) {
                error = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
        throw new IllegalStateException("Qdrant请求失败: " + status + " " + error);
    }

    private int resolveRestPort() {
        int grpcPort = settingsService.getIntSetting("qdrant", "port", qdrantProperties.getPort());
        return settingsService.getIntSetting("qdrant", "rest_port", grpcPort == 6334 ? 6333 : grpcPort);
    }

    private String normalizeHttpHost(String host) {
        if (host == null || host.isBlank()) {
            return "http://localhost";
        }
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return host.replaceAll(":\\d+$", "");
        }
        return "http://" + host.replaceAll(":\\d+$", "");
    }

    private List<Float> toList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
    }

    private String truncate(String text, int maxLength) {
        String normalized = safeText(text);
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }
}