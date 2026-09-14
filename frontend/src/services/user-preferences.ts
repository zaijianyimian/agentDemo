// 用户偏好持久化工具：localStorage 中的焦点模式、离线缓存开关与最近操作日志读写
import { getCurrentUserId } from '@/services/auth-token'

const FOCUS_MODE_KEY = 'app.focusMode'
const OFFLINE_CACHE_KEY = 'app.offlineCacheEnabled'
const ACTION_LOG_KEY = 'app.recentActions'
const BUSINESS_CACHE_SCHEMA = 'v1'

const userScopedKey = (key: string): string | null => {
  const userId = getCurrentUserId()
  return userId ? `agent-demo:${BUSINESS_CACHE_SCHEMA}:user:${userId}:${key}` : null
}

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
    localStorage.removeItem(ACTION_LOG_KEY)
    const key = userScopedKey(ACTION_LOG_KEY)
    if (!key) return []
    const raw = localStorage.getItem(key)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

export const pushRecentAction = (item: RecentActionItem): void => {
  const key = userScopedKey(ACTION_LOG_KEY)
  if (!key) return
  const latest = [item, ...readRecentActions()].slice(0, 30)
  localStorage.setItem(key, JSON.stringify(latest))
}

export const clearRecentActions = (): void => {
  localStorage.removeItem(ACTION_LOG_KEY)
  const key = userScopedKey(ACTION_LOG_KEY)
  if (key) localStorage.removeItem(key)
}

export const readCachedPayload = <T>(key: string): T | null => {
  if (!isOfflineCacheEnabled()) return null
  try {
    localStorage.removeItem(key)
    const scopedKey = userScopedKey(key)
    if (!scopedKey) return null
    const raw = localStorage.getItem(scopedKey)
    if (!raw) return null
    return JSON.parse(raw) as T
  } catch {
    return null
  }
}

export const writeCachedPayload = <T>(key: string, payload: T): void => {
  if (!isOfflineCacheEnabled()) return
  const scopedKey = userScopedKey(key)
  if (!scopedKey) return
  localStorage.setItem(scopedKey, JSON.stringify(payload))
}
