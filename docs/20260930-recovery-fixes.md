# 日程与故障恢复修复（2026-09-30）

Java 只负责内部调用认证、日程 CRUD 和消息传输；模型与工具决策仍由 Python 执行。

## 首次 Docker 部署

在 `agentDemo` 目录复制 `.env.example` 为 `.env`，填写 MySQL、PostgreSQL、RabbitMQ、
JWT 和内部调用密钥以及 Python 模型参数。`CHAT_MODEL`、`CHAT_API_KEY`、`CHAT_BASE_URL`
只传给 Python；`GRAPH_INTERNAL_TOKEN` 必须至少 32 字符。

运行 `docker compose up -d --build`。构建上下文是两个仓库的父目录。
Dockerfile 专用 ignore 文件排除本地 `.env`、`application.yaml`、依赖目录和数据目录；
Java 镜像使用 `application.example.yaml`，通过环境变量配置。

首次建库使用显式挂载：MySQL `schema_init.sql`；PostgreSQL `000_bootstrap.sql` →
邮件 event_id → 聊天表 → 消息 turn_id。不会自动运行历史重建/删除迁移。
nginx 使用 Docker DNS 和 backend/graph 服务名。

## 已有数据库

不要删除已有数据卷。先备份，再手动执行所需增量 SQL：

- MySQL：`src/main/resources/sql/migration_schedule_ai_source.sql`（尚未执行时）。
- PostgreSQL：`graph/migrations/20260930_chat_turn_idempotency.sql`。

已有聊天记录的 turn_id 保持 NULL，不猜测旧请求身份。
新迁移执行后再启动新代码。Compose 的首次初始化脚本不会迁移已有数据卷。
历史 `20260913_add_email_user_scope.sql` 会删表，不能用于已有库升级。

## 恢复语义

- 非 UTC 偏移（例如 UTC+08:00）可通过 Java 校验。内部响应携带时间偏移，
  Python 不再依赖浏览器接口的 LocalDateTime 格式。
- 同一请求的工具调用保存在 checkpoint；失败后用空输入恢复，保持原工具调用 ID。
  Java 唯一索引保证重复调用返回同一条日程。MySQL 并发冲突回读使用锁定读。
- 聊天消息按用户、会话、turn_id、角色去重。完成的请求直接返回原回复；相同
  请求 ID 携带不同内容返回冲突。前端在失败后保留请求 ID 和消息于用户隔离的
  sessionStorage，重发同一消息恢复；清空会话会清除该标识。
- 尚有未完成 checkpoint 时，新请求不能覆盖它；先重试原请求或明确清空会话。
  SSE 必须收到 DONE 才确认完成；半截输出不会被当作成功。
- Java 临时故障从 ToolNode 向外传播，由 MQ 延迟重试；永久业务拒绝记录动作失败。
  邮件执行通过 PostgreSQL 事务级 advisory lock 防止多实例同时改同一个 checkpoint。
- 失败队列/重试队列发布失败时，保留原消息并延迟重新入队；不会直接丢弃。
  首次 MQ 连接失败后台重试，已建立连接由 aio-pika 恢复。
- 聊天执行默认 240 秒超时，可用 CHAT_EXECUTION_TIMEOUT_SECONDS 调整。
  续租失败取消当前执行，助手写入事务再次锁定会话行并检查占用者，阻止旧请求写回。

## 验证边界

单元测试包括真实 LangGraph 子图的提交后响应丢失恢复、完成请求重放、
失败队列发布失败、首次 MQ 连接恢复、消息重复与旧写入者拒绝，以及 Java 内部
时间偏移和认证契约。容器启动与真实 MySQL/PostgreSQL/RabbitMQ 集成仍需运行中的
Docker 引擎；配置校验成功不等于运行验收成功。
