import type { ApiResponse, PersonalInsight, TaskTemplate, ScheduledTask } from '@/types'
import { api } from './index'

export const personalService = {
  insights: async (): Promise<ApiResponse<PersonalInsight>> => {
    const response = await api.get('/personal/insights')
    return response.data
  },

  listTaskTemplates: async (): Promise<ApiResponse<TaskTemplate[]>> => {
    const response = await api.get('/personal/task-templates')
    return response.data
  },

  createTaskFromTemplate: async (templateId: string): Promise<ApiResponse<ScheduledTask>> => {
    const response = await api.post(`/personal/task-templates/${templateId}/create`)
    return response.data
  },

  exportBackup: async (): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/personal/backup/export')
    return response.data
  },

  importBackup: async (payload: Record<string, any>, replaceExisting = false): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.post('/personal/backup/import', payload, { params: { replaceExisting } })
    return response.data
  }
}