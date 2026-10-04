"""联调用的 OpenAI 兼容桩服务。

只在 `docker compose --profile mock-llm` 下启动，用于在没有真实 LLM Key 的环境里
验证链路本身：/ai 路由、SSE 逐块透传、聊天历史落库、Agent Tool 调用 Java 写日程、
以及邮件事件经 RabbitMQ 进 PostgreSQL 后前端可读。它不产生真实的模型输出。

用法：
    docker compose --profile mock-llm up -d
    # .env 中设置 CHAT_BASE_URL=http://mock-llm:9000/v1 后再起 graph
    docker compose up -d graph

覆盖的三种调用形态：
    1. response_format=json_schema  —— 邮件分析等 with_structured_output 默认模式
    2. tools + function_calling      —— RoutingAgent 的 RoutingDecision
    3. bind_tools 的真实工具         —— 仅在用户明确提到"日程"时调用一次
                                       create_schedule，否则返回纯文本收尾
"""

import asyncio
import json
import os
import time

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, StreamingResponse

app = FastAPI()

REPLY = "这是来自联调桩的固定回复，用于验证 SSE 透传与历史落库。"

# 每个流式分块之间的间隔。默认 0.01 秒，几百毫秒就发完，足够验证逐块到达，
# 但来不及验证「生成中取消」。调大它（MOCK_LLM_CHUNK_DELAY=0.5）即可让一条回复
# 持续十几秒，用于验收取消流式请求后并发保护位是否释放、半截回复是否入库。
CHUNK_DELAY = float(os.getenv("MOCK_LLM_CHUNK_DELAY", "0.01"))

# bind_tools 绑定的真实工具名。它们不是结构化输出 schema，不能一律回 tool_calls。
REAL_TOOL_NAMES = {"create_schedule", "get_current_time", "load_skill"}


def _is_structured_output(tools: list[dict]) -> bool:
    """区分「结构化输出 schema」与「Agent 真实工具」。

    with_structured_output(method="function_calling") 生成的函数名取自 schema 类名
    （RoutingDecision 等），而 bind_tools 绑定的工具有自己的语义名。
    """
    for tool in tools:
        if (tool.get("function") or {}).get("name", "") in REAL_TOOL_NAMES:
            return False
    return True


def _arguments_for(function: dict, last_message: str) -> dict:
    """按 schema 生成一份最小的合法返回值。

    同时兼容两种入参形态：
    - function_calling：{"name":..., "parameters": {...}}
    - json_schema：直接就是 {...}

    桩服务若只返回文本，Agent 侧的结构化解析就会失败（路由节点拿到 None，
    邮件分析抛 ValidationError）。这里对少数已知字段给出有意义的值，
    其余按类型填默认值。
    """
    schema = function.get("parameters") or function
    properties = schema.get("properties") or {}
    # required 为空时填充全部字段：EmailAnalysisResult 的字段都带默认值，
    # 只填 required 会得到一个全空对象，联调时看不出分析结果是否落库。
    required = schema.get("required") or list(properties)

    overrides: dict[str, object] = {
        "agent": "chat_agent",
        "reason": "联调桩：普通对话交给 chat_agent",
        "summary": REPLY,
        "category": "GENERAL",
        "priority": "NORMAL",
        "need_action": False,
        "need_create_schedule": False,
        "need_reply": False,
    }

    args: dict[str, object] = {}
    for name in required:
        if name in overrides:
            args[name] = overrides[name]
            continue
        declared = (properties.get(name) or {}).get("type", "string")
        if declared == "boolean":
            args[name] = False
        elif declared in {"integer", "number"}:
            args[name] = 0
        elif declared == "array":
            args[name] = []
        elif declared == "object":
            args[name] = {}
        else:
            args[name] = last_message
    return args


def _chunk(delta: dict, model: str, finish: str | None = None) -> bytes:
    payload = {
        "id": "chatcmpl-mock",
        "object": "chat.completion.chunk",
        "created": int(time.time()),
        "model": model,
        "choices": [{"index": 0, "delta": delta, "finish_reason": finish}],
    }
    return b"data: " + json.dumps(payload, ensure_ascii=False).encode() + b"\n\n"


def _completion(message: dict, model: str, finish_reason: str) -> dict:
    return {
        "id": "chatcmpl-mock",
        "object": "chat.completion",
        "created": int(time.time()),
        "model": model,
        "choices": [{"index": 0, "message": message, "finish_reason": finish_reason}],
        "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
    }


