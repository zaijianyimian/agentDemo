# AI Agent Demo - API接口文档

## 基础信息

- **服务地址**: `http://localhost:8000`
- **API前缀**: `/api`
- **认证方式**: JWT Bearer Token
- **响应格式**: JSON

## 统一响应格式

```json
{
  "success": true,
  "data": {},
  "message": "操作成功",
  "code": 200
}
```

---

## 1. 认证模块 (Auth)

### 1.1 获取拼图验证码

**接口**: `GET /api/auth/captcha/puzzle`

**说明**: 获取滑块验证码，用于登录/注册前的人机验证。

**响应**:
```json
{
  "success": true,
  "data": {
    "captchaId": "uuid-string",
    "backgroundImage": "base64-encoded-image",
    "sliderImage": "base64-encoded-image",
    "sliderX": 100
  }
}
```

---

### 1.2 验证拼图验证码

**接口**: `POST /api/auth/captcha/puzzle/verify`

**请求体**:
```json
{
  "captchaId": "uuid-string",
  "sliderPercent": 0.85
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "valid": true,
    "ticket": "verification-ticket"
  }
}
```

---

### 1.3 用户注册

**接口**: `POST /api/auth/register`

**请求体**:
```json
{
  "username": "testuser",
  "email": "test@example.com",
  "password": "password123",
  "displayName": "测试用户",
  "captchaTicket": "verification-ticket"
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "cooldownSeconds": 120,
    "message": "注册成功，请查收邮箱验证码完成确认后登录"
  }
}
```

---

### 1.4 密码登录

**接口**: `POST /api/auth/login/password`

**请求体**:
```json
{
  "username": "testuser",
  "password": "password123",
  "captchaTicket": "verification-ticket"
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "tokenType": "Bearer",
    "accessToken": "jwt-access-token",
    "refreshToken": "jwt-refresh-token",
    "expiresIn": 7200,
    "requiresSecondFactor": false,
    "user": {
      "id": 1,
      "username": "testuser",
      "email": "test@example.com",
      "displayName": "测试用户",
      "role": "USER",
      "emailVerified": true
    }
  }
}
```

---

### 1.5 发送邮箱验证码

**接口**: `POST /api/auth/login/email/send-code`

**请求体**:
```json
{
  "email": "test@example.com",
  "captchaTicket": "verification-ticket"
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "cooldownSeconds": 120,
    "message": "验证码已发送，请注意查收"
  }
}
```

---

### 1.6 邮箱验证码登录

**接口**: `POST /api/auth/login/email`

**请求体**:
```json
{
  "email": "test@example.com",
  "code": "123456",
  "captchaTicket": "verification-ticket"
}
```

**响应**: 同密码登录

---

### 1.7 刷新令牌

**接口**: `POST /api/auth/token/refresh`

**请求体**:
```json
{
  "refreshToken": "jwt-refresh-token"
}
```

**响应**: 同密码登录（返回新的access和refresh token）

---

### 1.8 获取当前用户信息

**接口**: `GET /api/auth/me`

**认证**: 需要 Bearer Token

