# AI Agent Demo 接口文档

## 基本信息

- **Base URL**: `http://localhost:8000/api`
- **编码**: UTF-8
- **数据格式**: JSON

### 文档说明

- 本文档已按 `2026-03-29` 的本地实测结果校正。
- 所有 `SSE` 接口在浏览器 `EventSource` 下可直接使用；命令行、测试脚本或网关联调时建议显式传 `Accept: text/event-stream`。
- `POST /api/schedule` 与 `PUT /api/schedule/{id}` 的 `eventTime` 同时支持 `yyyy-MM-ddTHH:mm:ss` 和 `yyyy-MM-dd HH:mm:ss`。
- `POST /api/model/test` 无论模型连通性成功还是失败，通常都会返回 `HTTP 200`，实际结果写在响应体 `data` 字段中。
- 知识库查询接口在向量集合异常时会尽量返回可读结果而不是直接抛出 `500`：`/query` 返回失败提示文本，`/search` 返回空列表。

---

## 接口列表

| 接口 | 方法 | 路径 | 描述 | 状态 |
|------|------|------|------|------|
| 普通聊天 | GET | `/chat/complete` | 返回完整响应文本 | ✅ 已通过 |
| 流式聊天 | GET | `/chat/stream` | SSE方式流式返回 | ✅ 已通过(需Accept头) |
| 流式聊天(JSON) | GET | `/chat/stream/json` | SSE方式JSON格式返回 | ✅ 已通过(需Accept头) |
| 结构化聊天 | GET | `/chat/structured` | 返回含元数据的完整响应 | ✅ 已通过 |
| 带会话记忆聊天 | GET | `/chat/complete/session` | 带记忆的完整响应，自动保存历史 | ✅ 已修复并验证 |
| 带会话记忆流式聊天 | GET | `/chat/stream/session` | 带记忆的SSE流式返回 | ✅ 已修复并验证 |
| 带会话记忆流式JSON | GET | `/chat/stream/session/json` | 带记忆的JSON流式返回 | ✅ 已修复并验证 |
| 测试流式响应 | GET | `/chat/stream/test` | 测试接口(带详细日志) | ✅ 新增 |
| 内容分析 | GET | `/analyze` | 分析文本的重要程度、标签等 | ✅ 已通过 |
| 文本向量化 | GET | `/embedding` | 返回文本的向量数组 | ❌ 超时(Ollama未启动) |
| 文本向量化(完整) | GET | `/embedding/full` | 返回完整的向量化响应 | ⏳ 待测 |
| 网络搜索 | GET | `/search` | 执行网络搜索 | ✅ 已通过 |
| 搜索+AI总结 | GET | `/search/summary` | 搜索并返回AI总结 | ✅ 已通过 |
| 带搜索聊天 | GET | `/search/chat` | 先搜索再回答(结构化) | ✅ 已通过 |
| 带搜索流式聊天 | GET | `/search/chat/stream` | 先搜索再流式回答 | ✅ 已通过 |
| 邮箱配置列表 | GET | `/email/config/list` | 获取所有邮箱配置 | ✅ 已通过 |
| 启用的邮箱配置 | GET | `/email/config/enabled` | 获取所有启用的邮箱配置 | ✅ 新增 |
| 邮箱配置详情 | GET | `/email/config/{id}` | 获取单个邮箱配置 | ✅ 新增 |
| 添加邮箱配置 | POST | `/email/config` | 添加邮箱配置 | ✅ 已通过 |
| 更新邮箱配置 | PUT | `/email/config` | 更新邮箱配置 | ⏳ 待测 |
| 删除邮箱配置 | DELETE | `/email/config/{id}` | 删除邮箱配置 | ✅ 已通过 |
| 启动邮件监听 | POST | `/email/listener/start/{id}` | 启动指定邮箱监听 | ⏳ 待测 |
| 停止邮件监听 | POST | `/email/listener/stop/{id}` | 停止指定邮箱监听 | ⏳ 待测 |
| 重载邮箱监听 | POST | `/email/listener/reload` | 重载所有邮箱监听 | ✅ 新增 |
| 监听状态 | GET | `/email/listener/status` | 获取监听状态 | ✅ 已通过 |
| 邮箱服务器模板 | GET | `/email/templates` | 获取常用邮箱服务器配置 | ✅ 已通过 |
| 测试邮箱连接 | POST | `/email/config/{id}/test` | 测试已保存的邮箱配置 | ⏳ 待测 |
| 测试新配置 | POST | `/email/config/test` | 测试未保存的邮箱配置 | ⏳ 待测 |
| MCP工具列表 | GET | `/mcp/tools` | 获取所有MCP工具 | ✅ 已通过 |
| MCP启用工具 | GET | `/mcp/tools/enabled` | 获取启用的MCP工具 | ✅ 已通过 |
| MCP工具详情 | GET | `/mcp/tools/{id}` | 获取单个MCP工具 | ✅ 已通过 |
| 添加MCP工具 | POST | `/mcp/tools` | 添加新MCP工具 | ✅ 已通过 |
| 更新MCP工具 | PUT | `/mcp/tools/{id}` | 更新MCP工具 | ✅ 已通过 |
| 删除MCP工具 | DELETE | `/mcp/tools/{id}` | 删除MCP工具 | ✅ 已通过 |
| 切换工具状态 | PUT | `/mcp/tools/{id}/toggle` | 启用/禁用工具 | ✅ 已通过 |
| 执行MCP工具 | POST | `/mcp/tools/{name}/execute` | 执行指定工具 | ✅ 已通过(需外部API) |
| 测试MCP工具 | POST | `/mcp/tools/{id}/test` | 测试工具执行 | ✅ 已通过(需外部API) |
| 验证工具配置 | POST | `/mcp/tools/{id}/validate` | 验证工具配置是否有效 | ✅ 新增 |
| MCP Agent对话 | GET/POST | `/mcp/agent/chat` | AI自动调用工具对话 | ✅ 已通过 |
| MCP Agent流式 | GET | `/mcp/agent/chat/stream` | AI流式对话(SSE) | ✅ 已通过 |
| MCP Agent带记忆 | GET | `/mcp/agent/chat/{sessionId}` | 带会话记忆对话 | ✅ 已通过 |
| 技能列表 | GET | `/skill/list` | 获取所有技能 | ✅ 已通过 |
| 启用的技能 | GET | `/skill/enabled` | 获取所有启用的技能 | ✅ 新增 |
| 内置技能 | GET | `/skill/builtin` | 获取内置技能 | ✅ 已通过 |
| 技能分类 | GET | `/skill/categories` | 获取技能分类 | ✅ 已通过 |
| 按分类获取技能 | GET | `/skill/category/{category}` | 根据分类获取技能列表 | ✅ 新增 |
| 技能详情 | GET | `/skill/{id}` | 获取单个技能 | ✅ 已通过 |
| 根据编码获取 | GET | `/skill/code/{code}` | 根据编码获取技能 | ✅ 已通过 |
| 添加技能 | POST | `/skill` | 添加新技能 | ✅ 已通过 |
| 更新技能 | PUT | `/skill/{id}` | 更新技能 | ✅ 已通过 |
| 删除技能 | DELETE | `/skill/{id}` | 删除技能 | ✅ 已通过(自定义技能) |
| 切换技能状态 | PUT | `/skill/{id}/toggle` | 切换启用状态 | ✅ 已通过 |
| 绑定工具 | POST | `/skill/{skillId}/tools/{toolId}` | 绑定工具到技能 | ✅ 已通过 |
| 解绑工具 | DELETE | `/skill/{skillId}/tools/{toolId}` | 解绑工具 | ✅ 已通过 |
| 技能工具列表 | GET | `/skill/{id}/tools` | 获取技能关联的工具 | ✅ 已通过 |
| 执行技能 | POST | `/skill/{code}/execute` | 执行技能 | ✅ 已通过(无工具时返回提示) |
| 测试技能执行 | POST | `/skill/{id}/test` | 测试指定技能执行 | ✅ 新增 |
| 重新加载技能 | POST | `/skill/reload` | 从YAML重新加载技能 | ✅ 已通过 |
| 导入技能 | POST | `/skill/import` | JSON导入技能 | ✅ 已通过 |
| 导出技能 | GET | `/skill/{id}/export` | 导出技能为JSON | ✅ 已通过 |
| 记忆提取存储 | POST | `/memory/extract-store` | 从对话提取并存储记忆 | ✅ 新增 |
| 记忆搜索 | GET | `/memory/search` | 搜索相关记忆 | ✅ 新增 |
| 日程列表 | GET | `/schedule/list` | 获取所有日程 | ✅ 已通过 |
| 最近日程 | GET | `/schedule/latest` | 获取最近的日程(默认5条) | ✅ 新增 |
| 今日日程 | GET | `/schedule/today` | 获取今天的日程 | ✅ 已通过 |
| 明日日程 | GET | `/schedule/tomorrow` | 获取明天的日程 | ✅ 已通过 |
| 日期日程 | GET | `/schedule/date/{date}` | 获取指定日期日程 | ✅ 已通过 |
| 日期范围日程 | GET | `/schedule/range` | 获取指定日期范围内的日程 | ✅ 新增 |
| 日程详情 | GET | `/schedule/{id}` | 获取日程详情 | ✅ 已通过 |
| 添加日程 | POST | `/schedule` | 添加日程 | ✅ 已通过 |
| 更新日程 | PUT | `/schedule/{id}` | 更新日程 | ✅ 已通过 |
| 删除日程 | DELETE | `/schedule/{id}` | 删除日程 | ✅ 已通过 |
| 完成日程 | PUT | `/schedule/{id}/complete` | 标记日程完成 | ✅ 已通过 |
| 取消日程 | PUT | `/schedule/{id}/cancel` | 取消日程 | ✅ 新增 |
| SSE推送流 | GET | `/schedule/stream` | SSE实时日程推送 | ✅ 已修复并验证 |
| 解析邮件 | POST | `/schedule/parse-email` | 从邮件提取日程 | ✅ 已通过 |
| 解析并保存 | POST | `/schedule/parse-and-save` | 解析并保存日程 | ✅ 已通过 |
| 日程文件列表 | GET | `/schedule/files` | 获取日程文件列表 | ✅ 新增 |
| 按日期查文件 | GET | `/schedule/file/date/{date}` | 按日期读取日程文件 | ✅ 新增 |
| 按文件名读取 | GET | `/schedule/file/{fileName}` | 按文件名读取日程文件 | ✅ 新增 |
| 按ID读文件 | GET | `/schedule/{id}/file` | 按日程ID读取文件 | ✅ 新增 |
| 文件上传 | POST | `/file/upload` | 上传文件并AI分析 | ✅ 已通过 |
| 文件列表 | GET | `/file/list` | 获取所有文件列表 | ✅ 已通过 |
| 文件详情 | GET | `/file/{id}` | 获取文件详情 | ✅ 已通过 |
| 删除文件 | DELETE | `/file/{id}` | 删除文件 | ✅ 已通过 |
| 按重要性搜索 | GET | `/file/search` | 按重要程度查询文件 | ✅ 已通过 |
| 笔记列表 | GET | `/note/list` | 获取所有笔记 | ✅ 已通过 |
| 笔记详情 | GET | `/note/{id}` | 获取笔记详情 | ✅ 已通过 |
| 创建笔记 | POST | `/note` | 创建笔记 | ✅ 已通过 |
| 更新笔记 | PUT | `/note/{id}` | 更新笔记 | ✅ 已通过 |
| 删除笔记 | DELETE | `/note/{id}` | 删除笔记 | ✅ 已通过 |
| 切换置顶 | PUT | `/note/{id}/pin` | 切换置顶状态 | ✅ 已通过 |
| AI总结笔记 | POST | `/note/{id}/summarize` | AI总结笔记内容 | ✅ 已通过 |
| 搜索笔记 | GET | `/note/search` | 搜索笔记 | ✅ 已通过 |
| 代码片段列表 | GET | `/snippet/list` | 获取所有代码片段 | ✅ 已通过 |
| 片段详情 | GET | `/snippet/{id}` | 获取片段详情 | ✅ 已通过 |
| 按语言查询 | GET | `/snippet/language/{language}` | 根据编程语言查询片段 | ✅ 新增 |
| 创建片段 | POST | `/snippet` | 创建代码片段 | ✅ 已通过 |
| 更新片段 | PUT | `/snippet/{id}` | 更新片段 | ✅ 已通过 |
| 删除片段 | DELETE | `/snippet/{id}` | 删除片段 | ✅ 已通过 |
| 搜索片段 | GET | `/snippet/search` | 搜索代码片段 | ✅ 已通过 |
| AI解释代码 | POST | `/snippet/{id}/explain` | AI解释代码 | ✅ 已通过 |
| 代码生成类型 | GET | `/code/types` | 获取支持的代码类型 | ✅ 已通过 |
| 生成代码 | POST | `/code/generate` | AI生成代码 | ✅ 已通过 |
| 保存代码 | POST | `/code/save` | 保存代码到文件 | ✅ 已通过 |
| 代码审查 | POST | `/code/review` | AI代码审查 | ✅ 已通过 |
| 代码转换 | POST | `/code/convert` | 代码语言转换 | ✅ 已通过 |
| 分析项目 | GET | `/code/analyze` | 分析项目结构 | ✅ 已通过 |
| 创建会话 | POST | `/chat/history/session` | 创建聊天会话 | ✅ 已通过 |
| 会话列表 | GET | `/chat/history/sessions` | 获取所有会话 | ✅ 已通过 |
| 会话详情 | GET | `/chat/history/session/{id}` | 获取会话详情 | ✅ 已通过 |
| 更新会话标题 | PUT | `/chat/history/session/{id}/title` | 更新会话标题 | ✅ 已通过 |
| 删除会话 | DELETE | `/chat/history/session/{id}` | 删除会话 | ✅ 已通过 |
| 会话消息列表 | GET | `/chat/history/session/{sessionId}/messages` | 获取会话消息 | ✅ 已通过 |
| 添加消息 | POST | `/chat/history/session/{sessionId}/message` | 添加消息到会话 | ✅ 已通过 |
| 清空会话消息 | DELETE | `/chat/history/session/{sessionId}/messages` | 清空会话消息 | ✅ 已通过 |
| 知识库列表 | GET | `/knowledge/list` | 获取所有知识库 | ✅ 新增 |
| 知识库详情 | GET | `/knowledge/{id}` | 获取知识库详情 | ✅ 新增 |
| 创建知识库 | POST | `/knowledge` | 创建知识库 | ✅ 已修复并验证 |
| 更新知识库 | PUT | `/knowledge/{id}` | 更新知识库 | ✅ 新增 |
| 删除知识库 | DELETE | `/knowledge/{id}` | 删除知识库 | ✅ 新增 |
| 启用禁用知识库 | PUT | `/knowledge/{id}/toggle` | 切换启用状态 | ✅ 新增 |
| 上传文档 | POST | `/knowledge/{baseId}/upload` | 上传文档到知识库 | ✅ 新增 |
| 文档列表 | GET | `/knowledge/{baseId}/documents` | 获取文档列表 | ✅ 新增 |
| 删除文档 | DELETE | `/knowledge/document/{docId}` | 删除文档 | ✅ 新增 |
| RAG问答 | GET | `/knowledge/{baseId}/query` | 基于知识库问答 | ✅ 已修复并验证 |
| 语义搜索 | GET | `/knowledge/{baseId}/search` | 搜索相关文档片段 | ✅ 已修复并验证 |
| 任务列表 | GET | `/task/list` | 获取所有定时任务 | ✅ 新增 |
| 任务详情 | GET | `/task/{id}` | 获取任务详情 | ✅ 新增 |
| 创建任务 | POST | `/task` | 创建定时任务 | ✅ 新增 |
| 更新任务 | PUT | `/task/{id}` | 更新任务 | ✅ 新增 |
| 删除任务 | DELETE | `/task/{id}` | 删除任务 | ✅ 新增 |
| 启用禁用任务 | PUT | `/task/{id}/toggle` | 切换启用状态 | ✅ 新增 |
| 手动执行任务 | POST | `/task/{id}/execute` | 手动执行任务 | ✅ 新增 |
| 任务类型 | GET | `/task/types` | 获取任务类型列表 | ✅ 新增 |
| 模型列表 | GET | `/model/list` | 获取所有模型配置 | ✅ 新增 |
| 模型详情 | GET | `/model/{id}` | 获取模型详情 | ✅ 新增 |
| 创建模型 | POST | `/model` | 创建模型配置 | ✅ 新增 |
| 更新模型 | PUT | `/model/{id}` | 更新模型配置 | ✅ 新增 |
| 删除模型 | DELETE | `/model/{id}` | 删除模型配置 | ✅ 新增 |
| 启用禁用模型 | PUT | `/model/{id}/toggle` | 切换启用状态 | ✅ 新增 |
| 设置默认模型 | PUT | `/model/{id}/default` | 设置为默认模型 | ✅ 新增 |
| 测试模型连接 | POST | `/model/test` | 测试模型连接 | ✅ 已验证(结果见响应体) |
| 提供商列表 | GET | `/model/providers` | 获取支持的提供商 | ✅ 新增 |

