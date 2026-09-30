# Agent Demo

日程与故障恢复的最新启动、增量迁移及验证说明见 [2026-09-30 修复说明](docs/20260930-recovery-fixes.md)。

一个采用 **Java 业务平台 + Python Agent Engine** 分层架构的智能邮件与个人工作台项目。

Java 不再承载 Agent Runtime。Spring Boot 负责认证、多租户、邮箱接入、普通业务 CRUD、文件与任务能力，并通过 RabbitMQ 投递邮件事件；浏览器聊天请求直接送至 Python Graph 服务。LLM、Agent、Memory、RAG、MCP、Skill、Tool 与自主决策由 Python 负责。

## 当前架构

```text
Browser / Frontend -- business API --> Spring Boot (Java, :8000)
Browser / Frontend -- AI API      --> Python Graph API (:8001)
Spring Boot 邮件监听 -- RabbitMQ --> Python EmailAgent
Python Graph -- /api/auth/introspect --> Spring Boot 认证接口
```

**前后端分流规则：AI 相关能力走 Python，其余业务 API 走 Java。** 判定依据是「是否需要
LLM 推理 / RAG / Agent 调度」，而不是模块名。

| 归属 | 路径前缀 |
| --- | --- |
| Python（AI） | `/api/chat`、`/api/knowledge`、`/api/search`、`/api/model`、`/api/skill`、`/api/markdown-skill`、`/api/snippet`、`/api/autonomy`、`/api/report`、`/api/mcp`、`/api/chatimport` |
| Java（业务） | `/api/auth`、`/api/email`、`/api/file`、`/api/task`、`/api/schedule`、`/api/backup`、`/api/dispatched`、`/api/dispatch`、`/api/inbox`、`/api/settings` |

这份清单在两处各有一份定义，**新增 AI 前缀时必须同步修改**，否则同一接口在开发环境与生产环境
会打到不同的后端：

- 开发：`frontend/vite.config.ts` 的 `AI_API_PREFIXES`
- 生产：`docker/nginx-frontend.conf` 的 `map $uri $api_upstream`

> 容器部署请使用仓库根的 `docker-compose.yml`，它会拉起完整拓扑
> （`backend` / `graph` / `frontend` / `mysql` / `postgres` / `rabbitmq`），
> nginx 的 `$api_upstream` 指向服务名 `backend:8000` 与 `graph:8001`。
> 单独构建 `Dockerfile` 的 `runtime` 阶段只含 Java 与 nginx，**不含 Python**，
> 此时 AI 前缀会返回 502（上游不存在）——这是部署不完整，不会静默回落到 Java。
>
> 前缀匹配已加边界 `(?=/|$)`：`/api/chats`、`/api/models`、`/api/search-config`
> 之类的业务路径不会被误判为 AI 接口。

核心原则：**Java 提供业务能力和安全边界，Python 负责智能决策与 Agent 执行。** Java 不直接连接 Python Agent 使用的向量库，也不实现本地 LLM/MCP/Memory 运行时。

## Java 层职责

当前 Java 主模块保留：

- `auth`：注册、登录、JWT、GitHub OAuth、人脸认证与用户生命周期。
- `email`：邮箱配置、OAuth、IMAP/POP3/Gmail/Microsoft Graph 接入、邮件监听、去重、附件存储、发送与通知。
- `schedule`：普通日程 CRUD 与事件推送。
- `task`：普通定时任务、提醒和执行记录。
- `file`：文件上传、校验、内容提取和元数据管理，不执行 AI 分析。
- `inbox`：Java 业务数据的统一收件箱聚合，不执行自主决策或语义搜索。
- `dispatch`：外部 Agent 产生的执行请求与终态结果、推送配置、消费去重与 outbox。没有合格隔离
  Worker 时任务以 `WORKER_UNAVAILABLE` 安全失败，Java 不启动本机进程或创建工作区。
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
- **日程（唯一业务数据源）**、任务等普通业务数据
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

## AI 创建日程的写入路径

聊天与邮件产生的日程**不再由 Python 另建副本**，而是经内部接口写入本库，
因此前端 `/api/schedule` 立即可见，并参与既有提醒与 SSE 推送。

```text
ChatAgent / EmailAgent  create_schedule Tool
   │  （模型只填 title / description / location / start_time / end_time）
   ▼
POST /internal/schedule          X-User-Id + X-Graph-Signature + X-Idempotency-Key
   ▼
InternalCallAuthenticator        校验共享密钥签名 → 建立可信用户身份
   ▼
ScheduleEventService.createIdempotently  →  schedule_event
```

要点：

- **内部请求必须认证**：HMAC-SHA256 签名，密钥 `GRAPH_INTERNAL_TOKEN`（≥32 字符）。
  未配置时 fail-closed 返回 503，不会以空密钥放行。
- **身份不由模型指定**：归属取自已认证的内部调用上下文，Tool 参数中没有 `user_id`。
- **幂等**：`uk_schedule_event_user_idempotency (user_id, idempotency_key)`。
  同一动作的重试、并发重试、以及「业务提交成功但响应丢失」都只产生一条日程，
  重复请求返回原始创建结果。
