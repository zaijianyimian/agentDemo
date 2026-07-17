import type { ApiResponse, SearchResult } from '@/types'
import { api } from './index'

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