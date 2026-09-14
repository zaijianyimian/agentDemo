// 邮箱新事件 SSE 连接工具：订阅后端 /api/email/events，解析 new-email 事件并回调
import { getAccessToken } from '@/services/auth-token'
import {
  captureSession,
  isCurrentSession,
  registerSessionController,
  type SessionSnapshot
} from '@/services/session-lifecycle'

export interface EmailNotificationEvent {
  accountEmail?: string
  from?: string
  fromName?: string
  subject?: string
  sentDate?: string
  receivedDate?: string
  detectedAt?: string
  messageId?: string
}

interface UserEventEnvelope<T> {
  event_id: string
  schema_version: number
  user_id: number
  occurred_at: string
  event_type: string
  resource_type: string
  resource_id: string
  payload: T
}

type EmailEventHandlers = {
  onEmail: (event: EmailNotificationEvent) => void
  onDisconnect?: () => void
}

/**
 * 连接邮箱事件 SSE 流，返回 AbortController 用于断开。
 * 读取过程中按 \n\n 切分事件，仅处理 new-email 类型，JSON 解析后回调 onEmail。
 */
export function connectEmailEventStream(handlers: EmailEventHandlers): AbortController | null {
  const token = getAccessToken()
  if (!token) return null

  const snapshot = captureSession()
  if (!snapshot.userId) return null
  const controller = new AbortController()
  const unregister = registerSessionController(controller, snapshot.generation)

  void readEmailEventStream(controller, token, snapshot, handlers).catch((error) => {
    if (!controller.signal.aborted && isCurrentSession(snapshot)) {
      console.warn('[邮箱监听SSE] 连接断开', error)
      handlers.onDisconnect?.()
    }
  }).finally(unregister)

  return controller
}

async function readEmailEventStream(
  controller: AbortController,
  token: string,
  snapshot: SessionSnapshot,
  handlers: EmailEventHandlers
) {
  const response = await fetch('/api/email/events', {
    method: 'GET',
    headers: {
      Accept: 'text/event-stream',
      Authorization: `Bearer ${token}`
    },
    signal: controller.signal
  })

  if (!response.ok || !response.body) {
    throw new Error(`邮箱监听事件流连接失败: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (!controller.signal.aborted) {
    const { value, done } = await reader.read()
    if (done) {
      buffer += decoder.decode()
      break
    }
    buffer += decoder.decode(value, { stream: true })

    let match = /\r?\n\r?\n/.exec(buffer)
    while (match?.index != null) {
      const rawEvent = buffer.slice(0, match.index)
      buffer = buffer.slice(match.index + match[0].length)
      handleSseEvent(rawEvent, snapshot, handlers)
      match = /\r?\n\r?\n/.exec(buffer)
    }
  }
}

function handleSseEvent(rawEvent: string, snapshot: SessionSnapshot, handlers: EmailEventHandlers) {
  const lines = rawEvent.split(/\r?\n/)
  let eventName = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (eventName !== 'new-email' || dataLines.length === 0) return

  try {
    const envelope = JSON.parse(dataLines.join('\n')) as UserEventEnvelope<EmailNotificationEvent>
    if (envelope.schema_version !== 1 || envelope.event_type !== 'new-email' || !envelope.payload) {
      throw new Error('不支持的邮件事件格式')
    }
    if (!isCurrentSession(snapshot) || String(envelope.user_id) !== snapshot.userId) {
      return
    }
    handlers.onEmail(envelope.payload)
  } catch (error) {
    console.warn('[邮箱监听SSE] 解析新邮件事件失败', error)
  }
}
