import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authService, authTokenStorage } from '@/services/api/auth'
import type { AuthTokenResponse, AuthUserProfile, EmailCodeSendResponse } from '@/types'
import { advanceSessionGeneration, currentSessionGeneration } from '@/services/session-lifecycle'

/**
 * 认证状态 Store
 *
 * 职责：管理当前登录用户、access/refresh token 与登录态相关的副作用（登录、登出、会话恢复）。
 * State 形状：
 *  - user: AuthUserProfile | null - 当前登录用户档案
 *  - initialized: boolean - 是否已完成首次会话恢复（hydrate）
 *  - degraded: boolean - 后端在会话恢复时不可达：保留令牌、允许进入应用，等待重试
 *  - isAuthenticated: computed - 是否持有有效 token 且 user 已加载
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUserProfile | null>(null)
  const initialized = ref(false)
  const degraded = ref(false)
  const sessionGeneration = ref(currentSessionGeneration())

  const isAuthenticated = computed(() => !!authTokenStorage.getAccessToken() && !!user.value)

  const setSession = (payload: AuthTokenResponse) => {
    if (!payload.accessToken || !payload.refreshToken || !payload.user) {
      throw new Error('登录态数据不完整')
    }
    sessionGeneration.value = advanceSessionGeneration()
    authTokenStorage.setTokens(payload.accessToken, payload.refreshToken)
    user.value = payload.user
    // 拿到后端确认的登录态即视为恢复正常，不再处于降级状态。
    degraded.value = false
  }

  /**
   * 清除本地 token 与 user，回到未登录状态（不触发后端登出请求）。
   */
  const clearSession = () => {
    sessionGeneration.value = advanceSessionGeneration()
    authTokenStorage.clearTokens()
    user.value = null
    degraded.value = false
  }

  /**
   * 仅当后端明确判定令牌失效（401/403）时才算鉴权失败。
   * 断网、超时、5xx 属于瞬时故障，必须保留本地令牌，否则一次抖动就会让用户重新登录。
   */
  const isAuthFailure = (error: any): boolean => {
    const status = error?.response?.status
    return status === 401 || status === 403
  }

  /**
   * 应用启动时恢复登录态：若本地有 token 则调用 /me 拉取用户信息。
   * 鉴权失败时清除会话；后端不可达时保留令牌并标记 degraded，允许用户继续进入应用，
   * 后续导航会重试恢复。后端恢复后自动回到正常状态。
   */
  const hydrate = async () => {
    if (initialized.value && !degraded.value) return
    initialized.value = true
    const token = authTokenStorage.getAccessToken()
    if (!token) {
      user.value = null
      return
    }
    try {
      const res = await authService.me()
      if (res.success && res.data) {
        user.value = res.data
        degraded.value = false
      } else {
        clearSession()
      }
    } catch (error) {
      if (isAuthFailure(error)) {
        clearSession()
      } else {
        // 瞬时故障：保留令牌与 degraded 标记，等待下一次导航重试。
        degraded.value = true
      }
    }
  }

  const loginByPassword = async (username: string, password: string): Promise<AuthTokenResponse> => {
    return await withAuthResponse(authService.loginByPassword({ username, password }), '登录失败')
  }

  const loginByEmailCode = async (email: string, code: string): Promise<AuthTokenResponse> => {
    return await withAuthResponse(authService.loginByEmailCode({ email, code }), '登录失败')
  }

  const verifyFaceLogin = async (preAuthToken: string, imageBase64: string): Promise<AuthTokenResponse> => {
    return await withAuthResponse(authService.verifyFaceLogin({ preAuthToken, imageBase64 }), '人脸验证失败')
  }

  const register = async (payload: { username: string; email: string; password: string; displayName?: string }) => {
    try {
      const res = await authService.register(payload)
      if (!res.success) {
        throw new Error(res.message || '注册失败')
      }
      return res.data as EmailCodeSendResponse | undefined
    } catch (e: any) {
      throw new Error(extractErrorMessage(e, '注册失败'))
    }
  }

  /**
   * 登出：调用后端登出接口并无论成功与否都清除本地会话。
   */
  const logout = async () => {
    try {
      await authService.logout()
    } finally {
      clearSession()
    }
  }

  const withAuthResponse = async (
    promise: Promise<{ success: boolean; message?: string; data?: AuthTokenResponse }>,
    fallbackMessage: string
  ): Promise<AuthTokenResponse> => {
    try {
      const res = await promise
      if (!res.success || !res.data) {
        throw new Error(res.message || fallbackMessage)
      }
      if (res.data.requiresSecondFactor) {
        return res.data
      }
      setSession(res.data)
      return res.data
    } catch (e: any) {
      throw new Error(extractErrorMessage(e, fallbackMessage))
    }
  }

  const extractErrorMessage = (e: any, fallbackMessage: string) => {
    const responseData = e?.response?.data
    const fieldErrors = responseData?.fieldErrors
    const firstFieldError = fieldErrors && typeof fieldErrors === 'object'
      ? Object.values(fieldErrors).find(value => typeof value === 'string')
      : undefined

    return responseData?.message || firstFieldError || e?.message || fallbackMessage
  }

  return {
    user,
    initialized,
    degraded,
    sessionGeneration,
    isAuthenticated,
    hydrate,
    setSession,
    clearSession,
    loginByPassword,
    loginByEmailCode,
    verifyFaceLogin,
    register,
    logout
  }
})
