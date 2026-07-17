import type { ApiResponse, Note, NoteSemanticHit } from '@/types'
import { api } from './index'

// 笔记服务
export const noteService = {
  // 获取笔记列表
  list: async (): Promise<ApiResponse<Note[]>> => {
    const response = await api.get('/note/list')
    return response.data
  },

  // 获取笔记详情
  get: async (id: number): Promise<ApiResponse<Note>> => {
    const response = await api.get(`/note/${id}`)
    return response.data
  },

  // 创建笔记
  create: async (data: Partial<Note>): Promise<ApiResponse<Note>> => {
    const response = await api.post('/note', data)
    return response.data
  },

  // 更新笔记
  update: async (id: number, data: Partial<Note>): Promise<ApiResponse<Note>> => {
    const response = await api.put(`/note/${id}`, data)
    return response.data
  },

  // 删除笔记
  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/note/${id}`)
    return response.data
  },

  // AI 总结笔记
  summarize: async (id: number): Promise<ApiResponse<Note>> => {
    const response = await api.post(`/note/${id}/summarize`)
    return response.data
  },

  // 切换置顶状态
  togglePin: async (id: number): Promise<ApiResponse<Note>> => {
    const response = await api.put(`/note/${id}/pin`)
    return response.data
  },

  search: async (keyword: string): Promise<ApiResponse<Note[]>> => {
    const response = await api.get('/note/search', { params: { keyword } })
    return response.data
  },

  semanticSearch: async (query: string, topK = 5): Promise<ApiResponse<NoteSemanticHit[]>> => {
    const response = await api.get('/note/semantic-search', { params: { query, topK } })
    return response.data
  },

  reindex: async (): Promise<ApiResponse<number>> => {
    const response = await api.post('/note/reindex')
    return response.data
  }
}