**响应**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "displayName": "测试用户",
    "role": "USER",
    "emailVerified": true
  }
}
```

---

### 1.9 修改密码

**接口**: `PUT /api/auth/password`

**认证**: 需要 Bearer Token

**请求体**:
```json
{
  "currentPassword": "oldpassword",
  "newPassword": "newpassword123"
}
```

---

### 1.10 退出登录

**接口**: `POST /api/auth/logout`

**认证**: 需要 Bearer Token

---

### 1.11 GitHub OAuth授权

**接口**: `GET /api/auth/oauth/github/authorize`

**参数**: `redirect` (可选，登录后跳转路径)

**响应**:
```json
{
  "success": true,
  "data": {
    "authorizeUrl": "https://github.com/login/oauth/authorize?...",
    "state": "oauth-state-string"
  }
}
```

---

### 1.12 GitHub OAuth换取令牌

**接口**: `POST /api/auth/oauth/github/exchange`

**请求体**:
```json
{
  "code": "github-code",
  "state": "oauth-state-string"
}
```

**响应**: 同密码登录

---

### 1.13 获取人脸验证状态

**接口**: `GET /api/auth/face/status`

**认证**: 需要 Bearer Token

**响应**:
```json
{
  "success": true,
  "data": {
    "enrolled": true,
    "enabled": true,
    "required": false
  }
}
```

---

### 1.14 注册人脸

**接口**: `POST /api/auth/face/register`

**认证**: 需要 Bearer Token

**请求体**:
```json
{
  "imageBase64": "base64-encoded-face-image"
}
```

---

### 1.15 开启/关闭人脸二次验证

**接口**: `PUT /api/auth/face/required`

**认证**: 需要 Bearer Token

**请求体**:
```json
{
  "required": true
}
```

---

### 1.16 人脸验证登录

**接口**: `POST /api/auth/face/verify-login`

**请求体**:
```json
{
  "preAuthToken": "pre-auth-token",
  "imageBase64": "base64-encoded-face-image"
}
```

**响应**: 同密码登录

---

## 2. 聊天模块 (Chat)

### 2.1 普通聊天

**接口**: `GET /api/chat/complete`

**参数**: `message` - 用户消息

**响应**: 直接返回文本内容

---

### 2.2 流式聊天（SSE）

**接口**: `GET /api/chat/stream`

**参数**: `message` - 用户消息

**响应格式**: Server-Sent Events (SSE)

```
data: 文本片段1
data: 文本片段2
...
```

---

### 2.3 流式聊天（JSON格式）

**接口**: `GET /api/chat/stream/json`

**参数**: `message` - 用户消息

**响应格式**: SSE，每个数据块为JSON

```
data: {"content":"文本片段","isComplete":false}
data: {"content":"完整内容","importance":3,"tags":["tag1"],"sentiment":"POSITIVE","summary":"摘要","isComplete":true}
event: complete
```

---

### 2.4 结构化聊天

**接口**: `GET /api/chat/structured`

**参数**: `message` - 用户消息

**响应**:
```json
{
  "success": true,
  "data": {
    "content": "AI回复内容",
    "importance": 3,
    "tags": ["技术", "编程"],
    "sentiment": "NEUTRAL",
    "summary": "内容摘要",
    "isComplete": true
  }
}
```

---

### 2.5 带记忆的普通聊天

**接口**: `GET /api/chat/complete/session`

**参数**:
- `message` - 用户消息
- `sessionId` - 会话ID

**响应**: 直接返回文本内容

---

### 2.6 带记忆的流式聊天（SSE）

**接口**: `GET /api/chat/stream/session`

**参数**:
- `message` - 用户消息
- `sessionId` - 会话ID

**响应格式**: SSE

---

### 2.7 带记忆的流式聊天（JSON格式）

**接口**: `GET /api/chat/stream/session/json`

**参数**:
- `message` - 用户消息
- `sessionId` - 会话ID

**响应格式**: SSE JSON

---

## 3. 聊天历史模块 (ChatHistory)

### 3.1 创建会话

**接口**: `POST /api/chat-history/session`

**请求体**:
```json
{
  "title": "新会话"
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "title": "新会话",
    "messageCount": 0
  }
}
```

---

### 3.2 获取会话列表

**接口**: `GET /api/chat-history/sessions`

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "title": "关于Python的讨论",
      "messageCount": 10,
      "lastMessageTime": "2026-03-31T10:00:00"
    }
  ]
}
```

---

### 3.3 获取会话消息