def _last_user_message(messages: list[dict]) -> str:
    for entry in reversed(messages or []):
        if entry.get("role") == "user":
            content = entry.get("content")
            return content if isinstance(content, str) else json.dumps(content, ensure_ascii=False)
    return ""


def _text_stream(text: str, model: str):
    """逐字发送，确保客户端看到真正的分块而不是一次性聚合。"""

    async def gen():
        for ch in text:
            yield _chunk({"content": ch}, model)
            await asyncio.sleep(CHUNK_DELAY)
        yield _chunk({}, model, finish="stop")
        yield b"data: [DONE]\n\n"

    return gen()


def _tool_call_stream(function: dict, model: str):
    async def gen():
        yield _chunk({"role": "assistant"}, model)
        yield _chunk(
            {
                "tool_calls": [
                    {
                        "index": 0,
                        "id": "call_mock_1",
                        "type": "function",
                        "function": function,
                    }
                ]
            },
            model,
        )
        yield _chunk({}, model, finish="tool_calls")
        yield b"data: [DONE]\n\n"

    return gen()


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok"}


@app.post("/v1/chat/completions")
async def chat_completions(request: Request):
    body = await request.json()
    stream = bool(body.get("stream"))
    model = body.get("model", "mock")
    messages = body.get("messages") or []
    tools = body.get("tools") or []
    last_message = _last_user_message(messages)

    # 1) with_structured_output 不带 method 时 LangChain 默认走 json_schema，
    #    内容必须是一段可直接解析的 JSON。
    response_format = body.get("response_format") or {}
    if response_format.get("type") in {"json_object", "json_schema"}:
        schema = (
            (response_format.get("json_schema") or {}).get("schema")
            or response_format.get("schema")
            or {}
        )
        content = json.dumps(_arguments_for(schema, last_message), ensure_ascii=False)
        if not stream:
            return JSONResponse(_completion({"role": "assistant", "content": content}, model, "stop"))
        return StreamingResponse(_text_stream(content, model), media_type="text/event-stream")

    # 2) bind_tools 的真实工具：只在用户明确提到日程时调用一次。
    #    每次请求都回 tool_calls 会让 chat->tools->chat 无限循环；
    #    不判断是否已执行过工具则会重复创建日程。
    already_used_tools = any(m.get("role") == "tool" for m in messages)
    has_schedule_tool = any(
        (t.get("function") or {}).get("name") == "create_schedule" for t in tools
    )
    if has_schedule_tool and "日程" in last_message and not already_used_tools:
        function = {
            "name": "create_schedule",
            "arguments": json.dumps(
                {
                    "title": "联调验证日程",
                    "start_time": "2026-10-02T10:00:00+08:00",
                    "end_time": "2026-10-02T11:00:00+08:00",
                    "description": "由 mock-llm 桩服务创建，用于验证 Agent -> Java 日程链路",
                    "location": "A 会议室",
                },
                ensure_ascii=False,
            ),
        }
        if not stream:
            return JSONResponse(
                _completion(
                    {
                        "role": "assistant",
                        "content": None,
                        "tool_calls": [
                            {
                                "id": "call_mock_schedule",
                                "type": "function",
                                "function": function,
                            }
                        ],
                    },
                    model,
                    "tool_calls",
                )
            )
        return StreamingResponse(
            _tool_call_stream(function, model), media_type="text/event-stream"
        )

    # 3) RoutingAgent 的 function_calling 结构化输出。
    if tools and _is_structured_output(tools):
        declared = tools[0].get("function") or {}
        function = {
            "name": declared.get("name", "RoutingDecision"),
            "arguments": json.dumps(
                _arguments_for(declared, last_message), ensure_ascii=False
            ),
        }
        if not stream:
            return JSONResponse(
                _completion(
                    {
                        "role": "assistant",
                        "content": None,
                        "tool_calls": [
                            {
                                "id": "call_mock_1",
                                "type": "function",
                                "function": function,
                            }
                        ],
                    },
                    model,
                    "tool_calls",
                )
            )
        return StreamingResponse(
            _tool_call_stream(function, model), media_type="text/event-stream"
        )

    # 4) 普通对话。
    if not stream:
        return JSONResponse(_completion({"role": "assistant", "content": REPLY}, model, "stop"))
    return StreamingResponse(_text_stream(REPLY, model), media_type="text/event-stream")
