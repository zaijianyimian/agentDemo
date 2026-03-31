# AI Agent Demo - 智能助手系统

## 项目概述

AI Agent Demo 是一个基于 **Spring Boot 3 + LangChain4j + Vue 3** 构建的全栈AI智能助手系统，实现了完整的 AI Agent 功能，包括智能对话、记忆存储、网络搜索、RAG知识库、工具编排、技能系统、邮件监听、日程管理、定时任务、报告生成与项目自治等能力。

## 技术架构

### 后端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.5.12 | 基础框架 |
| LangChain4j | 1.12.2-beta22 | AI应用开发框架 |
| MyBatis-Plus | 3.5.10.1 | ORM框架 |
| MySQL | 8+ | 主数据库 |
| Qdrant | 1.17.0 | 向量数据库 |
| gRPC/Protobuf | 1.69.0/3.25.5 | Qdrant通信协议 |
| Spring Security | - | 安全框架 |
| Spring WebFlux | - | 响应式Web框架（SSE流式响应） |
| Druid | 1.2.28 | 数据库连接池 |
| Caffeine | 3.1.8 | 本地缓存 |
| Jackson | 2.18.2 | JSON处理 |
| Lombok | - | 代码简化 |

### 前端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.5.13 | 前端框架 |
| TypeScript | 5.7.3 | 类型支持 |
| Vite | 6.2.3 | 构建工具 |
| Naive UI | 2.41.0 | UI组件库 |
| Pinia | 2.3.0 | 状态管理 |
| Vue Router | 4.5.0 | 路由管理 |
| Axios | 1.8.4 | HTTP客户端 |
| marked | 17.0.5 | Markdown渲染 |
| highlight.js | 11.11.1 | 代码高亮 |
| dayjs | 1.11.13 | 日期处理 |

### AI模型支持

- **OpenAI兼容接口**：支持阿里云通义千问、DeepSeek等兼容OpenAI API的模型
- **Ollama本地模型**：支持本地部署的Embedding模型（如nomic-embed-text）
- **多模型管理**：动态配置和切换不同的AI模型

## 功能模块

### 1. 用户认证系统 (Auth)

**实现技术**：
- Spring Security + JWT 令牌认证
- BCrypt 密码加密
- 拼图验证码（滑块验证）
- GitHub OAuth 第三方登录
- 人脸识别二次验证

**核心功能**：
- 用户注册/登录（密码登录、邮箱验证码登录）
- JWT Access Token + Refresh Token 双令牌机制
- Token版本控制（支持强制失效）
- 人脸注册与验证登录
- 用户缓存（Caffeine本地缓存）

**关键实现文件**：
- `AuthController.java` - 认证接口控制器
- `AuthService.java` - 认证业务逻辑
- `JwtTokenService.java` - JWT令牌服务
- `PuzzleCaptchaService.java` - 拼图验证码服务
- `FaceAuthService.java` - 人脸验证服务
- `GithubOAuthService.java` - GitHub OAuth服务

### 2. AI对话系统 (Chat)

**实现技术**：
- LangChain4j AiServices 动态代理
- WebFlux SSE 流式响应
- MessageWindowChatMemory 会话记忆（最多20条消息）
- 内容分析与情感识别

**核心功能**：
- 普通聊天接口（完整响应）
- 流式聊天接口（SSE实时推送）
- 带会话记忆的聊天
- 多模型动态切换
- 聊天历史持久化
- 内容重要性分析、标签提取、情感分析

**关键实现文件**：
- `ChatController.java` - 聊天接口控制器
- `ChatWithMemoryService.java` - 带记忆的聊天服务
- `QwenChatService.java` - 流式聊天服务
- `ChatHistoryService.java` - 聊天历史管理
- `ContentAnalysisService.java` - 内容分析服务
- `ModelManager.java` - 多模型管理器

### 3. 记忆存储系统 (Memory)

**实现技术**：
- Qdrant 向量数据库
- Ollama Embedding模型（nomic-embed-text，768维向量）
- Embedding缓存（避免重复计算）
- Metadata 过滤查询

**核心功能**：
- 对话记忆自动提取
- 向量化存储与检索
- 按会话ID/类别过滤
- 相似度匹配（阈值0.5）
- Top-K 检索（默认5条）

**关键实现文件**：
- `MemoryController.java` - 记忆接口控制器
- `MemoryStoreService.java` - 记忆存储服务
- `MemoryApplicationService.java` - 记忆应用服务
- `MemoryExtractor.java` - 记忆提取器
- `EmbeddingCacheService.java` - Embedding缓存服务

### 4. RAG知识库系统 (Knowledge)

**实现技术**：
- 文档上传与解析（txt、md等）
- 智能文本分块（支持句子边界切分）
- Qdrant 多集合管理（每个知识库独立集合）
- HTTP REST API 创建/删除向量集合

**核心功能**：
- 创建/删除知识库
- 文档上传与向量化
- 自定义分块大小与重叠
- 智能问答（基于知识库检索）
- 文档去重与增量上传
- 搜索相关文档片段

**关键实现文件**：
- `KnowledgeController.java` - 知识库接口控制器
- `RagService.java` - RAG检索服务

