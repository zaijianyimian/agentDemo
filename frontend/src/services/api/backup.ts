import type { ApiResponse, BackupResult, BackupFileInfo, RestoreResult } from '@/types'
import { api } from './index'

export const backupService = {
  createBackup: async (): Promise<ApiResponse<BackupResult>> => {
    const response = await api.post('/backup/create')
    return response.data
  },

  createAndDownload: async (): Promise<Blob> => {
    const response = await api.post('/backup/download', null, {
      responseType: 'blob'
    })
    return response.data
  },

  restoreFromFile: async (file: File, replaceExisting = false): Promise<ApiResponse<RestoreResult>> => {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post('/backup/restore', formData, {
      params: { replace: replaceExisting },
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    return response.data
  },

  listBackups: async (): Promise<ApiResponse<BackupFileInfo[]>> => {
    const response = await api.get('/backup/list')
    return response.data
  },

  getBackupInfo: async (fileName: string): Promise<ApiResponse<BackupFileInfo>> => {
    const response = await api.get(`/backup/info/${encodeURIComponent(fileName)}`)
    return response.data
  },

  downloadBackup: async (fileName: string): Promise<Blob> => {
    const response = await api.get(`/backup/download/${encodeURIComponent(fileName)}`, {
      responseType: 'blob'
    })
    return response.data
  },

  deleteBackup: async (fileName: string): Promise<ApiResponse<string>> => {
    const response = await api.delete(`/backup/${encodeURIComponent(fileName)}`)
    return response.data
  },

  cleanupOldBackups: async (): Promise<ApiResponse<string>> => {
    const response = await api.post('/backup/cleanup')
    return response.data
  },

  getBackupConfig: async (): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/backup/config')
    return response.data
  }
}