**接口**: `GET /api/chat-history/session/{sessionId}/messages`

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "role": "user",
      "content": "你好",
      "createTime": "2026-03-31T09:00:00"
    },
    {
      "id": 2,
      "role": "assistant",
      "content": "你好，有什么可以帮助你的？",
      "model": "qwen-plus",
      "createTime": "2026-03-31T09:00:05"
    }
  ]
}
```

---

### 3.4 删除会话

**接口**: `DELETE /api/chat-history/session/{sessionId}`

---

### 3.5 更新会话标题

**接口**: `PUT /api/chat-history/session/{sessionId}/title`

**请求体**:
```json
{
  "title": "新标题"
}
```

---

## 4. 记忆模块 (Memory)

### 4.1 存储记忆

**接口**: `POST /api/memory/store`

**请求体**:
```json
{
  "sessionId": "session-123",
  "summary": "用户正在学习Python编程",
  "category": "learning",
  "importance": 3,
  "tags": ["Python", "编程"],
  "shouldStore": true,
  "metadata": {
    "topic": "Python基础"
  }
}
```

---

### 4.2 搜索记忆

**接口**: `GET /api/memory/search`

**参数**:
- `query` - 搜索关键词
- `topK` (可选，默认5) - 返回数量
- `sessionId` (可选) - 按会话过滤
- `category` (可选) - 按类别过滤

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "score": 0.85,
      "text": "用户正在学习Python编程",
      "sessionId": "session-123",
      "category": "learning",
      "importance": "3",
      "createdAt": "2026-03-31T10:00:00",
      "tags": "Python,编程"
    }
  ]
}
```

---

## 5. 知识库模块 (Knowledge)

### 5.1 创建知识库

**接口**: `POST /api/knowledge/base`

**请求体**:
```json
{
  "name": "技术文档库",
  "description": "存储技术相关文档",
  "chunkSize": 500,
  "chunkOverlap": 50
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "技术文档库",
    "collectionName": "kb_xxx_xxx_xxx",
    "documentCount": 0,
    "enabled": true
  }
}
```

---

### 5.2 获取知识库列表

**接口**: `GET /api/knowledge/base/list`

---

### 5.3 获取知识库详情

**接口**: `GET /api/knowledge/base/{id}`

---

### 5.4 更新知识库

**接口**: `PUT /api/knowledge/base/{id}`

**请求体**: 同创建知识库

---

### 5.5 删除知识库

**接口**: `DELETE /api/knowledge/base/{id}`

---

### 5.6 上传文档

**接口**: `POST /api/knowledge/base/{baseId}/document`

**请求格式**: multipart/form-data

**参数**: `file` - 文档文件（txt、md、pdf、doc、docx）

**响应**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "fileName": "document.txt",
    "fileType": "txt",
    "fileSize": 1024,
    "chunkCount": 5,
    "status": "completed"
  }
}
```

---

### 5.7 获取文档列表

**接口**: `GET /api/knowledge/base/{baseId}/documents`

---

### 5.8 删除文档

**接口**: `DELETE /api/knowledge/document/{docId}`

---

### 5.9 知识库问答

**接口**: `GET /api/knowledge/base/{baseId}/query`

**参数**:
- `question` - 问题
- `topK` (可选，默认5) - 返回数量

**响应**:
```json
{
  "success": true,
  "data": "相关上下文内容..."
}
```

---

### 5.10 搜索文档片段

**接口**: `GET /api/knowledge/base/{baseId}/search`

**参数**:
- `query` - 搜索关键词
- `topK` (可选) - 返回数量

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "score": 0.85,
      "text": "文档片段内容",
      "docName": "document.txt",
      "docId": "1"
    }
  ]
}
```

---

## 6. 搜索模块 (Search)

### 6.1 网络搜索

**接口**: `GET /api/search`

**参数**: `query` - 搜索关键词

**响应**:
```json
{
  "success": true,
  "data": {
    "query": "Python教程",
    "results": [
      {
        "title": "Python教程 - 菜鸟教程",
        "url": "https://www.runoob.com/python",
        "snippet": "Python教程...",
        "source": "www.runoob.com"
      }
    ],
    "totalResults": 3
  }
}
```