---

## 1. 普通聊天接口

### 请求

```
GET /api/chat/complete
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message | String | 是 | 用户消息 |

### 返回类型

`String` - 纯文本响应

### 示例

**请求:**
```
GET http://localhost:8000/api/chat/complete?message=你好
```

**响应:**
```
你好！我是通义千问（Qwen），阿里巴巴集团旗下的超大规模语言模型...
```

---

## 2. 流式聊天接口 (SSE)

### 请求

```
GET /api/chat/stream
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message | String | 是 | 用户消息 |

### 返回类型

`text/event-stream` - SSE 流式响应

### 示例

**请求:**
```
GET http://localhost:8000/api/chat/stream?message=你好
```

**响应:**
```
data:你好
data:！我是通
data:义千问
data:...
```

### 前端使用

```javascript
const eventSource = new EventSource('http://localhost:8000/api/chat/stream?message=你好');
eventSource.onmessage = (event) => {
    console.log(event.data);  // 每个数据块
};
```

---

## 3. 流式聊天接口 (JSON格式)

### 请求

```
GET /api/chat/stream/json
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message | String | 是 | 用户消息 |

### 返回类型

`text/event-stream` - SSE 流式响应，JSON格式包装

### 响应结构

每个数据块格式:

```json
{
    "content": "你好",
    "importance": null,
    "tags": null,
    "sentiment": null,
    "summary": null,
    "isComplete": false
}
```

最后一个数据块包含完整元数据:

```json
{
    "content": "完整响应内容...",
    "importance": 3,
    "tags": ["问候", "自我介绍"],
    "sentiment": "POSITIVE",
    "summary": "用户打招呼，AI进行自我介绍",
    "isComplete": true
}
```

---

## 4. 结构化聊天接口

### 请求

```
GET /api/chat/structured
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message | String | 是 | 用户消息 |

### 返回类型

`application/json` - ChatResponse 对象

### 响应结构

| 字段 | 类型 | 描述 |
|------|------|------|
| content | String | 响应内容 |
| importance | Integer | 重要程度 (1-5) |
| tags | List\<String\> | 标签列表 |
| sentiment | String | 情感倾向 (POSITIVE/NEGATIVE/NEUTRAL) |
| summary | String | 一句话摘要 |
| isComplete | Boolean | 是否完成 (固定为true) |

### 示例

**请求:**
```
GET http://localhost:8000/api/chat/structured?message=如何学习Java
```

**响应:**
```json
{
    "content": "学习Java可以从以下几个方面入手：1. 掌握基础语法...",
    "importance": 4,
    "tags": ["编程", "学习", "Java", "技术建议"],
    "sentiment": "POSITIVE",
    "summary": "提供Java学习路线和建议",
    "isComplete": true
}
```

---

## 5. 内容分析接口

### 请求

