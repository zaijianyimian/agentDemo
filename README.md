# AI Agent Demo

基于 Spring Boot + LangChain4j 的智能 AI Agent 应用，集成大语言模型、向量数据库、网络搜索和邮件监听等功能。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | JDK 版本 |
| Spring Boot | 3.5.12 | 基础框架 |
| LangChain4j | 1.12.2-beta22 | AI 应用框架 |
| Qdrant | 1.17.0 | 向量数据库 |
| MySQL | 8.x | 关系型数据库 |
| MyBatis Plus | 3.5.10.1 | ORM 框架 |

## 功能特性

### 1. AI 聊天
- 普通聊天：返回完整响应
- 流式聊天：SSE 实时返回
- 结构化聊天：带元数据的完整响应

### 2. 内容分析
- 重要程度评估 (1-5级)
- 内容标签提取
- 情感分析 (正面/负面/中性)
- 自动摘要生成

### 3. 文本向量化
- 支持 Ollama 本地模型
- 向量缓存优化
- 完整/简化响应格式

### 4. 网络搜索
- 支持 Serper / Tavily / Bing 搜索引擎
- 搜索结果 AI 总结
- 带搜索的智能问答

### 5. 记忆管理
- Qdrant 向量存储
- 语义相似度检索
- 记忆提取与存储

### 6. 邮件监听
- 多邮箱配置管理
- 实时新邮件监听
- 可扩展邮件处理器

### 7. MCP 工具管理
- 工具配置数据库存储，动态管理
- 支持 HTTP API 和本地脚本两种工具类型
- AI 自动识别并调用合适的工具
- 无需重启应用即可增删改工具

### 8. AI 技能系统
- 技能是对 MCP 工具的高级封装，支持分类和链式调用
- 内置常用技能：网络搜索、AI对话、天气查询、邮件发送、文件操作
- 支持用户自定义技能，绑定多个工具实现复杂流程

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Qdrant 向量数据库
- Ollama (可选，用于本地向量模型)

### 配置步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd agentDemo
```

2. **创建数据库**
```sql
CREATE DATABASE agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

3. **初始化表结构**

执行以下 SQL 脚本创建数据表：
- `src/main/resources/sql/email_config.sql` - 邮件配置表
- `src/main/resources/sql/mcp_tool.sql` - MCP 工具配置表
- `src/main/resources/sql/skill.sql` - AI 技能表

4. **修改配置文件**

编辑 `src/main/resources/application.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/agent?serverTimezone=Asia/Shanghai
    username: your-username
    password: your-password

langchain4j:
  open-ai:
    chat-model:
      api-key: your-api-key        # 通义千问 API Key
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen-plus
  ollama:
    embedding-model:
      base-url: http://localhost:11434
      model-name: nomic-embed-text:latest

app:
  qdrant:
    host: your-qdrant-host
    port: 6334
```

5. **启动应用**
```bash
./gradlew bootRun
```

应用默认运行在 `http://localhost:8000`

## API 接口

### 聊天接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 普通聊天 | GET | `/api/chat/complete` | 返回完整响应 |
| 流式聊天 | GET | `/api/chat/stream` | SSE 流式返回 |
| 流式聊天(JSON) | GET | `/api/chat/stream/json` | SSE JSON格式 |
| 结构化聊天 | GET | `/api/chat/structured` | 带元数据响应 |

### 分析接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 内容分析 | GET | `/api/analyze` | 分析重要度、标签、情感 |
| 文本向量化 | GET | `/api/embedding` | 返回向量数组 |
| 文本向量化(完整) | GET | `/api/embedding/full` | 完整向量化响应 |

### 搜索接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 网络搜索 | GET | `/api/search` | 执行网络搜索 |
| 搜索+AI总结 | GET | `/api/search/summary` | 搜索并总结 |
| 带搜索聊天 | GET | `/api/search/chat` | 搜索后回答 |
| 带搜索流式聊天 | GET | `/api/search/chat/stream` | 搜索后流式回答 |

### 邮件接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 配置列表 | GET | `/api/email/config/list` | 获取所有配置 |
| 添加配置 | POST | `/api/email/config` | 添加邮箱配置 |
| 更新配置 | PUT | `/api/email/config` | 更新邮箱配置 |
| 删除配置 | DELETE | `/api/email/config/{id}` | 删除配置 |
| 启动监听 | POST | `/api/email/listener/start/{id}` | 启动监听 |
| 停止监听 | POST | `/api/email/listener/stop/{id}` | 停止监听 |
| 监听状态 | GET | `/api/email/listener/status` | 获取状态 |
| 服务器模板 | GET | `/api/email/templates` | 邮箱配置模板 |

