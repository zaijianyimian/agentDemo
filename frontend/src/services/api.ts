import axios from 'axios'
import type {
  ApiResponse,
  Document,
  ScheduleEvent,
  EmailConfig,
  McpTool,
  Skill,
  SearchResult,
  ChatSession,
  ChatMessageEntity,
  Note,
  CodeSnippet,
  CodeGenerateRequest,
  CodeGenerateResponse,
  KnowledgeBase,
  KnowledgeDocument,
  AiModelConfig,
  ScheduledTask,
  SystemSettings,
  AutonomyScanReport,
  AutonomyVerificationResult,
  AutonomyDraftResponse,
  AutonomyArtifact,
  AutonomyDiff,
  InboxSummary,
  NoteSemanticHit,
  GeneratedReport,
  ReportArtifact,
  ChatActionResult,
  AuthTokenResponse,
  AuthUserProfile,
  EmailCodeSendResponse,
  PuzzleCaptchaResponse,
  PuzzleCaptchaVerifyResponse,
  GithubAuthorizeResponse,
  GithubExchangeResponse,
  FaceStatusResponse,
  PersonalInsight,
  TaskTemplate
} from '@/types'
import {
  buildLoginRedirectUrl,
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens
} from '@/services/auth-token'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(config => {
  const requestUrl = String(config.url || '')
  const shouldSkipAuthHeader =
    requestUrl.startsWith('/auth/register') ||
    requestUrl.startsWith('/auth/login/') ||
    requestUrl.startsWith('/auth/token/refresh') ||
    requestUrl.startsWith('/auth/captcha/') ||
    requestUrl.startsWith('/auth/face/verify-login') ||
    requestUrl.startsWith('/auth/oauth/github/')

  if (shouldSkipAuthHeader) {
    if (config.headers && 'Authorization' in config.headers) {
      delete (config.headers as Record<string, string>).Authorization
    }
    return config
  }

  const token = getAccessToken()
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshingPromise: Promise<string> | null = null

api.interceptors.response.use(
  response => response,
  async (error) => {
    const originalRequest = error.config as any
    const status = error?.response?.status
    const requestUrl = String(originalRequest?.url || '')

    const shouldSkipRefresh =
      requestUrl.startsWith('/auth/token/refresh') ||
      requestUrl.startsWith('/auth/register') ||
      requestUrl.startsWith('/auth/login/') ||
      requestUrl.startsWith('/auth/captcha/') ||
      requestUrl.startsWith('/auth/face/verify-login')

    if (
      status === 401 &&
      !originalRequest?._retry &&
      !shouldSkipRefresh
    ) {
      const refreshToken = getRefreshToken()
      if (refreshToken) {
        originalRequest._retry = true
        try {
          if (!refreshingPromise) {
            refreshingPromise = axios
              .post('/api/auth/token/refresh', { refreshToken })
              .then(resp => {
                const payload = resp.data as ApiResponse<AuthTokenResponse>
                if (!payload?.success || !payload.data) {
                  throw new Error(payload?.message || '刷新令牌失败')
                }
                const accessToken = payload.data.accessToken
                const refreshTokenNext = payload.data.refreshToken
                if (!accessToken || !refreshTokenNext) {
                  throw new Error('刷新令牌失败')
                }
                setTokens(accessToken, refreshTokenNext)
                return accessToken
              })
              .finally(() => {
                refreshingPromise = null
              })
          }
          const latestAccessToken = await refreshingPromise
          originalRequest.headers = originalRequest.headers || {}
          originalRequest.headers.Authorization = `Bearer ${latestAccessToken}`
          return api(originalRequest)
        } catch (_e) {
          clearTokens()
          if (window.location.pathname !== '/login') {
            window.location.href = buildLoginRedirectUrl(window.location.pathname + window.location.search)
          }
        }
      } else if (window.location.pathname !== '/login') {
        window.location.href = buildLoginRedirectUrl(window.location.pathname + window.location.search)
      }
    }

    return Promise.reject(error)
  }
)

// 文件上传服务
export const fileService = {
  // 上传文件
  upload: async (file: File): Promise<ApiResponse<Document>> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post('/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return response.data
  },

  // 获取文件列表
  list: async (): Promise<ApiResponse<Document[]>> => {
    const response = await api.get('/file/list')
    return response.data
  },

  // 获取文件详情
  get: async (id: number): Promise<ApiResponse<Document>> => {
    const response = await api.get(`/file/${id}`)
    return response.data
  },

  // 删除文件
  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/file/${id}`)
    return response.data
  },

  // 搜索文件
  search: async (minImportance?: number, maxImportance?: number): Promise<ApiResponse<Document[]>> => {
    const params = new URLSearchParams()
    if (minImportance) params.append('minImportance', minImportance.toString())
    if (maxImportance) params.append('maxImportance', maxImportance.toString())
    const response = await api.get(`/file/search?${params.toString()}`)
    return response.data
  }
}

// 日程服务
export const scheduleService = {
  list: async (): Promise<ScheduleEvent[]> => {
    const response = await api.get('/schedule/list')
    return response.data
  },

  today: async (): Promise<ScheduleEvent[]> => {
    const response = await api.get('/schedule/today')
    return response.data
  },

  tomorrow: async (): Promise<ScheduleEvent[]> => {
    const response = await api.get('/schedule/tomorrow')
    return response.data
  },

  getByDate: async (date: string): Promise<ScheduleEvent[]> => {
    const response = await api.get(`/schedule/date/${date}`)
    return response.data
  },

  latest: async (limit = 5): Promise<ScheduleEvent[]> => {
    const response = await api.get('/schedule/latest', { params: { limit } })
    return response.data
  },

  range: async (startDate: string, endDate: string): Promise<ScheduleEvent[]> => {
    const response = await api.get('/schedule/range', { params: { startDate, endDate } })
    return response.data
  },

  get: async (id: number): Promise<ScheduleEvent> => {
    const response = await api.get(`/schedule/${id}`)
    return response.data
  },

  create: async (data: Partial<ScheduleEvent>): Promise<ScheduleEvent> => {
    const response = await api.post('/schedule', data)
    return response.data
  },

  update: async (id: number, data: Partial<ScheduleEvent>): Promise<ScheduleEvent> => {
    const response = await api.put(`/schedule/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<void> => {
    const response = await api.delete(`/schedule/${id}`)
    return response.data
  },

  complete: async (id: number): Promise<void> => {
    const response = await api.put(`/schedule/${id}/complete`)
    return response.data
  },

  cancel: async (id: number): Promise<void> => {
    const response = await api.put(`/schedule/${id}/cancel`)
    return response.data
  },

  parseEmail: async (payload: { subject: string; from: string; content: string }): Promise<ScheduleEvent> => {
    const response = await api.post('/schedule/parse-email', payload)
    return response.data
  },

  parseAndSave: async (payload: { subject: string; from: string; content: string }): Promise<ScheduleEvent> => {
    const response = await api.post('/schedule/parse-and-save', payload)
    return response.data
  },

  listFiles: async (): Promise<string[]> => {
    const response = await api.get('/schedule/files')
    return response.data
  },

  getFileByDate: async (date: string): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get(`/schedule/file/date/${date}`)
    return response.data
  },

  getFileByName: async (fileName: string): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get(`/schedule/file/${fileName}`)
    return response.data
  },

  getFileByEventId: async (id: number): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get(`/schedule/${id}/file`)
    return response.data
  }
}