```
GET /api/analyze
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| content | String | 是 | 需要分析的文本内容 |

### 返回类型

`application/json` - ContentAnalysis 对象

### 响应结构

| 字段 | 类型 | 描述 |
|------|------|------|
| importance | Integer | 重要程度 (1-5) |
| tags | List\<String\> | 内容标签 |
| sentiment | String | 情感倾向 (POSITIVE/NEGATIVE/NEUTRAL) |
| summary | String | 一句话摘要 |

### 重要程度说明

| 值 | 级别 | 描述 |
|----|------|------|
| 1 | 不重要 | 闲聊、寒暄 |
| 2 | 较低 | 一般信息 |
| 3 | 中等 | 有一定价值的信息 |
| 4 | 较高 | 重要信息 |
| 5 | 非常重要 | 关键信息、紧急事项 |

---

## 6. 文本向量化接口

### 请求

```
GET /api/embedding
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| text | String | 是 | 需要向量化的文本 |

### 返回类型

`application/json` - float[] 向量数组

### 示例

**请求:**
```
GET http://localhost:8000/api/embedding?text=你好
```

**响应:**
```json
[0.0123, -0.0456, 0.0789, ...]
```

---

## 7. 文本向量化接口 (完整)

### 请求

```
GET /api/embedding/full
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| text | String | 是 | 需要向量化的文本 |

### 返回类型

`application/json` - Response\<Embedding\> 对象

---

## 8. 网络搜索接口

### 请求

```
GET /api/search
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| query | String | 是 | 搜索关键词 |

### 返回类型

`application/json` - SearchResult.ListResult 对象

### 响应结构

| 字段 | 类型 | 描述 |
|------|------|------|
| query | String | 搜索关键词 |
| results | List\<SearchResult\> | 搜索结果列表(最多3条) |
| totalResults | Integer | 结果数量 |

### SearchResult 结构

| 字段 | 类型 | 描述 |
|------|------|------|
| title | String | 结果标题 |
| url | String | 结果链接 |
| snippet | String | 结果摘要 |
| source | String | 来源网站 |

### 示例

**请求:**
```
GET http://localhost:8000/api/search?query=Java教程
```

**响应:**
```json
{
    "query": "Java教程",
    "results": [
        {
            "title": "Java 教程 | 菜鸟教程",
            "url": "https://www.runoob.com/java/",
            "snippet": "Java 是由 Sun Microsystems 公司于1995年推出的...",
            "source": "www.runoob.com"
        }
    ],
    "totalResults": 3
}
```

---

## 9. 带搜索的聊天接口

### 请求

```
GET /api/search/chat
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message | String | 是 | 用户消息 |

### 返回类型

`application/json` - ChatResponse 对象

### 功能说明

1. 先执行网络搜索获取相关信息
2. 基于搜索结果生成回答
3. 分析回答内容返回结构化数据

### 示例

**请求:**
```
GET http://localhost:8000/api/chat/search?message=2024年世界杯在哪里举办
```

**响应:**
```json
{
    "content": "根据搜索结果，2024年没有世界杯...",
    "importance": 4,
    "tags": ["体育", "世界杯", "足球"],
    "sentiment": "NEUTRAL",
    "summary": "回答关于世界杯举办地的问题",
    "isComplete": true
}
```

---

## 10. 带搜索的流式聊天接口

### 请求

```
GET /api/search/chat/stream
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message | String | 是 | 用户消息 |

### 返回类型

`text/event-stream` - SSE 流式响应

### 功能说明

1. 先执行网络搜索
2. 流式返回基于搜索结果的回答
3. 最后返回结构化元数据

---

## 11. 搜索+AI总结接口

### 请求

```
GET /api/search/summary
```

### 参数

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| query | String | 是 | 搜索关键词 |

### 返回类型

`application/json` - SearchSummaryResponse 对象

### 响应结构

| 字段 | 类型 | 描述 |
|------|------|------|
| query | String | 搜索关键词 |
| searchResults | List\<SearchResult\> | 搜索结果列表 |
| aiSummary | String | AI总结内容 |

### 示例

**请求:**
```
GET http://localhost:8000/api/search/summary?query=Spring Boot 3新特性
```

**响应:**
```json
{
    "query": "Spring Boot 3新特性",
    "searchResults": [
        {
            "title": "Spring Boot 3 新特性详解",
            "url": "https://example.com/spring-boot-3",
            "snippet": "Spring Boot 3 基于 Spring Framework 6...",
            "source": "example.com"
        }
    ],
    "aiSummary": "Spring Boot 3的主要新特性包括：1. 要求Java 17+；2. 支持原生编译；3. 升级到Spring Framework 6..."
}
```

---

## 配置说明

### 搜索配置 (application.yaml)

```yaml
app:
  search:
    enabled: true                    # 是否启用搜索
    engine: serper                   # 搜索引擎: serper, tavily, bing
    api-key: your-api-key-here       # 搜索API密钥
    max-results: 3                   # 最大结果数(默认3条，节约成本)
```

### 支持的搜索引擎

| 引擎 | 免费额度 | 官网 |
|------|---------|------|
| Serper | 2500次/月 | https://serper.dev |
| Tavily | 1000次/月 | https://tavily.com |
| Bing | 按需付费 | https://azure.microsoft.com |

---

## 数据模型

### ChatResponse

```java
public class ChatResponse {
    private String content;        // 响应内容
    private Integer importance;    // 重要程度 (1-5)
    private List<String> tags;     // 标签列表
    private String sentiment;      // 情感倾向
    private String summary;        // 摘要
    private Boolean isComplete;    // 是否完成
}
```

### ContentAnalysis

```java
public class ContentAnalysis {
    private Integer importance;    // 重要程度 (1-5)
    private List<String> tags;     // 内容标签
    private Sentiment sentiment;   // 情感倾向枚举
    private String summary;        // 一句话摘要

    public enum Sentiment {
        POSITIVE,   // 积极正面
        NEGATIVE,   // 消极负面
        NEUTRAL     // 中性
    }
}
```

### SearchResult

```java
public class SearchResult {
    private String title;      // 标题
    private String url;        // 链接
    private String snippet;    // 摘要
    private String source;     // 来源
}
```

---

## 错误处理

所有接口在发生错误时返回:

```json
{
    "error": "错误描述信息"
}
```

常见错误码:
- 400: 请求参数错误
- 500: 服务器内部错误

---

## 使用建议

1. **实时对话场景**: 使用 `/chat/stream` 或 `/chat/stream/json` 流式接口
2. **需要结构化数据**: 使用 `/chat/structured` 获取带元数据的完整响应
3. **内容审核/分类**: 使用 `/analyze` 单独分析文本
4. **语义搜索/相似度计算**: 使用 `/embedding` 获取文本向量
5. **实时信息查询**: 使用 `/search/chat` 带搜索的聊天
6. **快速搜索总结**: 使用 `/search/summary` 获取搜索结果+AI总结

---

## 更新日志

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2026-03-28 | 1.6.0 | 新增文件上传管理系统，支持文件上传、AI自动分析重要程度、标签提取、情感分析、摘要生成，数据持久化存储 |
| 2026-03-27 | 1.5.0 | 新增日程管理系统，支持邮件自动解析日程、SSE实时推送、定时汇总提醒、多线程/虚拟线程支持 |
| 2026-03-27 | 1.4.0 | 新增AI技能系统，支持技能分类、工具绑定、链式调用，内置5个常用技能 |
| 2026-03-27 | 1.3.0 | 新增MCP工具管理功能，支持数据库存储工具配置，AI自动调用工具 |
| 2026-03-26 | 1.2.0 | 新增邮件监听功能，支持配置多个邮箱监听 |
| 2026-03-26 | 1.1.0 | 新增网络搜索功能，支持Serper/Tavily/Bing搜索引擎 |
| 2026-03-26 | 1.0.0 | 初始版本，包含基础聊天、流式响应、结构化输出、向量化等功能 |

---

## 邮件监听功能

### 功能说明

邮件监听功能支持：
- 配置多个邮箱账号
- 启动时自动加载已启用的邮箱配置
- 实时监听新邮件
- 可扩展的邮件处理器接口

### 数据库初始化

执行 `src/main/resources/sql/email_config.sql` 创建表结构。

### 邮箱配置接口

#### 1. 获取所有邮箱配置

```
GET /api/email/config/list
```

**响应:**
```json
[
    {
        "id": 1,
        "email": "test@qq.com",
        "host": "imap.qq.com",
        "protocol": "imap",
        "port": 993,
        "sslEnabled": true,
        "enabled": true,
        "folder": "INBOX",
        "pollInterval": 30
    }
]
```

#### 2. 添加邮箱配置

```
POST /api/email/config
Content-Type: application/json
```

**请求体:**
```json
{
    "email": "your_email@qq.com",
    "password": "your_auth_code",
    "host": "imap.qq.com",
    "protocol": "imap",
    "port": 993,
    "sslEnabled": true,
    "enabled": false,
    "folder": "INBOX",
    "pollInterval": 30,
    "remark": "QQ邮箱"
}
```

#### 3. 更新邮箱配置

```
PUT /api/email/config
Content-Type: application/json
```

#### 4. 删除邮箱配置

```
DELETE /api/email/config/{id}
```

### 监听管理接口

#### 1. 启动邮箱监听

```
POST /api/email/listener/start/{id}
```

**响应:**
```
已启动监听: test@qq.com
```

#### 2. 停止邮箱监听

```
POST /api/email/listener/stop/{id}
```

#### 3. 重载所有监听

```
POST /api/email/listener/reload
```

#### 4. 获取监听状态

```
GET /api/email/listener/status
```

**响应:**
```json
{
    "1": "已连接",
    "2": "未连接"
}
```

#### 5. 获取邮箱服务器模板

```
GET /api/email/templates
```

**响应:**
```json
[
    {"name": "QQ邮箱", "host": "imap.qq.com", "port": 993, "sslEnabled": true, "protocol": "imap"},
    {"name": "163邮箱", "host": "imap.163.com", "port": 993, "sslEnabled": true, "protocol": "imap"},
    {"name": "Gmail", "host": "imap.gmail.com", "port": 993, "sslEnabled": true, "protocol": "imap"}
]
```

### 常用邮箱配置