### MCP 工具管理接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 工具列表 | GET | `/api/mcp/tools` | 获取所有工具 |
| 启用工具 | GET | `/api/mcp/tools/enabled` | 获取启用的工具 |
| 工具详情 | GET | `/api/mcp/tools/{id}` | 获取单个工具 |
| 添加工具 | POST | `/api/mcp/tools` | 添加新工具 |
| 更新工具 | PUT | `/api/mcp/tools/{id}` | 更新工具 |
| 删除工具 | DELETE | `/api/mcp/tools/{id}` | 删除工具 |
| 启用/禁用 | PUT | `/api/mcp/tools/{id}/toggle` | 切换启用状态 |
| 执行工具 | POST | `/api/mcp/tools/{name}/execute` | 执行指定工具 |
| 测试工具 | POST | `/api/mcp/tools/{id}/test` | 测试工具执行 |

### MCP Agent 对话接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 普通对话 | GET/POST | `/api/mcp/agent/chat` | AI 自动调用工具 |
| 流式对话 | GET | `/api/mcp/agent/chat/stream` | SSE 流式响应 |
| 带记忆对话 | GET | `/api/mcp/agent/chat/{sessionId}` | 带会话记忆 |

### AI 技能管理接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 技能列表 | GET | `/api/skill/list` | 获取所有技能 |
| 内置技能 | GET | `/api/skill/builtin` | 获取内置技能 |
| 技能分类 | GET | `/api/skill/categories` | 获取技能分类 |
| 技能详情 | GET | `/api/skill/{id}` | 获取单个技能 |
| 添加技能 | POST | `/api/skill` | 添加新技能 |
| 更新技能 | PUT | `/api/skill/{id}` | 更新技能 |
| 删除技能 | DELETE | `/api/skill/{id}` | 删除技能 |
| 绑定工具 | POST | `/api/skill/{skillId}/tools/{toolId}` | 绑定工具到技能 |
| 执行技能 | POST | `/api/skill/{code}/execute` | 执行技能 |

详细 API 文档请参考 [API.md](./API.md)

## 项目结构

```
src/main/java/com/example/demo/
├── config/                 # 配置类
│   ├── AiConfiguration.java
│   ├── McpAgentConfiguration.java
│   ├── QdrantCollectionInitializer.java
│   ├── SkillInitializer.java
│   └── WebMvcConfig.java
├── controller/             # 控制器
│   ├── ChatController.java
│   ├── EmailController.java
│   ├── EmbeddingController.java
│   ├── McpAgentController.java
│   ├── McpToolController.java
│   ├── MemoryController.java
│   ├── SearchController.java
│   └── SkillController.java
├── dto/                    # 数据传输对象
│   ├── ChatResponse.java
│   ├── ContentAnalysis.java
│   ├── EmailMessage.java
│   ├── SearchResult.java
│   ├── SkillExecutionResult.java
│   └── ToolExecutionResult.java
├── entity/                 # 实体类
│   ├── EmailConfig.java
│   ├── McpTool.java
│   ├── Skill.java
│   ├── SkillToolMapping.java
│   └── ToolType.java
├── mapper/                 # MyBatis Mapper
│   ├── EmailConfigMapper.java
│   ├── McpToolMapper.java
│   ├── SkillMapper.java
│   └── SkillToolMappingMapper.java
├── memory/                 # 记忆模块
│   ├── MemoryRecord.java
│   └── MemoryExtractor.java
├── properties/             # 配置属性
│   ├── AppMemoryProperties.java
│   ├── OpenAiChatProperties.java
│   ├── OllamaEmbeddingProperties.java
│   ├── QdrantProperties.java
│   └── SearchProperties.java
├── service/                # 服务层
│   ├── ContentAnalysisService.java
│   ├── EmailListenerService.java
│   ├── EmbeddingCacheService.java
│   ├── EnhancedChatService.java
│   ├── McpAgentService.java
│   ├── McpToolAdapter.java
│   ├── McpToolService.java
│   ├── MemoryApplicationService.java
│   ├── MemoryStoreService.java
│   ├── QwenChatService.java
│   ├── SkillExecutor.java
│   ├── SkillService.java
│   ├── ToolCacheRefreshEvent.java
│   ├── WebSearchService.java
│   └── tool/               # 工具执行器
│       ├── ToolExecutor.java
│       ├── HttpApiToolExecutor.java
│       └── LocalScriptToolExecutor.java
└── DemoApplication.java    # 启动类
```

## 配置说明

### AI 模型配置

支持 OpenAI 兼容接口（如通义千问）和 Ollama 本地模型：

