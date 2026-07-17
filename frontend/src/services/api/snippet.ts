import type { ApiResponse, CodeSnippet } from '@/types'
import { api } from './index'

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