| 邮箱 | IMAP服务器 | 端口 | SSL |
|------|-----------|------|-----|
| QQ邮箱 | imap.qq.com | 993 | 是 |
| 163邮箱 | imap.163.com | 993 | 是 |
| 126邮箱 | imap.126.com | 993 | 是 |
| Gmail | imap.gmail.com | 993 | 是 |
| Outlook | outlook.office365.com | 993 | 是 |
| 阿里企业邮箱 | imap.qiye.aliyun.com | 993 | 是 |
| 腾讯企业邮箱 | imap.exmail.qq.com | 993 | 是 |

### 获取授权码

| 邮箱 | 获取方式 |
|------|---------|
| QQ邮箱 | 设置 → 账户 → POP3/IMAP/SMTP服务 → 生成授权码 |
| 163邮箱 | 设置 → POP3/SMTP/IMAP → 开启服务 → 获取授权码 |
| Gmail | Google账户 → 安全性 → 两步验证 → 应用专用密码 |
| Outlook | Microsoft账户 → 安全性 → 应用密码 |

### 测试邮箱连接

#### 1. 测试已保存的邮箱配置

```
POST /api/email/config/{id}/test
```

**响应:**
```json
{
    "success": true,
    "message": "连接成功",
    "durationMs": 523,
    "messageCount": 42,
    "errorDetail": ""
}
```

#### 2. 测试未保存的新配置

```
POST /api/email/config/test
Content-Type: application/json
```

**请求体:**
```json
{
    "email": "your@qq.com",
    "password": "your_auth_code",
    "host": "imap.qq.com",
    "port": 993,
    "sslEnabled": true,
    "protocol": "imap"
}
```

**响应字段说明:**

| 字段 | 类型 | 说明 |
|------|------|------|
| success | Boolean | 是否连接成功 |
| message | String | 结果描述 |
| durationMs | Long | 测试耗时(毫秒) |
| messageCount | Integer | 收件箱邮件数量(成功时) |
| errorDetail | String | 错误详情(失败时) |

**常见错误信息:**

| 错误信息 | 可能原因 |
|---------|---------|
| 认证失败：邮箱地址或密码/授权码错误 | 密码或授权码不正确 |
| 连接失败：无法连接到邮件服务器 | 服务器地址或端口错误 |
| SSL/TLS错误 | SSL配置不正确 |

### 扩展邮件处理器

实现 `EmailListenerService.EmailHandler` 接口：

```java
@Component
public class MyEmailHandler implements EmailListenerService.EmailHandler {

    @Override
    public void handle(EmailMessage emailMessage) {
        // 处理新邮件
        System.out.println("收到新邮件: " + emailMessage.getSubject());

        // 可以调用AI分析邮件内容
        // ContentAnalysis analysis = contentAnalysisService.analyze(emailMessage.getTextContent());
    }
}

// 在服务启动后设置处理器
@Autowired
private EmailListenerService emailListenerService;

@PostConstruct
public void init() {
    emailListenerService.setEmailHandler(myEmailHandler);
}
```

---

## MCP 工具管理功能

### 功能说明

MCP (Model Context Protocol) 工具管理功能支持：
- 数据库存储工具配置，动态管理
- 支持 HTTP API 和本地脚本两种工具类型
- AI 自动识别并调用合适的工具
- 无需重启应用即可增删改工具

### 数据库初始化

执行 `src/main/resources/sql/mcp_tool.sql` 创建表结构。

### 工具类型

| 类型 | 代码 | 说明 |
|------|------|------|
| HTTP API | `http_api` | 调用外部 HTTP 接口 |
| 本地脚本 | `local_script` | 执行本地脚本或命令 |

### 工具管理接口

#### 1. 获取所有工具

```
GET /api/mcp/tools
```

**响应:**
```json
[
    {
        "id": 1,
        "name": "weather_query",
        "displayName": "天气查询",
        "description": "查询指定城市的天气信息",
        "toolType": "http_api",
        "config": "{\"url\": \"https://api.weather.com/v1\", \"method\": \"GET\", \"timeout\": 30}",
        "inputSchema": "{\"type\": \"object\", \"properties\": {\"city\": {\"type\": \"string\"}}, \"required\": [\"city\"]}",
        "enabled": true,
        "createTime": "2026-03-27T10:00:00",
        "updateTime": "2026-03-27T10:00:00"
    }
]
```

#### 2. 获取启用的工具

```
GET /api/mcp/tools/enabled
```

#### 3. 获取单个工具

```
GET /api/mcp/tools/{id}
```

#### 4. 添加工具

```
POST /api/mcp/tools
Content-Type: application/json
```

**请求体:**
```json
{
    "name": "weather_query",
    "displayName": "天气查询",
    "description": "查询指定城市的天气信息，返回温度、湿度、天气状况等",
    "toolType": "http_api",
    "config": "{\"url\": \"https://api.weather.com/v1/current\", \"method\": \"GET\", \"timeout\": 30}",
    "inputSchema": "{\"type\": \"object\", \"properties\": {\"city\": {\"type\": \"string\", \"description\": \"城市名称\"}}, \"required\": [\"city\"]}",
    "enabled": true
}
```

**字段说明:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | 工具唯一标识，只能包含字母、数字、下划线 |
| displayName | String | 否 | 工具显示名称 |
| description | String | 是 | 工具描述，AI会根据此描述决定是否调用 |
| toolType | String | 是 | 工具类型: http_api 或 local_script |
| config | String | 是 | JSON格式的工具配置 |
| inputSchema | String | 否 | JSON Schema格式的输入参数定义 |
| enabled | Boolean | 否 | 是否启用，默认false |

#### 5. 更新工具

```
PUT /api/mcp/tools/{id}
Content-Type: application/json
```

#### 6. 删除工具

```
DELETE /api/mcp/tools/{id}
```

#### 7. 切换启用状态

```
PUT /api/mcp/tools/{id}/toggle
```

**响应:**
```
状态已切换
```

### 工具执行接口

#### 1. 执行指定工具

```
POST /api/mcp/tools/{name}/execute
Content-Type: application/json
```

**请求体:**
```json
{
    "city": "北京"
}
```

**响应:**
```json
{
    "success": true,
    "result": "{\"temperature\": 25, \"humidity\": 60, \"weather\": \"晴\"}",
    "error": "",
    "durationMs": 150
}
```

#### 2. 测试工具执行

```
POST /api/mcp/tools/{id}/test
Content-Type: application/json
```

即使工具未启用也可以测试执行。

### 工具配置示例

#### HTTP API 类型

```json
{
    "url": "https://api.example.com/endpoint",
    "method": "POST",
    "headers": {
        "Authorization": "Bearer ${API_KEY}",
        "Content-Type": "application/json"
    },
    "timeout": 30
}
```

**配置字段:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| url | String | 是 | API地址 |
| method | String | 否 | HTTP方法，默认GET |
| headers | Object | 否 | 请求头，支持环境变量占位符 `${ENV_VAR}` |
| timeout | Integer | 否 | 超时时间(秒)，默认30 |

#### 本地脚本类型

```json
{
    "scriptPath": "./scripts/tool.sh",
    "timeout": 10,
    "workingDir": "."
}
```

**配置字段:**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| scriptPath | String | 是 | 脚本路径 |
| timeout | Integer | 否 | 超时时间(秒)，默认30 |
| workingDir | String | 否 | 工作目录，默认当前目录 |

**支持的脚本类型:**

| 扩展名 | 执行方式 |
|--------|---------|
| .sh | bash script.sh |
| .py | python script.py |
| .bat/.cmd | cmd /c script.bat |
| 其他 | 直接执行 |

---

## MCP Agent 对话接口

### 功能说明

MCP Agent 对话接口支持 AI 自动调用已启用的工具。AI 会根据用户消息内容和工具描述自动判断是否需要调用工具。

### 1. 普通对话

```
GET /api/mcp/agent/chat?message=北京今天天气怎么样
```

或 POST 方式:

```
POST /api/mcp/agent/chat
Content-Type: application/json

{
    "message": "北京今天天气怎么样"
}
```

**响应:**
```
根据查询结果，北京今天天气晴朗，气温25°C，湿度60%...
```

### 2. 流式对话

```
GET /api/mcp/agent/chat/stream?message=帮我查一下天气
```

**响应 (SSE):**
```
data:根据
data:查询结果
data:...
```

### 3. 带会话记忆的对话

```
GET /api/mcp/agent/chat/{sessionId}?message=你好
```

**参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| sessionId | String | 会话ID，相同ID的对话会保持上下文记忆 |
| message | String | 用户消息 |

**示例:**
```
# 第一次对话
GET /api/mcp/agent/chat/user123?message=我叫张三
响应: 你好张三，很高兴认识你！

# 第二次对话（同一sessionId）
GET /api/mcp/agent/chat/user123?message=我叫什么名字
响应: 你叫张三。
```

### 工作流程

1. 用户发送消息
2. AI 分析消息内容
3. AI 根据工具描述判断是否需要调用工具
4. 如需调用，自动执行工具并将结果融入回答
5. 返回最终响应

### 使用场景

| 场景 | 说明 |
|------|------|
| 实时数据查询 | 天气、股票、新闻等需要实时数据的查询 |
| 外部系统集成 | 调用企业内部API、数据库查询等 |
| 自动化任务 | 执行脚本、发送通知等 |

---

## AI 技能系统

### 功能说明

AI 技能系统是对 MCP 工具的高级封装：
- 技能可以绑定多个工具，实现链式调用
- 支持技能分类管理
- 内置常用技能，支持用户自定义

### 数据库初始化

执行 `src/main/resources/sql/skill.sql` 创建表结构。

### 技能分类

| 分类 | 代码 | 说明 |
|------|------|------|
| 搜索类 | search | 网络搜索、文档搜索等 |
| 数据类 | data | 天气查询、股票查询、数据库查询等 |
| 系统类 | system | 文件操作、邮件发送、任务调度等 |
| AI类 | ai | 对话、内容分析、翻译等 |
| 自定义 | custom | 用户自定义技能 |

### 内置技能

