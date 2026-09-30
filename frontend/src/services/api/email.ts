import type { ApiResponse, EmailConfig } from '@/types'
import { api } from './index'

// 邮件配置服务
//
// ⚠️ EmailController 的返回并不统一：listConfigs / listEnabledConfigs / getConfig /
// getTemplates 返回裸 List，getMessage / getListenerStatus / getListenerOptions 返回裸 Map，
// testConfig / checkNetwork 等返回 ResponseEntity<Map>，其余写操作返回 ResponseEntity<String>
// （纯文本）。仓库里没有 ResponseBodyAdvice 做统一信封包装。
//
// 因此这里如实声明为联合类型，而不是谎称一律是 ApiResponse 信封——
// 谎报信封会让新写的调用方直接读 res.data 而拿到 undefined。
// 调用方需像 EmailConfig.vue 的 parseConfigList / parseObjectPayload 那样做形状归一化。
type EmailEnvelope<T> = ApiResponse<T> | T

export const emailService = {
  listConfigs: async (): Promise<EmailEnvelope<EmailConfig[]>> => {
    const response = await api.get('/email/config/list')
    return response.data
  },

  createConfig: async (data: Partial<EmailConfig>): Promise<EmailEnvelope<EmailConfig>> => {
    const response = await api.post('/email/config', data)
    return response.data
  },

  updateConfig: async (data: Partial<EmailConfig>): Promise<EmailEnvelope<EmailConfig>> => {
    const response = await api.put('/email/config', data)
    return response.data
  },

  deleteConfig: async (id: number): Promise<EmailEnvelope<void>> => {
    const response = await api.delete(`/email/config/${id}`)
    return response.data
  },

  startListener: async (id: number): Promise<EmailEnvelope<void>> => {
    const response = await api.post(`/email/listener/start/${id}`)
    return response.data
  },

  stopListener: async (id: number): Promise<EmailEnvelope<void>> => {
    const response = await api.post(`/email/listener/stop/${id}`)
    return response.data
  },

  getListenerStatus: async (): Promise<EmailEnvelope<any>> => {
    const response = await api.get('/email/listener/status')
    return response.data
  },

  getListenerOptions: async (): Promise<EmailEnvelope<any>> => {
    const response = await api.get('/email/listener/options')
    return response.data
  },

  getTemplates: async (): Promise<EmailEnvelope<any>> => {
    const response = await api.get('/email/templates')
    return response.data
  },

  getEnabledConfigs: async (): Promise<EmailEnvelope<EmailConfig[]>> => {
    const response = await api.get('/email/config/enabled')
    return response.data
  },

  getConfig: async (id: number): Promise<EmailEnvelope<EmailConfig>> => {
    const response = await api.get(`/email/config/${id}`)
    return response.data
  },

  reloadListeners: async (): Promise<EmailEnvelope<void>> => {
    const response = await api.post('/email/listener/reload')
    return response.data
  },

  // 测试已保存的邮箱配置
  testConfig: async (id: number): Promise<EmailEnvelope<{
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
  testNewConfig: async (data: Partial<EmailConfig>): Promise<EmailEnvelope<{
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
  checkNetwork: async (id: number): Promise<EmailEnvelope<{
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
  checkNewConfigNetwork: async (data: Partial<EmailConfig>): Promise<EmailEnvelope<{
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
  getMessage: async (messageId: string): Promise<EmailEnvelope<any>> => {
    const response = await api.get(`/email/messages/${encodeURIComponent(messageId)}`)
    return response.data
  }
}