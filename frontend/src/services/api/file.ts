import type { ApiResponse, Document } from '@/types'
import { api } from './index'

// 文件上传服务
export const fileService = {
  // 上传文件
  upload: async (file: File): Promise<ApiResponse<Document>> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post('/file/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return response.data
  },

  // 获取文件列表
  list: async (): Promise<ApiResponse<Document[]>> => {
    const response = await api.get('/file/list')
    return response.data
  },

  // 获取文件详情
  get: async (id: number): Promise<ApiResponse<Document>> => {
    const response = await api.get(`/file/${id}`)
    return response.data
  },

  // 删除文件
  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/file/${id}`)
    return response.data
  },

  // 搜索文件
  search: async (minImportance?: number, maxImportance?: number): Promise<ApiResponse<Document[]>> => {
    const params = new URLSearchParams()
    if (minImportance) params.append('minImportance', minImportance.toString())
    if (maxImportance) params.append('maxImportance', maxImportance.toString())
    const response = await api.get(`/file/search?${params.toString()}`)
    return response.data
  }
}