| 技能编码 | 名称 | 分类 | 说明 |
|---------|------|------|------|
| web_search | 网络搜索 | search | 在互联网上搜索信息 |
| ai_chat | AI 对话 | ai | 与 AI 进行对话 |
| weather_query | 天气查询 | data | 查询城市天气 |
| send_email | 发送邮件 | system | 发送邮件通知 |
| file_operations | 文件操作 | system | 读写本地文件 |

### 技能管理接口

#### 1. 获取所有技能

```
GET /api/skill/list
```

**响应:**
```json
[
    {
        "id": 1,
        "code": "weather_query",
        "name": "天气查询",
        "description": "查询指定城市的天气信息",
        "category": "data",
        "icon": "cloud",
        "enabled": false,
        "isBuiltin": true
    }
]
```

#### 2. 获取内置技能

```
GET /api/skill/builtin
```

#### 3. 获取技能分类

```
GET /api/skill/categories
```

**响应:**
```json
["search", "data", "system", "ai", "custom"]
```

#### 4. 获取技能详情

```
GET /api/skill/{id}
```

#### 5. 添加技能

```
POST /api/skill
Content-Type: application/json
```

**请求体:**
```json
{
    "code": "my_skill",
    "name": "我的技能",
    "description": "自定义技能描述",
    "category": "custom",
    "icon": "star",
    "enabled": true
}
```

#### 6. 更新技能

```
PUT /api/skill/{id}
Content-Type: application/json
```

#### 7. 删除技能

```
DELETE /api/skill/{id}
```

注意：内置技能不允许删除。

### 技能-工具映射

#### 1. 绑定工具到技能

```
POST /api/skill/{skillId}/tools/{toolId}?order=0&required=true
```

**参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| order | Integer | 调用顺序，数字越小越先执行 |
| required | Boolean | 是否必须执行 |

#### 2. 解绑工具

```
DELETE /api/skill/{skillId}/tools/{toolId}
```

#### 3. 获取技能关联的工具

```
GET /api/skill/{id}/tools
```

### 技能执行

#### 执行技能

```
POST /api/skill/{code}/execute
Content-Type: application/json
```

**请求体:**
```json
{
    "city": "北京"
}
```

**响应:**
```json
{
    "success": true,
    "skillCode": "weather_query",
    "result": {"temperature": 25, "humidity": 60, "weather": "晴"},
    "toolSteps": [
        {
            "toolName": "weather_query_tool",
            "success": true,
            "result": "{...}",
            "durationMs": 150
        }
    ],
    "totalDurationMs": 200
}
```

### 链式调用

当一个技能绑定多个工具时，会按 `order` 顺序依次执行：

1. 第一个工具的输入参数来自用户请求
2. 后续工具可以获取前一个工具的执行结果
3. 如果某个必须的工具执行失败，整个技能执行失败

---

## 日程管理系统

### 功能说明

日程管理系统支持：
- 收到邮件自动解析日程信息（AI提取时间、地点、事件）
- 文件 + 数据库双重存储
- 定时汇总和提醒（每天20:00汇总，每天08:00提醒）
- SSE 实时推送新日程给前端

### 数据库初始化

执行 `src/main/resources/sql/schedule_event.sql` 创建表结构。

### 日程查询接口

#### 1. 获取所有日程

```
GET /api/schedule/list
```

**响应:**
```json
[
    {
        "id": 1,
        "title": "项目评审会议",
        "description": "Q1项目评审",
        "eventTime": "2026-03-28T14:00:00",
        "eventDate": "2026-03-28",
        "location": "会议室A",
        "sourceEmail": "manager@example.com",
        "reminderStatus": "pending",
        "summaryStatus": "pending",
        "reminderEnabled": true,
        "status": "pending",
        "createTime": "2026-03-27T10:00:00"
    }
]
```

#### 2. 获取今日日程

```
GET /api/schedule/today
```

#### 3. 获取明日日程

```
GET /api/schedule/tomorrow
```

#### 4. 获取指定日期日程

```
GET /api/schedule/date/2026-03-28
```

#### 5. 获取日程详情

```
GET /api/schedule/{id}
```

### 日程管理接口

#### 1. 添加日程

```
POST /api/schedule
Content-Type: application/json
```

`eventTime` 支持以下两种格式：

- `2026-03-28T14:00:00`
- `2026-03-28 14:00:00`

**请求体:**
```json
{
    "title": "项目评审会议",
    "description": "Q1项目评审会议",
    "eventTime": "2026-03-28T14:00:00",
    "location": "会议室A",
    "reminderEnabled": true
}
```

#### 2. 更新日程

```
PUT /api/schedule/{id}
Content-Type: application/json
```

#### 3. 删除日程

```
DELETE /api/schedule/{id}
```

#### 4. 标记日程完成

```
PUT /api/schedule/{id}/complete
```

#### 5. 取消日程

```
PUT /api/schedule/{id}/cancel
```

### 邮件解析接口

#### 1. 从邮件提取日程

```
POST /api/schedule/parse-email
Content-Type: application/json
```

**请求体:**
```json
{
    "subject": "会议通知",
    "from": "manager@example.com",
    "content": "请于明天下午2点参加项目评审会议，地点在会议室A"
}
```

**响应:**
```json
{
    "title": "项目评审会议",
    "description": "请于明天下午2点参加项目评审会议，地点在会议室A",
    "eventTime": "2026-03-28T14:00:00",
    "location": "会议室A",
    "reminderEnabled": true
}
```

#### 2. 解析并保存日程

```
POST /api/schedule/parse-and-save
Content-Type: application/json
```

解析成功后自动保存到数据库，并通过 SSE 推送给前端。

### SSE 实时推送接口

#### 获取日程推送流

```
GET /api/schedule/stream
Accept: text/event-stream
```

**响应格式:**

连接建立后会先返回一个连接事件：
```
event: connected
data: schedule-stream-ready
```

连接保持期间会定时返回心跳：
```
event: ping
data: keep-alive
```

新日程创建时推送：
```
event: created
data: {"id":2,"title":"新会议","eventTime":"2026-03-29T10:00:00","status":"pending"}
```

日程更新、完成、取消时推送：
```
event: updated
data: {"id":1,"status":"completed"}
```

日程删除时推送：
```
event: deleted
data: {"id":1,"title":"项目评审会议","eventTime":"2026-03-29T10:00:00"}
```

**前端使用示例:**
```javascript
const eventSource = new EventSource('/api/schedule/stream');

eventSource.addEventListener('connected', (e) => {
    console.log('SSE 已连接:', e.data);
});

// 接收新日程
eventSource.addEventListener('created', (e) => {
    const schedule = JSON.parse(e.data);
    console.log('新日程:', schedule);
});

// 接收更新
eventSource.addEventListener('updated', (e) => {
    const schedule = JSON.parse(e.data);
    console.log('日程更新:', schedule);
});

// 接收删除
eventSource.addEventListener('deleted', (e) => {
    const schedule = JSON.parse(e.data);
    console.log('日程删除:', schedule);
});

eventSource.onerror = (e) => {
    console.error('SSE 连接错误');
};
```

### 配置说明

```yaml
app:
  schedule:
    enabled: true                        # 是否启用日程功能
    storage-path: ./data/schedules       # 日程文件存储路径
    user-email: user@example.com         # 接收汇总/提醒的邮箱
    daily-summary-cron: "0 0 20 * * ?"   # 每天20:00发送汇总
    morning-reminder-cron: "0 0 8 * * ?" # 每天08:00发送提醒
```

### 日程文件管理接口

日程以Markdown文件形式存储，数据库只存储文件路径。文件命名格式：`schedule-yyyy-MM-dd.md`。

#### 1. 获取所有日程文件列表

```
GET /api/schedule/files
```

获取存储目录下所有的日程文件列表。

**响应:**
```json
[
  "schedule-2026-03-29.md",
  "schedule-2026-03-30.md",
  "schedule-2026-03-31.md"
]
```

#### 2. 按日期读取日程文件

```
GET /api/schedule/file/date/{date}
```

获取指定日期的日程文件内容和数据库中的日程列表。

**路径参数:**
- `date`: 日期，格式 `yyyy-MM-dd`，如 `2026-03-29`

**响应:**
```json
{
  "date": "2026-03-29",
  "fileName": "schedule-2026-03-29.md",
  "content": "# 日程安排 - 2026-03-29\n\n## 会议\n\n- **时间**: 2026-03-29 14:00\n- **地点**: 会议室A\n- **描述**: 项目进度汇报\n\n---\n",
  "events": [
    {
      "id": 1,
      "title": "会议",
      "eventTime": "2026-03-29T14:00:00",
      "eventDate": "2026-03-29",
      "location": "会议室A",
      "filePath": "./data/schedules/schedule-2026-03-29.md"
    }
  ]
}
```

#### 3. 按文件名读取日程文件

```
GET /api/schedule/file/{fileName}
```

根据文件名读取日程文件内容。

**路径参数:**
- `fileName`: 文件名，如 `schedule-2026-03-29.md`

**响应:**
```json
{
  "fileName": "schedule-2026-03-29.md",
  "date": "2026-03-29",
  "content": "# 日程安排 - 2026-03-29\n\n..."
}
```

#### 4. 按日程ID读取文件

```
GET /api/schedule/{id}/file
```

根据日程事件ID获取对应的日程文件内容。

**路径参数:**
- `id`: 日程事件ID

**响应:**
```json
{
  "event": {
    "id": 1,
    "title": "会议",
    "eventTime": "2026-03-29T14:00:00",
    "eventDate": "2026-03-29",
    "location": "会议室A",
    "filePath": "./data/schedules/schedule-2026-03-29.md"
  },
  "fileName": "schedule-2026-03-29.md",
  "content": "# 日程安排 - 2026-03-29\n\n..."
}
```

**注意:** 如果日程事件没有 `filePath` 或 `eventDate`，则 `fileName` 为 null，`content` 为空字符串。

### 文件存储说明

日程文件存储在配置的目录下，文件内容为Markdown格式：

```
./data/schedules/
├── schedule-2026-03-29.md
├── schedule-2026-03-30.md
└── schedule-2026-03-31.md
```

