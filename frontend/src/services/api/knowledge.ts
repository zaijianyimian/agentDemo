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

import { fetchWithAuth } from '@/services/auth-fetch'

export type SourceKind = 'document' | 'memory'
export interface KnowledgeRecord {
  id: number
  kind: SourceKind
  title: string
  status: string
  attempts: number
  pages?: string[]
  source_url?: string
}
export interface KnowledgeHit {
  id: number
  title: string
  kind: SourceKind
  page: number
  snippet: string
  score: number
  source_url: string
}
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetchWithAuth(`/ai/knowledge${path}`, init)
  if (!response.ok) {
    let message = `请求失败 (${response.status})`
    try {
      const body = await response.json()
      if (typeof body.detail === 'string') message = body.detail
      else if (response.status === 422) message = '请检查输入内容和长度'
    } catch { /* 保留 HTTP 状态 */ }
    throw new Error(message)
  }
  return response.json() as Promise<T>
}
const json = (method: string, body: unknown): RequestInit => ({
  method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
})
export const knowledgeSourceService = {
  preferences: () => request<{ automatic: boolean; available: boolean }>('/memory/preferences'),
  setPreferences: (automatic: boolean) => request<{ automatic: boolean; available: boolean }>(
    '/memory/preferences', json('PUT', { automatic })),
  list: (kind: SourceKind, offset = 0) => request<KnowledgeRecord[]>(`/records?kind=${kind}&offset=${offset}`),
  get: (id: number) => request<KnowledgeRecord>(`/records/${id}`),
  upload: (file: File) => request<{ id: number; message: string }>(`/documents?filename=${encodeURIComponent(file.name)}`, {
    method: 'POST', headers: { 'Content-Type': 'application/octet-stream' }, body: file
  }),
  saveMemory: (title: string, content: string, id?: number) => request<{ id: number; message: string }>(
    id ? `/memories/${id}` : '/memories', json(id ? 'PUT' : 'POST', { title, content })),
  delete: (id: number) => request<{ message: string }>(`/records/${id}`, { method: 'DELETE' }),
  search: (kind: SourceKind, query: string) => request<{ results: KnowledgeHit[]; message: string }>(
    `/${kind}/search`, json('POST', { query, limit: 10 })),
  rebuild: (kind: SourceKind) => request<{ message: string }>(`/${kind}/rebuild`, { method: 'POST' })
}