// 邮件配置服务
export const emailService = {
  listConfigs: async (): Promise<ApiResponse<EmailConfig[]>> => {
    const response = await api.get('/email/config/list')
    return response.data
  },

  createConfig: async (data: Partial<EmailConfig>): Promise<ApiResponse<EmailConfig>> => {
    const response = await api.post('/email/config', data)
    return response.data
  },

  updateConfig: async (data: Partial<EmailConfig>): Promise<ApiResponse<EmailConfig>> => {
    const response = await api.put('/email/config', data)
    return response.data
  },

  deleteConfig: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/email/config/${id}`)
    return response.data
  },

  startListener: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.post(`/email/listener/start/${id}`)
    return response.data
  },

  stopListener: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.post(`/email/listener/stop/${id}`)
    return response.data
  },

  getListenerStatus: async (): Promise<ApiResponse<any>> => {
    const response = await api.get('/email/listener/status')
    return response.data
  },

  getTemplates: async (): Promise<ApiResponse<any>> => {
    const response = await api.get('/email/templates')
    return response.data
  },

  getEnabledConfigs: async (): Promise<ApiResponse<EmailConfig[]>> => {
    const response = await api.get('/email/config/enabled')
    return response.data
  },

  getConfig: async (id: number): Promise<ApiResponse<EmailConfig>> => {
    const response = await api.get(`/email/config/${id}`)
    return response.data
  },

  reloadListeners: async (): Promise<ApiResponse<void>> => {
    const response = await api.post('/email/listener/reload')
    return response.data
  },

  // 测试已保存的邮箱配置
  testConfig: async (id: number): Promise<ApiResponse<{
    success: boolean
    message: string
    durationMs: number
    messageCount: number
    errorDetail: string
  }>> => {
    const response = await api.post(`/email/config/${id}/test`)
    return response.data
  },

  // 测试新邮箱配置（未保存的）
  testNewConfig: async (data: Partial<EmailConfig>): Promise<ApiResponse<{
    success: boolean
    message: string
    durationMs: number
    messageCount: number
    errorDetail: string
  }>> => {
    const response = await api.post('/email/config/test', data)
    return response.data
  },

  // 检查已保存配置的网络连通性（服务器 -> 邮件服务器）
  checkNetwork: async (id: number): Promise<ApiResponse<{
    success: boolean
    message: string
    durationMs: number
    resolvedIp: string
    errorDetail: string
  }>> => {
    const response = await api.get(`/email/config/${id}/network-check`)
    return response.data
  },

  // 检查新配置的网络连通性（未保存）
  checkNewConfigNetwork: async (data: Partial<EmailConfig>): Promise<ApiResponse<{
    success: boolean
    message: string
    durationMs: number
    resolvedIp: string
    errorDetail: string
  }>> => {
    const response = await api.post('/email/config/network-check', data)
    return response.data
  }
}

// MCP工具服务
export const mcpToolService = {
  list: async (): Promise<ApiResponse<McpTool[]>> => {
    const response = await api.get('/mcp/tools')
    return response.data
  },

  getEnabled: async (): Promise<ApiResponse<McpTool[]>> => {
    const response = await api.get('/mcp/tools/enabled')
    return response.data
  },

  get: async (id: number): Promise<ApiResponse<McpTool>> => {
    const response = await api.get(`/mcp/tools/${id}`)
    return response.data
  },

  create: async (data: Partial<McpTool>): Promise<ApiResponse<McpTool>> => {
    const response = await api.post('/mcp/tools', data)
    return response.data
  },

  update: async (id: number, data: Partial<McpTool>): Promise<ApiResponse<McpTool>> => {
    const response = await api.put(`/mcp/tools/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/mcp/tools/${id}`)
    return response.data
  },

  toggle: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.put(`/mcp/tools/${id}/toggle`)
    return response.data
  },

  execute: async (name: string, params: any): Promise<ApiResponse<any>> => {
    const response = await api.post(`/mcp/tools/${name}/execute`, params)
    return response.data
  },

  test: async (id: number): Promise<ApiResponse<any>> => {
    const response = await api.post(`/mcp/tools/${id}/test`)
    return response.data
  },

  validate: async (id: number): Promise<ApiResponse<any>> => {
    const response = await api.post(`/mcp/tools/${id}/validate`)
    return response.data
  }
}

// AI技能服务
export const skillService = {
  list: async (): Promise<ApiResponse<Skill[]>> => {
    const response = await api.get('/skill/list')
    return response.data
  },

  getBuiltin: async (): Promise<ApiResponse<Skill[]>> => {
    const response = await api.get('/skill/builtin')
    return response.data
  },

  getCategories: async (): Promise<ApiResponse<string[]>> => {
    const response = await api.get('/skill/categories')
    return response.data
  },

  getEnabled: async (): Promise<ApiResponse<Skill[]>> => {
    const response = await api.get('/skill/enabled')
    return response.data
  },

  getByCategory: async (category: string): Promise<ApiResponse<Skill[]>> => {
    const response = await api.get(`/skill/category/${category}`)
    return response.data
  },

  getByCode: async (code: string): Promise<ApiResponse<Skill>> => {
    const response = await api.get(`/skill/code/${code}`)
    return response.data
  },

  get: async (id: number): Promise<ApiResponse<Skill>> => {
    const response = await api.get(`/skill/${id}`)
    return response.data
  },

  create: async (data: Partial<Skill>): Promise<ApiResponse<Skill>> => {
    const response = await api.post('/skill', data)
    return response.data
  },

  update: async (id: number, data: Partial<Skill>): Promise<ApiResponse<Skill>> => {
    const response = await api.put(`/skill/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/skill/${id}`)
    return response.data
  },

  toggle: async (id: number): Promise<ApiResponse<Skill>> => {
    const response = await api.put(`/skill/${id}/toggle`)
    return response.data
  },

  bindTool: async (skillId: number, toolId: number): Promise<ApiResponse<void>> => {
    const response = await api.post(`/skill/${skillId}/tools/${toolId}`)
    return response.data
  },

  unbindTool: async (skillId: number, toolId: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/skill/${skillId}/tools/${toolId}`)
    return response.data
  },

  getTools: async (id: number): Promise<ApiResponse<McpTool[]>> => {
    const response = await api.get(`/skill/${id}/tools`)
    return response.data
  },

  execute: async (code: string, params: any): Promise<ApiResponse<any>> => {
    const response = await api.post(`/skill/${code}/execute`, params)
    return response.data
  },

  // 重新加载技能
  reload: async (): Promise<ApiResponse<{ count: number }>> => {
    const response = await api.post('/skill/reload')
    return response.data
  },

  // 导入技能
  importSkill: async (json: string): Promise<ApiResponse<void>> => {
    // 将JSON字符串解析为对象
    const skillData = JSON.parse(json)
    const response = await api.post('/skill/import', skillData)
    return response.data
  },

  // 导出技能
  exportSkill: async (id: number): Promise<ApiResponse<string>> => {
    const response = await api.get(`/skill/${id}/export`)
    return response.data
  },

  test: async (id: number): Promise<ApiResponse<any>> => {
    const response = await api.post(`/skill/${id}/test`)
    return response.data
  }
}