**文件内容格式示例:**
```markdown
# 日程安排 - 2026-03-29

**更新时间**: 2026-03-29 10:30

---

## 1. 项目会议

- **时间**: 2026-03-29 14:00
- **地点**: 会议室A
- **描述**: 项目进度汇报
- **来源邮件**: sender@example.com

---

## 2. 客户电话

- **时间**: 2026-03-29 16:00
- **描述**: 确认合作细节

---
```

---

## 文件上传管理系统

### 功能说明

文件上传管理系统支持：
- 上传文件后自动进行AI分析
- 提取文件内容并判断重要程度(1-10级)
- 自动生成标签、情感分析和摘要
- 文件内容持久化存储到数据库
- 支持按重要程度筛选查询

### 数据库初始化

执行 `src/main/resources/sql/document.sql` 创建表结构。

### 支持的文件类型

| 类型 | 扩展名 | 说明 |
|------|--------|------|
| 文本文件 | txt, md | 支持内容提取和AI分析 |
| 文档文件 | pdf, doc, docx | 预留扩展，暂不支持内容提取 |

### 文件上传接口

#### 1. 上传文件

```
POST /api/file/upload
Content-Type: multipart/form-data
```

**请求参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | MultipartFile | 是 | 上传的文件 |

**响应:**
```json
{
    "success": true,
    "message": "文件上传并分析成功",
    "data": {
        "id": 1,
        "fileName": "test.txt",
        "fileType": "txt",
        "fileSize": 289,
        "importance": 9,
        "tags": "Spring Boot,Java,Best Practices,Configuration",
        "sentiment": "POSITIVE",
        "summary": "文件内容摘要...",
        "status": "analyzed",
        "createTime": "2026-03-28T11:37:30"
    }
}
```

**处理流程:**
1. 验证文件类型和大小（最大10MB）
2. 保存文件到本地存储目录
3. 提取文件内容（文本文件直接读取）
4. 调用AI分析服务分析内容
5. 保存分析结果到数据库

#### 2. 获取文件列表

```
GET /api/file/list
```

**响应:**
```json
{
    "success": true,
    "data": [
        {
            "id": 1,
            "fileName": "test.txt",
            "fileType": "txt",
            "fileSize": 289,
            "importance": 9,
            "tags": "Spring Boot,Java",
            "sentiment": "POSITIVE",
            "summary": "摘要内容",
            "status": "analyzed",
            "createTime": "2026-03-28 11:37:30"
        }
    ],
    "total": 1
}
```

#### 3. 获取文件详情

```
GET /api/file/{id}
```

**响应:**
```json
{
    "success": true,
    "data": {
        "id": 1,
        "fileName": "test.txt",
        "filePath": "./data/documents/xxx.txt",
        "fileType": "txt",
        "fileSize": 289,
        "content": "文件完整内容...",
        "importance": 9,
        "tags": "Spring Boot,Java",
        "sentiment": "POSITIVE",
        "summary": "摘要内容",
        "status": "analyzed",
        "createTime": "2026-03-28 11:37:30",
        "updateTime": "2026-03-28 11:37:35"
    }
}
```

#### 4. 删除文件

```
DELETE /api/file/{id}
```

**响应:**
```json
{
    "success": true,
    "message": "文档删除成功"
}
```

删除时会同时删除本地存储的文件和数据库记录。

#### 5. 按重要程度搜索

```
GET /api/file/search?minImportance=5&maxImportance=10
```

**参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| minImportance | Integer | 最小重要程度（可选） |
| maxImportance | Integer | 最大重要程度（可选） |

**响应:**
```json
{
    "success": true,
    "data": [
        {
            "id": 1,
            "fileName": "important.txt",
            "importance": 9,
            ...
        }
    ],
    "total": 1
}
```

### AI分析结果说明

#### 重要程度(importance)

AI根据内容自动评估重要程度，范围1-10：

| 级别 | 范围 | 描述 |
|------|------|------|
| 不重要 | 1-2 | 闲聊、寒暄、日常琐事 |
| 较低 | 3-4 | 一般信息、日常记录 |
| 中等 | 5-6 | 有一定价值的信息 |
| 较高 | 7-8 | 重要信息、值得关注的文档 |
| 非常重要 | 9-10 | 关键信息、紧急事项、核心文档 |

#### 情感倾向(sentiment)

| 值 | 说明 |
|------|------|
| POSITIVE | 正面、积极 |
| NEGATIVE | 负面、消极 |
| NEUTRAL | 中性 |

#### 处理状态(status)

| 值 | 说明 |
|------|------|
| pending | 等待处理 |
| analyzed | 分析完成 |
| failed | 分析失败 |

### 配置说明

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB        # 单文件最大大小
      max-request-size: 20MB     # 请求最大大小

app:
  file:
    upload-dir: ./data/documents     # 文件存储目录
    allowed-types: txt,md,pdf,doc,docx  # 允许的文件类型
```

### 使用示例

**上传文件:**
```bash
curl -X POST http://localhost:8000/api/file/upload \
  -F "file=@test.txt"
```

**获取重要文件列表:**
```bash
curl "http://localhost:8000/api/file/search?minImportance=8"
```

**删除文件:**
```bash
curl -X DELETE http://localhost:8000/api/file/1
```

---

## 笔记管理系统

### 功能说明

笔记管理系统支持：
- 笔记内容存储为 .md 文件，数据库只存储元数据和文件路径
- 支持 Markdown 编辑和实时预览
- AI 智能总结笔记内容
- 置顶管理、标签分类、搜索功能

### 数据库初始化

执行 `src/main/resources/sql/note.sql` 创建表结构。

### 存储方式

笔记内容以 `.md` 文件形式存储在 `data/notes/` 目录：
```
data/
└── notes/
    ├── 1.md
    ├── 2.md
    └── ...