---

### 6.2 获取搜索历史

**接口**: `GET /api/search/history`

---

## 7. MCP工具模块 (MCP Tools)

### 7.1 获取工具列表

**接口**: `GET /api/mcp/tools`

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "web_search",
      "displayName": "网络搜索",
      "description": "在互联网上搜索相关信息",
      "toolType": "http_api",
      "enabled": true
    }
  ]
}
```

---

### 7.2 创建工具

**接口**: `POST /api/mcp/tools`

**请求体**:
```json
{
  "name": "weather_query",
  "displayName": "天气查询",
  "description": "查询指定城市的天气信息",
  "toolType": "http_api",
  "config": {
    "url": "https://api.example.com/weather",
    "method": "GET",
    "headers": {},
    "timeout": 30
  },
  "inputSchema": {
    "type": "object",
    "properties": {
      "city": {
        "type": "string",
        "description": "城市名称"
      }
    },
    "required": ["city"]
  },
  "enabled": true
}
```

---

### 7.3 更新工具

**接口**: `PUT /api/mcp/tools/{id}`

---

### 7.4 删除工具

**接口**: `DELETE /api/mcp/tools/{id}`

---

### 7.5 执行工具

**接口**: `POST /api/mcp/tools/{id}/execute`

**请求体**:
```json
{
  "city": "北京"
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "success": true,
    "result": "{\"temperature\": 25, \"humidity\": 60}",
    "durationMs": 150
  }
}
```

---

### 7.6 刷新工具缓存

**接口**: `POST /api/mcp/tools/refresh`

---

## 8. 技能模块 (Skills)

### 8.1 获取技能列表

**接口**: `GET /api/skills`

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "code": "web_search",
      "name": "网络搜索",
      "description": "在互联网上搜索相关信息",
      "category": "search",
      "icon": "search",
      "enabled": true,
      "isBuiltin": true
    }
  ]
}
```

---

### 8.2 创建技能

**接口**: `POST /api/skills`

**请求体**:
```json
{
  "code": "my_skill",
  "name": "自定义技能",
  "description": "技能描述",
  "category": "custom",
  "icon": "tool",
  "enabled": true,
  "config": {}
}
```

---

### 8.3 更新技能

**接口**: `PUT /api/skills/{id}`

---

### 8.4 删除技能

**接口**: `DELETE /api/skills/{id}`

---

### 8.5 执行技能

**接口**: `POST /api/skills/{code}/execute`

**请求体**:
```json
{
  "query": "Python教程"
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "success": true,
    "skillCode": "web_search",
    "result": {...},
    "toolSteps": [
      {
        "toolName": "web_search_tool",
        "success": true,
        "result": "...",
        "durationMs": 150
      }
    ],
    "totalDurationMs": 160
  }
}
```

---

### 8.6 获取技能工具映射

**接口**: `GET /api/skills/{skillId}/tools`

---

### 8.7 添加技能工具映射

**接口**: `POST /api/skills/{skillId}/tools`

**请求体**:
```json
{
  "toolId": 1,
  "invokeOrder": 1,
  "isRequired": true
}
```

---

### 8.8 删除技能工具映射

**接口**: `DELETE /api/skills/{skillId}/tools/{toolId}`

---

## 9. MCP Agent模块

### 9.1 Agent对话

**接口**: `POST /api/mcp-agent/chat`

**请求体**:
```json
{
  "message": "帮我搜索Python教程"
}
```

**响应**: AI自动判断并调用工具，返回结果

---

### 9.2 Agent带记忆对话

**接口**: `POST /api/mcp-agent/chat/memory`

**请求体**:
```json
{
  "memoryId": "session-123",
  "message": "继续上次的讨论"
}
```

---

### 9.3 Agent流式对话

**接口**: `POST /api/mcp-agent/chat/stream`

**响应格式**: SSE

---

## 10. 邮件模块 (Email)