// 聊天服务
export const chatService = {
  // 普通聊天
  chat: async (message: string): Promise<string> => {
    const response = await api.get('/chat/complete', { params: { message } })
    return response.data
  },

  // MCP Agent聊天
  mcpChat: async (message: string): Promise<string> => {
    const response = await api.get('/mcp/agent/chat', { params: { message } })
    return response.data
  },

  chatWithSession: async (message: string, sessionId: number, model?: number): Promise<string> => {
    const response = await api.get('/chat/complete/session', { params: { message, sessionId, model } })
    return response.data
  },

  structured: async (message: string): Promise<ApiResponse<any> | any> => {
    const response = await api.get('/chat/structured', { params: { message } })
    return response.data
  },

  analyze: async (content: string): Promise<ApiResponse<any> | any> => {
    const response = await api.get('/analyze', { params: { content } })
    return response.data
  }
}

export const embeddingService = {
  get: async (text: string): Promise<any> => {
    const response = await api.get('/embedding', { params: { text } })
    return response.data
  },

  getFull: async (text: string): Promise<any> => {
    const response = await api.get('/embedding/full', { params: { text } })
    return response.data
  },

  test: async (): Promise<ApiResponse<any>> => {
    const response = await api.get('/embedding/test')
    return response.data
  }
}

