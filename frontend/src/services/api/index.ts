import axios from 'axios'
import type { ApiResponse, AuthTokenResponse } from '@/types'
import {
  buildLoginRedirectUrl,
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens
} from '@/services/auth-token'
import {
  advanceSessionGeneration,
  captureSession,
  isCurrentSession,
  registerSessionController,
  type SessionSnapshot
} from '@/services/session-lifecycle'

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

type SessionRequestMetadata = {
  snapshot: SessionSnapshot
  unregister: () => void
}

api.interceptors.request.use(config => {
  const snapshot = captureSession()
  const controller = new AbortController()
  const callerSignal = config.signal
  if (callerSignal) {
    if (callerSignal.aborted) {
      controller.abort()
    } else {
      callerSignal.addEventListener?.('abort', () => controller.abort(), { once: true })
    }
  }
  config.signal = controller.signal
  ;(config as any)._sessionMetadata = {
    snapshot,
    unregister: registerSessionController(controller, snapshot.generation)
  } satisfies SessionRequestMetadata

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

// 标记「后端明确判定令牌失效」，与断网/超时/5xx 这类瞬时故障区分开。
const AUTH_INVALID_FLAG = '__authInvalid'

const isDefinitiveAuthFailure = (error: any): boolean => {
  if (error?.[AUTH_INVALID_FLAG]) return true
  const status = error?.response?.status
  return status === 401 || status === 403
}

const authInvalidError = (message: string): Error => {
  const error: any = new Error(message)
  error[AUTH_INVALID_FLAG] = true
  return error
}

const redirectToLogin = () => {
  if (window.location.pathname === '/login') return
  window.location.href = buildLoginRedirectUrl(
    window.location.pathname + window.location.search
  )
}

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
  response => {
    const metadata = (response.config as any)._sessionMetadata as SessionRequestMetadata | undefined
    metadata?.unregister()
    if (metadata && !isCurrentSession(metadata.snapshot)) {
      return Promise.reject(new axios.CanceledError('Discarded response from a previous user session'))
    }
    return response
  },
  async error => {
    const originalRequest = error.config as any
    const metadata = originalRequest?._sessionMetadata as SessionRequestMetadata | undefined
    metadata?.unregister()
    if (metadata && !isCurrentSession(metadata.snapshot)) {
      return Promise.reject(new axios.CanceledError('Discarded response from a previous user session'))
    }
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
                  // 刷新接口返回 200 但业务失败：等同于令牌失效。
                  throw authInvalidError(payload?.message || '刷新令牌失败')
                }

                const accessToken = payload.data.accessToken
                const refreshTokenNext = payload.data.refreshToken
                if (!accessToken || !refreshTokenNext) {
                  throw authInvalidError('刷新令牌失败')
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
        } catch (refreshError) {
          // 只有后端明确判定令牌失效才清除本地会话；断网/超时/5xx 保留 refresh token，
          // 让调用方按普通错误提示用户重试，而不是强制登出。
          if (isDefinitiveAuthFailure(refreshError)) {
            clearTokens()
            advanceSessionGeneration()
            redirectToLogin()
          }
        }
      } else {
        // 本地已无 refresh token：确实无法续期，回到登录页。
        clearTokens()
        advanceSessionGeneration()
        redirectToLogin()
      }
    }

    applyBackendErrorMessage(error)
    return Promise.reject(error)
  }
)

export { api }
export default api
