import type { ApiResponse, GeneratedReport, ReportArtifact } from '@/types'
import { api } from './index'

export const reportService = {
  generate: async (period: 'daily' | 'weekly'): Promise<ApiResponse<GeneratedReport>> => {
    const response = await api.post('/report/generate', null, { params: { period } })
    return response.data
  },

  history: async (limit = 12): Promise<ApiResponse<ReportArtifact[]>> => {
    const response = await api.get('/report/history', { params: { limit } })
    return response.data
  },

  readArtifact: async (path: string): Promise<ApiResponse<string>> => {
    const response = await api.get('/report/artifact', { params: { path } })
    return response.data
  }
}