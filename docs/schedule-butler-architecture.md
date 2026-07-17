# 日程管家与本地执行中心架构备忘

本文记录日程管家、本地定时任务中心、Agent 执行适配层的设计边界，避免后续实现时范围发散。

## 目标

项目要做的是一个单体版的个人日程管家：

- 根据日程内容、时间、来源和冲突情况判断重要程度。
- 决定是否通知用户。
- 对低风险事项可以自动执行。
- 对高风险事项生成执行建议，等待用户确认。
- 定时任务能力参考 XXL-JOB 的任务模型，但不引入分布式调度和复杂 Admin。
- 前端第一版只需要能查看和删除日程，不需要引入完整任务中心 UI。

## 第一版范围

第一版应保持克制：

- 日程列表
- 日程详情
- 删除日程
- 日程重要性标签
- 通知状态
- 执行状态摘要
- 本地任务调度
- 执行日志
- 超时控制
- 失败重试
- 阻塞策略
- Agent 执行器统一接口
- Codex / Claude Code / OpenClaw 通过适配器接入

第一版不做：

- 分布式调度
- 多节点执行器
- XXL-JOB Admin 式复杂页面
- 复杂任务编排 UI
- 自动执行高风险操作
- 自动提交代码或推送远程仓库
- 自动执行删除、sudo、docker、git push 等高风险命令

## 总体架构

```text
┌────────────┐
│    用户    │
└─────┬──────┘
      │
      ▼
┌────────────────────────────┐
│ 前端：日程查看 / 详情 / 删除 │
└─────┬──────────────────────┘
      │
      ▼
┌────────────────────────────┐
│ ScheduleController          │
└─────┬──────────────────────┘
      │
      ▼
┌────────────────────────────┐
│ ScheduleService             │
│ - 日程 CRUD                 │
│ - 状态更新                  │
└─────┬──────────────────────┘
      │
      ▼
┌────────────────────────────┐
│ schedule_event              │
└────────────────────────────┘

邮件 / 手动录入 / 未来更多来源
      │
      ▼
┌────────────────────────────┐
│ 日程提取服务                │
│ - 规则解析                  │
│ - AI 提取                   │
└─────┬──────────────────────┘
      │
      ▼
┌────────────────────────────┐
│ ScheduleService             │
└────────────────────────────┘

┌────────────────────────────┐
│ Local Job Center            │
│ - Cron / 延迟触发           │
│ - 手动触发                  │
│ - 超时 / 重试 / 阻塞策略    │
│ - 执行日志                  │
└─────┬──────────────────────┘
      │
      ▼
┌────────────────────────────┐
│ ScheduleButlerService       │
│ - 判断重要性                │
│ - 判断是否通知              │
│ - 生成执行计划              │
└─────┬──────────────────────┘
      │
      ▼
┌────────────────────────────┐
│ AgentExecutionService       │
│ - 接收统一执行请求          │
│ - 调用执行器注册表          │
│ - 记录执行结果              │
└─────┬──────────────────────┘
      │
      ▼
┌────────────────────────────┐
│ AgentExecutorRegistry       │
└─────┬────────────┬─────────┘
      │            │
      ▼            ▼
┌───────────┐  ┌──────────────┐
│ Codex     │  │ Claude Code  │
│ Adapter   │  │ Adapter      │
└─────┬─────┘  └──────┬───────┘
      │               │
      ▼               ▼
┌───────────┐  ┌──────────────┐
│ codex CLI │  │ claude CLI   │
└───────────┘  └──────────────┘

      ┌────────────────┐
      │ OpenClaw       │
      │ Adapter        │
      └───────┬────────┘
              ▼
      ┌────────────────┐
      │ openclaw CLI   │
      └────────────────┘
```

## 分层职责

### 日程层

日程层只负责日程本身：

- 新增日程
- 查看日程
- 删除日程
- 更新状态
- 保存重要性和执行状态字段

前端第一版只依赖这一层。

### 管家层

管家层回答“该不该做”的问题：

- 日程是否重要？
- 是否紧急？
- 是否与已有日程冲突？
- 是否需要通知用户？
- 是否可以自动执行？
- 是否必须等待确认？

建议后续使用策略模式封装判断逻辑：

```text
ScheduleButlerService
  ├─ ImportanceStrategy
  ├─ NotificationStrategy
  └─ ExecutionPlanningStrategy
```

