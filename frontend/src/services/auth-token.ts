// 认证令牌存储工具：在 localStorage 中读写 access/refresh token，并提供登录跳转 URL 构造

const ACCESS_TOKEN_KEY = 'agent_access_token'
const REFRESH_TOKEN_KEY = 'agent_refresh_token'

export const getAccessToken = (): string => localStorage.getItem(ACCESS_TOKEN_KEY) || ''

export const getRefreshToken = (): string => localStorage.getItem(REFRESH_TOKEN_KEY) || ''

export const setTokens = (accessToken: string, refreshToken: string) => {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
}

export const clearTokens = () => {
  localStorage.removeItem(ACCESS_TOKEN_KEY)
  localStorage.removeItem(REFRESH_TOKEN_KEY)
}

export const hasAccessToken = (): boolean => !!getAccessToken()

export const buildLoginRedirectUrl = (pathWithQuery: string): string =>
  `/login?redirect=${encodeURIComponent(pathWithQuery)}`
