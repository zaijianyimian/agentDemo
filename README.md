# AI Agent Demo

一个基于 **Spring Boot 3 + LangChain4j + Vue 3** 的全栈 AI Agent 项目，覆盖聊天、记忆、搜索、RAG、工具编排、技能系统、邮件监听、日程管理、定时任务、报告生成与受控自治等能力。

## 项目定位

本项目面向“个人 AI 工作台”场景：
- 后端提供多模块 API、模型管理、工具执行与任务调度。
- 前端提供统一控制台，集中管理模型、知识库、任务、技能、收件箱与报告。
- 运行时数据落盘到本地目录，便于自托管和二次开发。

## 技术栈

### 后端
- Java 17
- Spring Boot 3.5.12
- LangChain4j 1.12.2-beta22
- MyBatis-Plus 3.5.10.1
- MySQL 8+
- Qdrant Client 1.17.0
- WebFlux (SSE 流式响应)
- Druid 数据源

### 前端
- Vue 3 + TypeScript
- Vite
- Naive UI
- Pinia
- Vue Router
- Axios

## 目录结构

```text
agentDemo/
├─ src/main/java/com/example/demo
│  ├─ config/          # Spring/AI/Qdrant/MCP/线程池等配置
│  ├─ controller/      # REST API 控制器
│  ├─ service/         # 业务实现（按领域分包）
│  ├─ mapper/          # MyBatis-Plus Mapper
│  ├─ entity/          # 数据实体
│  ├─ dto/             # 接口返回与传输对象
│  └─ memory/          # 记忆提取相关模型
├─ src/main/resources
│  ├─ application.yaml
│  ├─ skills.yaml
│  └─ sql/
│     ├─ agent.sql
│     ├─ search_history.sql
│     └─ system_settings.sql
├─ frontend/           # Vue 前端工程
├─ data/               # 运行时数据（文档/日程等）
├─ generated/          # 生成产物目录（自治报告/草稿等）
└─ logs/               # 运行日志
```

## 后端模块概览

控制器分组（`src/main/java/com/example/demo/controller`）：
- `/api/auth`
- `/api/chat`、`/api/chat/history`、`/api/chat/action`
- `/api/model`
- `/api/memory`
- `/api/search`
- `/api/knowledge`
- `/api/mcp/tools`、`/api/mcp/agent`
- `/api/skill`
- `/api/file`
- `/api/note`
- `/api/snippet`
- `/api/schedule`
- `/api/email`
- `/api/task`
- `/api/report`
- `/api/inbox`
- `/api/personal`
- `/api/autonomy`
- `/api/settings`
- `/api/embedding`、`/api/embedding/full`、`/api/analyze`

服务分组（`src/main/java/com/example/demo/service`）：
- `auth`：注册登录、JWT、邮箱验证码、GitHub OAuth
- `chat`：对话、会话历史、聊天动作
- `memory`：记忆提取与向量检索
- `search`：联网搜索、历史、兴趣分析
- `knowledge`：知识库与 RAG
- `mcp` / `tool` / `skill`：工具注册、技能绑定、执行
- `email` / `schedule` / `task`：邮件监听、日程、计划任务
- `report` / `inbox` / `autonomy`：报告、统一收件箱、项目自治
- `personal`：单用户效率洞察、模板任务、备份导入导出

## 前端页面（20 个路由）

当前路由定义在 `frontend/src/router/index.ts`：

| 路由 | 页面 |
|---|---|
| `/oauth/github/callback` | GitHub 登录回调 |
| `/login` | 登录页 |
| `/` | Dashboard |
| `/inbox` | 统一收件箱 |
| `/autonomy` | 自治中心 |
| `/reports` | 日报周报 |
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
| `/personal` | 单用户中心 |

## 快速开始

### 1) 环境要求
- JDK 17+
- MySQL 8+
- Node.js 18+
- Qdrant（REST 6333，gRPC 6334）
- 可选：Ollama（本地 embedding）

### 2) 初始化数据库

先创建数据库：

```sql
CREATE DATABASE agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

再执行脚本：
- `src/main/resources/sql/agent.sql`
- `src/main/resources/sql/search_history.sql`
- `src/main/resources/sql/system_settings.sql`

### 3) 配置后端

编辑 `src/main/resources/application.yaml`，至少确认：
- `spring.datasource.*`
- `langchain4j.open-ai.chat-model.*`
- `app.qdrant.*`
- `app.search.*`
- `app.mail.*`
- `app.security.*`（JWT 与登录安全配置）

注意：仓库中的配置可能包含本地敏感值，部署前请替换为你自己的配置。

### 4) 启动后端

Windows:

```bash
gradlew.bat bootRun
```

Linux/macOS:

```bash
./gradlew bootRun
```

后端默认地址：`http://localhost:8000`

### 5) 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：`http://localhost:3000`

Vite 代理会将 `/api` 转发到 `http://localhost:8000`（已配置 SSE 相关头）。

### 6) 首次登录

系统已启用鉴权，除登录相关接口外其余 API 默认都需要 Bearer Token。
- 首次使用可在登录页注册账号，或调用 `POST /api/auth/register`。
- 登录后前端会自动保存 `accessToken`/`refreshToken` 并在过期时尝试刷新。
- 如启用 GitHub OAuth，请先在 `app.security.github.*` 中完成配置。

## 常用命令

后端：

```bash
# 测试
./gradlew test

# 构建
./gradlew build
```

前端：

```bash
cd frontend

# 开发
npm run dev

# 生产构建
npm run build

# 预览
npm run preview
```

## 关键配置说明

`application.yaml` 中的核心配置段：
- `app.memory`：向量记忆检索参数
- `app.qdrant`：向量库连接
- `app.search`：联网搜索引擎及 key
- `app.schedule`：日程存储和提醒策略
- `app.file`：文档上传目录与类型
- `app.autonomy`：项目自治扫描、验证与产物输出
- `app.security`：JWT、刷新令牌、邮箱验证码与 GitHub OAuth

## 运行数据目录

- `data/documents`：文档上传与处理数据
- `data/schedules`：日程文件
- `generated/autonomy`：自治扫描/验证/草稿产物
- `logs`：运行日志

## 开发建议

- 大改前先执行 `./gradlew test` 与 `npm run build`。
- 接口定义可参考 `API.md`（比 README 更细）。
- 新增模块时优先沿用现有分层：`controller -> service -> mapper -> entity/dto`。
- 对外配置优先放 `application.yaml` + `properties` 类，避免硬编码。

## 当前状态说明

- 后端测试可通过（`./gradlew test`）。
- 前端可正常构建（`npm run build`）。
- 项目包含较多本地运行数据与产物目录，提交前建议清理无关文件。
