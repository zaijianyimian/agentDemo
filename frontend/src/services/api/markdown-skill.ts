import type { ApiResponse } from '@/types'
import { api } from './index'

/**
 * markdown skill 元数据（不含 body）
 */
export interface MarkdownSkillSummary {
  name: string
  description: string
  license?: string
  compatibility?: string
  tags?: string[]
  filePath?: string
  sizeBytes: number
  loadedAt?: string | number[] | null
}

/**
 * markdown skill 完整内容（含 body）
 */
export interface MarkdownSkillDetail extends MarkdownSkillSummary {
  body: string
}

/**
 * markdown skill 服务
 */
export const markdownSkillService = {
  // 列出所有 skill
  list: async (): Promise<ApiResponse<MarkdownSkillSummary[]>> => {
    const response = await api.get('/markdown-skill/list')
    return response.data
  },

  // 获取单个 skill 详情
  get: async (name: string): Promise<ApiResponse<MarkdownSkillDetail>> => {
    const response = await api.get(`/markdown-skill/${encodeURIComponent(name)}`)
    return response.data
  },

  // 给定查询返回最匹配的 skill
  match: async (query: string): Promise<ApiResponse<{ name: string; description: string; score: number } | null>> => {
    const response = await api.get('/markdown-skill/match', { params: { query } })
    return response.data
  },

  // 触发全量 reload
  reload: async (): Promise<ApiResponse<number>> => {
    const response = await api.post('/markdown-skill/reload')
    return response.data
  }
}