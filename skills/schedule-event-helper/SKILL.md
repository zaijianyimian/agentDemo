---
name: schedule-event-helper
description: 已废弃。日程 Agent 能力已迁移到 Python Graph 的 schedule-management Skill，本文件仅保留为迁移记录，不再被任何代码加载。
license: MIT
compatibility: 不再适用。日程 Agent 决策由 Python 侧 graph/app/agent/chat/skills/schedule-management/SKILL.md 承担。
metadata:
  author: agent-demo
  version: "2.0"
  status: deprecated
  superseded-by: graph/app/agent/chat/skills/schedule-management/SKILL.md
  tags:
    - 日程
    - 已废弃
    - schedule
---

# 日程助手（已废弃）

## 状态

本 Skill 属于 Java 侧的 Skill Runtime，已随 AI 能力迁移整体下线。
仓库中没有任何代码或配置会加载本文件。

## 为什么不直接删除

保留它是为了记录迁移前后的差异，避免后续误以为日程能力仍由 Java 提供。

## 迁移去向

| 能力 | 迁移前 | 迁移后 |
|------|--------|--------|
| 日程创建（自然语言） | Java `ScheduleCommandService` + Agent Tool | Python `create_schedule` Tool，归属由运行上下文注入 |
| 日程 CRUD | Java `ScheduleCommandService` | Java `ScheduleEventService`（由 `ScheduleController` 暴露） |
| 日程 Agent 决策 | Java Skill Runtime | Python ChatAgent + `schedule-management` Skill |

## 当前真实入口

- **Agent 侧日程创建**：`graph/app/tools/schedule_tool.py` 的 `create_schedule`，
  规格见 `graph/openspec/specs/chat-schedule-creation/spec.md`。
- **Java 侧确定性 CRUD**：`com.example.demo.schedule.application.ScheduleEventService`，
  HTTP 入口 `ScheduleController`（`/api/schedule/**`）。

> 历史版本的本文件曾声明「所有操作走 `ScheduleCommandService` 唯一入口」。
> 该类已确认零调用并被删除，**不要再按此描述编写代码或提示词**。
