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
  filePath?: string
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

export interface KnowledgeBase {
  id: number
  name: string
  description?: string
  collectionName: string
  chunkSize?: number
  chunkOverlap?: number
  documentCount?: number
  enabled: boolean
  createTime?: string
  updateTime?: string
}

export interface KnowledgeDocument {
  id: number
  baseId: number
  fileName: string
  filePath?: string
  fileType: string
  fileSize: number
  content?: string
  chunkCount?: number
  status: string
  errorMessage?: string
  createTime?: string
  updateTime?: string
}

export interface AiModelConfig {
  id: number
  name: string
  provider: string
  baseUrl: string
  modelName: string
  apiKey?: string
  apiKeyPreview?: string
  enabled: boolean
  isDefault: boolean
  createTime?: string
  updateTime?: string
}

export interface ScheduledTask {
  id: number
  name: string
  description?: string
  taskType: string
  cronExpression: string
  params?: string
  skillCode?: string
  enabled: boolean
  lastExecuteTime?: string | number[] | null
  lastExecuteResult?: string
  nextExecuteTime?: string | number[] | null
  executeCount?: number
  successCount?: number
  failCount?: number
  createTime?: string | number[] | null
  updateTime?: string | number[] | null
}

export interface SystemSettings {
  site_name?: string
  site_logo?: string
  default_theme?: 'light' | 'dark' | 'auto'
  [key: string]: any
}

export interface AutonomyFinding {
  severity: string
  title: string
  detail: string
  suggestion: string
}

export interface AutonomyScanReport {
  scanTime: string
  workspaceRoot: string
  metrics: Record<string, any>
  findings: AutonomyFinding[]
  reportPath: string
  summaryPath: string
}

export interface AutonomyVerificationStep {
  name: string
  success: boolean
  exitCode: number
  workingDirectory: string
  output: string
}

export interface AutonomyVerificationResult {
  verifyTime: string
  success: boolean
  steps: AutonomyVerificationStep[]
}

export interface AutonomyDraftResponse {
  generateTime: string
  target: string
  draftPath: string
  content: string
  policyNote: string
}

export interface AutonomyArtifact {
  type: string
  name: string
  path: string
  time: string
  preview: string
}

export interface AutonomyDiff {
  latestScanTime?: string
  previousScanTime?: string
  newCount: number
  resolvedCount: number
  persistentCount: number
  newFindings: AutonomyFinding[]
  resolvedFindings: AutonomyFinding[]
  persistentFindings: AutonomyFinding[]
}

export interface InboxItem {
  category: string
  title: string
  summary: string
  status: string
  route: string
  accent: string
  time: string
  meta?: Record<string, any>
}

export interface InboxSummary {
  generatedAt: string
  counts: Record<string, any>
  items: InboxItem[]
  warnings: string[]
}

export interface NoteSemanticHit {
  noteId: number
  title: string
  contentSnippet: string
  tags?: string
  aiSummary?: string
  score: number
  updateTime?: string
}

export interface GeneratedReport {
  period: string
  generatedAt: string
  path: string
  content: string
  metrics: Record<string, any>
}

export interface ReportArtifact {
  period: string
  name: string
  path: string
  time?: string
  preview: string
}

export interface ChatActionResult {
  target: string
  message: string
  entityId?: number
  route?: string
  payload?: Record<string, any>
}

export interface CommandPaletteItem {
  id: string
  label: string
  description: string
  type: 'route' | 'action'
  route?: string
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