// 搜索服务
export const searchService = {
  search: async (query: string): Promise<{ query: string; results: SearchResult[]; totalResults: number }> => {
    const response = await api.get('/search', { params: { query } })
    return response.data
  },

  searchWithSummary: async (query: string): Promise<ApiResponse<string>> => {
    const response = await api.get('/search/summary', { params: { query } })
    return response.data
  },

  searchChat: async (message: string): Promise<string> => {
    const response = await api.get('/search/chat', { params: { message } })
    return response.data
  },

  test: async (): Promise<ApiResponse<any>> => {
    const response = await api.get('/search/test')
    return response.data
  },

  // 搜索历史
  getHistory: async (limit = 50): Promise<ApiResponse<any[]>> => {
    const response = await api.get('/search/history', { params: { limit } })
    return response.data
  },

  getHotQueries: async (limit = 10): Promise<ApiResponse<any[]>> => {
    const response = await api.get('/search/hot', { params: { limit } })
    return response.data
  },

  getStatistics: async (): Promise<ApiResponse<any>> => {
    const response = await api.get('/search/statistics')
    return response.data
  },

  clearHistory: async (): Promise<ApiResponse<void>> => {
    const response = await api.delete('/search/history')
    return response.data
  },

  deleteHistoryItem: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/search/history/${id}`)
    return response.data
  },

  // 用户兴趣
  getInterests: async (): Promise<ApiResponse<any[]>> => {
    const response = await api.get('/search/interests')
    return response.data
  },

  getTopInterests: async (limit = 10): Promise<ApiResponse<any[]>> => {
    const response = await api.get('/search/interests/top', { params: { limit } })
    return response.data
  },

  getInterestReport: async (): Promise<ApiResponse<any>> => {
    const response = await api.get('/search/interests/report')
    return response.data
  },

  clearInterests: async (): Promise<ApiResponse<void>> => {
    const response = await api.delete('/search/interests')
    return response.data
  },

  deleteInterest: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/search/interests/${id}`)
    return response.data
  }
}

export const memoryService = {
  extractAndStore: async (sessionId: string, dialogues: string[]): Promise<ApiResponse<any>> => {
    const response = await api.post('/memory/extract-store', { sessionId, dialogues })
    return response.data
  },

  search: async (query: string, topK = 5): Promise<ApiResponse<any[]>> => {
    const response = await api.get('/memory/search', { params: { query, topK } })
    return response.data
  }
}

