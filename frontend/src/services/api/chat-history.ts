import type { ApiResponse, ChatSession, ChatMessageEntity } from '@/types'
import { api } from './index'

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