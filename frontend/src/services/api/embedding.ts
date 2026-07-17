import type { ApiResponse } from '@/types'
import { api } from './index'

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