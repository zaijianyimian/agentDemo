import type { ApiResponse, AiModelConfig } from '@/types'
import { api } from './index'

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
  },

  // 获取可用模型列表（按优先级排序，包含健康状态）
  available: async (): Promise<ApiResponse<AiModelConfig[]>> => {
    const response = await api.get('/model/available')
    return response.data
  },

  health: async (): Promise<ApiResponse<Array<Record<string, any>>>> => {
    const response = await api.get('/model/health')
    return response.data
  }
}