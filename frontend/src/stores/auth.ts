import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authService, authTokenStorage } from '@/services/api'
import type { AuthTokenResponse, AuthUserProfile, EmailCodeSendResponse } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUserProfile | null>(null)
  const initialized = ref(false)

  const isAuthenticated = computed(() => !!authTokenStorage.getAccessToken() && !!user.value)

  const setSession = (payload: AuthTokenResponse) => {
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

  const loginByPassword = async (username: string, password: string) => {
    await withAuthResponse(authService.loginByPassword({ username, password }), '登录失败')
  }

  const loginByEmailCode = async (email: string, code: string) => {
    await withAuthResponse(authService.loginByEmailCode({ email, code }), '登录失败')
  }

  const register = async (payload: { username: string; email: string; password: string; displayName?: string }) => {
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
  ) => {
    const res = await promise
    if (!res.success || !res.data) {
      throw new Error(res.message || fallbackMessage)
    }
    setSession(res.data)
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
    register,
    logout
  }
})
