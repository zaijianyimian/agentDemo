import type { ApiResponse, CodeGenerateRequest, CodeGenerateResponse } from '@/types'
import { api } from './index'

// 代码生成服务
export const codeGenService = {
  // 获取支持的代码类型
  getTypes: async (): Promise<ApiResponse<string[]>> => {
    const response = await api.get('/code/types')
    return response.data
  },

  // 生成代码
  generate: async (request: CodeGenerateRequest): Promise<ApiResponse<CodeGenerateResponse>> => {
    const response = await api.post('/code/generate', request)
    return response.data
  },

  // 保存代码到文件
  save: async (code: string, fileName: string, subDir?: string): Promise<ApiResponse<CodeGenerateResponse>> => {
    const response = await api.post('/code/save', { code, fileName, subDir })
    return response.data
  },

  // 代码审查
  review: async (code: string, language: string): Promise<ApiResponse<string>> => {
    const response = await api.post('/code/review', { code, language })
    return response.data
  },

  // 代码转换
  convert: async (code: string, fromLanguage: string, toLanguage: string): Promise<ApiResponse<string>> => {
    const response = await api.post('/code/convert', { code, fromLanguage, toLanguage })
    return response.data
  },

  // 分析项目结构
  analyze: async (path?: string): Promise<ApiResponse<Record<string, any>>> => {
    const response = await api.get('/code/analyze', { params: { path } })
    return response.data
  }
}