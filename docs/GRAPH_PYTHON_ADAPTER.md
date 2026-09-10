# Python Graph 对接说明（多用户版）

> 本文是 `agentDemo` Java 服务与 Python `graph` 项目的跨服务契约。
> Java 侧已经按此契约实现，Python 侧完成本文适配后再把 `GRAPH_ENABLED` 切为 `true`。

## 1. 最终职责边界

```text
Frontend
   |
   v
agentDemo / Java
   |- JWT / 用户 / 权限
   |- 邮箱账号配置与监听
   |- UI 业务数据
   |- MySQL（唯一 Java 数据源）
   |
   | HTTP / SSE
   v
Python Graph
   |- RoutingAgent
   |- EmailAgent
   |- ChatAgent
   |- Tool Calling
   |- Agent checkpoint / execution
   |- PostgreSQL（只有 Python 连接）
   |
   `- RabbitMQ（异步 Agent 事件）
```

硬性约束：

1. Java **不要连接 PostgreSQL**，也不要新增第二 `DataSource`、动态数据源或 `@DS`。
2. PostgreSQL 表结构只由 Python Repository 感知。
3. Java 需要读写 AI/Graph 数据时，只调用 Python 内部 API。
4. `user_id` 由 Java 从 JWT 解析后传给 Python，Python 不接受前端直接指定的用户身份。
5. Python 的每一个 Repository 查询都必须带 `user_id`，不能只按 `email_id`、`session_id` 查询。
6. 邮件异步链路由 Python 完成 `PG 入库 -> MQ 发布 -> EmailConsumer -> EmailAgent`。

## 2. Java 已实现的调用契约

Java 配置：

```yaml
app:
  graph:
    enabled: ${GRAPH_ENABLED:false}
    base-url: ${GRAPH_BASE_URL:http://127.0.0.1:8001}
    internal-token: ${GRAPH_INTERNAL_TOKEN:}
    connect-timeout-seconds: ${GRAPH_CONNECT_TIMEOUT_SECONDS:5}
    response-timeout-seconds: ${GRAPH_RESPONSE_TIMEOUT_SECONDS:120}
```

Java 内部调用会携带：

```http
X-Agent-Internal-Token: <GRAPH_INTERNAL_TOKEN>
X-User-Id: 123
```

Python 必须同时校验：

- `X-Agent-Internal-Token` 正确；
- `X-User-Id` 是合法整数；
- Header 中的 user id 与 JSON Body 的 `user_id` 一致。

不要只信 Body。

## 3. 邮件入口：POST /internal/emails/dispatch

Java 邮箱监听器收到新邮件后，会调用：

```http
POST /internal/emails/dispatch
Content-Type: application/json
```

请求示例：

```json
{
  "user_id": 123,
  "email_config_id": 7,
  "provider": "GENERIC_IMAP",
  "external_id": "7:GENERIC_IMAP:12345",
  "message_id": "<example@mail.example.com>",
  "sender": "hr@example.com",
  "sender_name": "HR",
  "receiver": ["user@example.com"],
  "cc": [],
  "subject": "Interview invitation",
  "content": "...",
  "html_content": "...",
  "sent_at": "2026-09-10T09:00:00",
  "received_at": "2026-09-10T09:00:05",
  "account_email": "user@example.com",
  "trigger": "poll",
  "attachment_count": 0,
  "attachments": []
}
```

返回：

```json
{
  "email_id": 1001,
  "status": "RECEIVED",
  "duplicate": false
}
```

重复邮件也返回 2xx：

```json
{
  "email_id": 1001,
  "status": "COMPLETED",
  "duplicate": true
}
```

Java 会把 HTTP 非 2xx、超时等视为投递失败，并撤销本地邮件去重 key，让下一次邮箱拉取可以重新提交。

## 4. Python 邮件请求模型建议

建议新增内部 API 请求模型，不要直接复用 MQ DTO：

```python
from datetime import datetime

from pydantic import BaseModel, Field


class EmailAttachmentIn(BaseModel):
    """Java 邮件附件元数据。

    Attributes:
        file_name: 原始文件名。
        content_type: MIME 类型。
        size: 文件字节数。
        file_path: Java 本机临时路径，仅用于过渡期诊断。
        content_id: 内联资源 Content-ID。
        disposition: attachment / inline。
    """

    file_name: str | None = None
    content_type: str | None = None
    size: int | None = Field(default=None, ge=0)
    file_path: str | None = None
    content_id: str | None = None
    disposition: str | None = None


class EmailDispatchRequest(BaseModel):
    """Java 提交给 Graph 的新邮件请求。"""

    user_id: int
    email_config_id: int | None = None
    provider: str | None = None
    external_id: str | None = None
    message_id: str | None = None
    sender: str
    sender_name: str | None = None
    receiver: list[str] = []
    cc: list[str] = []
    subject: str | None = None
    content: str | None = None
    html_content: str | None = None
    sent_at: datetime | None = None
    received_at: datetime | None = None
    account_email: str | None = None
    trigger: str | None = None
    attachment_count: int = Field(default=0, ge=0)
    attachments: list[EmailAttachmentIn] = []
```

实际代码建议把 list 默认值改成 `Field(default_factory=list)`。

## 5. PostgreSQL：email_message 必须增加 user_id

当前 `graph` 的 `email_message` 已存在，但多用户版必须调整为：

```sql
alter table email_message
    add column user_id bigint;

-- 历史数据按实际情况回填后再执行 NOT NULL。
-- Java 的 user_account 仍在 MySQL，因此这里不建立跨库外键。
alter table email_message
    alter column user_id set not null;

-- 删除旧的 provider + external_id 唯一索引。
drop index if exists uk_email_message_provider_external_id;

create unique index uk_email_message_user_source
    on email_message(user_id, provider, external_id)
    where provider is not null
      and external_id is not null;

create index idx_email_message_user_status
    on email_message(user_id, status);

create index idx_email_message_user_received_at
    on email_message(user_id, received_at desc);
```

建议额外记录 Java 邮箱配置来源：

```sql
alter table email_message
    add column source_config_id bigint;
```

`source_config_id` 只是 Java MySQL `email_config.id` 的外部引用，**不要建立 PostgreSQL FK**。

## 6. 邮件入库必须先幂等，再发 MQ

推荐流程：

```text
POST /internal/emails/dispatch
        |
        v
校验 internal token + user_id
        |
        v
PG UPSERT email_message
        |
        +---- duplicate + 已经处理中/完成 ---> 直接返回 duplicate=true
        |
        v
写 outbox_event
        |
        v
commit
        |
        v
Outbox Publisher -> RabbitMQ
```

不要采用：

```text
INSERT PG
RabbitMQ publish
```

然后假设二者永远同时成功。生产环境推荐 Outbox。

如果当前阶段暂时不做 Outbox，至少保证：

1. PG 入库成功后才发布 MQ；
2. MQ publish 使用 publisher confirm；
3. publish 失败时把邮件保留为 `RECEIVED`，由后台补偿扫描重发；
4. 不能因为 HTTP 重试重复创建邮件。

## 7. MQ 消息增加 user_id

当前 Python MQ DTO 只有 `email_id`。多用户版建议改成：

```python
from pydantic import BaseModel


class MQEmailMessage(BaseModel):
    """邮件 Agent 异步触发消息。"""

    email_id: int
    user_id: int
```

RabbitMQ Body：

```json
{
  "email_id": 1001,
  "user_id": 123
}
```

Exchange / Queue / Routing Key 可以继续沿用当前 `settings.rabbitmq_exchange`、`rabbitmq_queue`、`rabbitmq_routing_key`。

Consumer 不要只调用：

```python
await graph_executor.execute_email(email_id)
```

建议改成：

```python
await graph_executor.execute_email(
    email_id=message.email_id,
    user_id=message.user_id,
)
```

## 8. EmailAgent State 必须贯穿 user_id

你当前 `EmailAgentState` 已经有 `user_id`，继续保留它，不要在 `load_email_node` 后丢失。

初始状态至少：

```python
initial_state: EmailAgentState = {
    "user_id": user_id,
    "email_id": email_id,
}
```

Repository 查询从：

```sql
select *
from email_message
where email_id = %s
```

改为：

```sql
select *
from email_message
where email_id = %s
  and user_id = %s
```

所有 update 同理：

```sql
update email_message
set status = %s,
    updated_at = now()
where email_id = %s
  and user_id = %s
```

即便 email_id 是全局主键，也必须带 user_id，这属于纵深防御。

## 9. Chat：POST /internal/chat/complete

Java 的 Agent 模式启用 Graph 后会调用：

```http
POST /internal/chat/complete
```

请求：

```json
{
  "user_id": 123,
  "session_id": "456",
  "message": "帮我查看今天有哪些日程"
}
```

`session_id` 允许为 null。它当前是 Java MySQL `chat_session.id`，Python 可以直接把：

```text
(user_id, session_id)
```

作为 LangGraph thread / checkpoint namespace。

返回：

```json
{
  "content": "你今天有……",
  "execution_id": "01J..."
}
```

Java 仍负责把最终 user / assistant 消息写入 MySQL `chat_message`，所以 Python **不需要复制一套 UI 聊天历史表**。

Python PG 只保存真正属于 Agent Runtime 的数据，例如：

- LangGraph checkpoint；
- execution；
- tool call；
- memory / agent state；
- email analysis。

## 10. Chat SSE：POST /internal/chat/stream

请求与 `/internal/chat/complete` 相同：

```http
POST /internal/chat/stream
Accept: text/event-stream
```

推荐 Python 返回：

```text
data: 第一段文本

data: 第二段文本

event: done
data: [DONE]

```

Java 会过滤 `[DONE]`，再按现有前端 SSE 协议输出。

FastAPI 示例骨架：

```python
from collections.abc import AsyncIterator

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

router = APIRouter(prefix="/internal/chat")


async def event_stream(request: ChatRequest) -> AsyncIterator[str]:
    """把 Graph 流式结果转换为 SSE。

    Args:
        request: Java 传入的多用户聊天请求。

    Yields:
        SSE data 行。
    """
    async for chunk in graph_executor.stream_chat(
        user_id=request.user_id,
        session_id=request.session_id,
        message=request.message,
    ):
        yield f"data: {chunk}\n\n"
    yield "event: done\ndata: [DONE]\n\n"


@router.post("/stream")
async def stream_chat(request: ChatRequest) -> StreamingResponse:
    """执行流式 Agent 对话。"""
    return StreamingResponse(
        event_stream(request),
        media_type="text/event-stream",
    )
```

## 11. 内部 API 鉴权

建议 Python 增加统一 dependency：

```python
from fastapi import Header, HTTPException, status


async def verify_internal_request(
        x_agent_internal_token: str = Header(alias="X-Agent-Internal-Token"),
        x_user_id: int = Header(alias="X-User-Id"),
) -> int:
    """校验 Java 到 Python 的内部请求。

    Args:
        x_agent_internal_token: Java/Python 共享内部密钥。
        x_user_id: Java 从 JWT 中解析出的用户 ID。

    Returns:
        可信用户 ID。

    Raises:
        HTTPException: 内部密钥不合法时抛出。
    """
    if x_agent_internal_token != settings.internal_token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="invalid internal token",
        )
    return x_user_id
