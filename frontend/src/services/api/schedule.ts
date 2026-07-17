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

  parseEmail: async (payload: { subject: string; from: string; content: string }): Promise<ApiResponse<ScheduleEvent>> => {
    const response = await api.post('/schedule/parse-email', payload)
    return response.data
  },

  parseAndSave: async (payload: { subject: string; from: string; content: string }): Promise<ApiResponse<ScheduleEvent>> => {
    const response = await api.post('/schedule/parse-and-save', payload)
    return response.data
  },

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