import axios from 'axios'
import type { ApiResponse, Document, ScheduleEvent, EmailConfig, McpTool, Skill, SearchResult, ChatSession, ChatMessageEntity, Note, CodeSnippet, CodeGenerateRequest, CodeGenerateResponse } from '@/types'

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

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

// 定时任务服务
export const taskService = {
  // 获取任务列表
  list: async (): Promise<ApiResponse<any[]>> => {
    const response = await api.get('/task/list')
    return response.data
  },

  // 获取任务详情
  get: async (id: number): Promise<ApiResponse<any>> => {
    const response = await api.get(`/task/${id}`)
    return response.data
  },

  // 创建任务
  create: async (data: Partial<any>): Promise<ApiResponse<any>> => {
    const response = await api.post('/task', data)
    return response.data
  },

  // 更新任务
  update: async (id: number, data: Partial<any>): Promise<ApiResponse<any>> => {
    const response = await api.put(`/task/${id}`, data)
    return response.data
  },

  // 删除任务
  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/task/${id}`)
    return response.data
  },

  // 启用/禁用任务
  toggle: async (id: number): Promise<ApiResponse<any>> => {
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

export default api