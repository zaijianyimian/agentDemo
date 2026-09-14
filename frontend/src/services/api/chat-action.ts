import type { ApiResponse, ChatActionResult } from '@/types'
import { api } from './index'

export const chatActionService = {
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
