# AI Agent Demo API 文档

> 更新时间：2026-04-05
>
> 本文档按当前仓库源码整理，优先以 `controller`、`service`、前端 API 封装与安全配置为准。
>
> 当前状态：
>
> - 前端 `npm run build` 通过。
> - 后端 `.\gradlew.bat test` 无法通过，原因是 [`SystemSettingsController.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/controller/SystemSettingsController.java) 依赖的 `DataArchiveService` 已缺失。
> - 因此，下文列出的接口是“当前源码声明的接口面”，不是“已可运行验证通过”的接口面。

## 1. 基本信息

- Base URL：`http://localhost:8000/api`
- 编码：`UTF-8`
- 默认数据格式：`application/json`
- 流式接口：`text/event-stream`
- 鉴权方式：Bearer JWT

### 1.1 统一响应

项目中大量接口使用：

```json
{
  "success": true,
  "message": "success",
  "data": {},
  "total": 0
}
```

对应实现见 [`ApiResponse.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/dto/ApiResponse.java)。

### 1.2 不统一的返回风格

当前代码并没有做到所有接口都使用 `ApiResponse<T>`。下面这些模块要特别注意：

- 聊天接口大量直接返回 `String`、`ChatResponse` 或 `SSE`。
- `EmbeddingController` 与 `MemoryController` 直接返回原始对象。
- 邮件、MCP 工具、技能、文件上传等模块大量使用 `ResponseEntity`、`List`、`Map` 或普通字符串。
- 前端旧版 `api.ts` 中部分类型标注仍按 `ApiResponse<T>` 编写，联调时应以控制器真实签名为准。

### 1.3 全局错误响应

全局异常处理位于 [`GlobalExceptionHandler.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/exception/GlobalExceptionHandler.java)。

常见错误形态：

```json
{
  "success": false,
  "error": "INVALID_PARAM",
  "message": "参数错误说明"
}
```

可能出现的错误类型包括：

- `VALIDATION_ERROR`
- `INVALID_PARAM`
- `NETWORK_ERROR`
- `TIMEOUT_ERROR`
- `API_KEY_ERROR`
- `RATE_LIMIT_ERROR`
- `MODEL_ERROR`
- `INTERNAL_ERROR`
- `UNKNOWN_ERROR`

## 2. 当前代码差异

这些不是“接口设计说明”，而是当前代码需要特别注意的现实状态：

- 后端目前无法编译，原因是 `DataArchiveService` 文件缺失，但设置控制器仍保留数据导入导出接口。
- [`SecurityConfig.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/config/SecurityConfig.java) 仍放行 `/api/auth/captcha/puzzle` 与 `/api/auth/captcha/puzzle/verify`，但当前 [`AuthController.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/controller/AuthController.java) 中已经没有这两个接口。
- 带会话聊天当前始终使用“当前启用模型”；源码没有真正提供按请求切换模型的能力。
- 文件与知识库上传虽然允许 `pdf/doc/docx`，但当前真正提取文本内容的只有 `txt` 与 `md`。
- `Dockerfile` 只构建后端，不覆盖前端静态资源部署。

## 3. 鉴权规则

### 3.1 公开接口

根据 [`SecurityConfig.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/config/SecurityConfig.java)，以下接口无需 Bearer Token：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/auth/register` | 注册账号并发送注册验证码 |
| `POST` | `/api/auth/login/password` | 用户名/邮箱 + 密码登录 |
| `POST` | `/api/auth/login/email/send-code` | 发送邮箱验证码 |
| `POST` | `/api/auth/login/email` | 邮箱验证码登录 |
| `POST` | `/api/auth/token/refresh` | 刷新访问令牌 |
| `POST` | `/api/auth/face/verify-login` | 使用 `preAuthToken` 完成人脸二验 |
| `GET` | `/api/auth/oauth/github/authorize` | 获取 GitHub 授权链接 |
| `POST` | `/api/auth/oauth/github/exchange` | GitHub 回调换 token |
| `GET` | `/api/settings/proxy` | 获取代理配置 |

### 3.2 代码中已不存在但仍被放行的路径

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/auth/captcha/puzzle` | 安全配置中存在白名单，但控制器已无实现 |
| `POST` | `/api/auth/captcha/puzzle/verify` | 同上 |

### 3.3 登录二阶段流程

`/api/auth/login/password`、`/api/auth/login/email` 以及 GitHub 登录交换接口都有可能返回两类结果：

- 直接登录成功：返回 `accessToken`、`refreshToken`、`user`
- 触发人脸二验：返回 `requiresSecondFactor=true`、`preAuthToken`、`preAuthExpiresIn`

如果账号开启了人脸二次验证，需要继续调用：

```http
POST /api/auth/face/verify-login
```

## 4. 流式接口说明

### 4.1 SSE 接口清单

| 路径 | 说明 | 返回形式 |
| --- | --- | --- |
| `/api/chat/stream` | 普通流式聊天 | `ServerSentEvent<String>` |
| `/api/chat/stream/json` | JSON 包装流式聊天 | `ServerSentEvent<String>`，最后有 `event: complete` |
| `/api/chat/stream/session` | 带会话记忆流式聊天 | `ServerSentEvent<String>` |
| `/api/chat/stream/session/json` | 带会话记忆的 JSON 流 | `ServerSentEvent<String>`，最后有 `event: complete` |
| `/api/chat/stream/test` | 流式调试接口 | `ServerSentEvent<String>` |
| `/api/search/summary` | 先发搜索结果，再发总结 chunk | `text/event-stream`，最后发送 `[DONE]` |
| `/api/search/chat/stream` | 搜索增强流式聊天 | `text/event-stream`，数据块是 JSON 字符串 |
| `/api/mcp/agent/chat/stream` | MCP Agent 流式对话 | `Flux<String>` |
| `/api/mcp/agent/chat/stream/{sessionId}` | MCP Agent 带记忆流式对话 | `Flux<String>` |
| `/api/schedule/stream` | 日程实时推送 | `ServerSentEvent<String>`，含 `connected` / `ping` / 业务事件 |

### 4.2 日程流事件

`/api/schedule/stream` 的事件名可能包括：

- `connected`
- `ping`
- `created`
- `updated`
- `deleted`
- `completed`
- `cancelled`
- `created_from_email`

## 5. 常见请求体参考

### 5.1 认证

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "StrongPass123",
  "displayName": "Alice"
}
```

```json
{
  "username": "alice",
  "password": "StrongPass123"
}
```

```json
{
  "email": "alice@example.com",
  "code": "123456"
}
```

```json
{
  "preAuthToken": "pre-auth-token",
  "imageBase64": "data:image/png;base64,..."
}
```

### 5.2 聊天动作

```json
{
  "sessionId": 1,
  "content": "把这段对话整理成任务",
  "role": "user",
  "titleHint": "待办事项"
}
```

### 5.3 日程

```json
{
  "title": "项目评审",
  "description": "与团队确认本周进度",
  "eventTime": "2026-04-06T14:30:00",
  "location": "会议室 A",
  "reminderEnabled": true
}
```

### 5.4 邮箱配置

密码模式最小示例：

```json
{
  "email": "user@example.com",
  "password": "app-password",
  "authType": "password",
  "host": "imap.example.com",
  "protocol": "imap",
  "port": 993,
  "sslEnabled": true,
  "folder": "INBOX",
  "pollInterval": 30,
  "enabled": false,
  "remark": "主邮箱"
}
```

OAuth2 模式时可额外传：

- `oauthClientId`
- `oauthClientSecret`
- `oauthRefreshToken`
- `oauthAccessToken`
- `oauthTokenEndpoint`
- `oauthScope`

### 5.5 MCP 工具

```json
{
  "name": "weather_query",
  "displayName": "天气查询",
  "description": "查询指定城市天气",
  "toolType": "HTTP_API",
  "config": "{\"url\":\"https://api.example.com/weather\",\"method\":\"GET\",\"timeout\":30}",
  "inputSchema": "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}",
  "enabled": false,
  "remark": "示例工具"
}
```

### 5.6 技能

```json
{
  "code": "weather_skill",
  "name": "天气技能",
  "description": "结合天气工具返回天气信息",
  "category": "data",
  "icon": "cloud",
  "enabled": true,
  "isBuiltin": false
}
```

### 5.7 知识库

```json
{
  "name": "产品知识库",
  "description": "面向单用户场景的知识集合",
  "chunkSize": 500,
  "chunkOverlap": 50
}
```

### 5.8 定时任务

```json
{
  "name": "晚间日报",
  "description": "每日 20:00 生成日报",
  "taskType": "CHAT",
  "cronExpression": "0 0 20 * * ?",
  "params": "请生成今日工作总结"
}
```

### 5.9 模型配置

```json
{
  "name": "DeepSeek",
  "provider": "deepseek",
  "baseUrl": "https://api.deepseek.com/v1",
  "modelName": "deepseek-chat",
  "apiKey": "sk-xxx"
}
```

### 5.10 代码生成

```json
{
  "type": "entity",
  "name": "UserAccount",
  "fields": ["id:Long", "username:String", "email:String"],
  "packageName": "com.example.demo.entity",
  "description": "用户实体"
}
```

## 6. 接口清单

### 6.1 Auth

| 方法 | 路径 | 返回 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | `ApiResponse<EmailCodeSendResponse>` | 注册账号，落库后发送注册验证码 |
| `POST` | `/auth/login/password` | `ApiResponse<AuthTokenResponse>` | 密码登录，可能返回二阶段挑战 |
| `POST` | `/auth/login/email/send-code` | `ApiResponse<EmailCodeSendResponse>` | 发送邮箱登录验证码 |
| `POST` | `/auth/login/email` | `ApiResponse<AuthTokenResponse>` | 邮箱验证码登录，登录时会顺带确认邮箱已验证 |
| `POST` | `/auth/token/refresh` | `ApiResponse<AuthTokenResponse>` | 刷新 access token / refresh token |
| `GET` | `/auth/me` | `ApiResponse<AuthUserProfile>` | 获取当前用户信息 |
| `PUT` | `/auth/password` | `ApiResponse<Void>` | 修改密码，当前密码校验成功后会使旧 token 失效 |
| `POST` | `/auth/logout` | `ApiResponse<Void>` | 登出并递增 token version |
| `GET` | `/auth/oauth/github/authorize` | `ApiResponse<GithubAuthorizeResponse>` | 获取 GitHub 授权地址，可带 `redirect` 参数 |
| `POST` | `/auth/oauth/github/exchange` | `ApiResponse<GithubExchangeResponse>` | 用 `code` 与 `state` 换取本地登录态 |
| `GET` | `/auth/face/status` | `ApiResponse<FaceStatusResponse>` | 查询当前账号人脸状态 |
| `POST` | `/auth/face/register` | `ApiResponse<FaceStatusResponse>` | 绑定人脸向量 |
| `PUT` | `/auth/face/required` | `ApiResponse<FaceStatusResponse>` | 开启或关闭登录人脸二验 |
| `POST` | `/auth/face/verify-login` | `ApiResponse<AuthTokenResponse>` | 用 `preAuthToken` + 人脸图像完成登录 |

### 6.2 Chat / Chat History / Chat Action / Embedding / Memory

| 方法 | 路径 | 返回 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/chat/complete` | `String` | 普通聊天，参数 `message` |
| `GET` | `/chat/stream` | `text/event-stream` | 普通流式聊天，参数 `message` |
| `GET` | `/chat/stream/json` | `text/event-stream` | JSON 包装流式聊天，最后发送完整分析结果 |
| `GET` | `/chat/structured` | `ChatResponse` | 返回 `content`、`importance`、`tags`、`summary` 等 |
| `GET` | `/chat/complete/session` | `String` | 带会话记忆聊天，参数 `message`、`sessionId` |
| `GET` | `/chat/stream/session` | `text/event-stream` | 带会话记忆流式聊天 |
| `GET` | `/chat/stream/session/json` | `text/event-stream` | 带会话记忆的 JSON 流 |
| `GET` | `/chat/stream/test` | `text/event-stream` | 流式调试接口 |
| `POST` | `/chat/action/note` | `ApiResponse<ChatActionResult>` | 从对话创建笔记 |
| `POST` | `/chat/action/task` | `ApiResponse<ChatActionResult>` | 从对话创建任务 |
| `POST` | `/chat/action/schedule` | `ApiResponse<ChatActionResult>` | 从对话创建日程 |
| `POST` | `/chat/action/memory` | `ApiResponse<ChatActionResult>` | 从对话写入记忆 |
| `POST` | `/chat/history/session` | `ApiResponse<ChatSession>` | 创建聊天会话，`title` 为可选 query 参数 |
| `GET` | `/chat/history/sessions` | `ApiResponse<List<ChatSession>>` | 会话列表 |
| `GET` | `/chat/history/session/{id}` | `ApiResponse<ChatSession>` | 会话详情 |
| `PUT` | `/chat/history/session/{id}/title` | `ApiResponse<ChatSession>` | 更新标题，query 参数 `title` |
| `DELETE` | `/chat/history/session/{id}` | `ApiResponse<Void>` | 删除会话及其消息 |
| `GET` | `/chat/history/session/{sessionId}/messages` | `ApiResponse<List<ChatMessageEntity>>` | 查询会话消息 |
| `POST` | `/chat/history/session/{sessionId}/message` | `ApiResponse<ChatMessageEntity>` | 直接追加消息，query 参数 `role`、`content`、`model` |
| `DELETE` | `/chat/history/session/{sessionId}/messages` | `ApiResponse<Void>` | 清空会话消息 |
| `GET` | `/embedding` | `float[]` | 文本向量化，参数 `text` |
| `GET` | `/embedding/full` | `Response<Embedding>` | 返回 LangChain4j 完整 embedding 响应 |
| `GET` | `/embedding/test` | `ApiResponse<Map>` | 测试 embedding 服务是否可用 |
| `GET` | `/analyze` | `ContentAnalysis` | 内容分析，参数 `content` |
| `POST` | `/memory/extract-store` | `MemoryRecord` | 请求体需包含 `sessionId`、`recentMessages` |
| `GET` | `/memory/search` | `List<Map<String,Object>>` | 参数 `query`、`topK`、`sessionId`、`category` |

### 6.3 Search

| 方法 | 路径 | 返回 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/search` | `SearchResult.ListResult` | 普通搜索，参数 `query` |
| `GET` | `/search/summary` | `text/event-stream` | 先返回搜索结果对象，再逐块返回总结，最后 `[DONE]` |
| `GET` | `/search/chat` | `ChatResponse` | 搜索增强问答，参数 `message` |
| `GET` | `/search/chat/stream` | `text/event-stream` | 搜索增强流式问答 |
| `GET` | `/search/history` | `ApiResponse<List<SearchHistory>>` | 获取搜索历史，参数 `limit`，默认 50 |
| `GET` | `/search/hot` | `ApiResponse<List<Map>>` | 热门搜索，参数 `limit`，默认 10 |
| `GET` | `/search/statistics` | `ApiResponse<Map>` | 搜索统计 |
| `DELETE` | `/search/history` | `ApiResponse<Void>` | 清空搜索历史 |
| `DELETE` | `/search/history/{id}` | `ApiResponse<Void>` | 删除单条历史 |
| `GET` | `/search/interests` | `ApiResponse<List<UserInterest>>` | 获取兴趣标签 |
| `GET` | `/search/interests/top` | `ApiResponse<List<UserInterest>>` | 获取主要兴趣，参数 `limit` |
| `GET` | `/search/interests/report` | `ApiResponse<Map>` | 兴趣画像统计报告 |
| `DELETE` | `/search/interests` | `ApiResponse<Void>` | 清空兴趣画像 |
| `DELETE` | `/search/interests/{id}` | `ApiResponse<Void>` | 删除单个兴趣标签 |
| `GET` | `/search/test` | `ApiResponse<Map>` | 搜索模块自检 |

### 6.4 File / Knowledge

| 方法 | 路径 | 返回 | 说明 |
| --- | --- | --- | --- |
| `POST` | `/file/upload/image` | `Map` | 上传图片，返回图片访问 URL |
| `GET` | `/file/image/{filename}` | `byte[]` | 读取图片内容 |
| `POST` | `/file/upload` | `Map` | 上传文件并做内容分析 |
| `GET` | `/file/list` | `Map` | 文档列表，包含 `success`、`data`、`total` |
| `GET` | `/file/{id}` | `Map` | 文档详情 |
| `DELETE` | `/file/{id}` | `Map` | 删除文档及其磁盘文件 |
| `GET` | `/file/search` | `Map` | 按 `minImportance` / `maxImportance` 过滤 |
| `GET` | `/knowledge/list` | `ApiResponse<List<KnowledgeBase>>` | 知识库列表 |
| `GET` | `/knowledge/{id}` | `ApiResponse<KnowledgeBase>` | 知识库详情 |
| `POST` | `/knowledge` | `ApiResponse<KnowledgeBase>` | 创建知识库，自动创建 Qdrant collection |
| `PUT` | `/knowledge/{id}` | `ApiResponse<KnowledgeBase>` | 更新知识库 |
| `DELETE` | `/knowledge/{id}` | `ApiResponse<Void>` | 删除知识库与对应 collection |
| `PUT` | `/knowledge/{id}/toggle` | `ApiResponse<KnowledgeBase>` | 切换启用状态 |
| `POST` | `/knowledge/{baseId}/upload` | `ApiResponse<KnowledgeDocument>` | 上传文档到知识库，`multipart/form-data` |
| `GET` | `/knowledge/{baseId}/documents` | `ApiResponse<List<KnowledgeDocument>>` | 文档列表 |
| `DELETE` | `/knowledge/document/{docId}` | `ApiResponse<Void>` | 删除知识库文档 |
| `GET` | `/knowledge/{baseId}/query` | `ApiResponse<String>` | RAG 检索，参数 `question`、`topK` |
| `GET` | `/knowledge/{baseId}/search` | `ApiResponse<List<Map>>` | 文档片段搜索，参数 `query`、`topK` |

### 6.5 MCP Tool / MCP Agent / Skill

| 方法 | 路径 | 返回 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/mcp/tools` | `List<McpTool>` | 工具列表 |
| `GET` | `/mcp/tools/enabled` | `List<McpTool>` | 启用工具列表 |
| `GET` | `/mcp/tools/{id}` | `McpTool` / `404` | 工具详情 |
| `POST` | `/mcp/tools` | `String` | 添加工具 |
| `PUT` | `/mcp/tools/{id}` | `String` | 更新工具 |
| `DELETE` | `/mcp/tools/{id}` | `String` | 删除工具 |
| `PUT` | `/mcp/tools/{id}/toggle` | `String` | 启用或禁用工具 |
| `POST` | `/mcp/tools/{name}/execute` | `ToolExecutionResult` | 按工具名执行 |
| `POST` | `/mcp/tools/{id}/test` | `ToolExecutionResult` | 测试指定工具 |
| `POST` | `/mcp/tools/{id}/validate` | `Map` | 验证工具配置 |
| `POST` | `/mcp/tools/sync` | `ApiResponse<SyncResult>` | 手动执行 MCP 自动同步，支持 `dryRun` |
| `GET` | `/mcp/tools/sync/status` | `ApiResponse<SyncResult>` | 最近一次 MCP 同步结果 |
| `GET` | `/mcp/agent/chat` | `String` | MCP Agent 对话，参数 `message` |
| `POST` | `/mcp/agent/chat` | `String` | MCP Agent 对话，请求体 `{ "message": "..." }` |
| `GET` | `/mcp/agent/chat/{sessionId}` | `String` | MCP Agent 带记忆对话 |
| `GET` | `/mcp/agent/chat/stream` | `text/event-stream` | MCP Agent 流式对话 |
| `GET` | `/mcp/agent/chat/stream/{sessionId}` | `text/event-stream` | MCP Agent 带记忆流式对话 |
| `GET` | `/skill/list` | `List<Skill>` | 技能列表 |
| `GET` | `/skill/enabled` | `List<Skill>` | 启用技能列表 |
| `GET` | `/skill/builtin` | `List<Skill>` | 内置技能列表 |
| `GET` | `/skill/categories` | `List<String>` | 技能分类 |
| `GET` | `/skill/category/{category}` | `List<Skill>` | 分类筛选 |
| `GET` | `/skill/{id}` | `Skill` / `404` | 技能详情 |
| `GET` | `/skill/code/{code}` | `Skill` / `404` | 按技能编码查询 |
| `POST` | `/skill` | `String` | 创建技能 |
| `PUT` | `/skill/{id}` | `String` | 更新技能 |
| `DELETE` | `/skill/{id}` | `String` | 删除技能 |
| `PUT` | `/skill/{id}/toggle` | `String` | 切换启用状态 |
| `POST` | `/skill/{skillId}/tools/{toolId}` | `String` | 绑定工具，支持 query 参数 `order`、`required` |
| `DELETE` | `/skill/{skillId}/tools/{toolId}` | `String` | 解绑工具 |
| `GET` | `/skill/{id}/tools` | `List<McpTool>` | 查询技能绑定工具 |
| `POST` | `/skill/{code}/execute` | `ApiResponse<SkillExecutionResult>` | 按编码执行技能 |
| `POST` | `/skill/{id}/test` | `ApiResponse<SkillExecutionResult>` | 测试执行技能 |
| `POST` | `/skill/reload` | `Map` | 从 `skills.yaml` 重新加载技能 |
| `POST` | `/skill/reload-findskills` | `Map` | 从远端 `findskills` 地址同步技能 |
| `POST` | `/skill/sync` | `ApiResponse<FindskillsSyncResult>` | 手动执行远端技能同步，支持 `dryRun` |
| `GET` | `/skill/sync/status` | `ApiResponse<FindskillsSyncResult>` | 最近一次远端技能同步结果 |
| `POST` | `/skill/import` | `Map` | 导入技能 JSON |
| `GET` | `/skill/{id}/export` | `Map` / `404` | 导出技能 JSON |

### 6.6 Schedule / Email

| 方法 | 路径 | 返回 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/schedule/list` | `ApiResponse<List<ScheduleEvent>>` | 日程列表 |
| `GET` | `/schedule/latest` | `ApiResponse<List<ScheduleEvent>>` | 最近日程，参数 `limit`，默认 5 |
| `GET` | `/schedule/today` | `ApiResponse<List<ScheduleEvent>>` | 今日日程 |
| `GET` | `/schedule/tomorrow` | `ApiResponse<List<ScheduleEvent>>` | 明日日程 |
| `GET` | `/schedule/date/{date}` | `ApiResponse<List<ScheduleEvent>>` | 指定日期日程 |
| `GET` | `/schedule/{id}` | `ApiResponse<ScheduleEvent>` | 日程详情 |
| `GET` | `/schedule/range` | `ApiResponse<List<ScheduleEvent>>` | 范围查询，参数 `startDate`、`endDate` |
| `GET` | `/schedule/stream` | `text/event-stream` | 日程实时流 |
| `GET` | `/schedule/files` | `List<String>` | 所有日程文件名 |
| `GET` | `/schedule/file/date/{date}` | `Map` | 按日期读取 Markdown 文件与事件列表 |
| `GET` | `/schedule/file/{fileName}` | `Map` | 按文件名读取 Markdown 内容 |
| `GET` | `/schedule/{id}/file` | `Map` | 读取某条日程对应的文件内容 |
| `POST` | `/schedule` | `ApiResponse<ScheduleEvent>` | 创建日程 |
| `PUT` | `/schedule/{id}` | `ApiResponse<ScheduleEvent>` | 更新日程 |
| `DELETE` | `/schedule/{id}` | `ApiResponse<Void>` | 删除日程 |
| `PUT` | `/schedule/{id}/complete` | `ApiResponse<ScheduleEvent>` | 标记完成 |
| `PUT` | `/schedule/{id}/cancel` | `ApiResponse<ScheduleEvent>` | 标记取消 |
| `POST` | `/schedule/parse-email` | `ApiResponse<ScheduleEvent>` | 从邮件文本提取日程 |
| `POST` | `/schedule/parse-and-save` | `ApiResponse<ScheduleEvent>` | 从邮件文本提取并保存日程 |
| `GET` | `/email/config/list` | `List<EmailConfig>` | 邮箱配置列表 |
| `GET` | `/email/config/enabled` | `List<EmailConfig>` | 启用的邮箱配置 |
| `GET` | `/email/config/{id}` | `EmailConfig` | 单个邮箱配置 |
| `POST` | `/email/config` | `String` | 添加邮箱配置 |
| `PUT` | `/email/config` | `String` | 更新邮箱配置 |
| `DELETE` | `/email/config/{id}` | `String` | 删除邮箱配置 |
| `POST` | `/email/listener/start/{id}` | `String` | 启动监听并置 `enabled=true` |
| `POST` | `/email/listener/stop/{id}` | `String` | 停止监听并置 `enabled=false` |
| `POST` | `/email/listener/reload` | `String` | 重新加载所有监听器 |
| `GET` | `/email/listener/status` | `Map<Long,String>` | 查看监听状态 |
| `POST` | `/email/config/{id}/test` | `Map` | 测试已保存配置的邮箱连接 |
| `GET` | `/email/config/{id}/network-check` | `Map` | 测试服务器到邮箱主机的网络连通性 |
| `POST` | `/email/config/network-check` | `Map` | 测试未保存配置的网络连通性 |
| `POST` | `/email/config/test` | `Map` | 测试未保存配置的邮箱连接 |
| `GET` | `/email/templates` | `List<EmailTemplate>` | 常见邮箱模板 |

### 6.7 Note / Snippet / Code

| 方法 | 路径 | 返回 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/note/list` | `ApiResponse<List<Note>>` | 笔记列表 |
| `GET` | `/note/{id}` | `ApiResponse<Note>` | 笔记详情 |
| `POST` | `/note` | `ApiResponse<Note>` | 创建笔记 |
| `PUT` | `/note/{id}` | `ApiResponse<Note>` | 更新笔记 |
| `DELETE` | `/note/{id}` | `ApiResponse<Void>` | 删除笔记 |
| `PUT` | `/note/{id}/pin` | `ApiResponse<Note>` | 切换置顶 |
| `POST` | `/note/{id}/summarize` | `ApiResponse<Note>` | AI 总结笔记 |
| `GET` | `/note/search` | `ApiResponse<List<Note>>` | 关键词搜索 |
| `POST` | `/note/reindex` | `ApiResponse<Integer>` | 重新建立全部笔记向量索引 |
| `GET` | `/note/semantic-search` | `ApiResponse<List<NoteSemanticHit>>` | 语义搜索，参数 `query`、`topK` |
| `GET` | `/snippet/list` | `ApiResponse<List<CodeSnippet>>` | 代码片段列表 |
| `GET` | `/snippet/{id}` | `ApiResponse<CodeSnippet>` | 片段详情 |
| `POST` | `/snippet` | `ApiResponse<CodeSnippet>` | 创建片段 |
| `PUT` | `/snippet/{id}` | `ApiResponse<CodeSnippet>` | 更新片段 |
| `DELETE` | `/snippet/{id}` | `ApiResponse<Void>` | 删除片段 |
| `GET` | `/snippet/language/{language}` | `ApiResponse<List<CodeSnippet>>` | 按语言过滤 |
| `GET` | `/snippet/search` | `ApiResponse<List<CodeSnippet>>` | 关键词搜索 |
| `POST` | `/snippet/{id}/explain` | `ApiResponse<String>` | AI 解释代码 |
| `POST` | `/code/generate` | `ApiResponse<GenerateResponse>` | 代码生成 |
| `POST` | `/code/save` | `ApiResponse<GenerateResponse>` | 保存生成代码到磁盘 |
| `POST` | `/code/review` | `ApiResponse<String>` | 代码审查 |
| `POST` | `/code/convert` | `ApiResponse<String>` | 代码转换 |
| `GET` | `/code/analyze` | `ApiResponse<Map>` | 分析项目目录结构，参数 `path` |
| `GET` | `/code/types` | `ApiResponse<List<String>>` | 获取支持的代码类型 |

### 6.8 Task / Model / Settings

| 方法 | 路径 | 返回 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/task/list` | `ApiResponse<List<ScheduledTask>>` | 定时任务列表 |
| `GET` | `/task/{id}` | `ApiResponse<ScheduledTask>` | 任务详情 |
| `POST` | `/task` | `ApiResponse<ScheduledTask>` | 创建定时任务 |
| `PUT` | `/task/{id}` | `ApiResponse<ScheduledTask>` | 更新定时任务 |
| `DELETE` | `/task/{id}` | `ApiResponse<Void>` | 删除定时任务 |
| `PUT` | `/task/{id}/toggle` | `ApiResponse<ScheduledTask>` | 启用或禁用任务 |
| `POST` | `/task/{id}/execute` | `ApiResponse<String>` | 手动执行任务 |
| `GET` | `/task/types` | `ApiResponse<List<String>>` | 返回 `SKILL` / `CHAT` / `REMINDER` |
| `GET` | `/model/list` | `ApiResponse<List<Map>>` | 模型列表，隐藏完整 API Key |
| `GET` | `/model/{id}` | `ApiResponse<Map>` | 模型详情 |
| `POST` | `/model` | `ApiResponse<Map>` | 创建模型；首个模型会自动启用并设默认 |
| `PUT` | `/model/{id}` | `ApiResponse<Map>` | 更新模型 |
| `DELETE` | `/model/{id}` | `ApiResponse<Void>` | 删除模型 |
| `PUT` | `/model/{id}/toggle` | `ApiResponse<Map>` | 启用时会自动禁用其他模型，并设为默认模型 |
| `PUT` | `/model/{id}/default` | `ApiResponse<Map>` | 设置默认模型，要求该模型已启用 |
| `POST` | `/model/test` | `ApiResponse<String>` | 测试模型连接，不入库 |
| `GET` | `/model/providers` | `ApiResponse<List<Map>>` | 获取内置 provider 预设 |
| `GET` | `/settings` | `ApiResponse<Map<String,Map<String,String>>>` | 所有设置 |
| `GET` | `/settings/{category}` | `ApiResponse<Map<String,String>>` | 分类设置 |
| `GET` | `/settings/{category}/{key}` | `ApiResponse<String>` | 单个配置 |
| `PUT` | `/settings/{category}/{key}` | `ApiResponse<Void>` | 更新单个配置，请求体是纯字符串 |
| `PUT` | `/settings/{category}` | `ApiResponse<Void>` | 批量更新分类配置 |
| `DELETE` | `/settings/{category}/{key}` | `ApiResponse<Void>` | 删除配置 |
| `GET` | `/settings/system` | `ApiResponse<Map<String,String>>` | 便捷读取系统设置 |
| `PUT` | `/settings/system` | `ApiResponse<Void>` | 便捷更新系统设置 |
| `GET` | `/settings/qdrant` | `ApiResponse<Map<String,String>>` | 向量库设置 |
| `PUT` | `/settings/qdrant` | `ApiResponse<Void>` | 更新向量库设置 |
| `GET` | `/settings/search` | `ApiResponse<Map<String,String>>` | 搜索设置，返回时会掩码 `api_key` |
| `PUT` | `/settings/search` | `ApiResponse<Void>` | 更新搜索设置；带 `****` 的 `api_key` 会被视为“不更新” |
| `GET` | `/settings/schedule` | `ApiResponse<Map<String,String>>` | 日程设置 |
| `PUT` | `/settings/schedule` | `ApiResponse<Void>` | 更新日程设置 |
| `GET` | `/settings/file` | `ApiResponse<Map<String,String>>` | 文件设置 |
| `PUT` | `/settings/file` | `ApiResponse<Void>` | 更新文件设置 |
| `GET` | `/settings/model` | `ApiResponse<Map<String,String>>` | 模型参数设置 |
| `PUT` | `/settings/model` | `ApiResponse<Void>` | 更新模型参数设置 |
| `GET` | `/settings/proxy` | `ApiResponse<Map<String,String>>` | 获取代理配置，当前为公开接口 |
| `PUT` | `/settings/proxy` | `ApiResponse<Void>` | 更新代理配置 |
| `GET` | `/settings/data/export` | `byte[]` | 导出 ZIP；当前控制器声明存在，但后端编译被缺失服务阻塞 |
| `POST` | `/settings/data/import` | `ApiResponse<Map>` | 导入 ZIP；当前控制器声明存在，但后端编译被缺失服务阻塞 |

### 6.9 Inbox / Report / Autonomy / Personal

| 方法 | 路径 | 返回 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/inbox/summary` | `ApiResponse<InboxSummary>` | 统一收件箱聚合摘要，参数 `limit` |
| `POST` | `/report/generate` | `ApiResponse<GeneratedReport>` | 生成日报或周报，参数 `period=daily|weekly` |
| `GET` | `/report/history` | `ApiResponse<List<ReportArtifact>>` | 报告历史，参数 `limit` |
| `GET` | `/report/artifact` | `ApiResponse<String>` | 读取报告文件，参数 `path` |
| `GET` | `/autonomy/capabilities` | `ApiResponse<Map>` | 返回自治能力开关与策略 |
| `GET` | `/autonomy/history` | `ApiResponse<List<AutonomyArtifact>>` | 自治产物历史，参数 `limit` |
| `GET` | `/autonomy/diff` | `ApiResponse<AutonomyDiff>` | 对比最近两次扫描差异 |
| `GET` | `/autonomy/artifact` | `ApiResponse<String>` | 读取自治产物文件，参数 `path` |
| `POST` | `/autonomy/scan` | `ApiResponse<AutonomyScanReport>` | 执行项目结构扫描 |
| `POST` | `/autonomy/verify` | `ApiResponse<AutonomyVerificationResult>` | 运行后端测试 / 前端构建验证，请求体可选 `{ "backend": true, "frontend": true }` |
| `POST` | `/autonomy/draft` | `ApiResponse<AutonomyDraftResponse>` | 生成补全草稿，请求体可选 `{ "target": "general", "includeVerification": true }` |
| `GET` | `/personal/insights` | `ApiResponse<Map>` | 单用户效率指标 |
| `GET` | `/personal/task-templates` | `ApiResponse<Object>` | 内置任务模板列表 |
| `POST` | `/personal/task-templates/{templateId}/create` | `ApiResponse<ScheduledTask>` | 从模板创建任务 |
| `GET` | `/personal/backup/export` | `ApiResponse<Map>` | 导出个人数据备份 JSON |
| `POST` | `/personal/backup/import` | `ApiResponse<Map>` | 导入个人数据备份，参数 `replaceExisting` |

## 7. 附加说明

### 7.1 文件与知识库上传限制

- `FileUploadService` 允许类型由 `app.file.allowed-types` 控制，默认 `txt,md,pdf,doc,docx`。
- 但当前源码只有 `txt` 和 `md` 会提取正文内容；其他类型会保存文件，但内容分析能力有限。
- `RagService` 当前也只有 `txt` 与 `md` 会提取正文，其余类型会得到空字符串。

### 7.2 邮箱配置安全处理

- `password`、`oauthClientSecret`、`oauthRefreshToken`、`oauthAccessToken` 在响应里都是脱敏或直接不返回。
- 响应会额外包含：
  - `passwordConfigured`
  - `oauthClientSecretConfigured`
  - `oauthRefreshTokenConfigured`
  - `oauthAccessTokenConfigured`

### 7.3 模型切换策略

模型不是“多启用并存”模式，而是“单启用模型”模式：

- 创建第一个模型时会自动启用并设为默认。
- 启用某个模型时，其他已启用模型会被自动禁用。
- 带会话记忆的聊天接口始终使用当前启用的模型。

### 7.4 验证记录

本次更新过程中的实际验证结果：

- `frontend`: `npm run build` 通过
- `backend`: `.\gradlew.bat test` 失败，缺失 `DataArchiveService`