- **邮件 ID 两套编号**：Python 的 `email_id` 是 PostgreSQL 自增主键，与 Java 邮件主键
  不是同一套编号。两侧稳定的共同标识是 Java 投递时派生的确定性 `event_id`（UUID），
  日程按该 UUID 写入 `source_email_event_id`。
- **`/internal/**` 不经反代暴露**：nginx 显式 `location /internal/ { return 404; }`。

迁移脚本：`src/main/resources/sql/migration_schedule_ai_source.sql`（只增列与索引，
不删除既有数据）。详见 `graph/docs/ARCHITECTURE.md` §4.1–4.2。

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
├── dispatch/          # 执行请求、推送配置、消费去重与 outbox
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

## 本地启动

### 方式一：分别启动（开发默认）

```bash
# 1) 依赖：MySQL、PostgreSQL、RabbitMQ
# 2) 数据库初始化
#    MySQL:      src/main/resources/sql/schema_init.sql
#                + src/main/resources/sql/migration_schedule_ai_source.sql
#    PostgreSQL: graph/migrations/*.sql
# 3) Java :8000
./gradlew bootRun
# 4) Python :8001（在 ../graph）
uv sync --locked && uv run --locked python -m app.serve
# 5) 前端 :3000
cd frontend && npm ci && npm run dev
```

`GRAPH_INTERNAL_TOKEN` 必须同时配置在 Java 与 Python 两侧，否则日程无法由 AI 链路创建
（Java 侧会 fail-closed 拒绝 `/internal/**`）。

### 方式二：Docker Compose（完整拓扑）

```bash
cp .env.example .env      # 填入 MYSQL_*/RABBITMQ_*/POSTGRES_*/GRAPH_INTERNAL_TOKEN/JWT_SECRET
docker compose up -d --build
docker compose ps         # 等待各服务 healthy
```

编排包含 `backend`（Java :8000）、`graph`（Python :8001）、`frontend`（nginx :3000）、
`mysql`、`postgres`、`rabbitmq`。容器间一律用服务名通信，nginx 的 `$api_upstream`
指向 `backend:8000` / `graph:8001`，并通过 `depends_on: service_healthy` 保证启动顺序。

注意：`docker build` 的上下文是包含 `agentDemo` 与 `graph` 的**父目录**，
因为 Python 服务源码在同级仓库 `graph`。单独 `cd agentDemo && docker build .`
无法构建 `graph` 阶段。

## 后续开发原则

新增智能能力时优先判断其职责：

- **需要 LLM 推理、路由、记忆、工具选择、RAG、MCP 的能力 → Python Agent Engine。**
- **确定性的账户、邮箱、文件、日程、任务、权限与数据 CRUD → Java。**
- Java 如果需要触发 Agent，应使用明确的事件或内部协议。
- Python 需要写 Java 业务数据时，走 `/internal/**` 签名接口，不要自建副本。

## 当前前端缺口

以下路由指向同一个 `views/LegacyFeature.vue` 迁移说明页，功能尚未接入：

`/autonomy`、`/models`、`/knowledge`、`/knowledge-search`、`/search`、`/tools`、`/skills`、
`/markdown-skills`、`/snippets`、`/reports`、`/chatimport`

它们对应从 Java 剥离、Python 侧尚未重新暴露的 AI 能力。路由已存在；对应的 AI 前缀在
Python 侧现在返回 **501 + `AI_CAPABILITY_NOT_IMPLEMENTED`**，而不是 404 或空列表，
以明确区分「未实现」与「出错」。

`views/` 下另有 10 个同名旧视图（`AutonomyCenter.vue`、`Knowledge.vue`、`KnowledgeSearch.vue`、
`Skills.vue`、`Tools.vue`、`Models.vue`、`Reports.vue`、`Snippets.vue`、`Search.vue`、
`MarkdownSkills.vue`、`ChatImport.vue`）已无任何引用，属于迁移遗留。当前**有意保留**，作为上述路由
未来恢复功能时的参考实现，避免重复开发。

它们不会渲染，也**不会被打进产物**——`vite build` 从 `router/index.ts` 出发做 tree-shaking，
未注册的视图不会产生任何 chunk；只有 `vue-tsc` 仍会检查它们（`tsconfig.json` 的 `include`
覆盖 `src/**/*.vue`）。因此修改这些文件不会影响线上包体，但类型错误会让 CI 失败。

另有 `views/MaintenanceUnavailable.vue` 既未被路由引用、也未在上文列出，属于无出处的遗留文件，
可在确认不再需要后删除。

已修复的可见缺口：

| 缺口 | 处理 |
|---|---|
| `GET /api/schedule/share/{date}` 404（`/schedule-share/:date` 公开页必现） | Java 补齐该端点，返回 `{found, content}` |
| `POST /api/schedule/parse-and-save` 不存在 | 移除该调用；日程页「AI 添加」把描述带到聊天页，由 Agent 创建 |
| `POST /api/schedule/parse-email` 无调用方 | 移除该方法声明 |

