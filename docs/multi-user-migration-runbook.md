# Java 多用户迁移与发布 Runbook

本 runbook 用于将已确认属于单一历史用户的 Java/MySQL 业务数据切换到强 owner 多用户版本。数据库 DDL 不可视为整体事务；任一预检失败都必须保持维护模式。

## 1. 发布前准备

- 明确维护窗口、当值负责人、数据库快照位置和文件备份位置。
- 记录待接管历史数据的 `LEGACY_OWNER_USER_ID`；该账号必须已存在且 `enabled=1`。
- 使用一致性方式备份 MySQL 和共享业务目录。单独备份数据库或文件不足以支持安全回滚。
- 在隔离环境执行 `scripts/verify-multi-user-release.sh`，要求后端编译/测试、前端类型检查/构建、结构搜索和 MySQL 双跑全部通过。

## 2. 进入维护模式

按顺序暂停新业务写入：

1. 关闭新 HTTP/SSE 业务连接，等待进行中请求结束。
2. 暂停邮箱 listener 与重连定时器。
3. 暂停 scheduled-task 扫描、手动重试和 dispatch 任务生产。
4. 暂停 RabbitMQ execution request consumer 和 result outbox relay。
5. 确认无运行中的 Java 用户业务写入后，创建 MySQL 快照和文件树快照。

## 3. 数据库预检与迁移

先在目标库确认用户，不得用“第一个用户”或最近登录用户代替：

```sql
SELECT id, username, enabled, token_version
FROM user_account
WHERE id = <LEGACY_OWNER_USER_ID>;
```

在同一 MySQL 连接中设置 owner 并执行迁移：

```sql
SET @LEGACY_OWNER_USER_ID := <LEGACY_OWNER_USER_ID>;
SOURCE /absolute/path/to/src/main/resources/sql/migrant.sql;
```

验收要求：

- `migration_journal` 的 `01_expand`、`02_backfill_ownership`、`03_contract_checks`、`04_contract_constraints` 均为 `COMPLETED`，owner 与本次输入一致。
- 脚本末尾的所有 `rows_without_user` 均为 0。
- 不存在孤儿、跨 owner 父子关系、用户内重复 email/request/push config。
- 再执行一次相同脚本，数据行数和结构 checksum 不变，journal `attempt_count` 增加。

## 4. 历史文件迁移

- 设置可验证的非符号链接 `LEGACY_PATH_BASE`，扫描文档、邮件附件、日程和笔记历史路径。
- 使用 `LegacyOwnedFileMigrationService` 将文件复制到 `data/users/{owner}/<category>/legacy/`，校验 SHA-256 后更新 `storage_key`，最后删除旧文件。
- 拒绝绝对路径、`..`、符号链接和越出旧根目录的路径。无法与数据库记录匹配的文件必须阻止切换。
- 中断后保持维护模式，根据 `file_migration_journal` 重跑；不允许旧共享路径和新 owner 路径同时对普通 API 开放。

## 5. 部署与 A/B 验收

1. 部署新 Java/Vue 版本，保持 producer 暂停。
2. 使用原用户 A 和新测试用户 B 验证邮箱、listener state、session/message、文档、笔记、日程、任务、日志、dispatch 和 PushConfig。
3. 无认证请求必须返回 401；A 使用 B 的已知 ID 必须返回 404，且无文件、网络、JDBC 或进程副作用。
4. A/B 同时建立 SSE，事件只到 owner；令牌失效后旧订阅不再收信。
5. 验证 RabbitMQ 重复/伪造消息、定时线程复用和 outbox 断线恢复均保留 owner。
6. 普通用户对备份恢复和系统级写入获得 403。没有隔离 Worker 时 dispatch 仅返回 `WORKER_UNAVAILABLE`，不得创建进程或 worktree。

## 6. 恢复顺序

验收全部通过后，依次恢复 result outbox relay、RabbitMQ consumer、scheduler、邮箱 listener，最后开放 HTTP/SSE 流量。每步观察 401/403/404、owner 冲突、outbox 堆积和 listener 失败率。

## 7. 安全回滚

- Contract 切换前：停止所有生产者，恢复同一时点的 MySQL 与文件快照。
- 已开始多用户写入后：只能回退到仍强制 owner 的兼容版本，或在维护窗口恢复完整快照。
- 严禁重新部署无范围 Java 版本，严禁通过选择默认用户、恢复本地 executor 或共享 worktree 来“快速修复”。
