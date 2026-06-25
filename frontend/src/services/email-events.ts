import { getAccessToken } from '@/services/auth-token'

export interface EmailNotificationEvent {
  accountEmail?: string
  from?: string
  fromName?: string
  subject?: string
  sentDate?: string
  receivedDate?: string
  detectedAt?: string
}

type EmailEventHandlers = {
  onEmail: (event: EmailNotificationEvent) => void
  onDisconnect?: () => void
}

export function connectEmailEventStream(handlers: EmailEventHandlers): AbortController | null {
  const token = getAccessToken()
  if (!token) return null

  const controller = new AbortController()

  void readEmailEventStream(controller, token, handlers).catch((error) => {
    if (!controller.signal.aborted) {
      console.warn('[邮箱监听SSE] 连接断开', error)
      handlers.onDisconnect?.()
    }
  })

  return controller
}

async function readEmailEventStream(
  controller: AbortController,
  token: string,
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
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const rawEvent = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      handleSseEvent(rawEvent, handlers)
      boundary = buffer.indexOf('\n\n')
    }
  }
}

function handleSseEvent(rawEvent: string, handlers: EmailEventHandlers) {
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
    handlers.onEmail(JSON.parse(dataLines.join('\n')) as EmailNotificationEvent)
  } catch (error) {
    console.warn('[邮箱监听SSE] 解析新邮件事件失败', error)
  }
}
