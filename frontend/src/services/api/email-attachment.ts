import type { ApiResponse } from '@/types'
import { api } from './index'

/**
 * 邮件附件解析结果
 */
export interface EmailAttachmentAnalysis {
  id: number
  messageId: string
  accountEmail: string
  fileName: string
  contentType: string
  sizeBytes: number
  filePath: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED_SIZE' | 'SKIPPED_TYPE'
  skipReason: string
  summary: string
  rawText: string
  modelName: string
  errorDetail: string
  analyzedAt: string
  updateTime: string
}

/**
 * 附件元数据（来自 EmailMessage.Attachment）
 */
export interface EmailAttachment {
  fileName: string
  contentType: string
  size: number
  filePath: string
  contentId: string
  disposition: string
}

export const emailAttachmentService = {
  /**
   * 查询某封邮件的所有附件解析结果。
   * 注意：后端返回的是数组（直接返回 List），而非 ApiResponse。
   */
  listAnalyses: async (messageId: string): Promise<EmailAttachmentAnalysis[]> => {
    const response = await api.get(`/email/messages/${encodeURIComponent(messageId)}/analyses`)
    return response.data
  },

  /**
   * 获取附件原始文件 URL（用于预览 / 下载）。
   */
  fileUrl: (analysisId: number) =>
    `/api/email/analyses/${analysisId}/file`,

  /**
   * 手动重试某条解析记录。
   */
  retry: async (analysisId: number): Promise<ApiResponse<string>> => {
    const response = await api.post(`/email/analyses/${analysisId}/retry`)
    return response.data
  }
}
