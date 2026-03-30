const FOCUS_MODE_KEY = 'app.focusMode'
const OFFLINE_CACHE_KEY = 'app.offlineCacheEnabled'
const ACTION_LOG_KEY = 'app.recentActions'

export interface RecentActionItem {
  time: string
  title: string
  detail?: string
}

export const isFocusMode = (): boolean => localStorage.getItem(FOCUS_MODE_KEY) === 'true'
export const setFocusMode = (value: boolean): void => localStorage.setItem(FOCUS_MODE_KEY, String(value))

export const isOfflineCacheEnabled = (): boolean => localStorage.getItem(OFFLINE_CACHE_KEY) !== 'false'
export const setOfflineCacheEnabled = (value: boolean): void => localStorage.setItem(OFFLINE_CACHE_KEY, String(value))

export const readRecentActions = (): RecentActionItem[] => {
  try {
    const raw = localStorage.getItem(ACTION_LOG_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export const pushRecentAction = (item: RecentActionItem): void => {
  const latest = [item, ...readRecentActions()].slice(0, 30)
  localStorage.setItem(ACTION_LOG_KEY, JSON.stringify(latest))
}

export const clearRecentActions = (): void => {
  localStorage.removeItem(ACTION_LOG_KEY)
}

export const readCachedPayload = <T>(key: string): T | null => {
  if (!isOfflineCacheEnabled()) return null
  try {
    const raw = localStorage.getItem(key)
    if (!raw) return null
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export const writeCachedPayload = <T>(key: string, payload: T): void => {
  if (!isOfflineCacheEnabled()) return
  localStorage.setItem(key, JSON.stringify(payload))
}