### 10.1 获取邮箱配置列表

**接口**: `GET /api/email/configs`

---

### 10.2 创建邮箱配置

**接口**: `POST /api/email/config`

**请求体**:
```json
{
  "email": "user@163.com",
  "password": "授权码",
  "host": "imap.163.com",
  "protocol": "imap",
  "port": 993,
  "sslEnabled": true,
  "enabled": true,
  "folder": "INBOX",
  "pollInterval": 30
}
```

---

### 10.3 更新邮箱配置

**接口**: `PUT /api/email/config/{id}`

---

### 10.4 删除邮箱配置

**接口**: `DELETE /api/email/config/{id}`

---

### 10.5 发送邮件

**接口**: `POST /api/email/send`

**请求体**:
```json
{
  "to": "recipient@example.com",
  "subject": "邮件主题",
  "body": "邮件内容"
}
```

---

## 11. 日程模块 (Schedule)

### 11.1 获取日程列表

**接口**: `GET /api/schedule`

**参数**: `date` (可选) - 指定日期

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "title": "项目会议",
      "description": "讨论项目进度",
      "eventTime": "2026-03-31T14:00:00",
      "eventDate": "2026-03-31",
      "location": "会议室A",
      "status": "pending"
    }
  ]
}
```

---

### 11.2 创建日程

**接口**: `POST /api/schedule`

**请求体**:
```json
{
  "title": "项目会议",
  "description": "讨论项目进度",
  "eventTime": "2026-03-31T14:00:00",
  "location": "会议室A",
  "reminderEnabled": true
}
```

---

### 11.3 更新日程

**接口**: `PUT /api/schedule/{id}`

---

### 11.4 删除日程

**接口**: `DELETE /api/schedule/{id}`

---

### 11.5 获取今日日程汇总

**接口**: `GET /api/schedule/today`

---

## 12. 定时任务模块 (Tasks)

### 12.1 获取任务列表

**接口**: `GET /api/tasks`

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "每日提醒",
      "description": "每天8点发送提醒",
      "taskType": "REMINDER",
      "cronExpression": "0 0 8 * * ?",
      "enabled": true,
      "executeCount": 30,
      "successCount": 30,
      "failCount": 0
    }
  ]
}
```

---

### 12.2 创建任务

**接口**: `POST /api/tasks`

**请求体**:
```json
{
  "name": "每日提醒",
  "description": "每天8点发送提醒",
  "taskType": "SKILL",
  "cronExpression": "0 0 8 * * ?",
  "skillCode": "send_reminder",
  "params": "{}",
  "enabled": true
}
```

---

### 12.3 更新任务

**接口**: `PUT /api/tasks/{id}`

---

### 12.4 删除任务

**接口**: `DELETE /api/tasks/{id}`

---

### 12.5 启用/禁用任务

**接口**: `PUT /api/tasks/{id}/enabled`

**请求体**:
```json
{
  "enabled": true
}
```

---

## 13. 文件管理模块 (Files)

### 13.1 上传文件

**接口**: `POST /api/files/upload`

**请求格式**: multipart/form-data

**参数**: `file` - 文件（txt、md、pdf、doc、docx，最大10MB）

**响应**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "fileName": "document.txt",
    "fileType": "txt",
    "fileSize": 1024,
    "status": "analyzed",
    "importance": 3,
    "tags": ["技术", "编程"],
    "sentiment": "NEUTRAL",
    "summary": "文档摘要"
  }
}
```

---

### 13.2 获取文件列表

**接口**: `GET /api/files`

---

### 13.3 获取文件详情

**接口**: `GET /api/files/{id}`

---

### 13.4 删除文件

**接口**: `DELETE /api/files/{id}`

---

## 14. 笔记模块 (Notes)

### 14.1 获取笔记列表

**接口**: `GET /api/notes`

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "title": "学习笔记",
      "tags": "Python,学习",
      "aiSummary": "AI生成的摘要",
      "isPinned": true,
      "createTime": "2026-03-31T10:00:00"
    }
  ]
}
```

