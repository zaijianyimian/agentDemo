# AI Agent Demo - 技术文档

## 目录

1. [项目概述](#1-项目概述)
2. [技术架构](#2-技术架构)
3. [核心技术实现](#3-核心技术实现)
   - 3.1 [AI对话系统](#31-ai对话系统)
   - 3.2 [用户认证系统](#32-用户认证系统)
   - 3.3 [记忆存储系统](#33-记忆存储系统)
   - 3.4 [RAG知识库系统](#34-rag知识库系统)
   - 3.5 [MCP工具系统](#35-mcp工具系统)
   - 3.6 [技能编排系统](#36-技能编排系统)
   - 3.7 [邮件监听系统](#37-邮件监听系统)
   - 3.8 [项目自治系统](#38-项目自治系统)
4. [数据库设计](#4-数据库设计)
5. [前端实现](#5-前端实现)
6. [安全机制](#6-安全机制)
7. [部署架构](#7-部署架构)

---

## 1. 项目概述

AI Agent Demo 是一个基于 Spring Boot 3 + LangChain4j + Vue 3 构建的全栈AI智能助手系统。项目实现了完整的 AI Agent 功能，包括智能对话、记忆存储、网络搜索、RAG知识库、工具编排、技能系统、邮件监听、日程管理、定时任务、报告生成与项目自治等能力。

### 项目定位

- **单用户系统**：面向个人用户的AI助手
- **多模型支持**：支持阿里云通义千问、DeepSeek等OpenAI兼容接口
- **向量检索**：基于Qdrant的语义搜索
- **工具编排**：MCP协议风格的工具调用

---

## 2. 技术架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3 + Naive UI)                   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │  聊天   │ │  知识库 │ │  工具   │ │  设置   │ │  自治   │   │
│  └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘   │
└───────┼──────────┼──────────┼──────────┼──────────┼─────────────┘
        │          │          │          │          │
        ▼          ▼          ▼          ▼          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway (Spring Boot 3)                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              Spring Security (JWT认证)                       │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
        │          │          │          │          │
        ▼          ▼          ▼          ▼          ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│  Chat服务   │ │  RAG服务    │ │  工具服务   │ │  认证服务   │
│ (LangChain4j)│ │ (Qdrant)    │ │ (MCP)       │ │ (JWT/OAuth) │
└──────┬──────┘ └──────┬──────┘ └──────┬──────┘ └──────┬──────┘
       │               │               │               │
       ▼               ▼               ▼               ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│ 通义千问API │ │ Qdrant向量库│ │ HTTP/脚本   │ │ MySQL数据库 │
│ (阿里云)    │ │ (gRPC)      │ │ 执行器      │ │             │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘
```

### 2.2 技术选型

#### 后端技术栈

| 层级 | 技术 | 版本 | 选型理由 |
|------|------|------|----------|
| **框架** | Spring Boot | 3.5.12 | 主流企业级框架，生态完善 |
| **AI框架** | LangChain4j | 1.12.2-beta22 | Java生态最成熟的AI应用框架 |
| **ORM** | MyBatis-Plus | 3.5.10.1 | 增强型MyBatis，简化CRUD |
| **向量库** | Qdrant | 1.17.0 | 高性能向量数据库，支持gRPC |
| **安全** | Spring Security | - | 官方安全框架 |
| **缓存** | Caffeine | 3.1.8 | 高性能本地缓存 |
| **连接池** | Druid | 1.2.28 | 阿里开源，监控完善 |
| **响应式** | WebFlux | - | 支持SSE流式响应 |

#### 前端技术栈

| 层级 | 技术 | 版本 | 选型理由 |
|------|------|------|----------|
| **框架** | Vue | 3.5.13 | 组合式API，响应式设计 |
| **UI库** | Naive UI | 2.41.0 | Vue3生态优秀组件库 |
| **状态** | Pinia | 2.3.0 | Vue官方推荐状态管理 |
| **构建** | Vite | 6.2.3 | 极速开发体验 |
| **HTTP** | Axios | 1.8.4 | 成熟的HTTP客户端 |

---

## 3. 核心技术实现

### 3.1 AI对话系统

#### 3.1.1 技术方案

**使用技术**：
- LangChain4j AiServices（动态代理）
- OpenAI兼容API（通义千问）
- WebFlux SSE（流式响应）
- MessageWindowChatMemory（会话记忆）

**实现功能**：
1. 普通聊天（一次性返回）
2. 流式聊天（实时推送）
3. 带记忆的聊天
4. 内容分析（情感、标签、重要性）

#### 3.1.2 实现细节

**核心代码结构**：

```java
// 1. 定义AI服务接口
@AiService(
    wiringMode = EXPLICIT,
    chatModel = "chatModel",
    streamingChatModel = "streamingChatModel"
)
public interface QwenChatService {
    Flux<String> chat(String question);      // 流式响应
    String complete(String question);         // 完整响应
}

// 2. 带记忆的聊天服务
public interface ChatWithMemory {
    String chat(@V("memoryId") Object memoryId, @UserMessage String question);
    Flux<String> streamChat(@V("memoryId") Object memoryId, @UserMessage String question);
}
```

**实现原理**：

1. **AiServices动态代理**：
   - LangChain4j通过动态代理生成接口实现类
   - 自动处理消息格式转换、工具调用等
   - 通过`@V("memoryId")`注解实现会话隔离

2. **流式响应实现**：
   ```java
   @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
   public Flux<ServerSentEvent<String>> chatStream(@RequestParam("message") String message) {
       return qwenChatService.chat(message)
           .map(chunk -> ServerSentEvent.<String>builder()
               .data(chunk)
               .build());
   }
   ```

3. **会话记忆管理**：
   ```java
   // 使用AiServices构建时注入记忆提供者
   AiServices.builder(ChatWithMemory.class)
       .chatModel(chatModel)
       .streamingChatModel(streamingChatModel)
       .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
       .build();
   ```

4. **多模型动态管理**：
   ```java
   // ModelManager - 模型管理器
   private final Map<Long, ChatModel> chatModelCache = new ConcurrentHashMap<>();

   public ChatModel getChatModel(Long id) {
       return chatModelCache.computeIfAbsent(id, key -> {
           AiModelConfig config = configMapper.selectById(id);
           return OpenAiChatModel.builder()
               .baseUrl(config.getBaseUrl())
               .apiKey(decodingService.decode(config.getApiKey()))
               .modelName(config.getModelName())
               .timeout(Duration.ofSeconds(120))
               .build();
       });
   }
   ```

**内容分析服务**：
```java
// 使用结构化输出模型（强制返回JSON）
@AiService(wiringMode = EXPLICIT, chatModel = "structuredChatModel")
public interface ContentAnalysisService {
    ContentAnalysis analyze(String content);  // 返回结构化分析结果
}

// 配置结构化输出模型
@Bean("structuredChatModel")
public ChatModel structuredChatModel(OpenAiChatProperties properties) {
    return OpenAiChatModel.builder()
        .apiKey(properties.getApiKey())
        .modelName(properties.getModelName())
        .baseUrl(properties.getBaseUrl())
        .responseFormat("json_object")  // 强制JSON格式
        .build();
}
```

#### 3.1.3 数据流

```
用户消息 → ChatController → ChatWithMemoryService
    ↓
MessageWindowChatMemory（加载历史消息）
    ↓
OpenAI兼容API（通义千问）
    ↓
Flux<String> 流式返回 → SSE推送到前端
    ↓
保存消息到数据库 → 提取记忆 → 向量化存储
```

---

### 3.2 用户认证系统

#### 3.2.1 技术方案

**使用技术**：
- Spring Security（安全框架）
- JWT（HS256签名）
- BCrypt（密码加密）
- Caffeine（用户缓存）
- 拼图验证码（滑块验证）
- GitHub OAuth（第三方登录）
- 人脸识别（二次验证）

**实现功能**：
1. 密码登录
2. 邮箱验证码登录
3. GitHub OAuth登录
4. 人脸二次验证
5. JWT双令牌机制
6. Token版本控制

#### 3.2.2 实现细节

**JWT双令牌机制**：

```java
@Service
public class JwtTokenService {
    // Access Token - 短期有效（2小时）
    public String generateAccessToken(UserAccount user) {
        return JwtClaimsSet.builder()
            .issuer("agent-demo")
            .expiresAt(Instant.now().plus(120, ChronoUnit.MINUTES))
            .claim("type", "access")
            .claim("userId", user.getId())
            .claim("tokenVersion", user.getTokenVersion())
            .build();
    }

    // Refresh Token - 长期有效（7天）
    public String generateRefreshToken(UserAccount user) {
        return JwtClaimsSet.builder()
            .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
            .claim("type", "refresh")
            .claim("tokenVersion", user.getTokenVersion())
            .build();
    }
}
```

**Token版本控制**：
- 用户表存储`token_version`字段
- 密码修改/退出登录时版本号+1
- 请求时校验Token中的版本号与数据库是否一致
- 实现令牌强制失效

**Spring Security配置**：

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/auth/login/**", "/api/auth/register").permitAll()
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
        .addFilterAfter(tokenVersionValidationFilter, BearerTokenAuthenticationFilter.class);
    return http.build();
}
```

**拼图验证码实现**：
- 生成背景图和滑块图
- 记录滑块X坐标
- 用户滑动后计算匹配百分比
- 验证通过发放ticket

**人脸识别实现**：
```java
@Service
public class FaceAuthService {
    private static final int TARGET_SIZE = 32;        // 缩放尺寸
    private static final double DEFAULT_THRESHOLD = 0.85;  // 相似度阈值

    // 1. 提取人脸特征向量
    private List<Double> extractEmbedding(String imageBase64) {
        // Base64解码
        byte[] bytes = decodeImageBytes(imageBase64);
        // 读取图片
        BufferedImage raw = ImageIO.read(new ByteArrayInputStream(bytes));
        // 缩放到32x32灰度图
        BufferedImage scaled = new BufferedImage(32, 32, TYPE_BYTE_GRAY);
        // 提取像素值作为向量
        double[] vector = new double[32 * 32];
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                vector[y * 32 + x] = (scaled.getRGB(x, y) & 0xff) / 255.0;
            }
        }
        // L2归一化
        normalize(vector);
        return Arrays.stream(vector).boxed().toList();
    }

    // 2. 计算余弦相似度
    private double cosineSimilarity(List<Double> left, List<Double> right) {
        double dot = 0, normLeft = 0, normRight = 0;
        for (int i = 0; i < left.size(); i++) {
            dot += left.get(i) * right.get(i);
            normLeft += left.get(i) * left.get(i);
            normRight += right.get(i) * right.get(i);
        }
        return dot / (Math.sqrt(normLeft) * Math.sqrt(normRight));
    }

    // 3. 验证人脸
    public void verifyForLogin(Long userId, String imageBase64) {
        double similarity = verifySimilarity(userId, imageBase64);
        if (similarity < DEFAULT_THRESHOLD) {
            throw new IllegalArgumentException("人脸验证失败");
        }
    }
}
```

**人脸验证流程**：
1. 用户开启人脸二次验证
2. 登录成功后，返回`preAuthToken`和`requiresSecondFactor: true`
3. 前端调用`/api/auth/face/verify-login`验证人脸
4. 验证成功返回完整JWT令牌

---

### 3.3 记忆存储系统

#### 3.3.1 技术方案

**使用技术**：
- Qdrant向量数据库（gRPC协议）
- Ollama Embedding模型（nomic-embed-text，768维）
- Caffeine缓存（避免重复计算向量）
- LangChain4j EmbeddingStore

**实现功能**：
1. 对话记忆自动提取
2. 向量化存储
3. 语义检索
4. 会话/类别过滤

#### 3.3.2 实现细节

**Embedding缓存服务**：

```java
@Service
public class EmbeddingCacheService {
    private final EmbeddingModel embeddingModel;  // Ollama Embedding
    private final Cache<String, Embedding> cache; // Caffeine缓存

    public EmbeddingCacheService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.cache = Caffeine.newBuilder()
            .maximumSize(10000)           // 最多缓存1万条
            .expireAfterWrite(30, TimeUnit.MINUTES)  // 30分钟过期
            .build();
    }

    // 获取向量，优先从缓存读取
    public Embedding getEmbedding(String text) {
        return cache.get(text, this::generateEmbedding);
    }

    private Embedding generateEmbedding(String text) {
        TextSegment segment = TextSegment.from(text);
        return embeddingModel.embed(segment).content();
    }
}
```

**记忆存储服务**：

```java
@Service
public class MemoryStoreService {
    private final EmbeddingStore<TextSegment> embeddingStore;  // Qdrant
    private final EmbeddingCacheService embeddingCacheService;

    // 存储记忆
    public boolean save(MemoryRecord record) {
        if (!record.getShouldStore()) return false;

        // 构建元数据
        Metadata metadata = new Metadata();
        metadata.put("sessionId", record.getSessionId());
        metadata.put("category", record.getCategory());
        metadata.put("importance", String.valueOf(record.getImportance()));
        metadata.put("tags", String.join(",", record.getTags()));

        // 创建文本片段
        TextSegment segment = TextSegment.from(record.getSummary(), metadata);

        // 获取向量并存储
        Embedding embedding = embeddingCacheService.getEmbedding(record.getSummary());
        embeddingStore.add(embedding, segment);
        return true;
    }

    // 检索记忆
    public List<Map<String, Object>> search(String query, int topK, String sessionId) {
        Embedding queryEmbedding = embeddingCacheService.getEmbedding(query);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(topK)
            .minScore(0.5)  // 相似度阈值
            .filter(metadataKey("sessionId").isEqualTo(sessionId))  // 过滤条件
            .build();

        return embeddingStore.search(request).matches().stream()
            .map(this::toMap)
            .collect(Collectors.toList());
    }
}
```

**记忆提取器**：

```java
@Component
public class MemoryExtractor {
    // 从对话中提取结构化记忆
    public MemoryRecord extract(String sessionId, List<String> recentMessages) {
        String merged = String.join("\n", recentMessages);

        // 规则提取
        String summary = summarize(merged);        // 摘要
        int importance = calculateImportance(merged);  // 重要性评分
        String category = classify(merged);        // 分类
        List<String> tags = tagsOf(merged, category);  // 标签

        // 判断是否需要存储
        boolean shouldStore = importance >= 60 || isLongTermCategory(category);

        return MemoryRecord.builder()
            .sessionId(sessionId)
            .summary(summary)
            .importance(importance)
            .category(category)
            .tags(tags)
            .shouldStore(shouldStore)
            .build();
    }

    // 重要性计算（基于关键词规则）
    private int calculateImportance(String text) {
        int score = 20;
        if (text.contains("项目") || text.contains("方案")) score += 25;
        if (text.contains("以后") || text.contains("长期")) score += 20;
        if (text.contains("偏好") || text.contains("习惯")) score += 15;
        return Math.min(score, 100);
    }
}
```

**Qdrant配置**：

```java
@Bean
public EmbeddingStore<TextSegment> embeddingStore(QdrantProperties props) {
    return QdrantEmbeddingStore.builder()
        .host(props.getHost())      // 默认 localhost
        .port(props.getPort())      // gRPC端口 6334
        .collectionName("agent_memory")
        .build();
}

@Bean
public EmbeddingModel embeddingModel(OllamaEmbeddingProperties props) {
    return OllamaEmbeddingModel.builder()
        .baseUrl(props.getBaseUrl())  // http://localhost:11434
        .modelName("nomic-embed-text:latest")  // 768维向量
        .build();
}
```

---

### 3.4 RAG知识库系统

#### 3.4.1 技术方案

**使用技术**：
- Qdrant多集合管理
- 文档分块（可配置大小和重叠）
- HTTP REST API管理集合
- 向量检索（余弦相似度）

**实现功能**：
1. 创建/删除知识库
2. 文档上传与解析
3. 文档分块与向量化
4. 语义问答
5. 文档去重

#### 3.4.2 实现细节

**知识库服务**：

```java
@Service
public class RagService {
    private final Map<String, EmbeddingStore<TextSegment>> embeddingStoreCache = new HashMap<>();

    // 创建知识库
    public KnowledgeBase createKnowledgeBase(KnowledgeBase kb) {
        // 生成唯一集合名
        String collectionName = "kb_" + UUID.randomUUID().toString().substring(0, 16);
        kb.setCollectionName(collectionName);

        // 通过HTTP API创建Qdrant集合
        createQdrantCollection(collectionName);

        knowledgeBaseMapper.insert(kb);
        return kb;
    }

    // 创建Qdrant集合（HTTP REST API）
    private void createQdrantCollection(String collectionName) {
        String url = "http://" + host + ":6333/collections/" + collectionName;
        String json = """
        {
            "vectors": {
                "size": 768,
                "distance": "Cosine"
            }
        }
        """;
        // PUT请求创建集合
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.getOutputStream().write(json.getBytes(UTF_8));
    }

    // 上传文档
    public KnowledgeDocument uploadDocument(Long baseId, MultipartFile file) {
        // 1. 保存文件
        Path filePath = Paths.get(storagePath, collectionName, uniqueFileName);
        Files.copy(file.getInputStream(), filePath);

        // 2. 提取内容
        String content = extractContent(filePath, fileType);

        // 3. 去重检查
        KnowledgeDocument existing = findDuplicateOrUnchanged(baseId, fileName, contentHash);
        if (existing != null) return existing;

        // 4. 处理文档（分块+向量化）
        processDocument(doc, kb);

        return doc;
    }

    // 文档处理
    private void processDocument(KnowledgeDocument doc, KnowledgeBase kb) {
        // 分块
        List<String> chunks = splitText(doc.getContent(),
            kb.getChunkSize() != null ? kb.getChunkSize() : 500,
            kb.getChunkOverlap() != null ? kb.getChunkOverlap() : 50);

        // 获取EmbeddingStore
        EmbeddingStore<TextSegment> store = getEmbeddingStore(kb.getCollectionName());

        // 批量向量化存储
        for (int i = 0; i < chunks.size(); i++) {
            Metadata metadata = new Metadata();
            metadata.put("doc_id", doc.getId().toString());
            metadata.put("doc_name", doc.getFileName());
            metadata.put("chunk_index", String.valueOf(i));

            TextSegment segment = TextSegment.from(chunks.get(i), metadata);
            Embedding embedding = embeddingCacheService.getEmbedding(chunks.get(i));
            store.add(embedding, segment);
        }
    }

    // 智能分块（支持句子边界切分）
    private List<String> splitText(String text, int chunkSize, int overlap) {
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

            chunks.add(text.substring(start, end).trim());
            start = end - overlap;
        }
        return chunks;
    }

    // RAG问答
    public String query(Long baseId, String question, int topK) {
        Embedding queryEmbedding = embeddingCacheService.getEmbedding(question);
        EmbeddingStore<TextSegment> store = getEmbeddingStore(kb.getCollectionName());

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
            .queryEmbedding(queryEmbedding)
            .maxResults(topK)
            .minScore(0.5)
            .build();

        EmbeddingSearchResult<TextSegment> result = store.search(request);

        StringBuilder context = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            context.append(match.embedded().text()).append("\n\n");
        }
        return context.toString();
    }
}
```

---

### 3.5 MCP工具系统

#### 3.5.1 技术方案

**使用技术**：
- LangChain4j ToolSpecification
- JSON Schema参数校验
- HTTP API工具执行器
- 本地脚本执行器

**实现功能**：
1. 工具注册与管理
2. 工具执行
3. AI自动调用工具
4. 工具缓存与刷新

#### 3.5.2 实现细节

**工具适配器**：

```java
@Service
public class McpToolAdapter {
    private final AtomicReference<List<ToolSpecification>> cachedToolSpecs = new AtomicReference<>(List.of());

    // 将数据库工具配置转换为LangChain4j ToolSpecification
    public ToolSpecification convertToToolSpecification(McpTool tool) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
            .name(tool.getName())
            .description(tool.getDescription());

        // 解析输入参数Schema
        if (tool.getInputSchema() != null) {
            Map<String, Object> schema = objectMapper.readValue(tool.getInputSchema(), Map.class);
            builder.parameters(convertToJsonObjectSchema(schema));
        }

        return builder.build();
    }

    // 转换为LangChain4j JsonObjectSchema
    private JsonObjectSchema convertToJsonObjectSchema(Map<String, Object> schema) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        List<String> required = (List<String>) schema.get("required");

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> propSchema = (Map<String, Object>) entry.getValue();
            builder.addProperty(entry.getKey(),
                JsonStringSchema.builder()
                    .description((String) propSchema.get("description"))
                    .build());
        }

        if (!required.isEmpty()) {
            builder.required(required);
        }

        return builder.build();
    }

    // 执行工具调用请求
    public String executeToolRequest(ToolExecutionRequest request) {
        Map<String, Object> params = objectMapper.readValue(request.arguments(), Map.class);
        ToolExecutionResult result = mcpToolService.execute(request.name(), params);
        return objectMapper.writeValueAsString(Map.of(
            "success", result.isSuccess(),
            "result", result.getResult()
        ));
    }
}
```

**MCP Agent配置**：

```java
@Configuration
public class McpAgentConfiguration {
    // 动态工具提供者
    @Bean
    public ToolProvider mcpToolProvider(McpToolAdapter adapter) {
        return request -> {
            var builder = ToolProviderResult.builder();

            adapter.loadToolSpecifications().forEach(spec -> {
                ToolExecutor executor = (executionRequest, memoryId) -> {
                    return adapter.executeToolRequest(executionRequest);
                };
                builder.add(spec, executor);
            });

            return builder.build();
        };
    }

    // MCP Agent服务（支持自动调用工具）
    @Bean
    public McpAgentService mcpAgentService(ChatModel chatModel, ToolProvider toolProvider) {
        return AiServices.builder(McpAgentService.class)
            .chatModel(chatModel)
            .toolProvider(toolProvider)  // 注入工具提供者
            .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
            .build();
    }
}
```

**工具执行器**：

```java
// HTTP API工具执行器
@Component
public class HttpApiToolExecutor implements ToolExecutor {
    public ToolExecutionResult execute(McpTool tool, Map<String, Object> params) {
        Map<String, Object> config = objectMapper.readValue(tool.getConfig(), Map.class);
        String url = (String) config.get("url");
        String method = (String) config.get("method");
        int timeout = (int) config.getOrDefault("timeout", 30);

        // 构建请求
        WebClient webClient = WebClient.builder()
            .baseUrl(url)
            .build();

        // 发送请求
        String response = webClient.method(HttpMethod.valueOf(method))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(params)
            .retrieve()
            .bodyToMono(String.class)
            .block(Duration.ofSeconds(timeout));

        return ToolExecutionResult.success(response);
    }
}

// 本地脚本执行器
@Component
public class LocalScriptToolExecutor implements ToolExecutor {
    public ToolExecutionResult execute(McpTool tool, Map<String, Object> params) {
        Map<String, Object> config = objectMapper.readValue(tool.getConfig(), Map.class);
        String scriptPath = (String) config.get("scriptPath");

        // 构建命令
        ProcessBuilder pb = new ProcessBuilder("sh", scriptPath);
        pb.redirectErrorStream(true);

        // 执行脚本
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        return exitCode == 0
            ? ToolExecutionResult.success(output)
            : ToolExecutionResult.failure(output);
    }
}
```

---

### 3.6 技能编排系统

#### 3.6.1 技术方案

**使用技术**：
- YAML技能配置
- 技能-工具映射关系
- 链式调用顺序控制
- 参数映射

**实现功能**：
1. 技能定义与管理
2. 多工具组合调用
3. 调用顺序编排
4. 结果传递

#### 3.6.2 实现细节

**技能配置文件（skills.yaml）**：

```yaml
skills:
  - code: web_search
    name: 网络搜索
    description: 在互联网上搜索相关信息
    category: search
    enabled: true
    isBuiltin: true
    tools:
      - name: web_search_tool
        displayName: 网络搜索工具
        toolType: http_api
        config:
          url: https://api.serper.dev/search
          method: POST
          timeout: 30
          headers:
            X-API-KEY: ${SERPER_API_KEY}
        inputSchema:
          type: object
          properties:
            query:
              type: string
              description: 搜索关键词
          required:
            - query
```

**技能执行器**：

```java
@Service
public class SkillExecutor {
    public SkillExecutionResult execute(String skillCode, Map<String, Object> params) {
        Skill skill = skillService.getByCode(skillCode);
        List<SkillToolMapping> mappings = skillService.getSkillToolMappings(skill.getId());
        List<McpTool> tools = skillService.getSkillTools(skill.getId());

        // 按顺序执行工具
        List<ToolExecutionStep> steps = new ArrayList<>();
        Object lastResult = null;

        for (SkillToolMapping mapping : mappings) {
            // 合并参数：用户参数 + 上一步结果
            Map<String, Object> executionParams = new HashMap<>(params);
            if (lastResult != null) {
                executionParams.put("_previous_result", lastResult);
            }

            // 执行工具
            McpTool tool = findTool(tools, mapping.getToolId());
            ToolExecutionResult toolResult = mcpToolService.execute(tool, executionParams);

            // 记录步骤
            steps.add(ToolExecutionStep.builder()
                .toolName(tool.getName())
                .success(toolResult.isSuccess())
                .result(toolResult.getResult())
                .build());

            if (toolResult.isSuccess()) {
                lastResult = parseResult(toolResult.getResult());
            } else if (mapping.getIsRequired()) {
                // 必需工具失败则终止
                return SkillExecutionResult.failure(skillCode, "工具执行失败");
            }
        }

        return SkillExecutionResult.builder()
            .success(true)
            .skillCode(skillCode)
            .result(lastResult)
            .toolSteps(steps)
            .build();
    }
}
```

---

### 3.7 邮件监听系统

#### 3.7.1 技术方案

**使用技术**：
- Jakarta Mail API
- IMAP协议监听
- 虚拟线程池
- Spring Scheduling

**实现功能**：
1. IMAP邮件监听
2. 新邮件实时处理
3. 邮件内容解析
4. SMTP邮件发送

#### 3.7.2 实现细节

**邮件监听服务**：

```java
@Service
public class EmailListenerService {
    private final Map<Long, Store> storeMap = new ConcurrentHashMap<>();
    private final Map<Long, Folder> folderMap = new ConcurrentHashMap<>();
    private final ExecutorService executorService;  // 虚拟线程池

    @PostConstruct
    public void init() {
        loadAndStartListeners();
    }

    // 启动监听
    public void startListener(EmailConfig config) {
        executorService.submit(() -> connectAndListen(config));
    }

    // 连接并监听
    private void connectAndListen(EmailConfig config) throws MessagingException {
        // 配置邮件属性
        Properties props = new Properties();
        props.put("mail.store.protocol", "imap");
        props.put("mail.imap.host", config.getHost());
        props.put("mail.imap.port", config.getPort());
        props.put("mail.imap.ssl.enable", "true");

        // 创建Session
        Session session = Session.getInstance(props);
        Store store = session.getStore("imap");
        store.connect(config.getHost(), config.getEmail(), config.getPassword());

        // 打开文件夹
        Folder folder = store.getFolder("INBOX");
        folder.open(Folder.READ_WRITE);

        // 保存连接
        storeMap.put(config.getId(), store);
        folderMap.put(config.getId(), folder);

        // 添加消息监听器
        folder.addMessageCountListener(new MessageCountAdapter() {
            @Override
            public void messagesAdded(MessageCountEvent e) {
                for (Message message : e.getMessages()) {
                    processNewMessage(message, config);
                }
            }
        });

        // 保持连接并轮询
        keepAlive(config.getId());
    }

    // 解析邮件
    private EmailMessage parseMessage(Message message, EmailConfig config) {
        EmailMessage.EmailMessageBuilder builder = EmailMessage.builder()
            .from(message.getFrom()[0].toString())
            .subject(message.getSubject())
            .sentDate(message.getSentDate());

        // 解析邮件内容
        parseContent(message, builder);

        return builder.build();
    }

    // 解析内容（支持多部分）
    private void parseContent(Part part, EmailMessage.EmailMessageBuilder builder) {
        Object content = part.getContent();
        if (content instanceof String) {
            if (part.isMimeType("text/plain")) {
                builder.textContent((String) content);
            } else if (part.isMimeType("text/html")) {
                builder.htmlContent((String) content);
            }
        } else if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            for (int i = 0; i < multipart.getCount(); i++) {
                parseContent(multipart.getBodyPart(i), builder);
            }
        }
    }
}
```

**虚拟线程配置**：

```java
@Configuration
public class VirtualThreadConfig {
    @Bean("emailProcessingExecutor")
    public ExecutorService emailProcessingExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

---

### 3.8 项目自治系统

#### 3.8.1 技术方案

**使用技术**：
- 项目结构扫描
- 正则表达式解析
- ProcessBuilder执行命令
- AI补全草稿生成

**实现功能**：
1. 项目扫描与发现问题
2. 前后端一致性验证
3. 补全草稿生成
4. 构建测试验证

#### 3.8.2 实现细节

**项目扫描服务**：

```java
@Service
public class ProjectAutonomyService {
    // 扫描项目
    public AutonomyScanReport scanProject() {
        Path root = resolveWorkspaceRoot();
        List<AutonomyFinding> findings = new ArrayList<>();
        Map<String, Object> metrics = new LinkedHashMap<>();

        // 收集指标
        metrics.put("controllerCount", countFiles(backendControllerDir, "*.java"));
        metrics.put("viewCount", countFiles(frontendViewsDir, "*.vue"));
        metrics.put("routeCount", countRoutes(frontendRouter));
        metrics.put("readmeExists", Files.exists(readme));
        metrics.put("gitClean", isGitClean(root));

        // 发现问题
        if (!Files.exists(readme)) {
            findings.add(finding("high", "README 缺失",
                "项目根目录未找到 README.md", "补充项目概览"));
        }

        // 检查路由与页面一致性
        List<String> routeViews = extractRouteViews(frontendRouter);
        List<String> actualViews = listFileNames(frontendViewsDir, "*.vue");
        List<String> unroutedViews = actualViews.stream()
            .filter(name -> !routeViews.contains(name))
            .collect(Collectors.toList());
        if (!unroutedViews.isEmpty()) {
            findings.add(finding("medium", "存在未挂载页面",
                "未在路由中暴露: " + String.join(", ", unroutedViews), "检查导航"));
        }

        // 保存报告
        AutonomyScanReport report = AutonomyScanReport.builder()
            .scanTime(LocalDateTime.now())
            .metrics(metrics)
            .findings(findings)
            .build();
        objectMapper.writeValue(jsonPath.toFile(), report);

        return report;
    }

    // 验证项目（执行构建测试）
    public AutonomyVerificationResult verifyProject(boolean backend, boolean frontend) {
        List<AutonomyVerificationStep> steps = new ArrayList<>();

        if (backend) {
            steps.add(runCommand("backend-test",
                List.of("cmd.exe", "/c", "gradlew.bat test"), root));
        }
        if (frontend) {
            steps.add(runCommand("frontend-build",
                List.of("cmd.exe", "/c", "npm run build"), frontendDir));
        }

        return AutonomyVerificationResult.builder()
            .success(steps.stream().allMatch(AutonomyVerificationStep::isSuccess))
            .steps(steps)
            .build();
    }

    // 执行命令
    private AutonomyVerificationStep runCommand(String name, List<String> command, Path workDir) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workDir.toFile());
        builder.redirectErrorStream(true);

        Process process = builder.start();
        boolean finished = process.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }

        String output = new String(process.getInputStream().readAllBytes(), UTF_8);
        return AutonomyVerificationStep.builder()
            .name(name)
            .success(process.exitValue() == 0)
            .output(output)
            .build();
    }

    // 生成补全草稿
    public AutonomyDraftResponse generateCompletionDraft(String target) {
        AutonomyScanReport report = scanProject();

        // 构建提示词
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个单用户 AI Agent 项目的受控自治补全助手。")
            .append("请根据以下扫描结果，输出一份中文 Markdown 补全方案。")
            .append("\n扫描指标: ").append(report.getMetrics())
            .append("\n发现项:\n");
        for (AutonomyFinding f : report.getFindings()) {
            prompt.append("- [").append(f.getSeverity()).append("] ")
                .append(f.getTitle()).append(": ").append(f.getDetail()).append("\n");
        }

        // 调用AI生成
        String content = qwenChatService.complete(prompt.toString());

        // 保存草稿
        Path draftPath = outputDir.resolve("draft-" + timestamp + ".md");
        Files.writeString(draftPath, content);

        return AutonomyDraftResponse.builder()
            .draftPath(draftPath.toString())
            .content(content)
            .build();
    }
}
```

---

## 4. 数据库设计

### 4.1 核心表结构

#### 用户相关表

```sql
-- 用户账号表
CREATE TABLE user_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,     -- BCrypt加密
    display_name VARCHAR(100),
    role VARCHAR(30) DEFAULT 'USER',
    enabled TINYINT(1) DEFAULT 1,
    email_verified TINYINT(1) DEFAULT 0,
    face_auth_enabled TINYINT(1) DEFAULT 0,  -- 人脸二次验证
    token_version INT DEFAULT 0,              -- JWT版本号
    last_login_time DATETIME
);

-- 人脸认证向量表
CREATE TABLE user_face_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    embedding LONGTEXT NOT NULL,              -- 1024维向量(JSON)
    vector_dimension INT NOT NULL,            -- 向量维度
    quality_score DOUBLE,                     -- 图像质量分
    enabled TINYINT(1) DEFAULT 1
);

-- OAuth账号绑定表
CREATE TABLE oauth_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(30) NOT NULL,            -- github
    provider_user_id VARCHAR(100) NOT NULL,
    login VARCHAR(100)
);
```

#### AI相关表

```sql
-- AI模型配置表
CREATE TABLE ai_model_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,            -- openai/aliyun/deepseek
    base_url VARCHAR(200) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    api_key VARCHAR(500) NOT NULL,            -- 加密存储
    is_default TINYINT(1) DEFAULT 0,
    enabled TINYINT(1) DEFAULT 1
);

-- 聊天会话表
CREATE TABLE chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) DEFAULT '新会话',
    summary VARCHAR(500),
    message_count INT DEFAULT 0,
    last_message_time DATETIME
);

-- 聊天消息表
CREATE TABLE chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,                -- user/assistant
    content TEXT NOT NULL,
    model VARCHAR(50),
    FOREIGN KEY (session_id) REFERENCES chat_session(id)
);
```

#### 知识库相关表

```sql
-- 知识库表
CREATE TABLE knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    collection_name VARCHAR(100) NOT NULL,    -- Qdrant集合名
    embedding_model VARCHAR(100),
    chunk_size INT DEFAULT 500,
    chunk_overlap INT DEFAULT 50,
    document_count INT DEFAULT 0,
    enabled TINYINT(1) DEFAULT 1
);

-- 知识库文档表
CREATE TABLE knowledge_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    content LONGTEXT,
    chunk_count INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'pending',     -- pending/processing/completed/failed
    FOREIGN KEY (base_id) REFERENCES knowledge_base(id)
);
```

#### 工具技能表

```sql
-- MCP工具表
CREATE TABLE mcp_tool (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100),
    description VARCHAR(1000),
    tool_type VARCHAR(20) NOT NULL,           -- http_api/local_script
    config JSON NOT NULL,
    input_schema JSON,
    enabled TINYINT(1) DEFAULT 1
);

-- 技能表
CREATE TABLE skill (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    category VARCHAR(50),
    enabled TINYINT(1) DEFAULT 1,
    is_builtin TINYINT(1) DEFAULT 0,
    config JSON
);

-- 技能-工具映射表
CREATE TABLE skill_tool_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_id BIGINT NOT NULL,
    tool_id BIGINT NOT NULL,
    invoke_order INT DEFAULT 0,               -- 调用顺序
    is_required TINYINT(1) DEFAULT 1,
    FOREIGN KEY (skill_id) REFERENCES skill(id),
    FOREIGN KEY (tool_id) REFERENCES mcp_tool(id)
);
```

---

## 5. 前端实现

### 5.1 项目结构

```
frontend/
├── src/
│   ├── views/           # 21个页面组件
│   │   ├── Login.vue    # 登录页（拼图验证码+人脸验证）
│   │   ├── Chat.vue     # AI聊天（SSE流式响应）
│   │   ├── Knowledge.vue # 知识库管理
│   │   ├── Skills.vue   # 技能管理
│   │   └── ...
│   ├── router/          # 路由配置
│   ├── services/        # API服务
│   │   ├── api.ts       # API调用封装
│   │   ├── auth-fetch.ts # 认证请求
│   │   └── auth-token.ts # Token管理
│   ├── stores/          # Pinia状态
│   │   ├── auth.ts      # 认证状态
│   │   └── theme.ts     # 主题状态
│   └── types/           # TypeScript类型
```

### 5.2 核心实现

**SSE流式聊天**：

```typescript
// Chat.vue - 流式聊天实现
async function sendMessage(message: string) {
  const eventSource = new EventSource(
    `/api/chat/stream/session?message=${encodeURIComponent(message)}&sessionId=${sessionId}`
  );

  eventSource.onmessage = (event) => {
    // 实时追加内容
    responseContent.value += event.data;
  };

  eventSource.onerror = () => {
    eventSource.close();
  };
}
```

**认证状态管理**：

```typescript
// stores/auth.ts
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('access_token'),
    user: null as User | null
  }),

  actions: {
    async login(credentials: LoginRequest) {
      const response = await authApi.login(credentials);
      this.token = response.accessToken;
      this.user = response.user;
      localStorage.setItem('access_token', response.accessToken);
      localStorage.setItem('refresh_token', response.refreshToken);
    },

    logout() {
      this.token = null;
      this.user = null;
      localStorage.removeItem('access_token');
      localStorage.removeItem('refresh_token');
    }
  }
});
```

**API请求封装**：

```typescript
// services/auth-fetch.ts
export async function authFetch(url: string, options: RequestInit = {}) {
  const token = localStorage.getItem('access_token');

  const response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${token}`
    }
  });

  if (response.status === 401) {
    // Token过期，尝试刷新
    const refreshed = await refreshToken();
    if (refreshed) {
      return authFetch(url, options);
    } else {
      router.push('/login');
    }
  }

  return response;
}
```

---

## 6. 安全机制

### 6.1 认证安全

| 安全措施 | 实现方式 |
|---------|---------|
| 密码加密 | BCrypt算法 |
| 令牌签名 | JWT HS256 |
| 令牌过期 | Access Token 2小时，Refresh Token 7天 |
| 令牌失效 | Token版本号机制 |
| 防重放 | 拼图验证码ticket一次性使用 |
| API保护 | Spring Security接口鉴权 |

### 6.2 API密钥安全

```java
// API密钥加密存储
public class EncodingService {
    private static final String SECRET = "your-encoding-secret";

    public String encode(String plain) {
        // AES加密
        return Base64.getEncoder().encodeToString(
            cipher.doFinal(plain.getBytes())
        );
    }

    public String decode(String encoded) {
        // AES解密
        return new String(cipher.doFinal(
            Base64.getDecoder().decode(encoded)
        ));
    }
}
```

### 6.3 接口保护

```java
// SecurityConfig.java - 接口权限配置
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/login/**").permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .anyRequest().authenticated()
)
```

---

## 7. 部署架构

### 7.1 Docker部署

```dockerfile
# 多阶段构建
FROM gradle:8.7-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 7.2 系统依赖

| 组件 | 版本 | 用途 |
|------|------|------|
| MySQL | 8+ | 主数据库 |
| Qdrant | 1.17.0+ | 向量数据库 |
| Ollama | - | 本地Embedding模型 |

### 7.3 配置说明

```yaml
# application.yaml 核心配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/agent

langchain4j:
  open-ai:
    chat-model:
      api-key: sk-xxx
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen-plus

app:
  qdrant:
    host: localhost
    port: 6334
  memory:
    collection-name: agent_memory
    top-k: 5
    min-score: 0.5
```

---

## 总结

本项目是一个功能完整的AI智能助手系统，核心技术亮点包括：

1. **LangChain4j框架**：实现了AI服务动态代理、会话记忆、工具自动调用
2. **向量检索**：基于Qdrant的语义搜索，支持记忆存储和RAG知识库
3. **多模型管理**：支持动态配置和切换不同的AI模型
4. **安全认证**：JWT双令牌、人脸识别、GitHub OAuth
5. **工具编排**：MCP协议风格的工具调用和技能编排
6. **流式响应**：WebFlux SSE实时推送
7. **项目自治**：AI驱动的项目扫描和补全建议