```

### 笔记管理接口

#### 1. 获取所有笔记

```
GET /api/note/list
```

**响应:**
```json
[
    {
        "id": 1,
        "title": "学习笔记",
        "filePath": "data/notes/1.md",
        "content": "# 学习笔记\n\n今天学习了...",
        "tags": "学习,技术",
        "aiSummary": "这是一篇关于技术的学习笔记",
        "isPinned": true,
        "createTime": "2026-03-28T10:00:00",
        "updateTime": "2026-03-28T12:00:00"
    }
]
```

#### 2. 获取笔记详情

```
GET /api/note/{id}
```

返回笔记详情，包含从文件读取的内容。

#### 3. 创建笔记

```
POST /api/note
Content-Type: application/json
```

**请求体:**
```json
{
    "title": "新笔记",
    "content": "# 标题\n\n内容...",
    "tags": "标签1,标签2"
}
```

**处理流程:**
1. 插入数据库获取 ID
2. 在 `data/notes/` 创建 `{id}.md` 文件
3. 将内容写入文件
4. 更新数据库中的文件路径

#### 4. 更新笔记

```
PUT /api/note/{id}
Content-Type: application/json
```

**请求体:**
```json
{
    "title": "更新后的标题",
    "content": "更新后的内容",
    "tags": "新标签"
}
```

更新时会同时更新文件内容和数据库记录。

#### 5. 删除笔记

```
DELETE /api/note/{id}
```

删除时会同时删除文件和数据库记录。

#### 6. 切换置顶状态

```
PUT /api/note/{id}/pin
```

**响应:** 返回更新后的笔记对象

#### 7. AI 总结笔记

```
POST /api/note/{id}/summarize
```

调用 AI 分析笔记内容并生成摘要。

**响应:** 返回 AI 生成的摘要文本

#### 8. 搜索笔记

```
GET /api/note/search?keyword=技术
```

在标题、内容、标签中搜索关键词。

---

## 代码片段管理

### 功能说明

代码片段管理支持：
- 收藏常用代码片段
- 多语言支持（Java, Python, TypeScript, Vue, SQL 等）
- 一键复制、搜索功能
- AI 解释代码功能

### 数据库初始化

执行 `src/main/resources/sql/note.sql` 中的 `code_snippet` 表创建语句。

### 支持的语言

| 语言 | 代码 |
|------|------|
| Java | java |
| Python | python |
| JavaScript | javascript |
| TypeScript | typescript |
| Vue | vue |
| HTML | html |
| CSS | css |
| SQL | sql |
| Go | go |
| Rust | rust |
| C++ | cpp |
| Shell | shell |
| JSON | json |
| YAML | yaml |
| Markdown | markdown |

### 代码片段接口

#### 1. 获取所有片段

```
GET /api/snippet/list
```

**响应:**
```json
[
    {
        "id": 1,
        "title": "Java 单例模式",
        "code": "public class Singleton {...}",
        "language": "java",
        "description": "线程安全的单例模式实现",
        "tags": "设计模式,Java",
        "createTime": "2026-03-28T10:00:00",
        "updateTime": "2026-03-28T10:00:00"
    }
]
```

#### 2. 获取片段详情

```
GET /api/snippet/{id}
```

#### 3. 创建片段

```
POST /api/snippet
Content-Type: application/json
```

**请求体:**
```json
{
    "title": "Java 单例模式",
    "code": "public class Singleton {\n    private static volatile Singleton instance;\n    ...",
    "language": "java",
    "description": "双重检查锁定的单例模式",
    "tags": "设计模式,Java,单例"
}
```

#### 4. 更新片段

```
PUT /api/snippet/{id}
Content-Type: application/json
```

#### 5. 删除片段

```
DELETE /api/snippet/{id}
```

#### 6. 搜索片段

```
GET /api/snippet/search?keyword=单例
```

在标题、代码、描述、标签中搜索。

#### 7. AI 解释代码

```
POST /api/snippet/{id}/explain
```

调用 AI 解释代码的功能和用法。

**响应:** 返回 AI 生成的代码解释

---

## AI 代码生成

### 功能说明

AI 代码生成支持：
- 根据描述自动生成代码
- 支持多种代码类型（Entity, Service, Controller, Vue 组件等）
- 代码审查、代码语言转换
- 可选保存到项目文件

### 支持的代码类型

| 类型 | 代码 | 说明 |
|------|------|------|
| Entity | ENTITY | JPA/MyBatis 实体类 |
| Service | SERVICE | Spring Service 类 |
| Controller | CONTROLLER | Spring REST Controller |
| Mapper | MAPPER | MyBatis Mapper 接口 |
| DTO | DTO | 数据传输对象 |
| Vue | VUE | Vue 3 组件 |
| TypeScript | TYPESCRIPT | TypeScript 类型定义 |
| SQL | SQL | SQL 建表语句 |

### 代码生成接口

#### 1. 获取支持的代码类型

```
GET /api/code/types
```

**响应:**
```json
["ENTITY", "SERVICE", "CONTROLLER", "MAPPER", "DTO", "VUE", "TYPESCRIPT", "SQL"]
```

#### 2. 生成代码

```
POST /api/code/generate
Content-Type: application/json
```

**请求体:**
```json
{
    "type": "ENTITY",
    "name": "User",
    "description": "用户实体",
    "fields": [
        {"name": "id", "type": "Long"},
        {"name": "username", "type": "String"},
        {"name": "email", "type": "String"},
        {"name": "createTime", "type": "LocalDateTime"}
    ],
    "packageName": "com.example.demo.entity"
}
```

**响应:**
```json
{
    "code": "package com.example.demo.entity;\n\n@Data\n@TableName(\"user\")\npublic class User {\n    @TableId(type = IdType.AUTO)\n    private Long id;\n    private String username;\n    private String email;\n    private LocalDateTime createTime;\n}",
    "language": "java",
    "fileName": "User.java",
    "suggestedPath": "src/main/java/com/example/demo/entity/"
}
```

#### 3. 保存代码到文件

```
POST /api/code/save
Content-Type: application/json
```

**请求体:**
```json
{
    "code": "package com.example.demo.entity;...",
    "fileName": "User.java",
    "subDir": "entity"
}
```

#### 4. 代码审查

```
POST /api/code/review
Content-Type: application/json
```

**请求体:**
```json
{
    "code": "public void process() {...}",
    "language": "java"
}
```

**响应:** 返回 AI 的代码审查意见

#### 5. 代码转换

```
POST /api/code/convert
Content-Type: application/json
```

**请求体:**
```json
{
    "code": "public class User {...}",
    "fromLanguage": "java",
    "toLanguage": "typescript"
}
```

**响应:** 返回转换后的代码

#### 6. 分析项目结构

```
GET /api/code/analyze?path=src/main/java
```

返回项目目录结构和文件统计信息。

---

## 聊天历史管理

### 功能说明

聊天历史管理支持：
- 会话历史持久化存储
- 支持创建、切换、删除会话
- 自动保存对话记录
- 按会话隔离上下文

### 数据库初始化

执行 `src/main/resources/sql/chat_session.sql` 创建表结构。

### 会话管理接口

#### 1. 创建会话

```
POST /api/chat/history/session?title=新对话
```

**响应:**
```json
{
    "id": 1,
    "title": "新对话",
    "messageCount": 0,
    "createTime": "2026-03-28T10:00:00",
    "lastMessageTime": null
}
```

#### 2. 获取所有会话

```
GET /api/chat/history/sessions
```

**响应:**
```json
[
    {
        "id": 1,
        "title": "技术讨论",
        "messageCount": 10,
        "createTime": "2026-03-27T10:00:00",
        "lastMessageTime": "2026-03-28T09:30:00"
    }
]
```

#### 3. 获取会话详情

```
GET /api/chat/history/session/{id}
```

#### 4. 更新会话标题

```
PUT /api/chat/history/session/{id}/title?title=新标题
```

#### 5. 删除会话

```
DELETE /api/chat/history/session/{id}
```

删除会话及其所有消息。

### 消息管理接口

#### 1. 获取会话消息

```
GET /api/chat/history/session/{sessionId}/messages
```

**响应:**
```json
[
    {
        "id": 1,
        "sessionId": 1,
        "role": "user",
        "content": "你好",
        "model": null,
        "createTime": "2026-03-28T10:00:00"
    },
    {
        "id": 2,
        "sessionId": 1,
        "role": "assistant",
        "content": "你好！有什么可以帮助你的吗？",
        "model": "qwen-plus",
        "createTime": "2026-03-28T10:00:05"
    }
]
```

#### 2. 添加消息

```
POST /api/chat/history/session/{sessionId}/message?role=user&content=你好
```

#### 3. 清空会话消息

```
DELETE /api/chat/history/session/{sessionId}/messages
```

清空指定会话的所有消息，但保留会话本身。

---

## 21. 多模型管理系统

多模型管理功能支持在前端动态配置多个 AI 模型，基于 OpenAI 兼容协议，支持通义千问、DeepSeek、GLM、Claude 等模型。API Key 采用 AES-256 加密存储，确保安全。

### 21.1 获取模型列表

```
GET /api/model/list
```

获取所有已配置的模型列表，不返回完整的 API Key（只返回预览）。

**响应:**
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
            "apiKeyPreview": "sk-xxxx****",
            "isDefault": true,
            "enabled": true,
            "createTime": "2026-03-28T10:00:00",
            "updateTime": "2026-03-28T10:00:00"
        }
    ]
}
```

### 21.2 获取模型详情

```
GET /api/model/{id}
```

获取单个模型的配置详情。

### 21.3 创建模型

```
POST /api/model
```

创建新的模型配置。API Key 会自动加密存储。

**请求体:**
```json
{
    "name": "DeepSeek",
    "provider": "deepseek",
    "baseUrl": "https://api.deepseek.com/v1",
    "modelName": "deepseek-chat",
    "apiKey": "sk-your-api-key"
}
```

**参数说明:**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| name | String | 是 | 模型显示名称 |
| provider | String | 是 | 提供商标识 |
| baseUrl | String | 是 | API 请求地址 |
| modelName | String | 是 | 模型名称 |
| apiKey | String | 是 | API Key（会加密存储） |

**支持的提供商:**

| 提供商 | provider值 | 默认 baseUrl |
|--------|------------|--------------|
| OpenAI | openai | https://api.openai.com/v1 |
| 阿里云（通义千问） | aliyun | https://dashscope.aliyuncs.com/compatible-mode/v1 |
| DeepSeek | deepseek | https://api.deepseek.com/v1 |
| Anthropic（Claude） | anthropic | https://api.anthropic.com/v1 |
| 智谱AI（GLM） | glm | https://open.bigmodel.cn/api/paas/v4 |
| Moonshot（Kimi） | moonshot | https://api.moonshot.cn/v1 |
| 自定义 | other | 需手动输入 |

**响应:**
```json
{
    "success": true,
    "data": {
        "id": 2,
        "name": "DeepSeek",
        "provider": "deepseek",
        "baseUrl": "https://api.deepseek.com/v1",
        "modelName": "deepseek-chat",
        "apiKeyPreview": "sk-your****",
        "isDefault": false,
        "enabled": true,
        "createTime": "2026-03-28T11:00:00"
    }
}
```

### 21.4 更新模型

```
PUT /api/model/{id}
```

更新模型配置。如果传入新的 API Key，会自动加密；如果不传则保留原有加密后的 Key。

**请求体:**
```json
{
    "name": "DeepSeek V3",
    "provider": "deepseek",
    "baseUrl": "https://api.deepseek.com/v1",
    "modelName": "deepseek-chat",
    "apiKey": "sk-new-api-key"
}
```

### 21.5 删除模型

```
DELETE /api/model/{id}
```

删除模型配置，同时清理模型缓存。

**响应:**
```json
{
    "success": true,
    "data": null
}
```

### 21.6 启用/禁用模型

```
PUT /api/model/{id}/toggle
```

切换模型的启用状态。禁用的模型不会被加载到缓存。

**响应:**
```json
{
    "success": true,
    "data": {
        "id": 1,
        "enabled": false,
        ...
    }
}
```

### 21.7 设置默认模型

```
PUT /api/model/{id}/default
```

将指定模型设置为默认模型。系统会优先使用默认模型进行对话。

**响应:**
```json
{
    "success": true,
    "data": {
        "id": 2,
        "isDefault": true,
        ...
    }
}
```

### 21.8 测试模型连接

```
POST /api/model/test
```

测试模型配置是否能正常连接，不保存到数据库。

注意：该接口会调用 `modelManager.testConnection(...)`，当前实现通常返回 `HTTP 200`，并把“连接成功”或“连接失败”写入 `data` 字段。

**请求体:**
```json
{
    "baseUrl": "https://api.deepseek.com/v1",
    "modelName": "deepseek-chat",
    "apiKey": "sk-your-api-key"
}
```

**响应:**
```json
{
    "success": true,
    "data": "连接成功: OK"
}
```

如果连接失败：
```json
{
    "success": true,
    "data": "连接失败: Invalid API key"
}
```

### 21.9 获取提供商列表

```
GET /api/model/providers
```

获取系统支持的 AI 模型提供商列表及其默认 API 地址。

**响应:**
```json
{
    "success": true,
    "data": [
        {
            "value": "openai",
            "label": "OpenAI",
            "baseUrl": "https://api.openai.com/v1"
        },
        {
            "value": "aliyun",
            "label": "阿里云（通义千问）",
            "baseUrl": "https://dashscope.aliyuncs.com/compatible-mode/v1"
        },
        {
            "value": "deepseek",
            "label": "DeepSeek",
            "baseUrl": "https://api.deepseek.com/v1"
        },
        ...
    ]
}
```

---

## 22. 带会话记忆的聊天接口

### 22.1 带会话记忆的普通聊天

```
GET /api/chat/complete/session
```

与普通聊天类似，但会自动保存用户消息和AI响应到历史记录，并提取记忆存储。

**参数:**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message | String | 是 | 用户消息 |
| sessionId | Long | 是 | 会话ID |
| model | Long | 否 | 指定模型ID，不传则使用默认模型 |

**响应:** `String` - 纯文本响应

**示例:**
```bash
GET http://localhost:8000/api/chat/complete/session?message=你好&sessionId=1
```

### 22.2 带会话记忆的流式聊天

```
GET /api/chat/stream/session
```

SSE流式返回，同时保存消息到历史和记忆。

