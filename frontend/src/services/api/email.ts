import type { ApiResponse, EmailConfig } from '@/types'
import { api } from './index'

// 邮件配置服务
export const emailService = {
  listConfigs: async (): Promise<ApiResponse<EmailConfig[]>> => {
    const response = await api.get('/email/config/list')
    return response.data
  },

  createConfig: async (data: Partial<EmailConfig>): Promise<ApiResponse<EmailConfig>> => {
    const response = await api.post('/email/config', data)
    return response.data
  },

  updateConfig: async (data: Partial<EmailConfig>): Promise<ApiResponse<EmailConfig>> => {
    const response = await api.put('/email/config', data)
    return response.data
  },

  deleteConfig: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/email/config/${id}`)
    return response.data
  },

  startListener: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.post(`/email/listener/start/${id}`)
    return response.data
  },

  stopListener: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.post(`/email/listener/stop/${id}`)
    return response.data
  },

  getListenerStatus: async (): Promise<ApiResponse<any>> => {
    const response = await api.get('/email/listener/status')
    return response.data
  },

  getListenerOptions: async (): Promise<ApiResponse<any>> => {
    const response = await api.get('/email/listener/options')
    return response.data
  },

  getTemplates: async (): Promise<ApiResponse<any>> => {
    const response = await api.get('/email/templates')
    return response.data
  },

  getEnabledConfigs: async (): Promise<ApiResponse<EmailConfig[]>> => {
    const response = await api.get('/email/config/enabled')
    return response.data
  },

  getConfig: async (id: number): Promise<ApiResponse<EmailConfig>> => {
    const response = await api.get(`/email/config/${id}`)
    return response.data
  },

  reloadListeners: async (): Promise<ApiResponse<void>> => {
    const response = await api.post('/email/listener/reload')
    return response.data
  },

  // 测试已保存的邮箱配置
  testConfig: async (id: number): Promise<ApiResponse<{
    success: boolean
    message: string
    durationMs: number
    messageCount: number
    errorDetail: string
  }>> => {
    const response = await api.post(`/email/config/${id}/test`)
    return response.data
  },

  // 测试新邮箱配置（未保存的）
  testNewConfig: async (data: Partial<EmailConfig>): Promise<ApiResponse<{
    success: boolean
    message: string
    durationMs: number
    messageCount: number
    errorDetail: string
  }>> => {
    const response = await api.post('/email/config/test', data)
    return response.data
  },

  // 检查已保存配置的网络连通性（服务器 -> 邮件服务器）
  checkNetwork: async (id: number): Promise<ApiResponse<{
    success: boolean
    message: string
    durationMs: number
    resolvedIp: string
    errorDetail: string
  }>> => {
    const response = await api.get(`/email/config/${id}/network-check`)
    return response.data
  },

  // 检查新配置的网络连通性（未保存）
  checkNewConfigNetwork: async (data: Partial<EmailConfig>): Promise<ApiResponse<{
    success: boolean
    message: string
    durationMs: number
    resolvedIp: string
    errorDetail: string
  }>> => {
    const response = await api.post('/email/config/network-check', data)
    return response.data
  },

  // 邮件详情（前端 EmailDetail 页面用）
  getMessage: async (messageId: string): Promise<ApiResponse<any>> => {
    const response = await api.get(`/email/messages/${encodeURIComponent(messageId)}`)
    return response.data
  }
}