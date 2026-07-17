import type { ApiResponse, ScheduledTask, JobLog, JobLogPage } from '@/types'
import { api } from './index'

// 定时任务服务
export const taskService = {
  // 获取任务列表
  list: async (): Promise<ApiResponse<ScheduledTask[]>> => {
    const response = await api.get('/task/list')
    return response.data
  },

  // 获取任务详情
  get: async (id: number): Promise<ApiResponse<ScheduledTask>> => {
    const response = await api.get(`/task/${id}`)
    return response.data
  },

  // 创建任务
  create: async (data: Partial<ScheduledTask>): Promise<ApiResponse<ScheduledTask>> => {
    const response = await api.post('/task', data)
    return response.data
  },

  // 更新任务
  update: async (id: number, data: Partial<ScheduledTask>): Promise<ApiResponse<ScheduledTask>> => {
    const response = await api.put(`/task/${id}`, data)
    return response.data
  },

  // 删除任务
  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/task/${id}`)
    return response.data
  },

  // 启用/禁用任务
  toggle: async (id: number): Promise<ApiResponse<ScheduledTask>> => {
    const response = await api.put(`/task/${id}/toggle`)
    return response.data
  },

  // 手动执行任务
  execute: async (id: number): Promise<ApiResponse<string>> => {
    const response = await api.post(`/task/${id}/execute`)
    return response.data
  },

  // 执行日志分页
  logs: async (id: number, page = 1, size = 20): Promise<ApiResponse<JobLogPage>> => {
    const response = await api.get(`/task/${id}/logs`, { params: { page, size } })
    return response.data
  },

  // 最近 N 条日志
  recentLogs: async (id: number, limit = 10): Promise<ApiResponse<JobLog[]>> => {
    const response = await api.get(`/task/${id}/logs/recent`, { params: { limit } })
    return response.data
  }
}