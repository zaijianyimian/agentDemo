import type { ApiResponse } from '@/types'
import { api } from './index'

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