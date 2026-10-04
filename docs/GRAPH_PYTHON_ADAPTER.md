# Java 与 Python Graph 边界

Java 不再承载聊天网关、聊天历史、LLM 调用、Agent 决策或 AI 专属执行编排。

## 同域路由

浏览器只与一个源通信，按路径前缀分流：

| 浏览器路径 | 目标 | 说明 |
|---|---|---|
| `/api/*` | Java `:8000` | 认证、邮箱、日程、任务、文件、设置 |
| `/ai/*` | Python `:8001` | 聊天、会话、邮件 AI 分析 |

`/ai/*` 会被代理改写为 Python 的 `/api/*`：`/ai/chat/turn` → `/api/chat/turn`。
`docker/nginx-frontend.conf` 与 `frontend/vite.config.ts` 使用同一套改写规则，
因此开发与生产的前端请求路径完全相同。

Python 的 `/internal/*` 不在该前缀下，无法经浏览器入口访问：
`/ai/internal/...` 会被改写为 `/api/internal/...`，而 Python 未暴露该前缀。
端口 8001 同样不对公网开放。

## 浏览器聊天

浏览器请求 `/ai/chat/*`，Python 调用 Java 的 `POST /api/auth/introspect`
校验 Bearer access token，再以返回的 `user_id` 隔离会话、消息和 checkpoint。
Java 的该端点只负责认证。聊天请求不经过 Java 转发。

Python 默认 `AUTH_JAVA_BASE_URL=http://127.0.0.1:8000`（与 Java `SERVER_PORT`
默认值一致）。容器部署时 127.0.0.1 指向 Python 自身容器，必须改为 Java 的
服务名，例如 `http://backend:8000`。

## 邮件事件

Java 保留邮箱接入与新邮件 RabbitMQ 投递：`GraphEmailDispatchListener`、
`GraphEmailRabbitConfiguration` 和 `GraphGatewayProperties` 的邮件交换机、队列、
routing key 配置。Python 消费邮件事件并执行 Agent 分析。

事件体包含完整邮件内容与 `event_id`（`userId|provider|externalId` 派生的
确定性 UUIDv3），Python 以 `event_id` 唯一索引做幂等消费。`received_at` 由
Java 以无时区的 `LocalDateTime` 发送，Python 按 `JAVA_EVENT_TIMEZONE`
（默认 `Asia/Shanghai`）补齐时区后写入 `timestamptz`。

Python 消费失败时按消息头 `x-retry-count` 限次重投，超过
`RABBITMQ_MAX_RETRIES`（默认 3）后转发到死信交换机
`agent_email_dlx` / `agent_email_dlq`，不再无限重投。

## 日程归属

日程的唯一事实来源是 Java 的 MySQL `schedule_event` 与既有日程页面。

Agent 的 `create_schedule` Tool 调用 Java 的 `POST /api/internal/schedule`，
不再写 Python 自己的 PostgreSQL `schedule` 表——两套日程会导致 Agent 创建的
条目在日程页面中不可见。Python 的 `schedule` 表与相关 Service/Repository
已不再被日程创建路径调用，历史数据处置需单独确认。

`POST /api/schedule` 无法被 Python 调用：`ScheduleEventService.create` 只从
`CurrentUserContext` 取 userId，不接受请求体或请求头中的 userId。因此
`/api/internal/schedule` 由 `ExecutionContextFactory.forPersistedOwner` 绑定
可信身份，并复用既有的文件同步逻辑。

## 服务间认证

`/api/internal/**`（Java）与 `/internal/*`（Python）要求同一个共享令牌，
通过 `X-Internal-Token` 传递：

- Java：`app.graph.internal-token` ← `GRAPH_INTERNAL_TOKEN`
- Python：`INTERNAL_SERVICE_TOKEN`（入站）、`JAVA_SERVICE_TOKEN`（出站）

不能仅凭 `X-User-Id` 或请求体中的 `user_id` 授权：这两个字段都由调用方自行
填写。任一侧未配置令牌时一律拒绝（Java 503、Python 503），不会退化为放行。
两侧均使用定长比较，避免时序侧信道。

## 邮件分析结果来源

Java 的 `GET /api/email/messages/{messageId}` 读取监听进程内的 Caffeine 缓存
（200 条、1 小时过期），进程重启或淘汰后返回 `null`，不读数据库，不能作为
分析结果的长期来源。持久化结果由 Python 从 PostgreSQL 提供：

| 接口 | 说明 |
|---|---|
| `GET /ai/email/list` | 当前用户邮件及分析结果列表 |
| `GET /ai/email/messages/{email_id}` | 单封邮件详情 |
| `GET /ai/email/by-message-id/{message_id}` | 按 Message-ID 查询，供邮件详情页使用 |

三者都以 Java 内省得到的 `user_id` 过滤，用户之间互相不可见。

## 部署

`docker-compose.yml` 编排 MySQL、PostgreSQL、RabbitMQ、Python 与 Java 六个组件，
均带健康检查。Python 的 `migrations/` 下：

- `20260913_add_email_user_scope.sql` **是破坏性的**（DROP 三张表），仅用于空库重建。
- `20260913_add_email_event_id.sql`、`20260929_add_chat_session_message.sql`
  是非破坏性增量脚本，已有数据的库按此顺序执行。

LangGraph checkpointer 首次启动执行 `setup()` 自动建表。

旧 MySQL 聊天数据按产品决定舍弃。先完成新链路验收，再运行
`src/main/resources/sql/preflight_legacy_chat_tables.sql`；审阅结果后在维护窗口运行
`src/main/resources/sql/drop_legacy_chat_tables.sql`。应用启动不会自动删除表。

新的 Java MySQL 初始化与增量脚本不再创建旧 AI 表。已有数据库中的其他历史 AI
表会保留原数据，迁移脚本不再修改这些表；后续数据处置需单独确认。
