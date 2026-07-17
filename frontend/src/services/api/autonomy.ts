import type { ApiResponse, AutonomyScanReport, AutonomyVerificationResult, AutonomyDraftResponse, AutonomyArtifact, AutonomyDiff } from '@/types'
import { api } from './index'

export const autonomyService = {
  capabilities: async (): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/autonomy/capabilities')
    return response.data
  },

  scan: async (): Promise<ApiResponse<AutonomyScanReport>> => {
    const response = await api.post('/autonomy/scan')
    return response.data
  },

  verify: async (backend = true, frontend = true): Promise<ApiResponse<AutonomyVerificationResult>> => {
    const response = await api.post('/autonomy/verify', { backend, frontend })
    return response.data
  },

  draft: async (target = 'general', includeVerification = true): Promise<ApiResponse<AutonomyDraftResponse>> => {
    const response = await api.post('/autonomy/draft', { target, includeVerification })
    return response.data
  },

  history: async (limit = 12): Promise<ApiResponse<AutonomyArtifact[]>> => {
    const response = await api.get('/autonomy/history', { params: { limit } })
    return response.data
  },

  readArtifact: async (path: string): Promise<ApiResponse<string>> => {
    const response = await api.get('/autonomy/artifact', { params: { path } })
    return response.data
  },

  diff: async (): Promise<ApiResponse<AutonomyDiff>> => {
    const response = await api.get('/autonomy/diff')
    return response.data
  }
}