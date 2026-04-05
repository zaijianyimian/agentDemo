# AI Agent Demo

一个基于 Spring Boot 3、LangChain4j、MyBatis-Plus、Vue 3 和 Naive UI 的单用户 AI 工作台项目。当前代码覆盖认证、多模型管理、聊天与记忆、联网搜索、知识库 RAG、文件/笔记/代码片段管理、MCP 工具与技能、邮件监听、日程与定时任务、统一收件箱、报告生成以及受控自治扫描等能力。

> 状态快照（2026-04-05）
>
> - 前端 `frontend` 目录执行 `npm run build` 通过。
> - 后端执行 `.\gradlew.bat test` 失败，当前代码缺少 `src/main/java/com/example/demo/service/DataArchiveService.java`，导致 [`SystemSettingsController.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/controller/SystemSettingsController.java) 无法编译。
> - 本 README 与 [`API.md`](/D:/javaproject/agentDemo/API.md) 已按当前源码重新整理，不再沿用旧的“已验证通过”结论。

## 项目定位

这个项目更接近“个人 AI 控制台”而不是单一聊天应用：

- 后端提供统一 API、模型切换、向量检索、工具执行、邮件监听、任务调度和自治扫描。
- 前端提供 20 个业务页面，集中管理模型、工具、技能、文件、知识库、收件箱和自治结果。
- 运行数据默认落盘到 `data/`、`generated/` 和 `logs/`，适合本地自托管与二次开发。

## 技术栈

### 后端

- Java 17
- Spring Boot 3.5.12
- Spring Security + JWT Resource Server
- LangChain4j 1.12.2-beta22
- MyBatis-Plus 3.5.10.1
- MySQL 8+
- Qdrant Client 1.17.0
- WebFlux / SSE
- Druid
- Caffeine

### 前端

- Vue 3.5
- TypeScript 5.7
- Vite 6
- Naive UI
- Pinia
- Vue Router
- Axios

## 当前代码结构

```text
agentDemo/
├─ src/main/java/com/example/demo
│  ├─ config/            # 安全、缓存、LangChain4j、Qdrant、WebMvc 等配置
│  ├─ controller/        # 24 个 REST/SSE 控制器
│  ├─ service/           # 按业务域分包的服务实现
│  ├─ mapper/            # MyBatis-Plus Mapper
│  ├─ entity/            # 数据实体
│  ├─ dto/               # 请求/响应 DTO
│  └─ memory/            # 记忆提取模型
├─ src/main/resources
│  ├─ application.example.yaml
│  ├─ skills.yaml
│  └─ sql/
│     ├─ agent.sql
│     ├─ migration_20260331_face_auth_non_destructive.sql
│     ├─ search_history.sql
│     └─ system_settings.sql
├─ frontend/
│  ├─ src/views/         # 20 个页面
│  ├─ src/router/        # 路由与登录守卫
│  ├─ src/services/      # 前端 API 封装
│  └─ src/stores/        # Pinia 状态
├─ data/                 # 文档、笔记、知识库、日程、上传图片等运行数据
├─ generated/            # 自治与报告产物
├─ logs/                 # 日志
├─ API.md
└─ TECHNICAL_DOC.md
```

## 功能概览

### 后端模块

- `auth`：用户名/邮箱登录、JWT 刷新、GitHub OAuth、人脸二次验证。
- `chat`：普通聊天、SSE 流式聊天、结构化响应、会话历史、聊天动作。
- `memory`：从最近对话抽取记忆并写入向量库。
- `search`：Serper / Tavily / Bing 搜索、历史记录、兴趣画像。
- `knowledge`：知识库 CRUD、文档上传、分块、向量化、RAG 检索。
- `file` / `note` / `code`：文件分析、笔记文件化存储、代码片段与代码生成。
- `mcp` / `skill`：MCP 工具注册、执行、自动同步，技能加载、绑定与执行。
- `email` / `schedule` / `task`：邮箱配置、监听、网络检查，日程流推送，定时任务调度。
- `inbox` / `report` / `autonomy` / `personal`：统一收件箱、日报周报、自治扫描与草稿、个人备份与模板任务。

### 前端页面

当前路由定义在 [`frontend/src/router/index.ts`](/D:/javaproject/agentDemo/frontend/src/router/index.ts)，共 20 个页面：

| 路由 | 页面 |
| --- | --- |
| `/login` | 登录 |
| `/oauth/github/callback` | GitHub 登录回调 |
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

## 已知差异与风险

- 当前工作区缺少 `DataArchiveService`，后端无法编译；修复该文件或同步删除 [`SystemSettingsController.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/controller/SystemSettingsController.java) 中相关依赖前，后端不能启动。
- [`SecurityConfig.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/config/SecurityConfig.java) 仍保留 `/api/auth/captcha/puzzle` 与 `/api/auth/captcha/puzzle/verify` 白名单，但 [`AuthController.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/controller/AuthController.java) 已没有对应实现。
- 会话聊天当前始终使用“当前启用模型”。前端历史代码曾透传 `model` 参数，但后端实现不会按请求切换模型。
- 部分控制器直接返回字符串、列表或 `ResponseEntity`，并没有统一走 `ApiResponse<T>`；接口联调应以控制器签名为准。
- 文本提取并不覆盖所有上传类型。`file` 与 `knowledge` 模块会接受 `pdf/doc/docx`，但当前真正提取文本的只有 `txt` 和 `md`。