```yaml
langchain4j:
  open-ai:
    chat-model:
      api-key: your-api-key
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      model-name: qwen-plus
  ollama:
    embedding-model:
      base-url: http://localhost:11434
      model-name: nomic-embed-text:latest
```

### 向量数据库配置

```yaml
app:
  memory:
    collection-name: agent_memory
    top-k: 5
    min-score: 0.5
  qdrant:
    host: localhost
    port: 6334
```

### 搜索引擎配置

```yaml
app:
  search:
    enabled: true
    engine: serper           # serper / tavily / bing
    api-key: your-api-key
    max-results: 3
```

| 搜索引擎 | 免费额度 | 官网 |
|---------|---------|------|
| Serper | 2500次/月 | https://serper.dev |
| Tavily | 1000次/月 | https://tavily.com |
| Bing | 按需付费 | https://azure.microsoft.com |

## 邮件监听配置

### 常用邮箱服务器

| 邮箱 | IMAP服务器 | 端口 | SSL |
|------|-----------|------|-----|
| QQ邮箱 | imap.qq.com | 993 | 是 |
| 163邮箱 | imap.163.com | 993 | 是 |
| 126邮箱 | imap.126.com | 993 | 是 |
| Gmail | imap.gmail.com | 993 | 是 |
| Outlook | outlook.office365.com | 993 | 是 |

### 获取授权码

| 邮箱 | 获取方式 |
|------|---------|
| QQ邮箱 | 设置 → 账户 → POP3/IMAP/SMTP服务 → 生成授权码 |
| 163邮箱 | 设置 → POP3/SMTP/IMAP → 开启服务 → 获取授权码 |
| Gmail | Google账户 → 安全性 → 两步验证 → 应用专用密码 |

## MCP 工具配置

### 数据库表结构

执行 `src/main/resources/sql/mcp_tool.sql` 创建工具配置表。

### 工具类型

| 类型 | 说明 | config 字段示例 |
|------|------|----------------|
| http_api | 外部 HTTP API | `{"url": "https://api.example.com", "method": "GET", "timeout": 30}` |
| local_script | 本地脚本 | `{"scriptPath": "./scripts/tool.sh", "timeout": 10}` |

### 添加工具示例

```json
POST /api/mcp/tools
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

### AI 自动调用

启用工具后，在与 `/api/mcp/agent/chat` 对话时，AI 会根据工具描述自动判断是否需要调用：

```
用户：北京今天天气怎么样？
AI：[自动调用 weather_query 工具] 北京今天天气晴朗，温度 25°C...
```

### 环境变量支持

HTTP API 工具配置支持环境变量占位符：

```json
{
  "url": "https://api.example.com/search",
  "headers": {
    "Authorization": "Bearer ${API_KEY}"
  }
}
```

## AI 技能系统

### 数据库表结构

执行 `src/main/resources/sql/skill.sql` 创建技能配置表。

### 技能分类

| 分类 | 代码 | 说明 |
|------|------|------|
| 搜索类 | search | 网络搜索、文档搜索等 |
| 数据类 | data | 天气查询、股票查询等 |
| 系统类 | system | 文件操作、邮件发送等 |
| AI类 | ai | 对话、翻译、内容分析等 |
| 自定义 | custom | 用户自定义技能 |

### 内置技能

系统启动时自动创建以下内置技能：

| 技能编码 | 名称 | 分类 | 说明 |
|---------|------|------|------|
| web_search | 网络搜索 | search | 在互联网上搜索信息 |
| ai_chat | AI 对话 | ai | 与 AI 进行对话 |
| weather_query | 天气查询 | data | 查询城市天气 |
| send_email | 发送邮件 | system | 发送邮件通知 |
| file_operations | 文件操作 | system | 读写本地文件 |

### 技能-工具绑定

技能可以绑定多个工具，实现链式调用：

```bash
# 绑定工具到技能
POST /api/skill/1/tools/1?order=0&required=true

# order: 调用顺序，数字越小越先执行
# required: 是否必须执行成功
```

### 执行技能

```bash
POST /api/skill/weather_query/execute
Content-Type: application/json

{
  "city": "北京"
}
```

响应示例：
```json
{
  "success": true,
  "skillCode": "weather_query",
  "result": {"temperature": 25, "weather": "晴"},
  "totalDurationMs": 200
}
```

## 开发指南

### 构建项目

```bash
./gradlew build
```

### 运行测试

```bash
./gradlew test
```

### 打包部署

```bash
./gradlew bootJar
```

生成的 JAR 包位于 `build/libs/` 目录。

## 许可证

MIT License