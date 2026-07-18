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
  passwordConfigured?: boolean
  authType?: 'password' | 'oauth2_access_token' | 'oauth2_refresh_token'
  oauthClientId?: string
  oauthClientSecret?: string
  oauthClientSecretConfigured?: boolean
  oauthRefreshToken?: string
  oauthRefreshTokenConfigured?: boolean
  oauthAccessToken?: string
  oauthAccessTokenConfigured?: boolean
  oauthTokenEndpoint?: string
  oauthScope?: string
  host: string
  port: number
  sslEnabled: boolean
  protocol: string
  provider?: 'GENERIC_IMAP' | 'GENERIC_POP3' | 'GMAIL_API' | 'MICROSOFT_GRAPH'
  listenMode?: 'POLLING' | 'IMAP_IDLE' | 'WEBHOOK' | 'DELTA_SYNC'
  fallbackListenMode?: 'POLLING' | 'IMAP_IDLE' | 'WEBHOOK' | 'DELTA_SYNC' | null
  providerSettings?: string
  enabled: boolean
  folder: string
  pollInterval: number
  listenStartTime?: string | null
  listenEndTime?: string | null
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
  triggerStatus?: 0 | 1
  /**
   * 是否走 AI 处理：true 时定时任务触发会调 Claude Code CLI。
   * 把 description + params 作为 prompt 喂进去，结果写 job_log.result。
   */
  requiresAi?: boolean
  lastExecuteTime?: string | number[] | null
  lastExecuteResult?: string
  nextExecuteTime?: string | number[] | null
  executeCount?: number
  successCount?: number
  failCount?: number
  createTime?: string | number[] | null
  updateTime?: string | number[] | null
}

export interface JobLog {
  id: number
  jobId: number
  jobName: string
  handler: string
  triggerType: 'CRON' | 'MANUAL' | 'MISFIRE'
  triggerTime: string | number[] | null
  handleStartTime?: string | number[] | null
  handleEndTime?: string | number[] | null
  durationMs?: number
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  executorParam?: string
  result?: string
  errorMessage?: string
  alarmStatus?: 0 | 1
  createTime?: string | number[] | null
}

export interface JobLogPage {
  records: JobLog[]
  total: number
  page: number
  size: number
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

export interface AuthUserProfile {
  id: number
  username: string
  email: string
  displayName: string
  role: string
  emailVerified: boolean
}

export interface AuthTokenResponse {
  tokenType?: string
  accessToken?: string
  refreshToken?: string
  expiresIn?: number
  user?: AuthUserProfile
  requiresSecondFactor?: boolean
  preAuthToken?: string
  preAuthExpiresIn?: number
}

export interface EmailCodeSendResponse {
  cooldownSeconds: number
  message: string
}

export interface GithubAuthorizeResponse {
  authorizationUrl: string
  stateExpiresIn: number
}

export interface GithubExchangeResponse {
  token: AuthTokenResponse
  redirectPath: string
}

export interface FaceStatusResponse {
  enrolled: boolean
  required: boolean
  enabled: boolean
  vectorDimension?: number
  qualityScore?: number
}

export interface PersonalInsight {
  generatedAt: string
  enabledTasks: number
  todaySchedules: number
  pendingSchedules: number
  pinnedNotes: number
  snippetCount: number
  messageCount: number
  totalTokenUsage: number
  avgTokensPerMessage: number
}

export interface TaskTemplate {
  id: string
  name: string
  description: string
  taskType: string
  cronExpression: string
  params: string
}

// 通知类型
export interface NotificationDTO {
  id?: number
  type: string
  title: string
  content: string
  source?: string
  sourceId?: string
  isRead: boolean
  priority: string
  extraData?: string
  createTime: string
  readTime?: string
}

export interface NotificationStatsDTO {
  totalCount: number
  unreadCount: number
  todayCount: number
}

export interface NotificationCreateRequest {
  type: string
  title: string
  content: string
  source?: string
  sourceId?: string
  priority?: string
  extraData?: string
}

// 监听器状态类型
export interface ListenerStatusDTO {
  type: string
  name: string
  running: boolean
  configured: boolean
  statusDescription: string
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

export interface BackupResult {
  fileName?: string
  filePath?: string
  fileSize?: number
  createdAt?: string
  status: string
  message: string
}

export interface BackupFileInfo {
  fileName: string
  filePath: string
  fileSize: number
  fileSizeFormatted: string
  createdAt?: string
}

export interface RestoreResult {
  status: string
  message: string
  importedTables?: number
  importedRows?: number
  filesRestored?: number
  replaceExisting?: boolean
  importedAt?: string
}
