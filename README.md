# Agent Demo

一个采用 **Java 业务平台 + Python Agent Engine** 分层架构的智能邮件与个人工作台项目。

Java 不再承载 Agent Runtime。Spring Boot 负责认证、多租户、邮箱接入、普通业务 CRUD、文件与任务能力，并通过 RabbitMQ 投递邮件事件；浏览器聊天请求直接送至 Python Graph 服务。LLM、Agent、Memory、RAG、MCP、Skill、Tool 与自主决策由 Python 负责。

## 当前架构

```text
Browser / Frontend -- business API --> Spring Boot (Java)
Browser / Frontend -- /api/chat/* --> Python Graph Chat API
Spring Boot 邮件监听 -- RabbitMQ --> Python EmailAgent
Python Graph -- /api/auth/introspect --> Spring Boot 认证接口
```

核心原则：**Java 提供业务能力和安全边界，Python 负责智能决策与 Agent 执行。** Java 不直接连接 Python Agent 使用的向量库，也不实现本地 LLM/MCP/Memory 运行时。

## Java 层职责

当前 Java 主模块保留：

- `auth`：注册、登录、JWT、GitHub OAuth、人脸认证与用户生命周期。
- `email`：邮箱配置、OAuth、IMAP/POP3/Gmail/Microsoft Graph 接入、邮件监听、去重、附件存储、发送与通知。
- `schedule`：普通日程 CRUD、文件能力与事件推送。
- `task`：普通定时任务、提醒和执行记录。
- `file`：文件上传、校验、内容提取和元数据管理，不执行 AI 分析。
- `inbox`：Java 业务数据的统一收件箱聚合，不执行自主决策或语义搜索。
- `system`：系统设置、Java 关系型数据与 `data/`、`generated/` 文件备份。
- `infrastructure`：安全、多租户、缓存、Web 配置及邮件事件投递。

以下能力已经从 Java Runtime 剥离：

- LangChain4j / Java LLM Runtime
- Memory
- RAG / Embedding / Qdrant
- MCP Runtime
- Skill Runtime
- Autonomy
- AI Dispatch / Executor
- AI Search
- Java 邮件内容/附件 AI 分析
- Java 知识库向量化与语义检索

## Python Agent 职责

Python Agent Engine 负责：

- 请求路由与 Agent 选择。
- EmailAgent、ChatAgent、ScheduleAgent 等 Agent 实现。
- 模型调用、Prompt、Structured Output。
- 长期记忆、会话状态、Agent State。
- RAG、Embedding、Qdrant 等向量能力。
- MCP、Skill、Tool 注册与调用。
- Agent 重试、容错、执行记录与自主决策。

Java 与 Python 的协议边界见：`docs/GRAPH_PYTHON_ADAPTER.md`。

## 邮件处理链路

```text
Mailbox Provider
      |
      v
Java Email Listener
      |
      v
EmailReceivedEvent
      |
      v
GraphEmailDispatchListener
      |
      v
RabbitMQ
      |
      v
Python EmailAgent
```

Java 负责可靠接入邮箱和确定用户身份；Python 负责邮件理解、分类、摘要、决策及后续 Agent 行为。

## 聊天链路

```text
Frontend
   |
   v
Reverse proxy (/api/chat/*)
   |
   v
Python Graph Chat API
```

Python 调用 Java `/api/auth/introspect` 校验浏览器 Bearer 令牌，聊天会话与消息保存在 PostgreSQL。

## 数据所有权

建议保持数据库职责隔离，不做跨库 Join：

### Java / MySQL

- 用户与认证数据
- 邮箱配置与监听状态
- 日程、任务等普通业务数据
- 文件元数据
- 系统设置

### Python / PostgreSQL + Vector Store

- Agent conversation
- Agent state
- Agent execution
- Tool execution
- Memory
- Email Agent 分析结果
- Embedding metadata / Vector data

两侧通过 `user_id`、`email_id`、`session_id`、`execution_id` 等稳定标识关联。

## 技术栈

### Java

- Java 17
- Spring Boot 3.5
- Spring Security / JWT Resource Server
- Spring Modulith
- MyBatis-Plus
- MySQL
- Caffeine
- Jakarta Mail / Gmail API / Microsoft Graph 接入
- PDFBox / Apache POI
- Actuator / Prometheus

### Frontend

- Vue 3
- TypeScript
- Vite
- Naive UI
- Pinia
- Vue Router

### Python Agent Engine

- FastAPI
- LangGraph
- PostgreSQL
- RabbitMQ
- Qdrant
- MinIO

## Java 目录结构

```text
src/main/java/com/example/demo/
├── auth/              # 认证与用户
├── email/             # 邮箱接入与监听
├── file/              # 文件业务
├── inbox/             # 统一业务收件箱
├── infrastructure/
│   ├── config/
│   ├── graph/         # 邮件事件 RabbitMQ 配置
│   ├── properties/
│   ├── security/
│   └── web/
├── schedule/          # 日程业务
├── shared/            # 共享 DTO / 基础能力
├── system/            # 设置与备份
└── task/              # 普通任务调度
```

## 配置 Python Graph

Java 通过 `app.graph` 配置把新邮件投递给 Python；浏览器聊天请求由反向代理直接送至 Python：

```yaml
app:
  graph:
    enabled: true
    email-exchange: agent_email
    email-queue: agent_email_queue
    email-routing-key: agent_email
```

浏览器聊天使用 Java 签发的 Bearer 令牌，由 Python 调用 `/api/auth/introspect` 校验。
Python 的部署与反向代理配置见配套 `graph` 仓库的 `docs/ARCHITECTURE.md`。

## 本地检查

后端至少执行：

```bash
./gradlew compileJava compileTestJava
```

前端执行：

```bash
cd frontend
npm ci
npm run type-check
npm run build
```

PR 到 `improve` 会通过 `.github/workflows/refactor-check.yml` 自动执行上述编译与前端检查。

## 后续开发原则

新增智能能力时优先判断其职责：

- **需要 LLM 推理、路由、记忆、工具选择、RAG、MCP 的能力 → Python Agent Engine。**
- **确定性的账户、邮箱、文件、日程、任务、权限与数据 CRUD → Java。**
- Java 如果需要触发 Agent，应使用明确的事件或内部协议。

历史 Agent 管理页目前显示迁移说明，待 Python 提供对应接口后再接入。