// 聊天历史服务
export const chatHistoryService = {
  // 创建新会话
  createSession: async (title?: string): Promise<ApiResponse<ChatSession>> => {
    const params = title ? { title } : {}
    const response = await api.post('/chat/history/session', null, { params })
    return response.data
  },

  // 获取所有会话列表
  getSessions: async (): Promise<ApiResponse<ChatSession[]>> => {
    const response = await api.get('/chat/history/sessions')
    return response.data
  },

  // 获取会话详情
  getSession: async (id: number): Promise<ApiResponse<ChatSession>> => {
    const response = await api.get(`/chat/history/session/${id}`)
    return response.data
  },

  // 更新会话标题
  updateSessionTitle: async (id: number, title: string): Promise<ApiResponse<ChatSession>> => {
    const response = await api.put(`/chat/history/session/${id}/title`, null, { params: { title } })
    return response.data
  },

  // 删除会话
  deleteSession: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/chat/history/session/${id}`)
    return response.data
  },

  // 获取会话消息
  getSessionMessages: async (sessionId: number): Promise<ApiResponse<ChatMessageEntity[]>> => {
    const response = await api.get(`/chat/history/session/${sessionId}/messages`)
    return response.data
  },

  // 添加消息
  addMessage: async (sessionId: number, role: string, content: string, model?: string): Promise<ApiResponse<ChatMessageEntity>> => {
    const params: Record<string, string> = { role, content }
    if (model) params.model = model
    const response = await api.post(`/chat/history/session/${sessionId}/message`, null, { params })
    return response.data
  },

  // 清空会话消息
  clearSessionMessages: async (sessionId: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/chat/history/session/${sessionId}/messages`)
    return response.data
  }
}

// 笔记服务
export const noteService = {
  // 获取笔记列表
  list: async (): Promise<ApiResponse<Note[]>> => {
    const response = await api.get('/note/list')
    return response.data
  },

  // 获取笔记详情
  get: async (id: number): Promise<ApiResponse<Note>> => {
    const response = await api.get(`/note/${id}`)
    return response.data
  },

  // 创建笔记
  create: async (data: Partial<Note>): Promise<ApiResponse<Note>> => {
    const response = await api.post('/note', data)
    return response.data
  },

  // 更新笔记
  update: async (id: number, data: Partial<Note>): Promise<ApiResponse<Note>> => {
    const response = await api.put(`/note/${id}`, data)
    return response.data
  },

  // 删除笔记
  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/note/${id}`)
    return response.data
  },

  // AI 总结笔记
  summarize: async (id: number): Promise<ApiResponse<Note>> => {
    const response = await api.post(`/note/${id}/summarize`)
    return response.data
  },

  // 切换置顶状态
  togglePin: async (id: number): Promise<ApiResponse<Note>> => {
    const response = await api.put(`/note/${id}/pin`)
    return response.data
  },

  search: async (keyword: string): Promise<ApiResponse<Note[]>> => {
    const response = await api.get('/note/search', { params: { keyword } })
    return response.data
  },

  semanticSearch: async (query: string, topK = 5): Promise<ApiResponse<NoteSemanticHit[]>> => {
    const response = await api.get('/note/semantic-search', { params: { query, topK } })
    return response.data
  },

  reindex: async (): Promise<ApiResponse<number>> => {
    const response = await api.post('/note/reindex')
    return response.data
  }
}

// 代码片段服务
export const snippetService = {
  // 获取片段列表
  list: async (): Promise<ApiResponse<CodeSnippet[]>> => {
    const response = await api.get('/snippet/list')
    return response.data
  },

  // 获取片段详情
  get: async (id: number): Promise<ApiResponse<CodeSnippet>> => {
    const response = await api.get(`/snippet/${id}`)
    return response.data
  },

  // 创建片段
  create: async (data: Partial<CodeSnippet>): Promise<ApiResponse<CodeSnippet>> => {
    const response = await api.post('/snippet', data)
    return response.data
  },

  // 更新片段
  update: async (id: number, data: Partial<CodeSnippet>): Promise<ApiResponse<CodeSnippet>> => {
    const response = await api.put(`/snippet/${id}`, data)
    return response.data
  },

  // 删除片段
  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/snippet/${id}`)
    return response.data
  },

  // 搜索片段
  search: async (keyword: string): Promise<ApiResponse<CodeSnippet[]>> => {
    const response = await api.get('/snippet/search', { params: { keyword } })
    return response.data
  },

  listByLanguage: async (language: string): Promise<ApiResponse<CodeSnippet[]>> => {
    const response = await api.get(`/snippet/language/${language}`)
    return response.data
  },

  // AI 解释代码
  explain: async (id: number): Promise<ApiResponse<string>> => {
    const response = await api.post(`/snippet/${id}/explain`)
    return response.data
  }
}

