import {
  buildLoginRedirectUrl,
  clearTokens,
  getAccessToken,
  getRefreshToken,
  setTokens
} from '@/services/auth-token'

let refreshingPromise: Promise<string | null> | null = null

const refreshAccessToken = async (): Promise<string | null> => {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return null
  }

  if (!refreshingPromise) {
    refreshingPromise = (async () => {
      try {
        const resp = await fetch('/api/auth/token/refresh', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ refreshToken })
        })

        if (!resp.ok) {
          clearTokens()
          return null
        }

        const payload = await resp.json()
        if (!payload?.success || !payload?.data?.accessToken || !payload?.data?.refreshToken) {
          clearTokens()
          return null
        }

        setTokens(payload.data.accessToken, payload.data.refreshToken)
        return payload.data.accessToken as string
      } catch {
        clearTokens()
        return null
      } finally {
        refreshingPromise = null
      }
    })()
  }

  return refreshingPromise
}

export const fetchWithAuth = async (
  input: RequestInfo | URL,
  init: RequestInit = {},
  allowRetry = true
): Promise<Response> => {
  const token = getAccessToken()
  const headers = new Headers(init.headers || {})
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(input, { ...init, headers })
  if (response.status !== 401 || !allowRetry) {
    return response
  }

  const refreshedAccessToken = await refreshAccessToken()
  if (!refreshedAccessToken) {
    if (window.location.pathname !== '/login') {
      window.location.href = buildLoginRedirectUrl(window.location.pathname + window.location.search)
    }
    return response
  }

  const retryHeaders = new Headers(init.headers || {})
  retryHeaders.set('Authorization', `Bearer ${refreshedAccessToken}`)
  return fetch(input, { ...init, headers: retryHeaders })
}

