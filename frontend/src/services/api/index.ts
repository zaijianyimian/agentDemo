import axios from 'axios'
import type { ApiResponse, AuthTokenResponse } from '@/types'
import {
  buildLoginRedirectUrl,
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens
} from '@/services/auth-token'

// 统一 API 客户端：支持可配置后端地址、认证注入、令牌刷新和标准错误消息透传。

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'

const publicAuthRoutes = [
  '/auth/register',
  '/auth/has-users',
  '/auth/login/',
  '/auth/token/refresh',
  '/auth/face/verify-login',
  '/auth/oauth/github/',
  '/auth/password/reset'
]

const isPublicAuthRoute = (requestUrl: string): boolean =>
  publicAuthRoutes.some(route => requestUrl.startsWith(route))

const api = axios.create({
  baseURL: apiBaseUrl,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(config => {
  const requestUrl = String(config.url || '')
  if (isPublicAuthRoute(requestUrl)) {
    if (config.headers && 'Authorization' in config.headers) {
      delete (config.headers as Record<string, string>).Authorization
    }
    return config
  }

  const token = getAccessToken()
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshingPromise: Promise<string> | null = null

type ApiErrorPayload = ApiResponse<unknown> & {
  code?: string
  error?: string
  timestamp?: string
  details?: Record<string, unknown>
}

const applyBackendErrorMessage = (error: any): void => {
  const payload = error?.response?.data as ApiErrorPayload | undefined
  if (!payload) {
    return
  }

  if (payload.message) {
    error.message = payload.message
  }

  const apiCode = payload.code || payload.error
  if (apiCode) {
    error.apiCode = apiCode
  }

  if (payload.details) {
    error.apiDetails = payload.details
  }
}

api.interceptors.response.use(
  response => response,
  async error => {
    const originalRequest = error.config as any
    const status = error?.response?.status
    const requestUrl = String(originalRequest?.url || '')
    const shouldSkipRefresh = isPublicAuthRoute(requestUrl)

    if (
      status === 401 &&
      !originalRequest?._retry &&
      !shouldSkipRefresh
    ) {
      const refreshToken = getRefreshToken()
      if (refreshToken) {
        originalRequest._retry = true
        try {
          if (!refreshingPromise) {
            refreshingPromise = api
              .post('/auth/token/refresh', { refreshToken })
              .then(resp => {
                const payload = resp.data as ApiResponse<AuthTokenResponse>
                if (!payload?.success || !payload.data) {
                  throw new Error(payload?.message || '刷新令牌失败')
                }

                const accessToken = payload.data.accessToken
                const refreshTokenNext = payload.data.refreshToken
                if (!accessToken || !refreshTokenNext) {
                  throw new Error('刷新令牌失败')
                }

                setTokens(accessToken, refreshTokenNext)
                return accessToken
              })
              .finally(() => {
                refreshingPromise = null
              })
          }

          const latestAccessToken = await refreshingPromise
          originalRequest.headers = originalRequest.headers || {}
          originalRequest.headers.Authorization = `Bearer ${latestAccessToken}`
          return api(originalRequest)
        } catch (_error) {
          clearTokens()
          if (window.location.pathname !== '/login') {
            window.location.href = buildLoginRedirectUrl(
              window.location.pathname + window.location.search
            )
          }
        }
      } else if (window.location.pathname !== '/login') {
        window.location.href = buildLoginRedirectUrl(
          window.location.pathname + window.location.search
        )
      }
    }

    applyBackendErrorMessage(error)
    return Promise.reject(error)
  }
)

export { api }
export default api
