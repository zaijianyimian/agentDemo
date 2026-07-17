import type { ApiResponse, AuthTokenResponse, AuthUserProfile, EmailCodeSendResponse, GithubAuthorizeResponse, GithubExchangeResponse, FaceStatusResponse } from '@/types'
import { api } from './index'
import {
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens
} from '@/services/auth-token'

export const authService = {
  hasUsers: async (): Promise<ApiResponse<boolean>> => {
    const response = await api.get('/auth/has-users')
    return response.data
  },

  register: async (payload: { username: string; email: string; password: string; displayName?: string }): Promise<ApiResponse<EmailCodeSendResponse>> => {
    const response = await api.post('/auth/register', payload)
    return response.data
  },

  loginByPassword: async (payload: { username: string; password: string }): Promise<ApiResponse<AuthTokenResponse>> => {
    const response = await api.post('/auth/login/password', payload)
    return response.data
  },

  sendEmailCode: async (payload: { email: string }): Promise<ApiResponse<EmailCodeSendResponse>> => {
    const response = await api.post('/auth/login/email/send-code', payload)
    return response.data
  },

  loginByEmailCode: async (payload: { email: string; code: string }): Promise<ApiResponse<AuthTokenResponse>> => {
    const response = await api.post('/auth/login/email', payload)
    return response.data
  },

  me: async (): Promise<ApiResponse<AuthUserProfile>> => {
    const response = await api.get('/auth/me')
    return response.data
  },

  changePassword: async (payload: { currentPassword: string; newPassword: string }): Promise<ApiResponse<void>> => {
    const response = await api.put('/auth/password', payload)
    return response.data
  },

  logout: async (): Promise<ApiResponse<void>> => {
    const response = await api.post('/auth/logout')
    return response.data
  },

  githubAuthorize: async (redirect?: string): Promise<ApiResponse<GithubAuthorizeResponse>> => {
    const response = await api.get('/auth/oauth/github/authorize', { params: { redirect } })
    return response.data
  },

  githubExchange: async (payload: { code: string; state: string }): Promise<ApiResponse<GithubExchangeResponse>> => {
    const response = await api.post('/auth/oauth/github/exchange', payload)
    return response.data
  },

  faceStatus: async (): Promise<ApiResponse<FaceStatusResponse>> => {
    const response = await api.get('/auth/face/status')
    return response.data
  },

  registerFace: async (payload: { imageBase64: string }): Promise<ApiResponse<FaceStatusResponse>> => {
    const response = await api.post('/auth/face/register', payload)
    return response.data
  },

  toggleFaceRequired: async (payload: { required: boolean }): Promise<ApiResponse<FaceStatusResponse>> => {
    const response = await api.put('/auth/face/required', payload)
    return response.data
  },

  verifyFaceLogin: async (payload: { preAuthToken: string; imageBase64: string }): Promise<ApiResponse<AuthTokenResponse>> => {
    const response = await api.post('/auth/face/verify-login', payload)
    return response.data
  },

  sendResetPasswordCode: async (payload: { email: string }): Promise<ApiResponse<EmailCodeSendResponse>> => {
    const response = await api.post('/auth/password/reset/send-code', payload)
    return response.data
  },

  resetPassword: async (payload: { email: string; code: string; newPassword: string }): Promise<ApiResponse<void>> => {
    const response = await api.post('/auth/password/reset', payload)
    return response.data
  }
}

export const authTokenStorage = {
  getAccessToken,
  getRefreshToken,
  setTokens,
  clearTokens
}