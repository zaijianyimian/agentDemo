// 文件上传相关类型
export interface Document {
  id: number
  fileName: string
  filePath: string
  fileType: string
  fileSize: number
  content: string
  importance: number
  tags: string
  sentiment: string
  summary: string
  status: string
  createTime: string
  updateTime: string
}

// 日程事件类型
export interface ScheduleEvent {
  id: number
  title: string
  description: string
  eventTime: string
  eventDate: string
  location: string
  sourceEmail: string
  reminderStatus: string
  summaryStatus: string
  reminderEnabled: boolean
  status: string
  createTime: string
  updateTime: string
}

// 邮件配置类型
export interface EmailConfig {
  id: number
  email: string
  password: string
  host: string
  port: number
  sslEnabled: boolean
  protocol: string
  enabled: boolean
  folder: string
  pollInterval: number
  remark: string
  createTime: string
  updateTime: string
}

// MCP工具类型
export interface McpTool {
  id: number
  name: string
  displayName: string
  description: string
  toolType: string
  config: string
  inputSchema: string
  enabled: boolean
  remark?: string
  createTime: string
  updateTime: string
}

// AI技能类型
export interface Skill {
  id: number
  code: string
  name: string
  description: string
  category: string
  icon: string
  enabled: boolean
  isBuiltin: boolean
  createTime: string
  updateTime: string
}

// API响应类型
export interface ApiResponse<T> {
  success: boolean
  message?: string
  data?: T
  total?: number
}

// 搜索结果类型
export interface SearchResult {
  title: string
  url: string
  snippet: string
}

// 聊天消息类型
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: string
  isStreaming?: boolean
}

// 聊天会话类型
export interface ChatSession {
  id: number
  title: string
  summary?: string
  messageCount: number
  lastMessageTime?: string
  createTime: string
  updateTime: string
}

// 聊天消息实体类型（数据库存储）
export interface ChatMessageEntity {
  id: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  model?: string
  tokenCount?: number
  createTime: string
}

// 笔记类型
export interface Note {
  id: number
  title: string
  content: string
  tags?: string
  aiSummary?: string
  isPinned: boolean
  createTime: string
  updateTime: string
}

// 代码片段类型
export interface CodeSnippet {
  id: number
  title: string
  code: string
  language?: string
  description?: string
  tags?: string
  createTime: string
  updateTime: string
}

// 代码生成请求类型
export interface CodeGenerateRequest {
  type: string
  name: string
  fields?: string[]
  packageName?: string
  description?: string
  options?: Record<string, any>
}

// 代码生成响应类型
export interface CodeGenerateResponse {
  success: boolean
  code?: string
  fileName?: string
  filePath?: string
  message?: string
}