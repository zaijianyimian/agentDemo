import type { ChatMessageEntity, ChatSession } from '@/types'
import { fetchWithAuth } from '@/services/auth-fetch'

// Python 的 /api/chat 接口直接返回对象或数组，不使用 Java 的 ApiResponse 包装。
const request = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
  const response = await fetchWithAuth(`/api/chat${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers }
  })
  if (!response.ok) {
    let detail = `请求失败 (${response.status})`
    try {
      const body = await response.json()
      if (typeof body.detail === 'string') detail = body.detail
    } catch { /* 保留 HTTP 状态信息 */ }
    throw new Error(detail)
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}

const toSession = (item: any): ChatSession => ({
  id: item.session_id,
  title: item.title,
  messageCount: item.message_count,
  lastMessageTime: item.last_message_at || undefined,
  createTime: item.created_at,
  updateTime: item.updated_at
})

const toMessage = (item: any): ChatMessageEntity => ({
  id: item.message_id,
  sessionId: item.session_id,
  role: item.role,
  content: item.content,
  createTime: item.created_at
})

export const chatHistoryService = {
  createSession: async (title?: string) =>
    toSession(await request<unknown>('/sessions', { method: 'POST', body: JSON.stringify({ title: title || null }) })),
  getSessions: async () =>
    (await request<unknown[]>('/sessions')).map(toSession),
  updateSessionTitle: async (id: string, title: string) =>
    toSession(await request<unknown>(`/sessions/${id}/title`, { method: 'PUT', body: JSON.stringify({ title }) })),
  deleteSession: async (id: string) =>
    request<void>(`/sessions/${id}`, { method: 'DELETE' }),
  getSessionMessages: async (id: string) =>
    (await request<unknown[]>(`/sessions/${id}/messages`)).map(toMessage),
  clearSessionMessages: async (id: string) =>
    toSession(await request<unknown>(`/sessions/${id}/messages`, { method: 'DELETE' }))
}