### 本地任务中心

本地任务中心回答“什么时候做、失败怎么办”的问题。

它参考 XXL-JOB 的核心模型，但只保留单体项目需要的部分：

- 任务定义
- 任务触发
- 执行日志
- 超时控制
- 重试
- 阻塞策略
- 失败通知
- 最近执行状态

建议增强 `scheduled_task`：

```text
timeout_seconds
retry_count
retry_interval_seconds
misfire_strategy
block_strategy
notify_on_failure
notify_on_success
last_execute_status
```

建议新增 `scheduled_task_log`：

```text
id
task_id
schedule_id
executor_type
trigger_type
start_time
end_time
duration_ms
status
retry_index
handler_name
input_snapshot
result_summary
raw_output
error_message
create_time
```

第一版阻塞策略只做：

```text
SERIAL   同一个任务串行执行
DISCARD  上一次未结束时丢弃本次触发
```

第一版误触发策略只做：

```text
DO_NOTHING  错过就跳过
FIRE_ONCE   错过后补偿执行一次
```

暂不做 `COVER`，因为强行中断 Java 任务和外部 Agent 子进程都比较复杂。

### Agent 执行层

Agent 执行层回答“让谁去做”的问题。

Claude Code / OpenClaw / Codex 的调用方式不同，但对业务层应该表现为同一种能力。因此这里使用适配器模式。

统一接口：

```java
public interface AgentExecutor {
    AgentType type();

    AgentExecutionResult execute(AgentExecutionRequest request);
}
```

统一请求：

```java
class AgentExecutionRequest {
    Long scheduleId;
    Long taskId;
    String goal;
    String workspacePath;
    Map<String, Object> context;
    Duration timeout;
    boolean requireUserConfirmation;
    AgentPermission permission;
}
```

统一结果：

```java
class AgentExecutionResult {
    boolean success;
    String summary;
    String output;
    String errorMessage;
    Map<String, Object> artifacts;
}
```

适配器：

```text
AgentExecutor
  ├─ CodexAgentExecutor
  ├─ ClaudeCodeAgentExecutor
  ├─ OpenClawAgentExecutor
  └─ InternalSkillAgentExecutor
```

业务层只依赖 `AgentExecutor`，不直接依赖具体 CLI 或 API。

## 策略模式与适配器模式的边界

这里需要同时使用两种模式，但职责不同。

策略模式用于“判断逻辑可替换”：

```text
重要性判断策略
  - RuleBasedImportanceStrategy
  - AiImportanceStrategy
  - HybridImportanceStrategy

通知策略
  - AlwaysNotifyStrategy
  - ImportantOnlyNotifyStrategy
  - QuietHoursNotificationStrategy

执行计划策略
  - ConservativeExecutionPlanningStrategy
  - AutoExecutionPlanningStrategy
```

适配器模式用于“外部工具接入方式不同”：

```text
统一接口：AgentExecutor

CodexAgentExecutor
ClaudeCodeAgentExecutor
OpenClawAgentExecutor
InternalSkillAgentExecutor
```

一句话区分：

```text
策略模式回答：怎么判断？
适配器模式回答：怎么接入？
```

## Agent 接入方式

第一阶段建议采用 CLI 进程适配器：

```text
AgentExecutionService
  -> AgentExecutorRegistry
      -> CodexAgentExecutor
          -> ProcessBuilder 启动 codex
      -> ClaudeCodeAgentExecutor
          -> ProcessBuilder 启动 claude
      -> OpenClawAgentExecutor
          -> ProcessBuilder 启动 openclaw
```

每个适配器负责：

- 把统一请求转换成对应工具的命令参数或输入。
- 设置工作目录。
- 设置环境变量。
- 注入 prompt。
- 捕获 stdout / stderr。
- 控制超时。
- 终止超时进程。
- 把外部输出转换成统一结果。
- 返回执行摘要和错误信息。

## 执行器路由

不要让用户每次手动选择执行器。可以先提供默认路由：

```text
代码修改类       -> Codex / Claude Code
代码解释类       -> Codex / Claude Code / 内部 ChatModel
网页自动化类     -> OpenClaw
文本总结类       -> 内部 ChatModel
提醒类           -> InternalReminderExecutor
邮件发送类       -> InternalEmailExecutor
```