---

### 14.2 创建笔记

**接口**: `POST /api/notes`

**请求体**:
```json
{
  "title": "学习笔记",
  "content": "笔记内容...",
  "tags": "Python,学习"
}
```

---

### 14.3 更新笔记

**接口**: `PUT /api/notes/{id}`

---

### 14.4 删除笔记

**接口**: `DELETE /api/notes/{id}`

---

### 14.5 置顶/取消置顶

**接口**: `PUT /api/notes/{id}/pin`

**请求体**:
```json
{
  "pinned": true
}
```

---

## 15. 代码片段模块 (Snippets)

### 15.1 获取代码片段列表

**接口**: `GET /api/snippets`

**参数**: `language` (可选) - 按语言过滤

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "title": "Python快速排序",
      "code": "def quicksort(arr): ...",
      "language": "python",
      "description": "快速排序实现",
      "tags": "算法,排序"
    }
  ]
}
```

---

### 15.2 创建代码片段

**接口**: `POST /api/snippets`

**请求体**:
```json
{
  "title": "Python快速排序",
  "code": "def quicksort(arr): ...",
  "language": "python",
  "description": "快速排序实现",
  "tags": "算法,排序"
}
```

---

### 15.3 更新代码片段

**接口**: `PUT /api/snippets/{id}`

---

### 15.4 删除代码片段

**接口**: `DELETE /api/snippets/{id}`

---

## 16. 模型管理模块 (Models)

### 16.1 获取模型配置列表

**接口**: `GET /api/models`

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "通义千问",
      "provider": "aliyun",
      "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1",
      "modelName": "qwen-plus",
      "isDefault": true,
      "enabled": true
    }
  ]
}
```

---

### 16.2 创建模型配置

**接口**: `POST /api/models`

**请求体**:
```json
{
  "name": "DeepSeek",
  "provider": "deepseek",
  "baseUrl": "https://api.deepseek.com/v1",
  "modelName": "deepseek-chat",
  "apiKey": "your-api-key",
  "isDefault": false,
  "enabled": true
}
```

---

### 16.3 更新模型配置

**接口**: `PUT /api/models/{id}`

---

### 16.4 删除模型配置

**接口**: `DELETE /api/models/{id}`

---

### 16.5 测试模型连接

**接口**: `POST /api/models/{id}/test`

**响应**:
```json
{
  "success": true,
  "data": "连接成功: OK"
}
```

---

### 16.6 设置默认模型

**接口**: `PUT /api/models/{id}/default`

---

## 17. 项目自治模块 (Autonomy)

### 17.1 获取自治能力

**接口**: `GET /api/autonomy/capabilities`

**响应**:
```json
{
  "success": true,
  "data": {
    "enabled": true,
    "workspaceRoot": "/path/to/project",
    "outputDir": "/path/to/generated/autonomy",
    "canScan": true,
    "canGenerateDraft": true,
    "canVerifyBackend": true,
    "canVerifyFrontend": true,
    "allowSourceWrite": false,
    "allowRemoteUpdate": false,
    "policy": "默认只扫描、生成补全草稿..."
  }
}
```

---

### 17.2 扫描项目

**接口**: `POST /api/autonomy/scan`

**响应**:
```json
{
  "success": true,
  "data": {
    "scanTime": "2026-03-31T10:00:00",
    "workspaceRoot": "/path/to/project",
    "metrics": {
      "controllerCount": 26,
      "viewCount": 21,
      "routeCount": 20,
      "readmeExists": true,
      "apiDocExists": true,
      "gitClean": false
    },
    "findings": [
      {
        "severity": "medium",
        "title": "工作区非干净状态",
        "detail": "检测到git工作区存在未提交改动",
        "suggestion": "执行自动补全前，建议先提交..."
      }
    ],
    "reportPath": "/path/to/scan-report.json",
    "summaryPath": "/path/to/scan-report.md"
  }
}
```

