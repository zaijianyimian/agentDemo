package com.example.demo.service.note;

import com.example.demo.dto.NoteSemanticHit;
import com.example.demo.entity.Note;
import com.example.demo.mapper.NoteMapper;
import com.example.demo.properties.QdrantProperties;
import com.example.demo.service.memory.EmbeddingCacheService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NoteVectorService {

    private static final String COLLECTION_NAME = "agent_notes";

    private final EmbeddingCacheService embeddingCacheService;
    private final QdrantProperties qdrantProperties;
    private final NoteMapper noteMapper;
    private final ObjectMapper objectMapper;

    public NoteVectorService(EmbeddingCacheService embeddingCacheService,
                             QdrantProperties qdrantProperties,
                             NoteMapper noteMapper,
                             ObjectMapper objectMapper) {
        this.embeddingCacheService = embeddingCacheService;
        this.qdrantProperties = qdrantProperties;
        this.noteMapper = noteMapper;
        this.objectMapper = objectMapper;
    }

    public void syncNote(Note note) {
        if (note == null || note.getId() == null) {
            return;
        }
        String text = buildIndexText(note);
        if (text.isBlank()) {
            deleteNote(note.getId());
            return;
        }

        try {
            Embedding embedding = embeddingCacheService.getEmbedding(text, "note:" + note.getId() + ":" + text.hashCode());
            float[] vector = embedding.vector();
            ensureCollection(vector.length);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("noteId", note.getId());
            payload.put("title", safeText(note.getTitle()));
            payload.put("tags", safeText(note.getTags()));
            payload.put("aiSummary", safeText(note.getAiSummary()));
            payload.put("snippet", truncate(note.getContent(), 220));
            payload.put("updatedAt", note.getUpdateTime() != null ? note.getUpdateTime().toString() : "");

            Map<String, Object> body = Map.of(
                    "points", List.of(Map.of(
                            "id", note.getId(),
                            "vector", toList(vector),
                            "payload", payload
                    ))
            );
            sendJson("PUT", "/collections/" + COLLECTION_NAME + "/points?wait=true", body);
        } catch (Exception e) {
            log.warn("同步笔记向量失败: noteId={}", note.getId(), e);
        }
    }

    public void deleteNote(Long noteId) {
        if (noteId == null) {
            return;
        }
        try {
            sendJson("POST", "/collections/" + COLLECTION_NAME + "/points/delete?wait=true",
                    Map.of("points", List.of(noteId)));
        } catch (Exception e) {
            log.debug("删除笔记向量失败或集合不存在: noteId={}", noteId, e);
        }
    }

    public int reindexAll() {
        List<Note> notes = noteMapper.findAllOrderByPinnedAndTime();
        if (notes.isEmpty()) {
            return 0;
        }
        notes.stream()
                .sorted(Comparator.comparing(Note::getId))
                .forEach(this::syncNote);
        return notes.size();
    }

    public List<NoteSemanticHit> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            Embedding embedding = embeddingCacheService.getEmbedding(query, "note-search:" + query.hashCode());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vector", toList(embedding.vector()));
            body.put("limit", Math.max(1, topK));
            body.put("with_payload", true);
            body.put("score_threshold", 0.35);

            JsonNode root = sendJson("POST", "/collections/" + COLLECTION_NAME + "/points/search", body);
            JsonNode result = root.path("result");
            if (!result.isArray()) {
                return List.of();
            }

            List<NoteSemanticHit> hits = new ArrayList<>();
            for (JsonNode item : result) {
                Long noteId = item.path("id").isNumber() ? item.path("id").asLong() : null;
                Note note = noteId != null ? noteMapper.selectById(noteId) : null;
                JsonNode payload = item.path("payload");
                hits.add(NoteSemanticHit.builder()
                        .noteId(noteId)
                        .title(pickValue(note != null ? note.getTitle() : null, payload.path("title").asText("")))
                        .contentSnippet(pickValue(note != null ? truncate(note.getContent(), 220) : null, payload.path("snippet").asText("")))
                        .tags(pickValue(note != null ? note.getTags() : null, payload.path("tags").asText("")))
                        .aiSummary(pickValue(note != null ? note.getAiSummary() : null, payload.path("aiSummary").asText("")))
                        .score(item.path("score").asDouble())
                        .updateTime(note != null ? note.getUpdateTime() : parseTime(payload.path("updatedAt").asText("")))
                        .build());
            }
            return hits;
        } catch (Exception e) {
            log.warn("笔记语义检索失败", e);
            return List.of();
        }
    }

    private void ensureCollection(int vectorSize) {
        try {
            sendRaw("GET", "/collections/" + COLLECTION_NAME, null, false);
        } catch (Exception ignored) {
            try {
                Map<String, Object> body = Map.of(
                        "vectors", Map.of(
                                "size", vectorSize,
                                "distance", "Cosine"
                        )
                );
                sendJson("PUT", "/collections/" + COLLECTION_NAME, body);
            } catch (Exception e) {
                throw new IllegalStateException("创建笔记向量集合失败", e);
            }
        }
    }

    private JsonNode sendJson(String method, String path, Object body) throws Exception {
        String response = sendRaw(method, path, objectMapper.writeValueAsString(body), true);
        return objectMapper.readTree(response);
    }

    private String sendRaw(String method, String path, String body, boolean tolerateDelete404) throws Exception {
        String host = normalizeHttpHost(qdrantProperties.getHost());
        URL url = new URL(host + ":6333" + path);
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
        throw new IllegalStateException("Qdrant 请求失败: " + status + " " + error);
    }

    private String buildIndexText(Note note) {
        StringBuilder sb = new StringBuilder();
        appendLine(sb, note.getTitle());
        appendLine(sb, note.getTags());
        appendLine(sb, note.getAiSummary());
        appendLine(sb, note.getContent());
        return sb.toString().trim();
    }

    private void appendLine(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(value);
        }
    }

    private List<Float> toList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float value : vector) {
            list.add(value);
        }
        return list;
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

    private String pickValue(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private LocalDateTime parseTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}