后续可以由 `ExecutionPlanningStrategy` 根据任务类型、风险等级、可用执行器和用户偏好选择。

## 权限边界

Agent 执行层是风险最高的部分。必须先定义权限边界。

默认允许：

- 读取指定 workspace。
- 修改指定目录内文件。
- 执行白名单命令。
- 生成总结、计划、补丁或草稿。

默认禁止或需要确认：

- 删除文件。
- 执行 `rm`。
- 执行 `sudo`。
- 执行 `docker`。
- 执行 `git push`。
- 访问系统目录。
- 读取敏感配置。
- 任意网络访问。
- 自动提交代码。

建议执行请求携带权限配置：

```text
workspacePath
allowedCommands
allowFileWrite
allowNetwork
requireApproval
timeoutSeconds
```

## 用户确认机制

日程管家不要默认自动执行所有任务。建议分级：

```text
LOW      只记录，不通知
MEDIUM   通知用户，但不执行
HIGH     通知用户，并生成执行建议
AUTO     可自动执行，但只限低风险任务
CONFIRM  必须等待用户确认
```

例子：

```text
“明早 9 点开会”             -> 通知
“帮我整理今天邮件并总结”     -> 可自动执行
“修改项目代码并提交”         -> 必须确认
“删除过期文件”               -> 必须确认
```

## 幂等性

失败重试时不能重复造成副作用。

需要为每次执行记录：

```text
executionId
scheduleId
taskId
dedupeKey
status
retryIndex
```

高副作用任务重试前必须检查是否已经成功执行。

## 日志与审计

所有执行都必须落日志，尤其是 Agent 执行。

日志至少记录：

- 任务 ID
- 日程 ID
- 执行器类型
- 触发方式
- 开始时间
- 结束时间
- 耗时
- 状态
- 输入摘要
- 输出摘要
- 错误信息
- 重试次数

建议日志分两层：

```text
result_summary  可展示给前端
raw_output      后端调试使用，限制访问
```

## 敏感信息脱敏

Agent 输出和执行日志里可能包含：

- 邮件内容
- API Key
- 数据库密码
- 用户隐私
- 本地路径
- Token

入库前应做基础脱敏。前端默认只展示摘要，不直接展示完整 raw output。

## Agent 可用性检测

执行前需要检测执行器是否可用：

```text
codex 是否安装
claude 是否安装
openclaw 是否安装
版本是否满足
当前账号是否登录
命令是否可以启动
```

建议提供：

```text
AgentCapabilityService
```

返回：

```text
CODEX: available / unavailable / reason
CLAUDE_CODE: available / unavailable / reason
OPENCLAW: available / unavailable / reason
```

## Prompt 模板

不要在适配器里拼业务 prompt。建议由独立组件生成统一 prompt：

```text
AgentTaskPromptBuilder
```

输入：

- 日程信息
- 用户意图
- 允许动作
- 禁止动作
- 输出格式
- 上下文文件

输出：

- 适合交给 Agent 的任务 prompt

这样适配器只负责调用外部工具，不负责理解业务。

## 推荐实现顺序

建议拆成几个 OpenSpec change：

```text
1. add-local-job-center
   本地任务调度、执行日志、重试、超时、阻塞策略。

2. add-agent-executor-adapters
   AgentExecutor 抽象、执行器注册表、Codex / Claude Code / OpenClaw 适配器。

3. add-schedule-butler
   日程重要性判断、通知决策、执行计划。

4. simplify-schedule-ui
   前端收敛为日程查看、详情、删除。
```

优先级：

```text
Local Job Center
  -> Agent Executor Adapters
      -> Schedule Butler
          -> Minimal Schedule UI
```

原因：

- 本地任务中心是执行和审计的地基。
- Agent 适配器需要依赖统一日志、超时和重试。
- 日程管家依赖执行层才能真正执行计划。
- 前端可以最后收敛，不影响后端核心设计。

## 当前结论

最终边界如下：

```text
日程层：负责日程数据。
管家层：负责判断是否重要、是否通知、是否执行。
任务中心：负责什么时候执行、失败怎么办、如何记录日志。
执行层：负责用哪个 Agent 执行。
适配器层：负责接入 Codex / Claude Code / OpenClaw。
前端：第一版只负责查看和删除日程。
```

