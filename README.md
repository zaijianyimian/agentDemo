# AI Agent Demo

基于 Spring Boot + LangChain4j 的智能 AI Agent 应用，集成大语言模型、向量数据库、网络搜索和邮件监听等功能。

## 技术栈

### 后端
| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 | JDK 版本 |
| Spring Boot | 3.5.12 | 基础框架 |
| LangChain4j | 1.12.2-beta22 | AI 应用框架 |
| Qdrant | 1.17.0 | 向量数据库 |
| MySQL | 8.x | 关系型数据库 |
| MyBatis Plus | 3.5.10.1 | ORM 框架 |

### 前端
| 技术 | 版本 | 说明 |
|------|------|------|
| Vue | 3.x | 前端框架 |
| TypeScript | 5.x | 类型支持 |
| Naive UI | 2.x | UI 组件库 |
| Pinia | 2.x | 状态管理 |
| Vue Router | 4.x | 路由管理 |

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
- 邮箱连接测试功能

### 7. MCP 工具管理
- 工具配置数据库存储，动态管理
- 支持 HTTP API 和本地脚本两种工具类型
- AI 自动识别并调用合适的工具
- 无需重启应用即可增删改工具
- **集成 [mcp.so](https://mcp.so/zh) 工具市场**
- 支持 JSON 导入导出工具配置

### 8. AI 技能系统
- 技能是对 MCP 工具的高级封装，支持分类和链式调用
- 内置常用技能：网络搜索、AI对话、天气查询、邮件发送、文件操作
- 支持用户自定义技能，绑定多个工具实现复杂流程
- **集成 [skills.sh](https://skills.sh) 技能市场**
- 支持 YAML 配置自动加载
- 支持 JSON 导入导出技能配置

### 9. 日程管理系统
- 收到邮件自动解析日程（AI提取时间、地点、事件）
- **文件+数据库双重存储**：日程以Markdown文件形式存储，数据库只存路径
- **文件命名格式**：`schedule-yyyy-MM-dd.md`（如 `schedule-2026-03-29.md`）
- **按日期查询**：支持按日期范围查询日程
- **查看文件内容**：点击日程可查看对应的Markdown文件内容
- 每天 20:00 发送日程汇总邮件（需配置SMTP）
- 每天 08:00 发送日程提醒（需配置SMTP）
- SSE 实时推送新日程给前端

### 10. 文件上传与AI分析
- 上传文件后自动进行AI分析
- 自动判断重要程度(1-10级)
- 提取标签、情感分析、生成摘要
- 文件内容持久化存储到数据库
- 支持按重要程度筛选查询

### 11. 多线程/虚拟线程支持
- 自动检测 Java 版本，Java 21+ 使用虚拟线程
- Java 8-20 使用优化的线程池
- 多个专用线程池：邮件处理、工具执行、日程通知

### 12. Vue3 前端界面
- 橙黄色科技感主题设计
- 深色/浅色模式切换
- 10 个功能模块页面
- SSE 流式输出支持
- 响应式布局设计
- Markdown 渲染支持（聊天、笔记）

### 13. 聊天历史管理
- 会话历史持久化存储
- 支持创建、切换、删除会话
- 自动保存对话记录
- 按会话隔离上下文

### 14. Markdown 笔记系统
- 笔记内容存储为 .md 文件，数据库只存元数据
- 支持 Markdown 编辑和实时预览
- AI 智能总结笔记内容
- 置顶管理、标签分类

### 15. 代码片段收藏
- 收藏常用代码片段
- 多语言支持（Java, Python, TypeScript 等）
- 一键复制、搜索功能
- AI 解释代码功能

### 16. AI 代码生成
- 根据描述自动生成代码（Entity/Service/Controller/Vue 组件）
- 代码审查、代码转换
- 可选保存到项目文件

### 17. RAG 知识库系统
- 创建多个知识库，每个知识库独立向量空间
- 上传文档自动分块、向量化存储到 Qdrant
- 支持智能语义检索，返回相关文档片段
- 可配置分块大小和重叠大小

### 18. 定时任务系统
- 支持三种任务类型：技能执行、AI对话、提醒
- 自定义 Cron 表达式配置执行时间
- 任务启用/禁用、手动执行
- 执行历史记录和统计

### 19. 多模型支持
- 在前端动态配置多个 AI 模型
- 支持 OpenAI 兼容协议（通义千问、DeepSeek、GLM、Claude 等）
- API Key 加密存储，安全可靠
- 模型测试连接、设置默认模型

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Node.js 18+ (前端)
- Qdrant 向量数据库
- Ollama (可选，用于本地向量模型)

### 后端配置步骤

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
- `src/main/resources/sql/ai_model_config.sql` - AI 模型配置表
- `src/main/resources/sql/email_config.sql` - 邮件配置表
- `src/main/resources/sql/mcp_tool.sql` - MCP 工具配置表
- `src/main/resources/sql/skill.sql` - AI 技能表
- `src/main/resources/sql/skill_tool_mapping.sql` - 技能工具映射表
- `src/main/resources/sql/schedule_event.sql` - 日程事件表
- `src/main/resources/sql/document.sql` - 文件上传记录表
- `src/main/resources/sql/chat_session.sql` - 聊天会话表
- `src/main/resources/sql/note.sql` - 笔记表
- `src/main/resources/sql/knowledge.sql` - 知识库表
- `src/main/resources/sql/scheduled_task.sql` - 定时任务表

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

5. **启动后端**
```bash
./gradlew bootRun
```

后端默认运行在 `http://localhost:8000`

### 前端配置步骤

1. **安装依赖**
```bash
cd frontend
npm install
```

2. **启动开发服务器**
```bash
npm run dev
```

前端默认运行在 `http://localhost:3000`

## 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| 仪表盘 | `/` | 系统概览、快捷操作 |
| 模型配置 | `/models` | AI模型管理、多模型切换、API Key加密存储 |
| AI 聊天 | `/chat` | 流式对话、MCP Agent对话、Markdown渲染 |
| 知识库 | `/knowledge` | RAG知识库管理、文档上传、智能问答 |
| 定时任务 | `/tasks` | 任务管理、Cron配置、执行历史 |
| 笔记 | `/notes` | Markdown笔记管理、AI总结、实时预览 |
| 代码片段 | `/snippets` | 代码收藏、多语言支持、AI解释 |
| 文件管理 | `/files` | 文件上传、AI分析、按重要性筛选 |
| 日程管理 | `/schedule` | 日程查看、添加、SSE实时推送 |
| 技能管理 | `/skills` | 技能列表、导入导出、技能市场 |
| 工具管理 | `/tools` | MCP工具管理、导入导出、工具市场 |
| 邮箱配置 | `/email` | 邮箱配置、监听状态管理 |
| 向量搜索 | `/search` | 记忆管理、语义搜索 |

## 技能市场集成

### skills.sh 集成

系统已集成 [skills.sh](https://skills.sh) 技能市场：

1. **访问技能市场**
   - 前端 → 技能管理 → 导入技能 → 技能市场
   - 直接访问 https://skills.sh

2. **导入技能**
   - 从技能市场复制 JSON 配置
   - 在前端粘贴导入
   - 或通过 API 导入：
   ```bash
   POST /api/skill/import
   Content-Type: application/json

   {
     "code": "custom_skill",
     "name": "自定义技能",
     "description": "技能描述",
     "category": "custom",
     "icon": "star",
     "enabled": true,
     "tools": []
   }
   ```

3. **导出技能**
   ```bash
   GET /api/skill/{id}/export
   ```

4. **YAML 配置加载**

   在 `src/main/resources/skills.yaml` 中配置技能，启动时自动加载：
   ```yaml
   skills:
     - code: web_search
       name: 网络搜索
       description: 在互联网上搜索相关信息
       category: search
       icon: search
       enabled: true
       tools:
         - name: web_search_tool
           displayName: 网络搜索工具
           toolType: http_api
           config:
             url: https://api.serper.dev/search
   ```

## MCP 工具市场集成

### mcp.so 集成

系统已集成 [mcp.so](https://mcp.so/zh) MCP 工具市场：

1. **访问工具市场**
   - 前端 → 工具管理 → MCP工具市场
   - 直接访问 https://mcp.so/zh

2. **导入工具**
   ```bash
   POST /api/mcp/tools
   Content-Type: application/json

   {
     "name": "weather_query",
     "displayName": "天气查询",
     "description": "查询指定城市的天气信息",
     "toolType": "HTTP_API",
     "config": "{\"url\": \"https://api.weather.com/v1/current\"}",
     "inputSchema": "{\"type\": \"object\", \"properties\": {\"city\": {\"type\": \"string\"}}}",
     "enabled": true
   }
   ```

3. **工具类型**
   - `HTTP_API` - 外部 HTTP API 调用
   - `LOCAL_SCRIPT` - 本地脚本执行

## API 接口

### 聊天接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 普通聊天 | GET | `/api/chat/complete` | 返回完整响应 |
| 流式聊天 | GET | `/api/chat/stream` | SSE 流式返回 |
| 流式聊天(JSON) | GET | `/api/chat/stream/json` | SSE JSON格式 |
| 结构化聊天 | GET | `/api/chat/structured` | 带元数据响应 |
| 带会话记忆聊天 | GET | `/api/chat/complete/session` | 带记忆的完整响应 |
| 带会话记忆流式 | GET | `/api/chat/stream/session` | 带记忆的SSE流式 |
| 带会话记忆JSON | GET | `/api/chat/stream/session/json` | 带记忆的JSON流式 |

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
| 启用的配置 | GET | `/api/email/config/enabled` | 获取启用的配置 |
| 配置详情 | GET | `/api/email/config/{id}` | 获取单个配置 |
| 添加配置 | POST | `/api/email/config` | 添加邮箱配置 |
| 更新配置 | PUT | `/api/email/config` | 更新邮箱配置 |
| 删除配置 | DELETE | `/api/email/config/{id}` | 删除配置 |
| 测试连接 | POST | `/api/email/config/{id}/test` | 测试已保存配置 |
| 测试新配置 | POST | `/api/email/config/test` | 测试未保存配置 |
| 启动监听 | POST | `/api/email/listener/start/{id}` | 启动监听 |
| 停止监听 | POST | `/api/email/listener/stop/{id}` | 停止监听 |
| 重载监听 | POST | `/api/email/listener/reload` | 重载所有监听 |
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
| 验证工具 | POST | `/api/mcp/tools/{id}/validate` | 验证工具配置 |

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
| 启用的技能 | GET | `/api/skill/enabled` | 获取启用的技能 |
| 内置技能 | GET | `/api/skill/builtin` | 获取内置技能 |
| 技能分类 | GET | `/api/skill/categories` | 获取技能分类 |
| 按分类获取 | GET | `/api/skill/category/{category}` | 根据分类获取技能 |
| 技能详情 | GET | `/api/skill/{id}` | 获取单个技能 |
| 添加技能 | POST | `/api/skill` | 添加新技能 |
| 更新技能 | PUT | `/api/skill/{id}` | 更新技能 |
| 删除技能 | DELETE | `/api/skill/{id}` | 删除技能 |
| 绑定工具 | POST | `/api/skill/{skillId}/tools/{toolId}` | 绑定工具到技能 |
| 执行技能 | POST | `/api/skill/{code}/execute` | 执行技能 |
| 测试技能 | POST | `/api/skill/{id}/test` | 测试技能执行 |
| 重新加载 | POST | `/api/skill/reload` | 从YAML重新加载技能 |
| 导入技能 | POST | `/api/skill/import` | JSON导入技能 |
| 导出技能 | GET | `/api/skill/{id}/export` | 导出技能为JSON |

### 日程管理接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 日程列表 | GET | `/api/schedule/list` | 获取所有日程 |
| 最近日程 | GET | `/api/schedule/latest` | 获取最近的日程 |
| 今日日程 | GET | `/api/schedule/today` | 获取今天的日程 |
| 明日日程 | GET | `/api/schedule/tomorrow` | 获取明天的日程 |
| 日期查询 | GET | `/api/schedule/date/{date}` | 获取指定日期日程 |
| 日期范围查询 | GET | `/api/schedule/range` | 获取日期范围内的日程 |
| 添加日程 | POST | `/api/schedule` | 添加日程 |
| 更新日程 | PUT | `/api/schedule/{id}` | 更新日程 |
| 删除日程 | DELETE | `/api/schedule/{id}` | 删除日程 |
| 完成日程 | PUT | `/api/schedule/{id}/complete` | 标记完成 |
| 取消日程 | PUT | `/api/schedule/{id}/cancel` | 取消日程 |
| 解析邮件 | POST | `/api/schedule/parse-email` | 从邮件提取日程 |
| 解析并保存 | POST | `/api/schedule/parse-and-save` | 解析并保存日程 |
| **日程文件列表** | GET | `/api/schedule/files` | **获取所有日程文件列表** |
| **按日期查文件** | GET | `/api/schedule/file/date/{date}` | **按日期查询日程文件内容** |
| **按文件名读取** | GET | `/api/schedule/file/{fileName}` | **按文件名读取日程文件内容** |
| **按ID读文件** | GET | `/api/schedule/{id}/file` | **按日程ID读取文件内容** |

### 文件上传管理接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 上传文件 | POST | `/api/file/upload` | 上传并AI分析 |
| 文件列表 | GET | `/api/file/list` | 获取所有文件 |
| 文件详情 | GET | `/api/file/{id}` | 获取文件详情 |
| 删除文件 | DELETE | `/api/file/{id}` | 删除文件 |
| 按重要性搜索 | GET | `/api/file/search` | 按重要程度查询 |

### 聊天历史管理接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 创建会话 | POST | `/api/chat/history/session` | 创建新会话 |
| 会话列表 | GET | `/api/chat/history/sessions` | 获取所有会话 |
| 会话详情 | GET | `/api/chat/history/session/{id}` | 获取会话详情 |
| 更新标题 | PUT | `/api/chat/history/session/{id}/title` | 更新会话标题 |
| 删除会话 | DELETE | `/api/chat/history/session/{id}` | 删除会话 |
| 会话消息 | GET | `/api/chat/history/session/{sessionId}/messages` | 获取会话消息 |
| 添加消息 | POST | `/api/chat/history/session/{sessionId}/message` | 添加消息 |
| 清空消息 | DELETE | `/api/chat/history/session/{sessionId}/messages` | 清空会话消息 |

### 笔记管理接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 笔记列表 | GET | `/api/note/list` | 获取所有笔记 |
| 笔记详情 | GET | `/api/note/{id}` | 获取笔记详情 |
| 创建笔记 | POST | `/api/note` | 创建笔记 |
| 更新笔记 | PUT | `/api/note/{id}` | 更新笔记 |
| 删除笔记 | DELETE | `/api/note/{id}` | 删除笔记 |
| 切换置顶 | PUT | `/api/note/{id}/pin` | 切换置顶状态 |
| AI 总结 | POST | `/api/note/{id}/summarize` | AI 总结笔记 |
| 搜索笔记 | GET | `/api/note/search` | 搜索笔记 |

### 代码片段管理接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 片段列表 | GET | `/api/snippet/list` | 获取所有片段 |
| 片段详情 | GET | `/api/snippet/{id}` | 获取片段详情 |
| 按语言查询 | GET | `/api/snippet/language/{language}` | 根据语言查询片段 |
| 创建片段 | POST | `/api/snippet` | 创建片段 |
| 更新片段 | PUT | `/api/snippet/{id}` | 更新片段 |
| 删除片段 | DELETE | `/api/snippet/{id}` | 删除片段 |
| 搜索片段 | GET | `/api/snippet/search` | 搜索片段 |
| AI 解释 | POST | `/api/snippet/{id}/explain` | AI 解释代码 |

### AI 代码生成接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 支持类型 | GET | `/api/code/types` | 获取支持的代码类型 |
| 生成代码 | POST | `/api/code/generate` | AI 生成代码 |
| 保存代码 | POST | `/api/code/save` | 保存代码到文件 |
| 代码审查 | POST | `/api/code/review` | AI 代码审查 |
| 代码转换 | POST | `/api/code/convert` | 代码语言转换 |
| 分析项目 | GET | `/api/code/analyze` | 分析项目结构 |

### RAG 知识库接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 知识库列表 | GET | `/api/knowledge/list` | 获取所有知识库 |
| 知识库详情 | GET | `/api/knowledge/{id}` | 获取知识库详情 |
| 创建知识库 | POST | `/api/knowledge` | 创建知识库 |
| 更新知识库 | PUT | `/api/knowledge/{id}` | 更新知识库 |
| 删除知识库 | DELETE | `/api/knowledge/{id}` | 删除知识库 |
| 启用/禁用 | PUT | `/api/knowledge/{id}/toggle` | 切换启用状态 |
| 上传文档 | POST | `/api/knowledge/{baseId}/upload` | 上传文档到知识库 |
| 文档列表 | GET | `/api/knowledge/{baseId}/documents` | 获取文档列表 |
| 删除文档 | DELETE | `/api/knowledge/document/{docId}` | 删除文档 |
| RAG问答 | GET | `/api/knowledge/{baseId}/query` | 基于知识库问答 |
| 语义搜索 | GET | `/api/knowledge/{baseId}/search` | 搜索相关文档片段 |

### 定时任务接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 任务列表 | GET | `/api/task/list` | 获取所有任务 |
| 任务详情 | GET | `/api/task/{id}` | 获取任务详情 |
| 创建任务 | POST | `/api/task` | 创建定时任务 |
| 更新任务 | PUT | `/api/task/{id}` | 更新任务 |
| 删除任务 | DELETE | `/api/task/{id}` | 删除任务 |
| 启用/禁用 | PUT | `/api/task/{id}/toggle` | 切换启用状态 |
| 手动执行 | POST | `/api/task/{id}/execute` | 手动执行任务 |
| 任务类型 | GET | `/api/task/types` | 获取任务类型列表 |

### 记忆管理接口

| 接口 | 方法 | 路径 | 描述 |
|------|------|------|------|
| 提取存储记忆 | POST | `/memory/extract-store` | 从对话提取并存储记忆 |
| 搜索记忆 | GET | `/memory/search` | 搜索相关记忆 |

详细 API 文档请参考 [API.md](./API.md)

新增接口文档请参考 [api_new.md](./api_new.md)

## 项目结构

### 后端结构

```
src/main/java/com/example/demo/
├── config/                 # 配置类
│   ├── AiConfiguration.java
│   ├── McpAgentConfiguration.java
│   ├── QdrantCollectionInitializer.java
│   ├── SkillInitializer.java
│   ├── VirtualThreadConfig.java
│   └── WebMvcConfig.java
├── controller/             # 控制器
│   ├── ChatController.java
│   ├── ChatHistoryController.java
│   ├── CodeGenController.java
│   ├── CodeSnippetController.java
│   ├── EmailController.java
│   ├── EmbeddingController.java
│   ├── FileController.java
│   ├── KnowledgeController.java
│   ├── McpAgentController.java
│   ├── McpToolController.java
│   ├── MemoryController.java
│   ├── ModelController.java
│   ├── NoteController.java
│   ├── ScheduleController.java
│   ├── SearchController.java
│   ├── SkillController.java
│   └── TaskController.java
├── dto/                    # 数据传输对象
├── entity/                 # 实体类
├── exception/              # 异常处理
├── mapper/                 # MyBatis Mapper
├── memory/                 # 记忆模块
├── properties/             # 配置属性
├── service/                # 服务层（按功能分类）
│   ├── ai/                 # AI模型管理服务
│   ├── chat/               # 对话服务
│   ├── code/               # 代码生成服务
│   ├── email/              # 邮件服务
│   ├── file/               # 文件上传服务
│   ├── knowledge/          # RAG知识库服务
│   ├── mcp/                # MCP工具服务
│   ├── memory/             # 记忆服务
│   ├── note/               # 笔记服务
│   ├── schedule/           # 日程服务
│   ├── search/             # 搜索服务
│   ├── skill/              # 技能执行服务
│   ├── task/               # 定时任务服务
│   └── tool/               # 工具执行器
└── DemoApplication.java    # 启动类
```

### 前端结构

```
frontend/
├── src/
│   ├── views/              # 页面组件
│   │   ├── Dashboard.vue   # 仪表盘
│   │   ├── Models.vue      # 模型配置
│   │   ├── Chat.vue        # AI聊天 (Markdown渲染)
│   │   ├── Knowledge.vue   # RAG知识库
│   │   ├── Tasks.vue       # 定时任务
│   │   ├── Notes.vue       # 笔记管理
│   │   ├── Snippets.vue    # 代码片段
│   │   ├── FileManager.vue # 文件管理
│   │   ├── Schedule.vue    # 日程管理
│   │   ├── Skills.vue      # 技能管理
│   │   ├── Tools.vue       # 工具管理
│   │   ├── EmailConfig.vue # 邮箱配置
│   │   └── Search.vue      # 向量搜索
│   ├── components/         # 公共组件
│   ├── services/           # API服务
│   ├── stores/             # Pinia状态管理
│   ├── router/             # 路由配置
│   ├── types/              # TypeScript类型
│   └── styles/             # 样式文件
├── package.json
└── vite.config.ts
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
      timeout: 120
      max-retries: 3
  ollama:
    embedding-model:
      base-url: http://localhost:11434
      model-name: nomic-embed-text:latest
```

#### 多模型动态配置

系统支持在前端动态配置多个 AI 模型，无需修改配置文件：

1. **访问模型管理页面**: 前端 → 模型配置 (`/models`)
2. **添加模型**: 点击"添加模型"，选择提供商或自定义 API 地址
3. **支持的提供商**:
   | 提供商 | API 地址 |
   |--------|----------|
   | OpenAI | https://api.openai.com/v1 |
   | 阿里云（通义千问） | https://dashscope.aliyuncs.com/compatible-mode/v1 |
   | DeepSeek | https://api.deepseek.com/v1 |
   | Anthropic（Claude） | https://api.anthropic.com/v1 |
   | 智谱AI（GLM） | https://open.bigmodel.cn/api/paas/v4 |
   | Moonshot（Kimi） | https://api.moonshot.cn/v1 |
4. **API Key 安全**: 所有 API Key 采用 AES-256 加密存储，数据库中只保存加密后的密文
5. **设置默认模型**: 点击星标图标设置默认模型，系统优先使用默认模型进行对话
6. **测试连接**: 添加模型前可先测试连接是否正常

#### 加密密钥配置

```yaml
app:
  encryption:
    key: "AiAgentSecretKey32Bytes!!"  # 请修改为自己的32字符密钥
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

### 日程管理配置

```yaml
app:
  # 邮件发送配置 (SMTP) - 用于发送日程汇总和提醒
  mail:
    smtp-host: smtp.163.com          # SMTP服务器地址
    smtp-port: 465                   # SMTP端口
    smtp-username: your@163.com      # 发件邮箱
    smtp-password: "授权码"           # SMTP授权码（非邮箱密码）
    smtp-ssl-enabled: true           # 启用SSL
    from-name: AI Agent              # 发件人名称

  schedule:
    enabled: true                        # 是否启用日程功能
    storage-path: ./data/schedules       # 日程文件存储路径
    user-email: user@example.com         # 接收汇总/提醒的邮箱
    daily-summary-cron: "0 0 20 * * ?"   # 每天20:00发送汇总
    morning-reminder-cron: "0 0 8 * * ?" # 每天08:00发送提醒
  file:
    upload-dir: ./data/documents         # 文件存储目录
    allowed-types: txt,md,pdf,doc,docx   # 允许的文件类型
```

#### SMTP授权码获取

以163邮箱为例：
1. 登录163邮箱 → 设置 → POP3/SMTP/IMAP
2. 开启"IMAP/SMTP服务"
3. 获取**授权码**（不是邮箱密码）
4. 将授权码填入 `smtp-password`

## 相关链接

| 资源 | 链接 |
|------|------|
| 技能市场 | https://skills.sh |
| MCP 工具市场 | https://mcp.so/zh |
| MCP GitHub | https://github.com/modelcontextprotocol |
| Serper 搜索 | https://serper.dev |
| Tavily 搜索 | https://tavily.com |

## 开发指南

### 构建项目

```bash
# 后端
./gradlew build

# 前端
cd frontend && npm run build
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