---

### 17.3 验证项目

**接口**: `POST /api/autonomy/verify`

**请求体**:
```json
{
  "backend": true,
  "frontend": true
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "verifyTime": "2026-03-31T10:05:00",
    "success": true,
    "steps": [
      {
        "name": "backend-test",
        "success": true,
        "exitCode": 0,
        "workingDirectory": "/path/to/project",
        "output": "BUILD SUCCESSFUL"
      },
      {
        "name": "frontend-build",
        "success": true,
        "exitCode": 0,
        "workingDirectory": "/path/to/frontend",
        "output": "Build completed"
      }
    ]
  }
}
```

---

### 17.4 生成补全草稿

**接口**: `POST /api/autonomy/draft`

**请求体**:
```json
{
  "target": "general",
  "includeVerification": true
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "generateTime": "2026-03-31T10:10:00",
    "target": "general",
    "draftPath": "/path/to/draft.md",
    "content": "# 补全方案\n...",
    "policyNote": "当前实现默认只生成补全草稿..."
  }
}
```

---

### 17.5 获取自治历史

**接口**: `GET /api/autonomy/history`

**参数**: `limit` (默认12) - 返回数量

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "type": "scan",
      "name": "scan-20260331-100000.json",
      "path": "/path/to/file",
      "time": "2026-03-31T10:00:00",
      "preview": "..."
    }
  ]
}
```

---

### 17.6 读取自治产物

**接口**: `GET /api/autonomy/artifact`

**参数**: `path` - 文件路径

---

### 17.7 比较扫描差异

**接口**: `GET /api/autonomy/diff`

**响应**:
```json
{
  "success": true,
  "data": {
    "latestScanTime": "2026-03-31T10:00:00",
    "previousScanTime": "2026-03-30T10:00:00",
    "newCount": 1,
    "resolvedCount": 2,
    "persistentCount": 3,
    "newFindings": [...],
    "resolvedFindings": [...],
    "persistentFindings": [...]
  }
}
```

---

## 18. 系统设置模块 (Settings)

### 18.1 获取所有设置

**接口**: `GET /api/settings`

---

### 18.2 获取单个设置

**接口**: `GET /api/settings/{category}/{key}`

---

### 18.3 更新设置

**接口**: `PUT /api/settings/{category}/{key}`

**请求体**:
```json
{
  "value": "true"
}
```

---

## 19. Embedding模块

### 19.1 获取文本向量

**接口**: `POST /api/embedding`

**请求体**:
```json
{
  "text": "需要向量化的文本"
}
```

**响应**:
```json
{
  "success": true,
  "data": {
    "embedding": [0.1, 0.2, ...],
    "dimension": 768
  }
}
```

---

## 20. 收件箱模块 (Inbox)

### 20.1 获取统一收件箱

**接口**: `GET /api/inbox`

**响应**:
```json
{
  "success": true,
  "data": [
    {
      "type": "email",
      "title": "项目更新通知",
      "content": "...",
      "source": "your_mail@example.com",
      "time": "2026-03-31T09:00:00"
    }
  ]
}
```

---

## 21. 个人效率模块 (Personal)

### 21.1 获取效率洞察

**接口**: `GET /api/personal/insights`

---

### 21.2 获取活动统计

**接口**: `GET /api/personal/activity`

---

## 错误响应格式

```json
{
  "success": false,
  "data": null,
  "message": "错误描述",
  "code": 400
}
```

常见错误码：
- 400 - 请求参数错误
- 401 - 未认证/认证失败
- 403 - 无权限
- 404 - 资源不存在
- 500 - 服务器内部错误

---

## 认证说明

除认证相关接口外，其他接口均需要在请求头中携带JWT令牌：

```
Authorization: Bearer <access_token>
```

令牌过期后需使用 `refreshToken` 刷新获取新的令牌。