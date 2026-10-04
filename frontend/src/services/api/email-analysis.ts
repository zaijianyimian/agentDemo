import { fetchWithAuth } from '@/services/auth-fetch'

// 邮件 AI 分析结果来自 Python / PostgreSQL。
// Java 的 GET /api/email/messages/{messageId} 读取监听进程内存缓存
// （200 条 / 1 小时过期），不能作为长期结果来源，因此这里单独走 /ai 前缀。
// 该路径由反向代理转发到 Python，开发与生产规则一致。

export interface AiEmailAnalysis {
  email_id: number
  user_id: number
  event_id: string | null
  sender: string
  receiver: string | null
  cc: string | null
  subject: string | null
  content: string | null
  html_content: string | null
  received_at: string | null
  status: string
  summary: string | null
  category: string | null
  priority: string | null
  need_action: boolean
  error_message: string | null
  processed_at: string | null
  attachment_count: number
  created_at: string
  updated_at: string
}

const request = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
  const response = await fetchWithAuth(`/ai/email${path}`, {
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
  return response.json() as Promise<T>
}

export interface EmailSearchHit {
  email_id: number
  subject: string
  sender: string
  received_at: string | null
  snippet: string
  score: number
  source_url: string
}

export interface EmailIndexStatus {
  enabled: boolean
  counts: Record<string, number>
  total_emails?: number
  message: string
}

export const emailAnalysisService = {
  search: (query: string, startFrom?: string, startBefore?: string) =>
    request<{ results: EmailSearchHit[]; message: string }>('/search', {
      method: 'POST',
      body: JSON.stringify({ query, limit: 10, start_from: startFrom, start_before: startBefore })
    }),
  indexStatus: () => request<EmailIndexStatus>('/index/status'),
  rebuildIndex: () => request<{ queued: number; message: string }>('/index/rebuild', { method: 'POST' }),
  getById: (id: number) => request<AiEmailAnalysis>(`/messages/${id}`),
  /** 按邮件自身 Message-ID 取回 AI 分析结果；未分析过时返回 404。 */
  getByMessageId: async (messageId: string): Promise<AiEmailAnalysis | null> => {
    try {
      return await request<AiEmailAnalysis>(
        `/by-message-id/${encodeURIComponent(messageId)}`
      )
    } catch (error) {
      // 该邮件尚未进入 Python 分析流程时，详情页应继续展示而不报错。
      if (error instanceof Error && error.message.includes('404')) return null
      throw error
    }
  },
  list: async (limit = 20, offset = 0): Promise<AiEmailAnalysis[]> =>
    request<AiEmailAnalysis[]>(`/list?limit=${limit}&offset=${offset}`)
}
