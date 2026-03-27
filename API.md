# AI Agent Demo 接口文档

## 基本信息

- **Base URL**: `http://localhost:8000/api`
- **编码**: UTF-8
- **数据格式**: JSON

---

## 接口列表

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 普通聊天 | GET | `/chat/complete` | 返回完整响应文本 |
| 流式聊天 | GET | `/chat/stream` | SSE方式流式返回 |
| 流式聊天(JSON) | GET | `/chat/stream/json` | SSE方式JSON格式返回 |
| 结构化聊天 | GET | `/chat/structured` | 返回含元数据的完整响应 |
| 内容分析 | GET | `/analyze` | 分析文本的重要程度、标签等 |
| 文本向量化 | GET | `/embedding` | 返回文本的向量数组 |
| 文本向量化(完整) | GET | `/embedding/full` | 返回完整的向量化响应 |
| 网络搜索 | GET | `/search` | 执行网络搜索 |
| 搜索+AI总结 | GET | `/search/summary` | 搜索并返回AI总结 |
| 带搜索聊天 | GET | `/search/chat` | 先搜索再回答(结构化) |
| 带搜索流式聊天 | GET | `/search/chat/stream` | 先搜索再流式回答 |
| 邮箱配置列表 | GET | `/email/config/list` | 获取所有邮箱配置 |
| 添加邮箱配置 | POST | `/email/config` | 添加邮箱配置 |
| 更新邮箱配置 | PUT | `/email/config` | 更新邮箱配置 |
| 删除邮箱配置 | DELETE | `/email/config/{id}` | 删除邮箱配置 |
| 启动邮件监听 | POST | `/email/listener/start/{id}` | 启动指定邮箱监听 |
| 停止邮件监听 | POST | `/email/listener/stop/{id}` | 停止指定邮箱监听 |
| 监听状态 | GET | `/email/listener/status` | 获取监听状态 |
| 邮箱服务器模板 | GET | `/email/templates` | 获取常用邮箱服务器配置 |
| MCP工具列表 | GET | `/mcp/tools` | 获取所有MCP工具 |
| MCP启用工具 | GET | `/mcp/tools/enabled` | 获取启用的MCP工具 |
| MCP工具详情 | GET | `/mcp/tools/{id}` | 获取单个MCP工具 |
| 添加MCP工具 | POST | `/mcp/tools` | 添加新MCP工具 |
| 更新MCP工具 | PUT | `/mcp/tools/{id}` | 更新MCP工具 |
| 删除MCP工具 | DELETE | `/mcp/tools/{id}` | 删除MCP工具 |
| 切换工具状态 | PUT | `/mcp/tools/{id}/toggle` | 启用/禁用工具 |
| 执行MCP工具 | POST | `/mcp/tools/{name}/execute` | 执行指定工具 |
| 测试MCP工具 | POST | `/mcp/tools/{id}/test` | 测试工具执行 |
| MCP Agent对话 | GET/POST | `/mcp/agent/chat` | AI自动调用工具对话 |
| MCP Agent流式 | GET | `/mcp/agent/chat/stream` | AI流式对话(SSE) |
| MCP Agent带记忆 | GET | `/mcp/agent/chat/{sessionId}` | 带会话记忆对话 |
| 技能列表 | GET | `/skill/list` | 获取所有技能 |
| 内置技能 | GET | `/skill/builtin` | 获取内置技能 |
| 技能分类 | GET | `/skill/categories` | 获取技能分类 |
| 技能详情 | GET | `/skill/{id}` | 获取单个技能 |
| 添加技能 | POST | `/skill` | 添加新技能 |
| 更新技能 | PUT | `/skill/{id}` | 更新技能 |
| 删除技能 | DELETE | `/skill/{id}` | 删除技能 |
| 绑定工具 | POST | `/skill/{skillId}/tools/{toolId}` | 绑定工具到技能 |
| 执行技能 | POST | `/skill/{code}/execute` | 执行技能 |

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