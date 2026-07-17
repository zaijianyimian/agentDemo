import type { ApiResponse, KnowledgeBase, KnowledgeDocument } from '@/types'
import { api } from './index'

export const knowledgeService = {
  list: async (): Promise<ApiResponse<KnowledgeBase[]>> => {
    const response = await api.get('/knowledge/list')
    return response.data
  },

  get: async (id: number): Promise<ApiResponse<KnowledgeBase>> => {
    const response = await api.get(`/knowledge/${id}`)
    return response.data
  },

  create: async (data: Partial<KnowledgeBase>): Promise<ApiResponse<KnowledgeBase>> => {
    const response = await api.post('/knowledge', data)
    return response.data
  },

  update: async (id: number, data: Partial<KnowledgeBase>): Promise<ApiResponse<KnowledgeBase>> => {
    const response = await api.put(`/knowledge/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/knowledge/${id}`)
    return response.data
  },

  toggle: async (id: number): Promise<ApiResponse<KnowledgeBase>> => {
    const response = await api.put(`/knowledge/${id}/toggle`)
    return response.data
  },

  upload: async (baseId: number, file: File): Promise<ApiResponse<KnowledgeDocument>> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post(`/knowledge/${baseId}/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return response.data
  },

  listDocuments: async (baseId: number): Promise<ApiResponse<KnowledgeDocument[]>> => {
    const response = await api.get(`/knowledge/${baseId}/documents`)
    return response.data
  },

  deleteDocument: async (docId: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/knowledge/document/${docId}`)
    return response.data
  },

  query: async (baseId: number, question: string, topK = 5): Promise<ApiResponse<string>> => {
    const response = await api.get(`/knowledge/${baseId}/query`, { params: { question, topK } })
    return response.data
  },

  search: async (baseId: number, query: string, topK = 10): Promise<ApiResponse<any[]>> => {
    const response = await api.get(`/knowledge/${baseId}/search`, { params: { query, topK } })
    return response.data
  }
}