## 快速开始

### 1. 环境要求

- JDK 17+
- MySQL 8+
- Node.js 18+
- Qdrant
  - REST 端口通常为 `6333`
  - gRPC / 客户端端口当前配置示例为 `6334`
- 可选：Ollama（embedding）
- 可选：Serper / Tavily / Bing 搜索 API Key
- 可选：SMTP / IMAP 邮箱账号

### 2. 初始化数据库

先创建数据库：

```sql
CREATE DATABASE agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后依次执行：

- [`src/main/resources/sql/agent.sql`](/D:/javaproject/agentDemo/src/main/resources/sql/agent.sql)
- [`src/main/resources/sql/migration_20260331_face_auth_non_destructive.sql`](/D:/javaproject/agentDemo/src/main/resources/sql/migration_20260331_face_auth_non_destructive.sql)
- [`src/main/resources/sql/search_history.sql`](/D:/javaproject/agentDemo/src/main/resources/sql/search_history.sql)
- [`src/main/resources/sql/system_settings.sql`](/D:/javaproject/agentDemo/src/main/resources/sql/system_settings.sql)

### 3. 配置后端

仓库提供的是 [`application.example.yaml`](/D:/javaproject/agentDemo/src/main/resources/application.example.yaml)。如果本地还没有 `application.yaml`，先复制一份再修改。

至少需要确认这些配置段：

- `spring.datasource.*`
- `langchain4j.open-ai.chat-model.*`
- `langchain4j.ollama.embedding-model.*`
- `app.qdrant.*`
- `app.memory.*`
- `app.search.*`
- `app.mail.*`
- `app.schedule.*`
- `app.file.*`
- `app.security.*`
- `app.mcp.auto-sync.*`
- `app.skills.*`
- `app.autonomy.*`

安全相关说明：

- `app.security.jwt-secret` 至少 32 字节。
- `app.security.data-secret` 默认跟随 `JWT_SECRET`，用于敏感字段加密。
- GitHub OAuth 需要配置 `app.security.github.*`。

### 4. 修复当前后端编译阻塞

在启动后端前，先处理当前代码中的编译问题：

- 恢复 `src/main/java/com/example/demo/service/DataArchiveService.java`，或
- 移除 [`SystemSettingsController.java`](/D:/javaproject/agentDemo/src/main/java/com/example/demo/controller/SystemSettingsController.java) 中对该服务的依赖与相关导入导出接口。

当前源码状态下，直接执行 `.\gradlew.bat test` 会失败。

### 5. 启动后端

Windows：

```bash
gradlew.bat bootRun
```

Linux / macOS：

```bash
./gradlew bootRun
```

默认地址：`http://localhost:8000`

### 6. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:3000`

[`frontend/vite.config.ts`](/D:/javaproject/agentDemo/frontend/vite.config.ts) 已配置 `/api` 代理，并对 `/stream` 请求补充了 SSE 相关头。

### 7. Docker

仓库根目录的 [`Dockerfile`](/D:/javaproject/agentDemo/Dockerfile) 只打包后端服务，不包含前端构建产物。若要容器化完整系统，需要另外处理前端静态资源部署。

## 鉴权与接口约定

- 除以下公开接口外，其余接口默认都需要 Bearer Token：
  - `POST /api/auth/register`
  - `POST /api/auth/login/password`
  - `POST /api/auth/login/email/send-code`
  - `POST /api/auth/login/email`
  - `POST /api/auth/token/refresh`
  - `POST /api/auth/face/verify-login`
  - `GET /api/auth/oauth/github/authorize`
  - `POST /api/auth/oauth/github/exchange`
  - `GET /api/settings/proxy`
- 登录成功可能直接返回 `accessToken` / `refreshToken`，也可能返回 `requiresSecondFactor=true` 和 `preAuthToken`，此时需要继续调用人脸二验接口。
- SSE 接口包括聊天、搜索、日程和 MCP Agent；不同接口的事件格式并不完全统一，详见 [`API.md`](/D:/javaproject/agentDemo/API.md)。
- 常规控制器常用 `ApiResponse<T>`，但邮件、工具、技能、文件、聊天等模块存在直接返回字符串、原始列表或 `ResponseEntity` 的实现。

## 运行时目录

- `data/documents`：文件上传与分析文档
- `data/knowledge`：知识库原始文件
- `data/notes`：笔记 Markdown 文件
- `data/schedules`：按日期落盘的日程文件
- `data/uploads`：图片上传目录（Logo 等）
- `generated/autonomy`：自治扫描、验证、草稿产物
- `generated/reports`：日报周报 Markdown 文件
- `logs`：日志文件

## 开发与验证

### 已执行

- `frontend`: `npm run build` 通过

### 未通过

- 根目录: `.\gradlew.bat test`
  - 失败原因：缺少 `DataArchiveService`

## 文档入口

- 接口清单与请求体参考：[`API.md`](/D:/javaproject/agentDemo/API.md)
- 较详细的历史技术说明：[`TECHNICAL_DOC.md`](/D:/javaproject/agentDemo/TECHNICAL_DOC.md)
