package com.example.demo.service;

import com.example.demo.properties.QdrantProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant 向量数据库导出/导入服务
 * 支持导出所有 collection 的向量点和 payload，以及导入恢复
 */
@Slf4j
@Service
public class QdrantVectorExportService {

    private static final int BATCH_SIZE = 100;  // 每批处理的点数量
    private static final int TIMEOUT = 30000;   // 请求超时时间（毫秒）

    private final QdrantProperties qdrantProperties;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;

    public QdrantVectorExportService(QdrantProperties qdrantProperties,
                                     SystemSettingsService settingsService,
                                     ObjectMapper objectMapper) {
        this.qdrantProperties = qdrantProperties;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    /**
     * 导出所有向量数据
     *
     * @return 包含所有 collection 数据的 Map
     */
    public Map<String, Object> exportAllVectors() {
        Map<String, Object> exportData = new LinkedHashMap<>();
        exportData.put("version", "1.0");
        exportData.put("exportedAt", java.time.LocalDateTime.now().toString());

        try {
            // 获取所有 collection
            List<String> collections = listCollections();
            log.info("发现 {} 个向量集合", collections.size());

            Map<String, Object> collectionsData = new LinkedHashMap<>();

            for (String collectionName : collections) {
                try {
                    Map<String, Object> collectionData = exportCollection(collectionName);
                    collectionsData.put(collectionName, collectionData);
                    log.info("导出向量集合: {} ({} 个向量点)",
                            collectionName,
                            collectionData.get("pointsCount"));
                } catch (Exception e) {
                    log.warn("导出向量集合失败: {}", collectionName, e);
                    collectionsData.put(collectionName, Map.of(
                            "error", e.getMessage(),
                            "exported", false
                    ));
                }
            }

            exportData.put("collections", collectionsData);
            exportData.put("totalCollections", collections.size());

        } catch (Exception e) {
            log.error("导出向量数据失败", e);
            exportData.put("error", e.getMessage());
        }

        return exportData;
    }

    /**
     * 导出单个 collection 的数据
     */
    private Map<String, Object> exportCollection(String collectionName) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        // 获取 collection 信息
        JsonNode collectionInfo = sendRequest("GET", "/collections/" + collectionName, null);
        result.put("info", objectMapper.convertValue(collectionInfo.path("result"), Map.class));

        // 获取所有向量点
        List<Map<String, Object>> allPoints = new ArrayList<>();
        int offset = 0;
        int totalPoints = 0;

        while (true) {
            Map<String, Object> scrollBody = new LinkedHashMap<>();
            scrollBody.put("limit", BATCH_SIZE);
            scrollBody.put("with_payload", true);
            scrollBody.put("with_vector", true);
            if (offset > 0) {
                scrollBody.put("offset", offset);
            }

            JsonNode scrollResult = sendRequest("POST",
                    "/collections/" + collectionName + "/points/scroll",
                    scrollBody);

            JsonNode points = scrollResult.path("result").path("points");
            if (!points.isArray() || points.isEmpty()) {
                break;
            }

            for (JsonNode point : points) {
                Map<String, Object> pointData = new LinkedHashMap<>();
                pointData.put("id", objectMapper.convertValue(point.path("id"), Object.class));
                pointData.put("vector", objectMapper.convertValue(point.path("vector"), List.class));
                pointData.put("payload", objectMapper.convertValue(point.path("payload"), Map.class));
                allPoints.add(pointData);
            }

            totalPoints += points.size();
            offset += BATCH_SIZE;

            // 检查是否还有更多数据
            JsonNode nextPageOffset = scrollResult.path("result").path("next_page_offset");
            if (nextPageOffset.isMissingNode() || nextPageOffset.isNull()) {
                break;
            }
        }

        result.put("points", allPoints);
        result.put("pointsCount", allPoints.size());
        result.put("exported", true);

        return result;
    }

    /**
     * 导入向量数据
     *
     * @param importData 导出的数据
     * @param replaceExisting 是否替换现有数据
     * @return 导入结果统计
     */
    public Map<String, Object> importVectors(Map<String, Object> importData, boolean replaceExisting) {
        Map<String, Object> result = new LinkedHashMap<>();
        int totalCollections = 0;
        int totalPoints = 0;
        int failedCollections = 0;

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> collections = (Map<String, Object>) importData.get("collections");

            if (collections == null || collections.isEmpty()) {
                result.put("message", "没有向量数据需要导入");
                return result;
            }

            for (Map.Entry<String, Object> entry : collections.entrySet()) {
                String collectionName = entry.getKey();
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> collectionData = (Map<String, Object>) entry.getValue();

                    // 检查是否导出成功
                    if (!Boolean.TRUE.equals(collectionData.get("exported"))) {
                        log.warn("跳过未成功导出的集合: {}", collectionName);
                        failedCollections++;
                        continue;
                    }

                    int imported = importCollection(collectionName, collectionData, replaceExisting);
                    totalCollections++;
                    totalPoints += imported;
                    log.info("导入向量集合: {} ({} 个向量点)", collectionName, imported);

                } catch (Exception e) {
                    log.error("导入向量集合失败: {}", collectionName, e);
                    failedCollections++;
                }
            }

