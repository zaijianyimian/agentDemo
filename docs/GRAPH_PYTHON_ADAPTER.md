# Java 与 Python Graph 边界

Java 不再承载聊天网关、聊天历史、LLM 调用、Agent 决策或 AI 专属执行编排。

## 浏览器聊天

浏览器请求同域 `/api/chat/*`，反向代理将该路径原样转发至 Python Graph。
Python 调用 Java 的 `POST /api/auth/introspect` 校验 Bearer access token，
再以返回的 `user_id` 隔离会话、消息和 checkpoint。Java 的该端点只负责认证。

## 邮件事件

Java 保留邮箱接入与新邮件 RabbitMQ 投递：`GraphEmailDispatchListener`、
`GraphEmailRabbitConfiguration` 和 `GraphGatewayProperties` 的邮件交换机、队列、
routing key 配置。Python 消费邮件事件并执行 Agent 分析。

## 部署

仅将 `/api/chat/*` 暴露给浏览器；Python 的 `/internal/*` 和端口 8001 不对公网开放。
PostgreSQL 中的 UI 会话、消息表由 `graph/migrations/20260929_add_chat_session_message.sql`
建立，LangGraph checkpointer 首次启动执行 `setup()`。配置和 Nginx 示例见
`D:\mine\graph\docs\ARCHITECTURE.md`。

旧 MySQL 聊天数据按产品决定舍弃。先完成新链路验收，再运行
`src/main/resources/sql/preflight_legacy_chat_tables.sql`；审阅结果后在维护窗口运行
`src/main/resources/sql/drop_legacy_chat_tables.sql`。应用启动不会自动删除表。

新的 Java MySQL 初始化与增量脚本不再创建旧 AI 表。已有数据库中的其他历史 AI
表会保留原数据，迁移脚本不再修改这些表；后续数据处置需单独确认。