调试脚本或命令行调用时建议显式传 `Accept: text/event-stream`。

**参数:**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message | String | 是 | 用户消息 |
| sessionId | Long | 是 | 会话ID |
| model | Long | 否 | 指定模型ID，不传则使用默认模型 |

**响应:** `text/event-stream` - SSE流式响应

### 22.3 带会话记忆的流式JSON聊天

```
GET /api/chat/stream/session/json
```

SSE JSON格式流式返回，最后包含完整元数据。

调试脚本或命令行调用时建议显式传 `Accept: text/event-stream`。

**参数:**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message | String | 是 | 用户消息 |
| sessionId | Long | 是 | 会话ID |
| model | Long | 否 | 指定模型ID，不传则使用默认模型 |

**响应:** `text/event-stream` - SSE流式响应，JSON格式包装

### 22.4 测试流式响应

```
GET /api/chat/stream/test
```

测试接口，每个token都会记录时间戳，用于诊断流式响应问题。

**参数:**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| message | String | 是 | 用户消息 |

---

## 23. 记忆管理接口

### 23.1 提取并存储记忆

```
POST /memory/extract-store
```

从对话中提取重要信息并存储到向量数据库。

**请求体:**
```json
{
    "sessionId": "session-123",
    "recentMessages": [
        "user: 我叫张三",
        "assistant: 你好张三，很高兴认识你！"
    ]
}
```

**响应:**
```json
{
    "id": 1,
    "sessionId": "session-123",
    "content": "用户名字是张三",
    "category": "personal",
    "importance": 3,
    "createTime": "2026-03-29T10:00:00"
}
```

### 23.2 搜索记忆

```
GET /memory/search
```

根据查询内容搜索相关记忆。

**参数:**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| query | String | 是 | 查询内容 |
| topK | int | 否 | 返回数量，默认5 |
| sessionId | String | 否 | 会话ID过滤 |
| category | String | 否 | 分类过滤 |

**响应:**
```json
[
    {
        "id": 1,
        "content": "用户名字是张三",
        "score": 0.95,
        "category": "personal"
    }
]
```

---

## 24. 邮箱配置扩展接口

### 24.1 获取启用的邮箱配置

```
GET /api/email/config/enabled
```

获取所有已启用的邮箱配置列表。

**响应:**
```json
[
    {
        "id": 1,
        "email": "user@qq.com",
        "host": "imap.qq.com",
        "enabled": true,
        ...
    }
]
```

### 24.2 获取单个邮箱配置

```
GET /api/email/config/{id}
```

根据ID获取单个邮箱配置详情。

### 24.3 重载邮箱监听

```
POST /api/email/listener/reload
```

重新加载所有邮箱监听，应用最新的配置变更。

---

## 25. MCP工具验证接口

### 25.1 验证工具配置

```
POST /api/mcp/tools/{id}/validate
```

验证指定工具的配置是否有效。

**响应:**
```json
{
    "valid": true,
    "toolName": "weather_query",
    "toolType": "HTTP_API"
}
```

---

## 26. 技能扩展接口

### 26.1 获取启用的技能

```
GET /api/skill/enabled
```

获取所有已启用的技能列表。

### 26.2 按分类获取技能

```
GET /api/skill/category/{category}
```

根据分类获取技能列表。

**示例:**
```bash
GET http://localhost:8000/api/skill/category/search
```

### 26.3 测试技能执行

```
POST /api/skill/{id}/test
```

测试指定技能的执行，不保存执行记录。

**请求体:**
```json
{
    "param1": "value1"
}
```

---

## 27. 日程扩展接口

### 27.1 获取最近日程

```
GET /api/schedule/latest
```

获取最近的日程列表，默认返回5条。

**参数:**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| limit | int | 否 | 返回数量，默认5 |

### 27.2 按日期范围查询

```
GET /api/schedule/range
```

获取指定日期范围内的日程。

**参数:**

| 参数名 | 类型 | 必填 | 描述 |
|--------|------|------|------|
| startDate | String | 是 | 开始日期，格式：2026-03-29 |
| endDate | String | 是 | 结束日期，格式：2026-03-31 |

**示例:**
```bash
GET http://localhost:8000/api/schedule/range?startDate=2026-03-29&endDate=2026-03-31
```

### 27.3 取消日程

```
PUT /api/schedule/{id}/cancel
```

将日程状态标记为已取消。

---

## 28. 代码片段扩展接口

### 28.1 按语言查询片段

```
GET /api/snippet/language/{language}
```

根据编程语言查询代码片段。

**示例:**
```bash
GET http://localhost:8000/api/snippet/language/Java
GET http://localhost:8000/api/snippet/language/Python
```

**响应:**
```json
{
    "success": true,
    "data": [
        {
            "id": 1,
            "title": "Hello World",
            "language": "Java",
            "code": "public class HelloWorld { ... }",
            ...
        }
    ]
}
```

---

## 更新日志

| 日期 | 版本 | 更新内容 |
|------|------|----------|
| 2026-03-29 | 1.10.0 | 新增日程文件管理接口，支持按日期/文件名/日程ID读取Markdown日程文件内容 |
| 2026-03-29 | 1.9.0 | 新增带会话记忆的聊天接口、记忆管理接口、MCP工具验证接口、邮箱配置扩展接口、技能扩展接口、日程扩展接口、代码片段按语言查询接口 |
| 2026-03-28 | 1.8.0 | 新增多模型管理系统，支持动态配置多个AI模型、API Key加密存储、提供商预设配置、模型连接测试 |
| 2026-03-28 | 1.7.0 | 新增笔记系统（文件存储）、代码片段收藏、AI代码生成、聊天历史管理、Markdown渲染支持 |
| 2026-03-28 | 1.6.0 | 新增文件上传管理系统，支持文件上传、AI自动分析重要程度、标签提取、情感分析、摘要生成，数据持久化存储 |
| 2026-03-27 | 1.5.0 | 新增日程管理系统，支持邮件自动解析日程、SSE实时推送、定时汇总提醒、多线程/虚拟线程支持 |
| 2026-03-27 | 1.4.0 | 新增AI技能系统，支持技能分类、工具绑定、链式调用，内置5个常用技能 |
| 2026-03-27 | 1.3.0 | 新增MCP工具管理功能，支持数据库存储工具配置，AI自动调用工具 |
| 2026-03-26 | 1.2.0 | 新增邮件监听功能，支持配置多个邮箱监听 |
| 2026-03-26 | 1.1.0 | 新增网络搜索功能，支持Serper/Tavily/Bing搜索引擎 |
| 2026-03-26 | 1.0.0 | 初始版本，包含基础聊天、流式响应、结构化输出、向量化等功能 |
---

## 接口测试报告

**测试日期**: 2026-03-28
**测试环境**: Windows 11, localhost:8000

### 测试统计

| 类别 | 通过 | 失败 | 待测 | 通过率 |
|------|------|------|------|--------|
| 聊天接口 | 4 | 0 | 0 | 100% |
| 内容分析 | 1 | 0 | 0 | 100% |
| 向量化接口 | 0 | 1 | 1 | 0% |
| 搜索接口 | 4 | 0 | 0 | 100% |
| 邮箱配置 | 6 | 0 | 5 | 55% |
| MCP工具 | 10 | 0 | 0 | 100% |
| MCP Agent | 3 | 0 | 0 | 100% |
| 技能接口 | 14 | 0 | 0 | 100% |
| 日程管理 | 15 | 0 | 2 | 88% |
| 文件管理 | 5 | 0 | 0 | 100% |
| 笔记管理 | 7 | 0 | 0 | 100% |
| 代码片段 | 7 | 0 | 0 | 100% |
| 代码生成 | 5 | 0 | 0 | 100% |
| 聊天历史 | 8 | 0 | 0 | 100% |
| 模型管理 | 8 | 0 | 0 | 100% |
| **总计** | **97** | **1** | **8** | **92%** |

### 失败的接口及原因

| 接口 | 错误类型 | 可能原因 | 修复状态 |
|------|----------|----------|----------|
| /api/embedding | TIMEOUT(504) | Ollama服务未启动或连接超时 | 需启动Ollama |

### 修复记录 (2026-03-28)

1. **代码生成接口** (`/api/code/generate`)
   - 问题：当 type 参数为 null 时抛出 NullPointerException
   - 修复：添加参数校验，检查 type 和 name 是否为空
   - 状态：✅ 已通过

2. **MCP工具添加接口** (`/api/mcp/tools`)
   - 问题：createTime 和 updateTime 字段未设置；toolType需要使用displayName
   - 修复：在 add 方法中添加时间字段设置；JSON中使用"HTTP API"或"本地脚本"
   - 状态：✅ 已通过

3. **技能导入接口** (`/api/skill/import`)
   - 问题：缺少参数校验和时间字段设置
   - 修复：添加 code/name 参数校验，设置 createTime/updateTime
   - 状态：✅ 已通过

4. **日程解析邮件接口** (`/api/schedule/parse-email`, `/api/schedule/parse-and-save`)
   - 问题：接口未实现
   - 修复：新增接口实现，使用 AI 从邮件内容提取日程信息
   - 状态：✅ 已通过

5. **笔记创建接口** (`/api/note`)
   - 问题：content字段被MyBatis Plus尝试插入数据库但表中不存在
   - 修复：添加 `@TableField(exist = false)` 注解排除content字段
   - 状态：✅ 已通过

6. **笔记置顶接口** (`/api/note/{id}/pin`)
   - 问题：同上，content字段问题
   - 状态：✅ 已通过

7. **流式聊天接口** (`/api/chat/stream`, `/api/chat/stream/json`)
   - 问题：测试时需要正确的Accept请求头
   - 说明：使用 `Accept: text/event-stream` 头测试
   - 状态：✅ 已通过

### 待测试接口

以下接口因需要特定条件或数据而未测试：
- 向量化接口（需要启动Ollama服务）
- 邮箱监听启停（需要真实邮箱配置）
- 测试邮箱连接（需要真实邮箱配置）
- SSE推送流（需要特殊客户端处理）

### 建议

1. **启动 Ollama**: 如需向量化功能，请启动 Ollama 服务
