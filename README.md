# Agent Demo

一个采用 **Java 业务平台 + Python Agent Engine** 分层架构的智能邮件与个人工作台项目。

Java 不再承载 Agent Runtime。Spring Boot 负责认证、多租户、邮箱接入、普通业务 CRUD、文件与任务能力，并通过 RabbitMQ 投递邮件事件；浏览器聊天请求直接送至 Python Graph 服务。LLM、Agent、Memory、RAG、MCP、Skill、Tool 与自主决策由 Python 负责。

## 当前架构

```text
Browser / Frontend -- /api/*  --> Spring Boot (Java)
Browser / Frontend -- /ai/*   --> Python Graph API（聊天 + 邮件 AI 分析）
Spring Boot 邮件监听 -- RabbitMQ --> Python EmailAgent
Python Graph -- /api/auth/introspect --> Spring Boot 认证接口
Python Graph -- /api/internal/schedule --> Spring Boot 日程写入
```

浏览器只与一个源通信，按前缀分流：`/api/*` 归 Java，`/ai/*` 转发到 Python
（`/ai/chat/...` → Python 的 `/api/chat/...`）。生产 nginx 与 Vite 开发代理
使用同一套改写规则，两种环境下前端请求路径完全一致。Python 的 `/internal/*`
不经反代暴露。

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
Reverse proxy (/ai/chat/*  ->  Python /api/chat/*)
   |
   v
Python Graph Chat API
```

Python 调用 Java `/api/auth/introspect` 校验浏览器 Bearer 令牌，聊天会话与消息
保存在 PostgreSQL。聊天请求不经过 Java 转发。

## 日程链路

日程的唯一事实来源是 Java 的 MySQL `schedule_event` 与既有日程页面。Agent 的
`create_schedule` Tool 调用受保护的 `POST /api/internal/schedule`，由 Java 写入，
因此 Agent 创建的日程立即出现在现有日程页面与提醒中。Python 不再另存一份日程。

## 服务间认证

Python 的 `/internal/*` 与 Java 的 `/api/internal/**` 共享同一个服务令牌，通过
`X-Internal-Token` 传递（`GRAPH_INTERNAL_TOKEN` ↔ `INTERNAL_SERVICE_TOKEN` /
`JAVA_SERVICE_TOKEN`）。不能仅凭 `X-User-Id` 或请求体中的 `user_id` 授权——这两个
字段都由调用方自行填写。任一侧未配置令牌时一律拒绝，不会退化为放行。

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
./gradlew test
```

前端执行：

```bash
cd frontend
npm ci
npm run type-check
npm run build
```

PR 到 `improve` 会通过 `.github/workflows/refactor-check.yml` 自动执行上述编译与前端检查。

## 本地集成启动

`docker-compose.yml` 编排六个组件：MySQL、PostgreSQL、RabbitMQ、Python
Agent Engine、Java 后端与前端 nginx，均带健康检查，`backend` 依赖基础设施
healthy 后才启动。

宿主机上通常已经跑着 MySQL / PostgreSQL / RabbitMQ，所以这三个服务**不对宿主机
发布端口**（容器间已由 `agentdemo_default` 网络互通）。Java 只对容器内的 nginx
监听 8000，Python Graph 也不发布端口，宿主机上唯一暴露的是 nginx 的 3000 端口。

### 端口隔离

Python 的 `/docs` 与 `/internal/*` 不能经浏览器触达，默认部署因此**不发布** graph
的 8001 端口，只在容器网络内供 nginx（`graph:8001`）与 Java（`http://graph:8001`）
访问。端口隔离排在服务令牌之前，不依赖防火墙或令牌来代替。

宿主机 Vite 开发需要直连 Python 时，用开发覆盖配置，它**只绑定 127.0.0.1**：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d graph
cd frontend && npm run dev     # Vite 的 /ai 代理指向 http://localhost:8001
```

不要在集成/验收部署中使用该覆盖文件。验收时可直接确认宿主机直连已关闭：

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8001/docs   # 期望 000（连接被拒）
docker compose ps --format '{{.Name}}\t{{.Ports}}'                      # 只有 agentdemo 列出 3000
```

前置条件：Python 项目（默认 `../emailagent`，可用 `GRAPH_CONTEXT` 覆盖）需与
本仓库同级。首次运行前在 `.env` 中补齐以下**必需**变量——缺任一项
`docker compose` 会直接报错退出，而不会以空值静默启动：

```bash
MYSQL_PASSWORD=...            # MySQL root 密码
POSTGRES_PASSWORD=...         # Python 侧 PostgreSQL 密码
GRAPH_INTERNAL_TOKEN=...      # Java 与 Python 的服务间共享令牌
OPENAI_API_KEY=...            # LLM 分析所需
```

生成共享令牌：`openssl rand -hex 32`

启动与检查：

```bash
docker compose up -d --build

# 六个组件的健康状态
docker compose ps

# 基础设施就绪后再看应用
docker compose logs -f backend graph
```

逐项健康检查：

```bash
curl -fsS http://127.0.0.1:3000/nginx-health                 # 前端 nginx
docker compose exec backend \
  wget -qO- http://127.0.0.1:8000/actuator/health/liveness   # Java（只在容器内监听，未发布到宿主机）
# Python 同样不对宿主机发布端口，健康检查在容器内做
docker compose exec graph \
  python -c "import urllib.request;print(urllib.request.urlopen('http://127.0.0.1:8001/docs',timeout=5).status)"
docker compose exec postgres pg_isready -U agent -d agent    # PostgreSQL
docker compose exec rabbitmq rabbitmq-diagnostics -q ping     # RabbitMQ
docker compose exec mysql mysqladmin ping                    # MySQL
```

浏览器唯一入口是 <http://localhost:3000>。Java 只对容器内的 nginx 监听 8000，
不发布到宿主机；MySQL/PostgreSQL/RabbitMQ 同样不对宿主机发布端口，
宿主机上通常已有同款服务。聊天请求走 `/ai/chat/*`，业务接口走 `/api/*`。

### 首次启动的数据库准备

MySQL 会在数据卷为空时自动执行 `src/main/resources/sql/schema_init.sql`
（全部是 `CREATE TABLE IF NOT EXISTS`，不含 DROP/TRUNCATE）。

PostgreSQL **不会**自动建业务表，需在 stack 起来后执行一次 `emailagent/migrations/`
下的脚本。三个脚本都**非破坏性且可重复执行**，空库初始化和已有库升级用同一套顺序：

```bash
cd ../emailagent
C="docker compose -f ../agentDemo/docker-compose.yml exec -T postgres psql -v ON_ERROR_STOP=1 -U agent -d agent"
$C -f - < migrations/20260913_add_email_user_scope.sql    # 1. 邮件与日程表结构
$C -f - < migrations/20260913_add_email_event_id.sql      # 2. event_id 列与唯一索引
$C -f - < migrations/20260929_add_chat_session_message.sql # 3. 聊天会话与消息
```

**顺序不能调换**：第 2 个依赖第 1 个建出的 `email_message` 表。

各脚本在两种情形下的行为：

- **空库初始化**：表不存在时直接建表，user_id 收紧为 `NOT NULL`。
- **已有库升级**：只补齐缺失的列与索引，绝不 DROP、绝不改写已有行。
- **单用户旧结构且表内已有数据**：`20260913_add_email_user_scope.sql` 会**中止并
  报错退出**，因为每行的 `user_id` 归属无法自动推断。它不会删数据，也不会写入
  猜测的用户 ID；请人工确认归属后按报错提示补 `user_id` 再重跑。
- **存在 `user_id` 为空的历史行**：脚本发出 NOTICE 并**跳过** `NOT NULL` 约束，
  同样不会自行回填。补齐后再重跑一次即可收紧。

LangGraph checkpoint 由应用启动时的 `setup()` 自动建表，不在上面这批脚本里。

`test-migrations.sh` 覆盖上述全部情形（空库首次执行、重复执行、已有库升级、
旧结构有数据、旧结构空表），全部在独立的 `migtest_*` 测试库上进行，不触碰
`agent` 库与其数据：

```bash
./test-migrations.sh
```

### 端到端验证要点

```bash
# 1. 注册并登录，取得 Bearer 令牌
curl -sX POST http://127.0.0.1:3000/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"u1","password":"Passw0rd!","email":"u1@example.test"}'

# 2. 令牌内省（Python 用它换取可信 user_id）
curl -sX POST http://127.0.0.1:3000/api/auth/introspect \
  -H "Authorization: Bearer $TOKEN"

# 3. 创建会话（SSE 流式聊天见下）
curl -sX POST http://127.0.0.1:3000/ai/chat/sessions \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'

# 4. SSE 流式聊天
curl -N -X POST http://127.0.0.1:3000/ai/chat/turn/stream \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"session_id\":\"$SESSION_ID\",\"message\":\"你好\"}"

# 5. 无效令牌必须被拒绝
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:3000/ai/chat/sessions \
  -H 'Authorization: Bearer invalid-token'          # 期望 401

# 6. /internal/* 不得经浏览器入口访问
curl -s -o /dev/null -w '%{http_code}\n' \
  http://127.0.0.1:3000/ai/internal/chat/complete   # 期望 404
```

邮件链路需要真实邮箱账号与 LLM Key 才能观察到完整结果；日程链路可由
ChatAgent 触发 `create_schedule` 后在现有日程页面确认。

以上场景已脚本化为 `./test-e2e.sh`：它会自建两个隔离测试账号，走完整链路并
逐项断言（注册/内省、SSE 逐块与 `[DONE]`、取消后释放占用、A/B 越权、
`create_schedule` 落 MySQL 并可由现有日程 API 查到、服务令牌拒绝）。它不打印
令牌与邮件正文。邮件事件的 **Java 发布环节**需要真实可登录的邮箱账号，见
「已知阻塞」。

### 没有 LLM Key 时：mock-llm 桩服务
`mock-llm` 是一个 OpenAI 兼容的桩服务（默认不启动，profile `mock-llm`），
用于在没有可用 Key 的环境里验证**链路本身**——`/ai` 路由、SSE 逐块透传、
聊天历史落库、Agent 调 `create_schedule` 写 Java 日程、邮件事件经 RabbitMQ
进 PostgreSQL 后前端可读。它返回固定文本，**不能用来评估回答质量**。

```bash
docker compose --profile mock-llm up -d mock-llm

# 用 shell 环境变量临时覆盖，不要写进 .env
CHAT_BASE_URL=http://mock-llm:9000/v1 OPENAI_API_KEY=mock \
  docker compose up -d graph
```

`app/llm/chat_model.py` 读的是 `CHAT_API_KEY` / `CHAT_BASE_URL`，未设置时回落到
`OPENAI_API_KEY` / `OPENAI_API_BASE`；`.env` 里留空的值也会回落到后者。

桩服务覆盖了 Agent 侧的三种调用形态：`json_schema` 结构化输出（邮件分析）、
`function_calling` 结构化输出（RoutingAgent 决策）、以及 `bind_tools` 绑定的
真实工具（仅在用户消息含"日程"时调用一次 `create_schedule`）。

桩服务的 `MOCK_LLM_CHUNK_DELAY` 控制每个流式分块的间隔（默认 0.01 秒）。
验收「生成中取消」时调大它，让一条回复持续十几秒，从而能在中途断开：

```bash
MOCK_LLM_CHUNK_DELAY=0.5 docker compose --profile mock-llm up -d --force-recreate mock-llm
# ... 跑取消用例 ...
docker compose --profile mock-llm up -d --force-recreate mock-llm   # 恢复默认
```

### 真实 LLM 配置：凭据来源必须成对配置

`app/llm/chat_model.py` 优先读 `CHAT_API_KEY` / `CHAT_BASE_URL`，为空时回落到
`OPENAI_API_KEY` / `OPENAI_API_BASE`。**Key 与 Base 必须成对来自同一家服务**：
如果 Key 来自第三方网关而 `OPENAI_API_BASE` 留空，请求会带上该 Key 发往
`api.openai.com`，等于把网关凭据泄露给 OpenAI 官方地址。因此使用网关时必须同时
设置 `CHAT_BASE_URL` 与 `CHAT_API_KEY`（或同时设置 `OPENAI_API_BASE` 与
`OPENAI_API_KEY`），不要只设置 Key。

## 后续开发原则

新增智能能力时优先判断其职责：

- **需要 LLM 推理、路由、记忆、工具选择、RAG、MCP 的能力 → Python Agent Engine。**
- **确定性的账户、邮箱、文件、日程、任务、权限与数据 CRUD → Java。**
- Java 如果需要触发 Agent，应使用明确的事件或内部协议。

历史 Agent 管理页目前显示迁移说明，待 Python 提供对应接口后再接入。
