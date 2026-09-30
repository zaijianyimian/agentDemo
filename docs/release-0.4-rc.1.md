# 0.4-rc.1 候选版

本版本配套 graph 仓库的 `0.4-rc.1` 标签使用。Java 负责认证、数据与日程 CRUD、消息传输；模型调用和工具决策由 Python 执行。

## 主要修改

- Java 内部日程接口增加签名认证、持久化用户校验和工具调用幂等；修复时区与时间偏移契约。
- 聊天请求保持稳定 turn_id，支持 checkpoint 恢复和完成回复重放；SSE 中断保留重试身份，续租失败取消执行，旧请求不能写回。
- 邮件处理增加 PostgreSQL 执行锁、临时故障重试和失败队列保护；首次 MQ 连接失败可后台恢复。
- 清理废弃 Java AI 接口与前端调用，更新前端及多用户边界。
- 更新 Docker Compose、初始建库顺序、配置模板和构建上下文敏感文件排除规则。

## 升级步骤

1. 备份已有数据库和文件；两个仓库均检出 `0.4-rc.1`，放在同一父目录。
2. 已有 MySQL 按需执行 `src/main/resources/sql/migration_schedule_ai_source.sql`；已有 PostgreSQL 执行配套 graph 的 `migrations/20260930_chat_turn_idempotency.sql`。
3. 从 `.env.example` 准备实际环境变量，再启动新代码。已有数据卷不会自动执行首次初始化脚本。
4. 不要对已有数据库执行会删表的历史 `20260913_add_email_user_scope.sql`。

完整配置与恢复语义见 [修复及迁移说明](20260930-recovery-fixes.md) 和 [多用户迁移手册](multi-user-migration-runbook.md)。

## 验证与候选版限制

- Java：141 个测试，0 失败，2 跳过。
- Python：141 个测试通过，1 跳过；包含真实 LangGraph 子图故障恢复回归。
- 前端类型检查、生产构建与 Compose 配置检查通过。
- Docker 引擎不可用，容器实际启动及真实 MySQL/PostgreSQL/RabbitMQ 集成尚未验收。本版本为预发布候选版，生产升级前需完成迁移演练与启动验收。

发布的源码包不包含本地 `.env`、实际 application.yaml 或依赖目录。graph 是私有配套仓库，需要相应访问权限。