### 5. 网络搜索系统 (Search)

**实现技术**：
- WebClient 响应式HTTP客户端
- 多搜索引擎支持（Serper/Tavily/Bing）
- 搜索历史记录
- 用户兴趣分析

**核心功能**：
- 网络实时搜索
- 结构化搜索结果
- 搜索历史管理
- 自动提取域名来源

**关键实现文件**：
- `SearchController.java` - 搜索接口控制器
- `WebSearchService.java` - 网络搜索服务
- `SearchHistoryService.java` - 搜索历史服务

### 6. MCP工具系统 (MCP Tools)

**实现技术**：
- JSON Schema 输入参数校验
- HTTP API 工具执行器
- 本地脚本工具执行器
- MCP协议客户端适配

**核心功能**：
- 工具注册与管理
- 工具配置（URL、方法、超时等）
- 工具执行与结果解析
- 工具启用/禁用控制

**关键实现文件**：
- `McpToolController.java` - MCP工具控制器
- `McpToolService.java` - MCP工具服务
- `ToolExecutor.java` - 工具执行器接口
- `HttpApiToolExecutor.java` - HTTP API工具执行器
- `LocalScriptToolExecutor.java` - 本地脚本执行器

### 7. 技能编排系统 (Skills)

**实现技术**：
- YAML技能配置文件
- 技能-工具映射关系
- 链式调用顺序控制
- 参数映射与结果传递

**核心功能**：
- 技能定义与管理
- 多工具组合调用
- 调用顺序编排
- 必需/可选工具控制
- 执行结果汇总

**关键实现文件**：
- `SkillController.java` - 技能接口控制器
- `SkillExecutor.java` - 技能执行器
- `SkillLoaderService.java` - 技能加载服务
- `skills.yaml` - 技能配置文件

### 8. MCP Agent系统 (MCP Agent)

**实现技术**：
- LangChain4j AiServices 动态工具注入
- ToolSpecification 自动生成
- Flux 流式响应

**核心功能**：
- AI自动调用已注册工具
- 带记忆的工具调用对话
- 流式工具调用响应

**关键实现文件**：
- `McpAgentController.java` - MCP Agent控制器
- `McpAgentService.java` - MCP Agent接口
- `McpAgentConfiguration.java` - MCP Agent配置
- `McpToolAdapter.java` - 工具适配器

### 9. 邮件系统 (Email)

**实现技术**：
- JavaMail API（Jakarta Mail）
- IMAP邮件监听
- SMTP邮件发送
- Spring Mail集成

**核心功能**：
- 邮箱配置管理
- IMAP邮件监听（轮询方式）
- 邮件内容解析与处理
- SMTP邮件发送
- 日程提取（从邮件提取日程事件）

**关键实现文件**：
- `EmailController.java` - 邮件接口控制器
- `EmailListenerService.java` - IMAP监听服务
- `EmailSenderService.java` - SMTP发送服务
- `EmailProcessingService.java` - 邮件处理服务

### 10. 日程管理系统 (Schedule)

**实现技术**：
- 文件存储（Markdown格式）
- 定时提醒（Spring Scheduling）
- 邮件汇总发送

**核心功能**：
- 日程创建与管理
- 日程提醒（每日8:00）
- 日程汇总（每日20:00）
- 从邮件自动提取日程
- 日程状态跟踪

**关键实现文件**：
- `ScheduleController.java` - 日程接口控制器
- `ScheduleFileService.java` - 日程文件服务
- `ScheduleSummaryService.java` - 日程汇总服务

### 11. 定时任务系统 (Tasks)

**实现技术**：
- Spring Scheduling
- Cron表达式
- 任务执行统计

**核心功能**：
- 任务创建与管理
- 支持技能调用任务
- 任务启用/禁用
- 执行历史记录
- 成功/失败统计

**关键实现文件**：
- `TaskController.java` - 任务接口控制器
- `ScheduledTaskService.java` - 定时任务服务

### 12. 文件管理系统 (Files)

**实现技术**：
- MultipartFile上传
- 文件内容提取
- AI智能分析

**核心功能**：
- 文件上传
- 文件类型限制（txt、md、pdf、doc、docx）
- 文件大小限制（10MB）
- AI自动分析（重要性、标签、情感、摘要）

**关键实现文件**：
- `FileController.java` - 文件接口控制器
- `FileUploadService.java` - 文件上传服务

### 13. 笔记系统 (Notes)

**实现技术**：
- Markdown文件存储
- AI摘要生成
- 置顶排序

**核心功能**：
- 笔记创建与管理
- 标签分类
- AI自动摘要
- 置顶标记

**关键实现文件**：
- `NoteController.java` - 笔记接口控制器

### 14. 代码片段系统 (Snippets)

**实现技术**：
- 代码高亮（highlight.js）
- 语言分类
- AI分析

**核心功能**：
- 代码片段保存
- 语言标签
- 描述说明
- 搜索查询

**关键实现文件**：
- `CodeSnippetController.java` - 代码片段控制器
- `CodeSnippetService.java` - 代码片段服务

### 15. 项目自治系统 (Autonomy)

