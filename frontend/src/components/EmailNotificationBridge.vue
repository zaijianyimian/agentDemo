<script setup lang="ts">
// 邮箱通知桥接组件：监听后端 SSE 事件流，收到新邮件后弹出系统通知
import { onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useNotification } from 'naive-ui'
import { hasAccessToken } from '@/services/auth-token'
import {
  connectEmailEventStream,
  type EmailNotificationEvent
} from '@/services/email-events'

const route = useRoute()
const notification = useNotification()

let streamController: AbortController | null = null
let reconnectTimer: number | null = null
let reconnectDelay = 1000
const MAX_RECONNECT_DELAY = 30000

function connect() {
  if (streamController || !hasAccessToken() || isAuthPath(route.path)) return

  streamController = connectEmailEventStream({
    onEmail: handleNewEmail,
    onDisconnect: scheduleReconnect
  })

  if (streamController) {
    reconnectDelay = 1000
  }
}

function disconnect() {
  if (reconnectTimer != null) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  streamController?.abort()
  streamController = null
}

function scheduleReconnect() {
  streamController = null
  if (reconnectTimer != null || !hasAccessToken() || isAuthPath(route.path)) return

  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connect()
  }, reconnectDelay)
  reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY)
}

function handleNewEmail(event: EmailNotificationEvent) {
  const subject = event.subject?.trim() || '无主题邮件'
  const sender = event.fromName?.trim() || event.from?.trim() || '未知发件人'
  const account = event.accountEmail ? `收件邮箱：${event.accountEmail}` : ''

  notification.info({
    title: '您有新邮箱消息，已开始处理',
    content: `${subject}\n来自：${sender}${account ? `\n${account}` : ''}`,
    duration: 8000,
    keepAliveOnHover: true
  })
}

function isAuthPath(path: string) {
  return path === '/login' || path === '/oauth/github/callback'
}

watch(() => route.path, (path) => {
  if (isAuthPath(path)) {
    disconnect()
  } else {
    connect()
  }
})

onMounted(connect)
onUnmounted(disconnect)
</script>