            result.put("importedCollections", totalCollections);
            result.put("importedPoints", totalPoints);
            result.put("failedCollections", failedCollections);
            result.put("success", failedCollections == 0);

        } catch (Exception e) {
            log.error("导入向量数据失败", e);
            result.put("error", e.getMessage());
            result.put("success", false);
        }

        return result;
    }

    /**
     * 导入单个 collection
     */
    @SuppressWarnings("unchecked")
    private int importCollection(String collectionName, Map<String, Object> collectionData,
                                 boolean replaceExisting) throws Exception {
        // 检查 collection 是否存在
        boolean exists = collectionExists(collectionName);

        // 获取向量维度
        Map<String, Object> info = (Map<String, Object>) collectionData.get("info");
        int vectorSize = extractVectorSize(info);

        if (exists && replaceExisting) {
            // 删除现有 collection
            sendRequest("DELETE", "/collections/" + collectionName, null);
            exists = false;
        }

        if (!exists) {
            // 创建新的 collection
            createCollection(collectionName, vectorSize);
        }

        // 导入向量点
        List<Map<String, Object>> points = (List<Map<String, Object>>) collectionData.get("points");
        if (points == null || points.isEmpty()) {
            return 0;
        }

        // 批量导入
        int imported = 0;
        for (int i = 0; i < points.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, points.size());
            List<Map<String, Object>> batch = points.subList(i, end);

            Map<String, Object> upsertBody = new LinkedHashMap<>();
            upsertBody.put("points", batch);

            sendRequest("PUT", "/collections/" + collectionName + "/points?wait=true", upsertBody);
            imported += batch.size();
        }

        return imported;
    }

    /**
     * 从 collection info 中提取向量维度
     */
    @SuppressWarnings("unchecked")
    private int extractVectorSize(Map<String, Object> info) {
        if (info == null) {
            return 1536;  // 默认维度
        }
        try {
            Map<String, Object> result = (Map<String, Object>) info.get("result");
            if (result != null) {
                Map<String, Object> config = (Map<String, Object>) result.get("config");
                if (config != null) {
                    Map<String, Object> params = (Map<String, Object>) config.get("params");
                    if (params != null) {
                        Map<String, Object> vectors = (Map<String, Object>) params.get("vectors");
                        if (vectors != null) {
                            Object size = vectors.get("size");
                            if (size instanceof Number) {
                                return ((Number) size).intValue();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("提取向量维度失败，使用默认值", e);
        }
        return 1536;
    }

    /**
     * 创建 collection
     */
    private void createCollection(String collectionName, int vectorSize) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vectors", Map.of(
                "size", vectorSize,
                "distance", "Cosine"
        ));
        sendRequest("PUT", "/collections/" + collectionName, body);
        log.info("创建向量集合: {} (维度: {})", collectionName, vectorSize);
    }

    /**
     * 检查 collection 是否存在
     */
    private boolean collectionExists(String collectionName) {
        try {
            sendRequest("GET", "/collections/" + collectionName, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取所有 collection 列表
     */
    private List<String> listCollections() throws Exception {
        JsonNode response = sendRequest("GET", "/collections", null);
        List<String> collections = new ArrayList<>();

        JsonNode result = response.path("result").path("collections");
        if (result.isArray()) {
            for (JsonNode collection : result) {
                String name = collection.path("name").asText();
                if (name != null && !name.isEmpty()) {
                    collections.add(name);
                }
            }
        }

        return collections;
    }

    /**
     * 发送 HTTP 请求到 Qdrant
     */
    private JsonNode sendRequest(String method, String path, Object body) throws Exception {
        String host = normalizeHttpHost(settingsService.getSetting("qdrant", "host", qdrantProperties.getHost()));
        int restPort = resolveRestPort();

        URL url = new URL(host + ":" + restPort + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT);
        conn.setReadTimeout(TIMEOUT);
        conn.setRequestProperty("Content-Type", "application/json");

        if (body != null) {
            conn.setDoOutput(true);
            String jsonBody = objectMapper.writeValueAsString(body);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = conn.getResponseCode();
        String response;

        if (status >= 200 && status < 300) {
            try (InputStream is = conn.getInputStream()) {
                response = readAllBytes(is);
            }
        } else {
            String error;
            try (InputStream is = conn.getErrorStream()) {
                error = is != null ? readAllBytes(is) : "Unknown error";
            }
            conn.disconnect();
            throw new IOException("Qdrant 请求失败 [" + status + "]: " + error);
        }

        conn.disconnect();
        return objectMapper.readTree(response);
    }

    private String readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int n;
        while ((n = is.read(data)) != -1) {
            buffer.write(data, 0, n);
        }
        return buffer.toString(StandardCharsets.UTF_8);
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

    private int resolveRestPort() {
        int grpcPort = settingsService.getIntSetting("qdrant", "port", qdrantProperties.getPort());
        return settingsService.getIntSetting("qdrant", "rest_port", grpcPort == 6334 ? 6333 : grpcPort);
    }
}