// 代码生成服务
export const codeGenService = {
  // 获取支持的代码类型
  getTypes: async (): Promise<ApiResponse<string[]>> => {
    const response = await api.get('/code/types')
    return response.data
  },

  // 生成代码
  generate: async (request: CodeGenerateRequest): Promise<ApiResponse<CodeGenerateResponse>> => {
    const response = await api.post('/code/generate', request)
    return response.data
  },

  // 保存代码到文件
  save: async (code: string, fileName: string, subDir?: string): Promise<ApiResponse<CodeGenerateResponse>> => {
    const response = await api.post('/code/save', { code, fileName, subDir })
    return response.data
  },

  // 代码审查
  review: async (code: string, language: string): Promise<ApiResponse<string>> => {
    const response = await api.post('/code/review', { code, language })
    return response.data
  },

  // 代码转换
  convert: async (code: string, fromLanguage: string, toLanguage: string): Promise<ApiResponse<string>> => {
    const response = await api.post('/code/convert', { code, fromLanguage, toLanguage })
    return response.data
  },

  // 分析项目结构
  analyze: async (path?: string): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/code/analyze', { params: { path } })
    return response.data
  }
}

export const knowledgeService = {
  list: async (): Promise<ApiResponse<KnowledgeBase[]>> => {
    const response = await api.get('/knowledge/list')
    return response.data
  },

  get: async (id: number): Promise<ApiResponse<KnowledgeBase>> => {
    const response = await api.get(`/knowledge/${id}`)
    return response.data
  },

  create: async (data: Partial<KnowledgeBase>): Promise<ApiResponse<KnowledgeBase>> => {
    const response = await api.post('/knowledge', data)
    return response.data
  },

  update: async (id: number, data: Partial<KnowledgeBase>): Promise<ApiResponse<KnowledgeBase>> => {
    const response = await api.put(`/knowledge/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/knowledge/${id}`)
    return response.data
  },

  toggle: async (id: number): Promise<ApiResponse<KnowledgeBase>> => {
    const response = await api.put(`/knowledge/${id}/toggle`)
    return response.data
  },

  upload: async (baseId: number, file: File): Promise<ApiResponse<KnowledgeDocument>> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post(`/knowledge/${baseId}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return response.data
  },

  listDocuments: async (baseId: number): Promise<ApiResponse<KnowledgeDocument[]>> => {
    const response = await api.get(`/knowledge/${baseId}/documents`)
    return response.data
  },

  deleteDocument: async (docId: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/knowledge/document/${docId}`)
    return response.data
  },

  query: async (baseId: number, question: string, topK = 5): Promise<ApiResponse<string>> => {
    const response = await api.get(`/knowledge/${baseId}/query`, { params: { question, topK } })
    return response.data
  },

  search: async (baseId: number, query: string, topK = 10): Promise<ApiResponse<any[]>> => {
    const response = await api.get(`/knowledge/${baseId}/search`, { params: { query, topK } })
    return response.data
  }
}

export const modelService = {
  list: async (): Promise<ApiResponse<AiModelConfig[]>> => {
    const response = await api.get('/model/list')
    return response.data
  },

  get: async (id: number): Promise<ApiResponse<AiModelConfig>> => {
    const response = await api.get(`/model/${id}`)
    return response.data
  },

  create: async (data: Partial<AiModelConfig>): Promise<ApiResponse<AiModelConfig>> => {
    const response = await api.post('/model', data)
    return response.data
  },

  update: async (id: number, data: Partial<AiModelConfig>): Promise<ApiResponse<AiModelConfig>> => {
    const response = await api.put(`/model/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/model/${id}`)
    return response.data
  },

  toggle: async (id: number): Promise<ApiResponse<AiModelConfig>> => {
    const response = await api.put(`/model/${id}/toggle`)
    return response.data
  },

  setDefault: async (id: number): Promise<ApiResponse<AiModelConfig>> => {
    const response = await api.put(`/model/${id}/default`)
    return response.data
  },

  test: async (data: Partial<AiModelConfig>): Promise<ApiResponse<string>> => {
    const response = await api.post('/model/test', data)
    return response.data
  },

  providers: async (): Promise<ApiResponse<Array<{ value: string; label: string; baseUrl: string }>>> => {
    const response = await api.get('/model/providers')
    return response.data
  }
}

