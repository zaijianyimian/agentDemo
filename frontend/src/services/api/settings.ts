import type { ApiResponse, SystemSettings } from '@/types'
import { api } from './index'

export const settingsService = {
  getAll: async (): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/settings')
    return response.data
  },

  getSystem: async (): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/settings/system')
    return response.data
  },

  updateSystem: async (payload: SystemSettings): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/system', payload)
    return response.data
  },

  updateModel: async (payload: Record<string, any>): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/model', payload)
    return response.data
  },

  updateQdrant: async (payload: Record<string, any>): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/qdrant', payload)
    return response.data
  },

  updateSearch: async (payload: Record<string, any>): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/search', payload)
    return response.data
  },

  updateSchedule: async (payload: Record<string, any>): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/schedule', payload)
    return response.data
  },

  updateFile: async (payload: Record<string, any>): Promise<ApiResponse<any>> => {
    const response = await api.put('/settings/file', payload)
    return response.data
  },

  exportDataZip: async (): Promise<Blob> => {
    const response = await api.get('/settings/data/export', {
      responseType: 'blob'
    })
    return response.data
  },

  importDataZip: async (file: File, replaceExisting = true): Promise<ApiResponse<Record<string, any>>> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post('/settings/data/import', formData, {
      params: { replaceExisting },
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return response.data
  },

  uploadImage: async (file: File): Promise<ApiResponse<string>> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post('/file/upload/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return response.data
  }
}