```

随后 API 内再校验：

```python
if trusted_user_id != request.user_id:
    raise HTTPException(status_code=403, detail="user id mismatch")
```

生产环境如果 Java/Python 跨机器，建议再加：

- 内网/VPC；
- TLS；
- mTLS 或网关级身份；
- internal token 定期轮换。

## 12. LangGraph 多用户 checkpoint

不要只使用：

```python
thread_id = session_id
```

至少使用：

```python
thread_id = f"user:{user_id}:session:{session_id}"
```

如果 checkpointer 支持 namespace，可拆为：

```text
namespace = user:{user_id}
thread_id = session:{session_id}
```

这样不同用户即使 session_id 意外碰撞，也不会共享 Agent state。

## 13. Schedule / Memory / Tool 查询规则

`graph` 当前 `schedule` 已经有 `user_id`，所有查询继续强制按 user_id：

```sql
select *
from schedule
where user_id = %s
order by start_time;
```

所有 Agent Tool 都必须从 State/Runtime 读取 user_id，禁止让模型自己生成 user_id，也禁止把 user_id 暴露为 LLM 可自由填写的 tool 参数。

推荐：

```python
async def create_schedule_tool(
        runtime: ToolRuntime,
        title: str,
        start_time: datetime,
        end_time: datetime,
) -> Schedule:
    """为当前 Agent 用户创建日程。"""
    user_id = runtime.state["user_id"]
    return await schedule_service.create(
        user_id=user_id,
        title=title,
        start_time=start_time,
        end_time=end_time,
        source_type="CHAT",
    )