// 定时任务服务
export const taskService = {
  // 获取任务列表
  list: async (): Promise<ApiResponse<ScheduledTask[]>> => {
    const response = await api.get('/task/list')
    return response.data
  },

  // 获取任务详情
  get: async (id: number): Promise<ApiResponse<ScheduledTask>> => {
    const response = await api.get(`/task/${id}`)
    return response.data
  },

  // 创建任务
  create: async (data: Partial<ScheduledTask>): Promise<ApiResponse<ScheduledTask>> => {
    const response = await api.post('/task', data)
    return response.data
  },

  // 更新任务
  update: async (id: number, data: Partial<ScheduledTask>): Promise<ApiResponse<ScheduledTask>> => {
    const response = await api.put(`/task/${id}`, data)
    return response.data
  },

  // 删除任务
  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/task/${id}`)
    return response.data
  },

  // 启用/禁用任务
  toggle: async (id: number): Promise<ApiResponse<ScheduledTask>> => {
    const response = await api.put(`/task/${id}/toggle`)
    return response.data
  },

  // 手动执行任务
  execute: async (id: number): Promise<ApiResponse<string>> => {
    const response = await api.post(`/task/${id}/execute`)
    return response.data
  },

  // 获取任务类型列表
  getTypes: async (): Promise<ApiResponse<string[]>> => {
    const response = await api.get('/task/types')
    return response.data
  }
}

export const settingsService = {
  getAll: async (): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/settings')
    return response.data
  },

  getSystem: async (): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/settings/system')
    return response.data
  },

  updateSystem: async (payload: SystemSettings): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/system', payload)
    return response.data
  },

  updateModel: async (payload: Record<string, any>): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/model', payload)
    return response.data
  },

  updateQdrant: async (payload: Record<string, any>): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/qdrant', payload)
    return response.data
  },

  updateSearch: async (payload: Record<string, any>): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/search', payload)
    return response.data
  },

  updateSchedule: async (payload: Record<string, any>): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/schedule', payload)
    return response.data
  },

  updateFile: async (payload: Record<string, any>): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/file', payload)
    return response.data
  },

  uploadImage: async (file: File): Promise<ApiResponse<string>> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post('/file/upload/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return response.data
  }
}

export const autonomyService = {
  capabilities: async (): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/autonomy/capabilities')
    return response.data
  },

  scan: async (): Promise<ApiResponse<AutonomyScanReport>> => {
    const response = await api.post('/autonomy/scan')
    return response.data
  },

  verify: async (backend = true, frontend = true): Promise<ApiResponse<AutonomyVerificationResult>> => {
    const response = await api.post('/autonomy/verify', { backend, frontend })
    return response.data
  },

  draft: async (target = 'general', includeVerification = true): Promise<ApiResponse<AutonomyDraftResponse>> => {
    const response = await api.post('/autonomy/draft', { target, includeVerification })
    return response.data
  },

  history: async (limit = 12): Promise<ApiResponse<AutonomyArtifact[]>> => {
    const response = await api.get('/autonomy/history', { params: { limit } })
    return response.data
  },

  readArtifact: async (path: string): Promise<ApiResponse<string>> => {
    const response = await api.get('/autonomy/artifact', { params: { path } })
    return response.data
  },

  diff: async (): Promise<ApiResponse<AutonomyDiff>> => {
    const response = await api.get('/autonomy/diff')
    return response.data
  }
}

export const inboxService = {
  summary: async (limit = 18): Promise<ApiResponse<InboxSummary>> => {
    const response = await api.get('/inbox/summary', { params: { limit } })
    return response.data
  }
}

export const reportService = {
  generate: async (period: 'daily' | 'weekly'): Promise<ApiResponse<GeneratedReport>> => {
    const response = await api.post('/report/generate', null, { params: { period } })
    return response.data
  },

  history: async (limit = 12): Promise<ApiResponse<ReportArtifact[]>> => {
    const response = await api.get('/report/history', { params: { limit } })
    return response.data
  },

  readArtifact: async (path: string): Promise<ApiResponse<string>> => {
    const response = await api.get('/report/artifact', { params: { path } })
    return response.data
  }
}

export const chatActionService = {
  createNote: async (payload: { sessionId?: number; content: string; role?: string; titleHint?: string }): Promise<ApiResponse<ChatActionResult>> => {
    const response = await api.post('/chat/action/note', payload)
    return response.data
  },

  createTask: async (payload: { sessionId?: number; content: string; role?: string; titleHint?: string }): Promise<ApiResponse<ChatActionResult>> => {
    const response = await api.post('/chat/action/task', payload)
    return response.data
  },

  createSchedule: async (payload: { sessionId?: number; content: string; role?: string; titleHint?: string }): Promise<ApiResponse<ChatActionResult>> => {
    const response = await api.post('/chat/action/schedule', payload)
    return response.data
  },

  storeMemory: async (payload: { sessionId?: number; content: string; role?: string; titleHint?: string }): Promise<ApiResponse<ChatActionResult>> => {
    const response = await api.post('/chat/action/memory', payload)
    return response.data
  }
}

export const authService = {
  getPuzzleCaptcha: async (): Promise<ApiResponse<PuzzleCaptchaResponse>> => {
    const response = await api.get('/auth/captcha/puzzle')
    return response.data
  },

  verifyPuzzleCaptcha: async (payload: { captchaId: string; sliderPercent: number }): Promise<ApiResponse<PuzzleCaptchaVerifyResponse>> => {
    const response = await api.post('/auth/captcha/puzzle/verify', payload)
    return response.data
  },

  register: async (payload: { username: string; email: string; password: string; captchaTicket: string; displayName?: string }): Promise<ApiResponse<EmailCodeSendResponse>> => {
    const response = await api.post('/auth/register', payload)
    return response.data
  },

  loginByPassword: async (payload: { username: string; password: string; captchaTicket: string }): Promise<ApiResponse<AuthTokenResponse>> => {
    const response = await api.post('/auth/login/password', payload)
    return response.data
  },

  sendEmailCode: async (payload: { email: string; captchaTicket: string }): Promise<ApiResponse<EmailCodeSendResponse>> => {
    const response = await api.post('/auth/login/email/send-code', payload)
    return response.data
  },

  loginByEmailCode: async (payload: { email: string; code: string; captchaTicket: string }): Promise<ApiResponse<AuthTokenResponse>> => {
    const response = await api.post('/auth/login/email', payload)
    return response.data
  },

  me: async (): Promise<ApiResponse<AuthUserProfile>> => {
    const response = await api.get('/auth/me')
    return response.data
  },

  changePassword: async (payload: { currentPassword: string; newPassword: string }): Promise<ApiResponse<void>> => {
    const response = await api.put('/auth/password', payload)
    return response.data
  },

  logout: async (): Promise<ApiResponse<void>> => {
    const response = await api.post('/auth/logout')
    return response.data
  },

  githubAuthorize: async (redirect?: string): Promise<ApiResponse<GithubAuthorizeResponse>> => {
    const response = await api.get('/auth/oauth/github/authorize', { params: { redirect } })
    return response.data
  },

  githubExchange: async (payload: { code: string; state: string }): Promise<ApiResponse<GithubExchangeResponse>> => {
    const response = await api.post('/auth/oauth/github/exchange', payload)
    return response.data
  },

  faceStatus: async (): Promise<ApiResponse<FaceStatusResponse>> => {
    const response = await api.get('/auth/face/status')
    return response.data
  },

  registerFace: async (payload: { imageBase64: string }): Promise<ApiResponse<FaceStatusResponse>> => {
    const response = await api.post('/auth/face/register', payload)
    return response.data
  },

  toggleFaceRequired: async (payload: { required: boolean }): Promise<ApiResponse<FaceStatusResponse>> => {
    const response = await api.put('/auth/face/required', payload)
    return response.data
  },

  verifyFaceLogin: async (payload: { preAuthToken: string; imageBase64: string }): Promise<ApiResponse<AuthTokenResponse>> => {
    const response = await api.post('/auth/face/verify-login', payload)
    return response.data
  }
}

export const personalService = {
  insights: async (): Promise<ApiResponse<PersonalInsight>> => {
    const response = await api.get('/personal/insights')
    return response.data
  },

  listTaskTemplates: async (): Promise<ApiResponse<TaskTemplate[]>> => {
    const response = await api.get('/personal/task-templates')
    return response.data
  },

  createTaskFromTemplate: async (templateId: string): Promise<ApiResponse<ScheduledTask>> => {
    const response = await api.post(`/personal/task-templates/${templateId}/create`)
    return response.data
  },

  exportBackup: async (): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/personal/backup/export')
    return response.data
  },

  importBackup: async (payload: Record<string, any>, replaceExisting = false): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.post('/personal/backup/import', payload, { params: { replaceExisting } })
    return response.data
  }
}

export const authTokenStorage = {
  getAccessToken,
  getRefreshToken,
  setTokens,
  clearTokens
}

export default api
