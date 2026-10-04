import type { ApiResponse } from '@/types'
import { api } from './index'

export interface BackupResult {
  fileName: string
  fileSize: number
}

export interface BackupFileInfo {
  fileName: string
  fileSize: number
  createdAt: string
}

// 数据备份服务：服务端 ZIP 快照的创建、列表、下载、删除与清理。
export const backupService = {
  create: async (): Promise<ApiResponse<BackupResult>> => {
    const response = await api.post('/backup/create')
    return response.data
  },

  list: async (): Promise<ApiResponse<BackupFileInfo[]>> => {
    const response = await api.get('/backup/list')
    return response.data
  },

  delete: async (fileName: string): Promise<ApiResponse<string>> => {
    const response = await api.delete(`/backup/${encodeURIComponent(fileName)}`)
    return response.data
  },

  cleanup: async (): Promise<ApiResponse<string>> => {
    const response = await api.post('/backup/cleanup')
    return response.data
  },

  // 下载走 blob：备份是 ZIP 二进制流，不能当 JSON 解析。
  download: async (fileName: string): Promise<Blob> => {
    const response = await api.get(`/backup/download/${encodeURIComponent(fileName)}`, {
      responseType: 'blob',
      timeout: 0
    })
    return response.data
  }
}
