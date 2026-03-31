import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authService, authTokenStorage } from '@/services/api'
import type { AuthTokenResponse, AuthUserProfile, EmailCodeSendResponse } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUserProfile | null>(null)
  const initialized = ref(false)

  const isAuthenticated = computed(() => !!authTokenStorage.getAccessToken() && !!user.value)

  const setSession = (payload: AuthTokenResponse) => {
    if (!payload.accessToken || !payload.refreshToken || !payload.user) {
      throw new Error('登录态数据不完整')
    }
    authTokenStorage.setTokens(payload.accessToken, payload.refreshToken)
    user.value = payload.user
  }

  const clearSession = () => {
    authTokenStorage.clearTokens()
    user.value = null
  }

  const hydrate = async () => {
    if (initialized.value) return
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
      } else {
        clearSession()
      }
    } catch {
      clearSession()
    }
  }

  const loginByPassword = async (username: string, password: string, captchaTicket: string): Promise<AuthTokenResponse> => {
    return await withAuthResponse(authService.loginByPassword({ username, password, captchaTicket }), '登录失败')
  }

  const loginByEmailCode = async (email: string, code: string, captchaTicket: string): Promise<AuthTokenResponse> => {
    return await withAuthResponse(authService.loginByEmailCode({ email, code, captchaTicket }), '登录失败')
  }

  const verifyFaceLogin = async (preAuthToken: string, imageBase64: string): Promise<AuthTokenResponse> => {
    return await withAuthResponse(authService.verifyFaceLogin({ preAuthToken, imageBase64 }), '人脸验证失败')
  }

  const register = async (payload: { username: string; email: string; password: string; captchaTicket: string; displayName?: string }) => {
    const res = await authService.register(payload)
    if (!res.success) {
      throw new Error(res.message || '注册失败')
    }
    return res.data as EmailCodeSendResponse | undefined
  }

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
    const res = await promise
    if (!res.success || !res.data) {
      throw new Error(res.message || fallbackMessage)
    }
    if (res.data.requiresSecondFactor) {
      return res.data
    }
    setSession(res.data)
    return res.data
  }

  return {
    user,
    initialized,
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