```

## 14. 附件处理

当前 Java 邮件附件先落在 Java 本机目录，payload 中的 `file_path` **不能作为跨容器永久协议**。

第一阶段可以：

- Python 忽略 `file_path`，只处理正文；
- Java 保留现有附件能力作为兼容路径。

下一阶段推荐改为对象存储：

```text
Java 收附件
  -> MinIO/S3
  -> payload 只传 object_key
  -> Python 按 user_id + object_key 读取
```

不要让 Python 根据 Java 传入的任意绝对路径直接读取宿主机文件。

## 15. Python 推荐修改位置

结合当前 `graph` 目录，建议按最小改动实施：

```text
app/
  api/
    internal_email.py        # 新增：POST /internal/emails/dispatch
    internal_chat.py         # 新增：complete / stream
  messages/
    model/
      mq_email.py            # 增加 user_id
      email_message.py       # 增加 user_id / source_config_id
    repository/
      email_message_repository.py  # 所有查询增加 user_id
    service/
      email_message_service.py     # 方法签名透传 user_id
  mq/
    producer.py              # 如当前没有 producer，则新增发布能力
    consumer.py              # 消费 email_id + user_id
  agent/
    executor.py              # execute_email/execute_chat 接收 user_id
    email/
      state/email_state.py   # 保证 user_id 必填
      nodes/load_email_node.py
      nodes/save_analysis_node.py
    routing/
      state/routing_state.py # user_id 全程保留
