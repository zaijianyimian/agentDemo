# Agent Demo

一个采用 **Java 业务平台 + Python Agent Engine** 分层架构的智能邮件与个人工作台项目。

Java 不再承载 Agent Runtime。Spring Boot 负责认证、多租户、邮箱接入、普通业务 CRUD、文件与任务能力，并通过 `GraphGatewayClient` 将聊天与邮件 Agent 请求转发给 Python Graph 服务；LLM、Agent、Memory、RAG、MCP、Skill、Tool 与自主决策统一由 Python 负责。

## 当前架构

```text
Browser / Frontend
       |
       v
Spring Boot (Java)
  - Auth / JWT / Multi-tenant
  - Email listener / OAuth / IMAP / POP3 / Gmail / Graph
  - Schedule / Task / Note / File / System CRUD
  - HTTP / SSE API
       |
       | GraphGatewayClient
       v
Python Agent Engine
  - RoutingAgent
  - EmailAgent / ChatAgent / ScheduleAgent
  - LLM / Prompt
  - Memory / RAG / Embedding
  - MCP / Skill / Tool
  - Agent state / execution / conversation
```

核心原则：**Java 提供业务能力和安全边界，Python 负责智能决策与 Agent 执行。** Java 不直接连接 Python Agent 使用的向量库，也不实现本地 LLM/MCP/Memory 运行时。

## Java 层职责

当前 Java 主模块保留：

- `auth`：注册、登录、JWT、GitHub OAuth、人脸认证与用户生命周期。
- `email`：邮箱配置、OAuth、IMAP/POP3/Gmail/Microsoft Graph 接入、邮件监听、去重、附件存储、发送与通知。
- `chat`：HTTP/SSE Graph 网关以及兼容的普通会话业务接口；不执行本地 LLM/Agent。
- `schedule`：普通日程 CRUD、文件能力与事件推送。
- `task`：普通定时任务、提醒和执行记录。
- `note`：笔记 CRUD 与关键词查询，不进行向量语义检索。
- `file`：文件上传、校验、内容提取和元数据管理，不执行 AI 分析。
- `inbox`：Java 业务数据的统一收件箱聚合，不执行自主决策或语义搜索。
- `personal`：普通个人生产力业务能力。
- `system`：系统设置、Java 关系型数据与 `data/`、`generated/` 文件备份。
- `infrastructure`：安全、多租户、缓存、Web 配置及 Java → Python Graph 网关。

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
GraphGatewayClient
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
ChatController (Java)
   |
   v
GraphGatewayClient
   |
   +---- HTTP complete ----> Python ChatAgent
   |
   +---- SSE stream -------> Python ChatAgent
```

`ChatController` 只负责鉴权、用户上下文和 HTTP/SSE 协议适配，不在 Java 中执行模型、Memory、Tool 或 MCP。

## 数据所有权

建议保持数据库职责隔离，不做跨库 Join：

### Java / MySQL

- 用户与认证数据
- 邮箱配置与监听状态
- 日程、任务、笔记等普通业务数据
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
- WebFlux / SSE（仅用于 Graph 网关）
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
├── chat/              # Graph 聊天网关 + 普通会话业务
├── email/             # 邮箱接入与监听
├── file/              # 文件业务
├── inbox/             # 统一业务收件箱
├── infrastructure/
│   ├── config/
│   ├── graph/         # GraphGatewayClient
│   ├── properties/
│   ├── security/
│   └── web/
├── note/              # 笔记 CRUD
├── personal/          # 个人生产力业务
├── schedule/          # 日程业务
├── shared/            # 共享 DTO / 基础能力
├── system/            # 设置与备份
└── task/              # 普通任务调度
```

## 配置 Python Graph

Java 通过 `app.graph` 配置连接 Python：

```yaml
app:
  graph:
    enabled: true
    base-url: http://127.0.0.1:8001
    internal-token: ${GRAPH_INTERNAL_TOKEN:}
```

内部调用会携带用户上下文和内部鉴权信息。生产环境应设置独立的 `GRAPH_INTERNAL_TOKEN`，不要将其提交到仓库。

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
- Java 如果需要触发 Agent，应扩展 `GraphGatewayClient`/内部协议，而不是重新引入 Java LLM Runtime。

前端仍可能保留部分历史 Agent 管理页面或 API 封装；它们后续应改为面向 Python Agent API，而不应促使 Java 恢复已经剥离的 Agent 模块。
