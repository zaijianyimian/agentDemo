import type { ApiResponse, InboxSummary } from '@/types'
import { api } from './index'

export const inboxService = {
  summary: async (limit = 18): Promise<ApiResponse<InboxSummary>> => {
    const response = await api.get('/inbox/summary', { params: { limit } })
    return response.data
  }
}