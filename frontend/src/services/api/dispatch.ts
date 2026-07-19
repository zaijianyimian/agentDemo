import type { ApiResponse } from '@/types'
import { api } from './index'

export interface DispatchedTaskSummary {
  id: number
  emailId: number
  subject?: string
  importance?: string
  executorHint?: string
  fallbackExecutor?: string
  sandboxLevel?: string
  status?: string
  retries?: number
  executorUsed?: string
  resultPath?: string
  pushStatus?: string
  errorMessage?: string
  createdAt?: string
  finishedAt?: string
}

export interface PushConfigPayload {
  id?: number
  pushEmail?: string
  pushThreshold?: 'high' | 'medium' | 'low'
  batchCron?: string
  immediateEnabled?: boolean
  workspaceMaxCount?: number
  workspaceMaxAgeDays?: number
  retryMax?: number
  executorTimeoutSeconds?: number
}

export const dispatchedService = {
  list: async (page = 1, size = 20): Promise<ApiResponse<{ records: DispatchedTaskSummary[]; total: number }>> => {
    const response = await api.get('/dispatched', { params: { page, size } })
    return response.data
  },

  get: async (id: number): Promise<ApiResponse<DispatchedTaskSummary>> => {
    const response = await api.get(`/dispatched/${id}`)
    return response.data
  },

  cancel: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.post(`/dispatched/${id}/cancel`)
    return response.data
  },

  rerun: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.post(`/dispatched/${id}/rerun`)
    return response.data
  },

  executorAvailability: async (): Promise<ApiResponse<Record<string, boolean>>> => {
    const response = await api.get('/dispatched/executors/availability')
    return response.data
  },

  /**
   * 拉取 Settings 页面配置的 executor 启停状态（如 {claude-code: true, codex: false}）。
   */
  executorSettings: async (): Promise<ApiResponse<Record<string, boolean>>> => {
    const response = await api.get('/dispatched/executors/settings')
    return response.data
  },

  /**
   * 一次性保存所有 executor 启停状态。
   */
  updateExecutorSettings: async (
    states: Record<string, boolean>
  ): Promise<ApiResponse<Record<string, boolean>>> => {
    const response = await api.put('/dispatched/executors/settings', states)
    return response.data
  }
}

export const pushConfigService = {
  get: async (): Promise<ApiResponse<PushConfigPayload>> => {
    const response = await api.get('/dispatch/push-config')
    return response.data
  },

  update: async (payload: PushConfigPayload): Promise<ApiResponse<PushConfigPayload>> => {
    const response = await api.put('/dispatch/push-config', payload)
    return response.data
  }
}