import type { ApiResponse, ScheduleEvent } from '@/types'
import { api } from './index'

// 日程服务
export const scheduleService = {
  list: async (): Promise<ApiResponse<ScheduleEvent[]>> => {
    const response = await api.get('/schedule/list')
    return response.data
  },

  today: async (): Promise<ApiResponse<ScheduleEvent[]>> => {
    const response = await api.get('/schedule/today')
    return response.data
  },

  tomorrow: async (): Promise<ApiResponse<ScheduleEvent[]>> => {
    const response = await api.get('/schedule/tomorrow')
    return response.data
  },

  getByDate: async (date: string): Promise<ApiResponse<ScheduleEvent[]>> => {
    const response = await api.get(`/schedule/date/${date}`)
    return response.data
  },

  latest: async (limit = 5): Promise<ApiResponse<ScheduleEvent[]>> => {
    const response = await api.get('/schedule/latest', { params: { limit } })
    return response.data
  },

  range: async (startDate: string, endDate: string): Promise<ApiResponse<ScheduleEvent[]>> => {
    const response = await api.get('/schedule/range', { params: { startDate, endDate } })
    return response.data
  },

  get: async (id: number): Promise<ApiResponse<ScheduleEvent>> => {
    const response = await api.get(`/schedule/${id}`)
    return response.data
  },

  create: async (data: Partial<ScheduleEvent>): Promise<ApiResponse<ScheduleEvent>> => {
    const response = await api.post('/schedule', data)
    return response.data
  },

  update: async (id: number, data: Partial<ScheduleEvent>): Promise<ApiResponse<ScheduleEvent>> => {
    const response = await api.put(`/schedule/${id}`, data)
    return response.data
  },

  delete: async (id: number): Promise<ApiResponse<void>> => {
    const response = await api.delete(`/schedule/${id}`)
    return response.data
  },

  complete: async (id: number): Promise<ApiResponse<ScheduleEvent>> => {
    const response = await api.put(`/schedule/${id}/complete`)
    return response.data
  },

  cancel: async (id: number): Promise<ApiResponse<ScheduleEvent>> => {
    const response = await api.put(`/schedule/${id}/cancel`)
    return response.data
  },

  // 说明：自然语言解析日程（parseEmail / parseAndSave）已迁移到 Python Agent Engine，
  // Java 不再提供对应端点，因此这里不再声明这两个方法，避免调用方拿到必然 404 的接口。
  // 需要用自然语言创建日程时，请使用聊天页（/chat），Agent 会创建后回到本列表。
  // 本页的「AI添加日程」入口会把描述带到聊天页。

  listFiles: async (): Promise<string[]> => {
    const response = await api.get('/schedule/files')
    return response.data
  },

  getFileByDate: async (date: string): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get(`/schedule/file/date/${date}`)
    return response.data
  },

  getFileByName: async (fileName: string): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get(`/schedule/file/${fileName}`)
    return response.data
  },

  getFileByEventId: async (id: number): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get(`/schedule/${id}/file`)
    return response.data
  },

  getSharedSchedule: async (date: string): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get(`/schedule/share/${date}`)
    return response.data
  }
}