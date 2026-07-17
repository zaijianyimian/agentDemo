import type { ApiResponse, McpTool, Skill } from '@/types'
import { api } from './index'

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

  getEnabled: async (): Promise<ApiResponse<Skill[]>> => {
    const response = await api.get('/skill/enabled')
    return response.data
  },

  getByCategory: async (category: string): Promise<ApiResponse<Skill[]>> => {
    const response = await api.get(`/skill/category/${category}`)
    return response.data
  },

  getByCode: async (code: string): Promise<ApiResponse<Skill>> => {
    const response = await api.get(`/skill/code/${code}`)
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

  toggle: async (id: number): Promise<ApiResponse<Skill>> => {
    const response = await api.put(`/skill/${id}/toggle`)
    return response.data
  },

  bindTool: async (skillId: number, toolId: number): Promise<ApiResponse<void>> => {
    const response = await api.post(`/skill/${skillId}/tools/${toolId}`)
    return response.data
  },

  unbindTool: async (skillId: number, toolId: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/skill/${skillId}/tools/${toolId}`)
    return response.data
  },

  getTools: async (id: number): Promise<ApiResponse<McpTool[]>> => {
    const response = await api.get(`/skill/${id}/tools`)
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
  },

  test: async (id: number): Promise<ApiResponse<any>> => {
    const response = await api.post(`/skill/${id}/test`)
    return response.data
  }
}