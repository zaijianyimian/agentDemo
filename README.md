# AI Agent Demo

一个基于 Spring Boot 3 + LangChain4j + Vue 3 的 AI Agent 全栈示例项目，覆盖聊天、向量记忆、网络搜索、RAG 知识库、MCP 工具、技能编排、邮件监听、日程管理、文件分析、代码生成等能力。

这份 README 已按当前仓库代码重新整理，重点修正了以下历史偏差：

- 数据库初始化脚本不是一堆分散的 SQL 文件，而是以 `src/main/resources/sql/agent.sql` 为主。
- 前端实际有 14 个页面路由，不是 10 个。
- 搜索页是网络搜索与搜索历史分析，不是“向量搜索”页面。
- 系统设置页已经存在，README 之前漏写了。
- 启动端口以代码配置为准：后端 `8000`，前端 `3000`。

## 项目概览

### 后端

- Java 17
- Spring Boot 3.5.12
- LangChain4j 1.12.2-beta22
- MyBatis-Plus 3.5.10.1
- MySQL 8.x
- Qdrant 1.17.0
- WebFlux + SSE 流式输出

### 前端

- Vue 3
- TypeScript
- Vite
- Naive UI
- Pinia
- Vue Router

## 当前实现的核心模块

### 1. AI 聊天

- 普通聊天、结构化聊天、SSE 流式聊天
- 支持按会话保存聊天历史
- 支持结合向量记忆进行多轮对话

对应接口：

- `GET /api/chat/complete`
- `GET /api/chat/structured`
- `GET /api/chat/stream`
- `GET /api/chat/complete/session`
- `GET /api/chat/stream/session`
- `GET /api/chat/history/...`

### 2. 向量记忆

- 对话内容提取为记忆记录
- 基于 Qdrant 做语义检索
- 支持按 `sessionId` 和分类召回

对应接口：

- `POST /api/memory/extract-store`
- `GET /api/memory/search`

### 3. 网络搜索

- 支持 Serper / Tavily / Bing
- 搜索结果可进行 AI 总结
- 记录搜索历史与用户兴趣画像

对应接口：

- `GET /api/search`
- `GET /api/search/summary`
- `GET /api/search/chat`
- `GET /api/search/chat/stream`
- `GET /api/search/history`
- `GET /api/search/interests`

### 4. RAG 知识库

- 多知识库管理
- 文档上传、分块、向量化
- 问答与语义检索

对应接口：

- `GET /api/knowledge/list`
- `POST /api/knowledge`
- `POST /api/knowledge/{baseId}/upload`
- `GET /api/knowledge/{baseId}/query`
- `GET /api/knowledge/{baseId}/search`

### 5. MCP 工具与 Agent

- 数据库存储工具配置
- 支持工具启停、测试、执行
- MCP Agent 可在对话中自动调用工具

对应接口：

- `GET /api/mcp/tools`
- `POST /api/mcp/tools`
- `POST /api/mcp/tools/{name}/execute`
- `GET /api/mcp/agent/chat`
- `GET /api/mcp/agent/chat/stream`

### 6. 技能系统

- 技能作为工具的组合与封装
- 支持 YAML 初始化、数据库管理、执行测试
- 支持技能与工具绑定

对应接口：

- `GET /api/skill/list`
- `POST /api/skill`
- `POST /api/skill/{code}/execute`
- `POST /api/skill/reload`
- `POST /api/skill/import`

### 7. 文件与笔记

- 文件上传后自动做 AI 分析
- 支持图片上传
- Markdown 笔记管理与 AI 总结
- 代码片段收藏与 AI 解释

对应接口：

- `POST /api/file/upload`
- `POST /api/file/upload/image`
- `GET /api/file/list`
- `GET /api/note/list`
- `POST /api/note/{id}/summarize`
- `GET /api/snippet/list`
- `POST /api/snippet/{id}/explain`

### 8. 邮件与日程

- 多邮箱配置与监听管理
- 可测试邮箱连接
- 可从邮件内容提取日程
- 日程数据支持数据库 + Markdown 文件联动

对应接口：

