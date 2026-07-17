import axios from 'axios'
import type { ApiResponse, AuthTokenResponse } from '@/types'
import {
  buildLoginRedirectUrl,
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens
} from '@/services/auth-token'

// Axios 实例：全局 /api baseURL、自动注入 Bearer token、401 时刷新一次并重试原请求

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(config => {
  const requestUrl = String(config.url || '')
  const shouldSkipAuthHeader =
    requestUrl.startsWith('/auth/register') ||
    requestUrl.startsWith('/auth/has-users') ||
    requestUrl.startsWith('/auth/login/') ||
    requestUrl.startsWith('/auth/token/refresh') ||
    requestUrl.startsWith('/auth/face/verify-login') ||
    requestUrl.startsWith('/auth/oauth/github/') ||
    requestUrl.startsWith('/auth/password/reset')

  if (shouldSkipAuthHeader) {
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

api.interceptors.response.use(
  response => response,
  async (error) => {
    const originalRequest = error.config as any
    const status = error?.response?.status
    const requestUrl = String(originalRequest?.url || '')

    const shouldSkipRefresh =
      requestUrl.startsWith('/auth/token/refresh') ||
      requestUrl.startsWith('/auth/register') ||
      requestUrl.startsWith('/auth/login/') ||
      requestUrl.startsWith('/auth/face/verify-login')

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
            refreshingPromise = axios
              .post('/api/auth/token/refresh', { refreshToken })
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
        } catch (_e) {
          clearTokens()
          if (window.location.pathname !== '/login') {
            window.location.href = buildLoginRedirectUrl(window.location.pathname + window.location.search)
          }
        }
      } else if (window.location.pathname !== '/login') {
        window.location.href = buildLoginRedirectUrl(window.location.pathname + window.location.search)
      }
    }

    return Promise.reject(error)
  }
)

export { api }
export default api