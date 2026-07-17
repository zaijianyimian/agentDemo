import type { ApiResponse } from '@/types'
import { api } from './index'

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