- `GET /api/email/config/list`
- `POST /api/email/listener/start/{id}`
- `POST /api/email/config/{id}/test`
- `GET /api/schedule/list`
- `GET /api/schedule/files`
- `GET /api/schedule/file/date/{date}`
- `POST /api/schedule/parse-and-save`

### 9. 代码生成与系统设置

- AI 生成代码、代码审查、代码转换
- 系统参数、Qdrant、搜索、文件、模型等配置可动态维护

对应接口：

- `POST /api/code/generate`
- `POST /api/code/review`
- `POST /api/code/convert`
- `GET /api/settings`
- `PUT /api/settings/system`
- `PUT /api/settings/search`

## 目录结构

```text
agentDemo/
├─ src/main/java/com/example/demo
│  ├─ config/        # Spring、AI、MCP、Qdrant、线程池等配置
│  ├─ controller/    # REST API
│  ├─ service/       # 业务逻辑
│  ├─ mapper/        # MyBatis-Plus Mapper
│  ├─ entity/        # 数据实体
│  └─ memory/        # 记忆提取相关模型
├─ src/main/resources
│  ├─ application.yaml
│  ├─ skills.yaml
│  └─ sql/
│     ├─ agent.sql
│     ├─ search_history.sql
│     └─ system_settings.sql
├─ frontend/         # Vue 3 前端
├─ data/             # 上传文件、日程等运行时数据
├─ generated/        # 代码生成输出目录
└─ logs/             # 运行日志
```

## 快速开始

### 运行环境

- JDK 17+
- MySQL 8.0+
- Node.js 18+
- Qdrant
- 可选：Ollama（本地 embedding）

### 1. 初始化数据库

先创建数据库：

```sql
CREATE DATABASE agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后执行以下脚本：

- `src/main/resources/sql/agent.sql`
- `src/main/resources/sql/search_history.sql`
- `src/main/resources/sql/system_settings.sql`

说明：

- `agent.sql` 已经合并了大部分核心业务表。
- `search_history.sql` 额外初始化搜索历史与用户兴趣表。
- `system_settings.sql` 初始化系统设置表和默认配置。

### 2. 修改后端配置

编辑 `src/main/resources/application.yaml`，至少确认这些配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/agent?serverTimezone=Asia/Shanghai
    username: your-username
    password: your-password

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

app:
  qdrant:
    host: localhost
    port: 6334
```

建议：

- 不要直接使用仓库里现有的本地敏感配置值。
- 首次运行前先替换数据库、模型、搜索、邮件相关参数。

### 3. 启动后端

Windows:

```bash
gradlew.bat bootRun
```

Linux / macOS:

```bash
./gradlew bootRun
```

默认地址：`http://localhost:8000`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:3000`

前端已通过 Vite 代理将 `/api` 转发到 `http://localhost:8000`。

## 前端页面路由

当前代码中的页面一共有 14 个：

| 路由 | 页面 |
|------|------|
| `/` | 仪表盘 |
| `/models` | 模型配置 |
| `/files` | 文件管理 |
| `/chat` | AI 聊天 |
| `/knowledge` | 知识库 |
| `/tasks` | 定时任务 |
| `/notes` | 笔记 |
| `/snippets` | 代码片段 |
| `/schedule` | 日程管理 |
| `/email` | 邮件配置 |
| `/search` | 网络搜索 |
| `/tools` | 工具管理 |
| `/skills` | 技能管理 |
| `/settings` | 系统设置 |

## 运行时数据说明

- `data/schedules`：日程 Markdown 文件
- `data/documents`：业务配置中的文档目录
- `data/uploads`：图片上传等静态文件
- `generated/`：AI 代码生成保存目录
- `logs/`：运行日志

## 我对 README 的校验结论

原 README 的主要问题不是“项目介绍错了”，而是“细节已经过期”：

- 数据库初始化步骤写成了多个并不存在的 SQL 文件。
- 前端功能页数量与真实路由不一致。
- 页面说明漏掉了 `Settings`。
- 搜索页描述不准确。
- 部分功能表述过于理想化，容易让读者误以为全部已经严格落地。

这版 README 已按当前仓库结构、路由、控制器和配置文件修正。
