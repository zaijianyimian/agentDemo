import type { ApiResponse, McpTool } from '@/types'
import { api } from './index'

// MCP工具服务
export const mcpToolService = {
  list: async (): Promise<ApiResponse<McpTool[]>> => {
    const response = await api.get('/mcp/tools')
    return response.data
  },

  getEnabled: async (): Promise<ApiResponse<McpTool[]>> => {
    const response = await api.get('/mcp/tools/enabled')
    return response.data
  },

  get: async (id: number): Promise<ApiResponse<McpTool>> => {
    const response = await api.get(`/mcp/tools/${id}`)
    return response.data
  },

  create: async (data: Partial<McpTool>): Promise<ApiResponse<McpTool>> => {
    const response = await api.post('/mcp/tools', data)
    return response.data
  },

  update: async (id: number, data: Partial<McpTool>): Promise<ApiResponse<McpTool>> => {
    const response = await api.put(`/mcp/tools/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/mcp/tools/${id}`)
    return response.data
  },

  toggle: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.put(`/mcp/tools/${id}/toggle`)
    return response.data
  },

  execute: async (name: string, params: any): Promise<ApiResponse<any>> => {
    const response = await api.post(`/mcp/tools/${name}/execute`, params)
    return response.data
  },

  test: async (id: number): Promise<ApiResponse<any>> => {
    const response = await api.post(`/mcp/tools/${id}/test`)
    return response.data
  },

  validate: async (id: number): Promise<ApiResponse<any>> => {
    const response = await api.post(`/mcp/tools/${id}/validate`)
    return response.data
  }
}