**实现技术**：
- 项目结构扫描
- 前后端一致性检查
- AI补全草稿生成
- 构建验证执行

**核心功能**：
- 项目扫描与发现问题
- 前后端一致性验证
- 补全草稿自动生成
- 构建测试验证
- 扫描历史对比
- 安全策略控制（不直接改源码）

**关键实现文件**：
- `AutonomyController.java` - 自治接口控制器
- `ProjectAutonomyService.java` - 项目自治服务

### 16. 系统设置 (Settings)

**核心功能**：
- 系统参数配置
- 知识库去重开关
- 增量上传开关

**关键实现文件**：
- `SystemSettingsController.java` - 系统设置控制器
- `SystemSettingsService.java` - 系统设置服务

## 项目结构

```
agentDemo/
├── src/main/java/com/example/demo/
│   ├── controller/          # 控制器层（26个接口）
│   ├── service/             # 服务层
│   │   ├── ai/              # AI模型管理
│   │   ├── auth/            # 认证服务
│   │   ├── chat/            # 聊天服务
│   │   ├── memory/          # 记忆服务
│   │   ├── knowledge/       # 知识库服务
│   │   ├── search/          # 搜索服务
│   │   ├── mcp/             # MCP工具服务
│   │   ├── skill/           # 技能服务
│   │   ├── email/           # 邮件服务
│   │   ├── schedule/        # 日程服务
│   │   ├── file/            # 文件服务
│   │   ├── code/            # 代码服务
│   │   ├── autonomy/        # 自治服务
│   │   └── tool/            # 工具执行器
│   ├── entity/              # 实体类（25个）
│   ├── mapper/              # MyBatis Mapper（22个）
│   ├── dto/                 # 数据传输对象
│   ├── config/              # 配置类（12个）
│   ├── properties/          # 配置属性（11个）
│   ├── memory/              # 记忆模块
│   └── exception/           # 异常处理
│
├── src/main/resources/
│   ├── application.yaml     # 主配置文件
│   ├── skills.yaml          # 技能配置
│   ├── sql/                 # 数据库脚本
│   └── logback-spring.xml   # 日志配置
│
├── frontend/                # Vue前端项目
│   ├── src/
│   │   ├── views/           # 页面组件（21个）
│   │   ├── router/          # 路由配置
│   │   ├── services/        # API服务
│   │   ├── stores/          # Pinia状态
│   │   └── types/           # TypeScript类型
│   ├── package.json         # NPM依赖
│   └── vite.config.ts       # Vite配置
│
├── data/                    # 运行时数据
├── generated/               # AI生成产物
├── logs/                    # 运行日志
├── build.gradle             # Gradle构建配置
└── Dockerfile               # Docker容器配置
```

## 数据库设计

### 主要数据表

| 表名 | 说明 |
|------|------|
| user_account | 用户账号表 |
| user_face_profile | 人脸认证向量表 |
| oauth_account | 第三方账号绑定表 |
| auth_email_code | 邮箱验证码表 |
| ai_model_config | AI模型配置表 |
| chat_session | 聊天会话表 |
| chat_message | 聊天消息表 |
| knowledge_base | 知识库配置表 |
| knowledge_document | 知识库文档表 |
| mcp_tool | MCP工具配置表 |
| skill | AI技能表 |
| skill_tool_mapping | 技能-工具映射表 |
| schedule_event | 日程事件表 |
| scheduled_task | 定时任务表 |
| note | 笔记表 |
| code_snippet | 代码片段表 |
| email_config | 邮箱配置表 |
| document | 文件上传记录表 |
| search_history | 搜索历史表 |
| user_interest | 用户兴趣表 |
| system_settings | 系统设置表 |

## 部署说明

### 环境要求

- Java 17+
- Node.js 18+
- MySQL 8+
- Qdrant 1.17.0+
- Ollama（可选，用于本地Embedding）

### 配置步骤

1. **数据库初始化**
   ```bash
   mysql -u root -p agent < src/main/resources/sql/agent.sql
   ```

2. **配置文件修改**
   编辑 `application.yaml`，配置：
   - MySQL连接信息
   - AI模型API密钥
   - Qdrant向量库地址
   - 搜索引擎API密钥
   - SMTP邮箱配置

3. **启动Qdrant**
   ```bash
   docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant:v1.17.0
   ```

4. **启动Ollama（可选）**
   ```bash
   ollama pull nomic-embed-text
   ollama serve
   ```

5. **启动后端**
   ```bash
   ./gradlew bootRun
   ```

6. **启动前端**
   ```bash
   cd frontend
   pnpm install
   pnpm dev
   ```

### Docker部署

```bash
docker build -t ai-agent-demo .
docker run -p 8000:8000 ai-agent-demo
```

## 安全策略

- JWT双令牌机制（Access Token 2小时，Refresh Token 7天）
- Token版本控制（密码修改/退出后自动失效）
- 拼图验证码防机器人
- 人脸识别二次验证
- API密钥加密存储
- Spring Security接口保护

## API文档

详细API接口文档请参考 `API1.md`。

## 开发团队

本项目为单用户AI Agent演示项目，用于展示现代AI应用开发架构。