```

不要为了对接 Java 把 Agent Graph 改成固定 workflow；HTTP/MQ 只是 Agent 的入口和基础设施，RoutingAgent / EmailAgent / ChatAgent 仍保持 Agent 决策职责。

## 16. 上线顺序

建议严格按以下顺序：

1. Java MySQL 执行 `src/main/resources/sql/migration_multi_user_core.sql`。
2. Python PG 给 `email_message` 增加 `user_id` 和多用户唯一索引。
3. Python Repository 全部增加 user_id 条件。
4. Python 实现 `/internal/emails/dispatch`。
5. Python 增加 MQ producer，并让 consumer 消费 `email_id + user_id`。
6. Python 修改 `GraphExecutor.execute_email()` 初始化 user_id。
7. Python 实现 `/internal/chat/complete`。
8. Python 实现 `/internal/chat/stream`。
9. 本地保持 `GRAPH_ENABLED=false` 分别验证 Java 旧链路。
10. 启动 Python Graph，先直接 curl 三个 internal API。
11. 设置同一 `GRAPH_INTERNAL_TOKEN`。
12. Java 设置 `GRAPH_ENABLED=true`。
13. 用两个不同 Java 用户分别创建会话和邮箱，做交叉 ID 越权测试。
14. 确认 Python PG 中每条 AI 数据都有正确 user_id。

## 17. 必测场景

### 邮件

- 同一个用户重复提交同一 external_id，只生成一个 email_message。
- 两个用户即使 external_id 相同，也互不冲突。
- Python 停机时 Java 邮件不会永久丢失，恢复后可以重试。
- MQ 重复投递不会重复执行不可幂等副作用。

### Chat

- User A 的 sessionId 不能被 User B 使用。
- `(user_id=A, session_id=1)` 与 `(user_id=B, session_id=1)` checkpoint 完全隔离。
- SSE 中断后不会串到其他用户流。

### Repository

重点搜索并禁止这种代码：

```python
WHERE email_id = %s
```

必须变成：

```python
WHERE email_id = %s AND user_id = %s
```

类似规则适用于 schedule、memory、execution、tool call、checkpoint metadata。

## 18. agentDemo 当前多用户范围

本轮 Java 已首先把最容易产生水平越权的核心边界多用户化：

- `email_config.user_id`；
- `chat_session.user_id`；
- `chat_message` 通过 chat_session 做归属校验；
- 邮箱监听状态按当前可见 email_config 过滤；
- 用户触发“重载邮箱监听”只影响自己的邮箱；
- Email -> Graph 事件携带可信 user_id；
- Agent Chat -> Graph 从 JWT 获取 user_id。

后续继续多用户化时，建议按优先级逐表处理：

1. schedule / scheduled_task；
2. note / document / knowledge_base；
3. user_interest / search_history；
4. 用户自定义 model / MCP / skill；
5. autonomy / dispatch 数据。

`system_settings`、系统内置 Skill、全局平台配置应继续作为全局表，不要机械地全部加 user_id。

---

完成 Python 适配后，Java 侧最终只需要：

```env
GRAPH_ENABLED=true
GRAPH_BASE_URL=http://graph:8001
GRAPH_INTERNAL_TOKEN=<same-secret-as-python>
```

Java 不需要 PostgreSQL 驱动，不需要第二数据源，也不需要知道 PG 表结构。
