package com.example.demo.service.knowledge;

import com.example.demo.entity.KnowledgeBase;
import com.example.demo.entity.KnowledgeDocument;
import com.example.demo.mapper.KnowledgeBaseMapper;
import com.example.demo.mapper.KnowledgeDocumentMapper;
import com.example.demo.properties.QdrantProperties;
import com.example.demo.service.SystemSettingsService;
import com.example.demo.service.memory.EmbeddingCacheService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import dev.langchain4j.data.document.Metadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 知识库服务
 * 处理文档上传、文本分块、向量化和智能问答
 */
@Slf4j
@Service
public class RagService {

    @Value("${app.knowledge.storage-path:./data/knowledge}")
    private String storagePath;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final EmbeddingCacheService embeddingCacheService;
    private final QdrantProperties qdrantProperties;
    private final SystemSettingsService systemSettingsService;

    // 每个知识库的 EmbeddingStore 缓存
    private final Map<String, EmbeddingStore<TextSegment>> embeddingStoreCache = new HashMap<>();

    public RagService(KnowledgeBaseMapper knowledgeBaseMapper,
                      KnowledgeDocumentMapper knowledgeDocumentMapper,
                      EmbeddingCacheService embeddingCacheService,
                      QdrantProperties qdrantProperties,
                      SystemSettingsService systemSettingsService) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.embeddingCacheService = embeddingCacheService;
        this.qdrantProperties = qdrantProperties;
        this.systemSettingsService = systemSettingsService;
    }

    @PostConstruct
    public void init() {
        try {
            Path path = Paths.get(storagePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("创建知识库存储目录: {}", storagePath);
            }
        } catch (IOException e) {
            log.error("创建存储目录失败: {}", storagePath, e);
        }
    }

    /**
     * 创建知识库
     */
    public KnowledgeBase createKnowledgeBase(KnowledgeBase kb) {
        // 生成集合名称
        String collectionName = "kb_" + UUID.randomUUID().toString().replace("-", "_").substring(0, 16);
        kb.setCollectionName(collectionName);
        kb.setDocumentCount(0);
        kb.setEnabled(true);

        // 在 Qdrant 创建集合（使用 HTTP API）
        createQdrantCollection(collectionName);

        knowledgeBaseMapper.insert(kb);
        log.info("知识库创建成功: {}, collection: {}", kb.getName(), collectionName);
        return kb;
    }

    /**
     * 在 Qdrant 创建向量集合（HTTP REST API）
     * HTTP REST API 使用端口 6333，gRPC 使用端口 6334
     */
    private void createQdrantCollection(String collectionName) {
        String host = normalizeHttpHost(qdrantProperties.getHost());
        // HTTP REST API 端口通常是 6333
        int restPort = 6333;

        try {
            String url = host + ":" + restPort + "/collections/" + collectionName;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("PUT");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            String json = """
            {
                "vectors": {
                    "size": 768,
                    "distance": "Cosine"
                }
            }
            """;

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int status = conn.getResponseCode();
            if (status == 200) {
                log.info("Qdrant 集合创建成功: {}", collectionName);
            } else {
                throw new IllegalStateException("创建集合返回状态异常: " + status);
            }
            conn.disconnect();
        } catch (Exception e) {
            log.error("创建 Qdrant 合失败: {}", collectionName, e);
            throw new IllegalStateException("创建知识库向量集合失败: " + collectionName, e);
        }
    }

    /**
     * 删除 Qdrant 集合（HTTP REST API）
     */
    private void deleteQdrantCollection(String collectionName) {
        String host = normalizeHttpHost(qdrantProperties.getHost());
        int restPort = 6333;

        try {
            String url = host + ":" + restPort + "/collections/" + collectionName;
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int status = conn.getResponseCode();
            if (status == 200) {
                log.info("Qdrant 集合删除成功: {}", collectionName);
            }
            conn.disconnect();
        } catch (Exception e) {
            log.warn("删除 Qdrant 集合失败: {}", collectionName, e);
        }
    }

    /**
     * 获取知识库的 EmbeddingStore
     */
    private EmbeddingStore<TextSegment> getEmbeddingStore(String collectionName) {
        return embeddingStoreCache.computeIfAbsent(collectionName, name ->
                QdrantEmbeddingStore.builder()
                        .host(qdrantProperties.getHost())
                        .port(qdrantProperties.getPort())
                        .collectionName(name)
                        .build()
        );
    }

    /**
     * 上传文档到知识库
     */
    public KnowledgeDocument uploadDocument(Long baseId, MultipartFile file) throws IOException {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(baseId);
        if (kb == null) {
            throw new IOException("知识库不存在: " + baseId);
        }

        // 验证文件
        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename);

        // 保存文件
        String uniqueFileName = UUID.randomUUID().toString() + "." + fileType;
        Path filePath = Paths.get(storagePath, kb.getCollectionName(), uniqueFileName);
        Files.createDirectories(filePath.getParent());
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 提取内容
        String content = extractContent(filePath, fileType);
        String contentHash = digest(content);

        // 单用户增强：支持去重与增量上传策略
        KnowledgeDocument existing = findDuplicateOrUnchanged(baseId, originalFilename, contentHash);
        if (existing != null) {
            // 去重命中时，删除本次临时落盘文件，避免产生孤儿文件
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
            log.info("跳过重复或未变化文档: baseId={}, file={}", baseId, originalFilename);
            return existing;
        }

        // 创建文档记录
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .baseId(baseId)
                .fileName(originalFilename)
                .filePath(filePath.toString())
                .fileType(fileType)
                .fileSize(file.getSize())
                .content(content)
                .status("pending")
                .build();
        knowledgeDocumentMapper.insert(doc);

        // 处理文档（分块+向量化）
        processDocument(doc, kb);

        return doc;
    }

    /**
     * 处理文档：分块、向量化、存储
     */
    private void processDocument(KnowledgeDocument doc, KnowledgeBase kb) {
        try {
            doc.setStatus("processing");
            knowledgeDocumentMapper.updateById(doc);

            // 分块
            int chunkSize = kb.getChunkSize() != null ? kb.getChunkSize() : 500;
            int chunkOverlap = kb.getChunkOverlap() != null ? kb.getChunkOverlap() : 50;
            List<String> chunks = splitText(doc.getContent(), chunkSize, chunkOverlap);
            doc.setChunkCount(chunks.size());

            // 获取 EmbeddingStore
            EmbeddingStore<TextSegment> store = getEmbeddingStore(kb.getCollectionName());

            // 批量向量化存储
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                Metadata metadata = new Metadata();
                metadata.put("doc_id", doc.getId().toString());
                metadata.put("doc_name", doc.getFileName());
                metadata.put("chunk_index", String.valueOf(i));
                metadata.put("base_id", kb.getId().toString());

                TextSegment segment = TextSegment.from(chunk, metadata);
                Embedding embedding = embeddingCacheService.getEmbedding(chunk);
                store.add(embedding, segment);
            }

            doc.setStatus("completed");
            knowledgeDocumentMapper.updateById(doc);

            // 更新知识库文档数量
            int count = knowledgeDocumentMapper.countCompletedByBaseId(kb.getId());
            kb.setDocumentCount(count);
            knowledgeBaseMapper.updateById(kb);

            log.info("文档处理完成: {}, 分块数: {}", doc.getFileName(), chunks.size());
        } catch (Exception e) {
            doc.setStatus("failed");
            doc.setErrorMessage(e.getMessage());
            knowledgeDocumentMapper.updateById(doc);
            log.error("文档处理失败: {}", doc.getFileName(), e);
        }
    }

    /**
     * 文本分块
     */
    private List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            // 尝试在句子边界切分
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf('.', end);
                int lastNewline = text.lastIndexOf('\n', end);
                int breakPoint = Math.max(lastPeriod, lastNewline);

                if (breakPoint > start + chunkSize / 2) {
                    end = breakPoint + 1;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            int nextStart = end - Math.max(0, chunkOverlap);
            // 防止 overlap 配置异常（例如 overlap >= chunkSize）导致死循环
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return chunks;
    }

    /**
     * 提取文件内容
     */
    private String extractContent(Path filePath, String fileType) throws IOException {
        if ("txt".equals(fileType) || "md".equals(fileType)) {
            return Files.readString(filePath);
        }
        log.warn("暂不支持 {} 类型文件提取", fileType);
        return "";
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int lastDot = filename.lastIndexOf('.');
        return lastDot == -1 ? "" : filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * RAG 问答 - 返回相关上下文
     */
    public String query(Long baseId, String question, int topK) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(baseId);
        if (kb == null || !Boolean.TRUE.equals(kb.getEnabled())) {
            return "知识库不存在或未启用";
        }

        Embedding queryEmbedding = embeddingCacheService.getEmbedding(question);
        EmbeddingStore<TextSegment> store = getEmbeddingStore(kb.getCollectionName());

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(0.5)
                .build();

        try {
            EmbeddingSearchResult<TextSegment> result = store.search(request);

            StringBuilder context = new StringBuilder();
            for (EmbeddingMatch<TextSegment> match : result.matches()) {
                context.append(match.embedded().text()).append("\n\n");
            }

            return context.isEmpty() ? "未找到相关信息" : context.toString();
        } catch (Exception e) {
            log.warn("知识库问答检索失败: baseId={}, collection={}", baseId, kb.getCollectionName(), e);
            return "知识库检索失败，请检查向量集合状态";
        }
    }

    /**
     * 搜索相关文档片段
     */
    public List<Map<String, Object>> searchChunks(Long baseId, String query, int topK) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(baseId);
        if (kb == null) {
            return new ArrayList<>();
        }

        Embedding queryEmbedding = embeddingCacheService.getEmbedding(query);
        EmbeddingStore<TextSegment> store = getEmbeddingStore(kb.getCollectionName());

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(0.5)
                .build();

        try {
            EmbeddingSearchResult<TextSegment> result = store.search(request);

            return result.matches().stream()
                    .map(match -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("score", match.score());
                        map.put("text", match.embedded().text());
                        Metadata metadata = match.embedded().metadata();
                        if (metadata != null) {
                            map.put("docName", metadata.getString("doc_name"));
                            map.put("docId", metadata.getString("doc_id"));
                        }
                        return map;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("知识库检索失败: baseId={}, collection={}", baseId, kb.getCollectionName(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取所有知识库
     */
    public List<KnowledgeBase> listKnowledgeBases() {
        return knowledgeBaseMapper.selectList(null);
    }

    /**
     * 获取知识库详情
     */
    public KnowledgeBase getKnowledgeBase(Long id) {
        return knowledgeBaseMapper.selectById(id);
    }

    /**
     * 更新知识库
     */
    public KnowledgeBase updateKnowledgeBase(KnowledgeBase kb) {
        KnowledgeBase existing = knowledgeBaseMapper.selectById(kb.getId());
        if (existing == null) {
            throw new IllegalArgumentException("知识库不存在: " + kb.getId());
        }

        kb.setCollectionName(existing.getCollectionName());
        kb.setDocumentCount(existing.getDocumentCount());
        knowledgeBaseMapper.updateById(kb);
        return knowledgeBaseMapper.selectById(kb.getId());
    }

    /**
     * 删除知识库
     */
    public void deleteKnowledgeBase(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(id);
        if (kb == null) return;

        deleteQdrantCollection(kb.getCollectionName());
        knowledgeBaseMapper.deleteById(id);
        embeddingStoreCache.remove(kb.getCollectionName());
    }

    /**
     * 获取知识库下的文档列表
     */
    public List<KnowledgeDocument> listDocuments(Long baseId) {
        return knowledgeDocumentMapper.selectByBaseId(baseId);
    }

    /**
     * 删除文档
     */
    public void deleteDocument(Long docId) throws IOException {
        KnowledgeDocument doc = knowledgeDocumentMapper.selectById(docId);
        if (doc == null) return;

        if (doc.getFilePath() != null) {
            Path path = Paths.get(doc.getFilePath());
            if (Files.exists(path)) {
                Files.delete(path);
            }
        }

        knowledgeDocumentMapper.deleteById(docId);

        KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getBaseId());
        if (kb != null) {
            kb.setDocumentCount(knowledgeDocumentMapper.countCompletedByBaseId(kb.getId()));
            knowledgeBaseMapper.updateById(kb);
        }
    }

    private String normalizeHttpHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Qdrant host 未配置");
        }
        if (host.startsWith("http://") || host.startsWith("https://")) {
            return host;
        }
        return "http://" + host;
    }

    private KnowledgeDocument findDuplicateOrUnchanged(Long baseId, String fileName, String contentHash) {
        boolean dedupeEnabled = "true".equalsIgnoreCase(
                systemSettingsService.getSetting("system", "knowledge_dedupe_enabled", "true"));
        boolean incrementalEnabled = "true".equalsIgnoreCase(
                systemSettingsService.getSetting("system", "knowledge_incremental_enabled", "true"));

        if (!dedupeEnabled && !incrementalEnabled) {
            return null;
        }

        List<KnowledgeDocument> docs = knowledgeDocumentMapper.selectByBaseId(baseId);
        for (KnowledgeDocument doc : docs) {
            String hash = digest(doc.getContent());
            if (dedupeEnabled && Objects.equals(hash, contentHash)) {
                return doc;
            }
            if (incrementalEnabled
                    && Objects.equals(doc.getFileName(), fileName)
                    && Objects.equals(hash, contentHash)) {
                return doc;
            }
        }
        return null;
    }

    private String digest(String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode());
        }
    }
}
