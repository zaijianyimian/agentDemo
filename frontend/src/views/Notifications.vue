<template>
  <div class="page-shell notifications-page">
    <section class="metrics-grid">
      <article
        v-for="card in metricCards"
        :key="card.label"
        class="metric-card"
      >
        <div class="metric-card__icon" :style="{ '--metric-color': card.color }">
          <n-icon size="20"><component :is="card.icon" /></n-icon>
        </div>
        <div class="metric-card__copy">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.hint }}</small>
        </div>
      </article>
    </section>

    <section class="section-grid">
      <div class="surface-panel span-8">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Notification Center</div>
            <h3>通知中心</h3>
          </div>
          <div class="head-actions">
            <n-button tertiary @click="loadNotifications" :loading="loading">刷新</n-button>
            <n-button tertiary @click="markAllAsRead" :disabled="stats.unreadCount === 0">
              全部已读
            </n-button>
          </div>
        </div>

        <div class="filter-bar">
          <n-radio-group v-model="activeFilter" class="filter-tabs">
            <n-radio-button value="all">全部</n-radio-button>
            <n-radio-button value="unread">未读</n-radio-button>
          </n-radio-group>
        </div>

        <div v-if="filteredNotifications.length" class="notification-list">
          <div
            v-for="notification in filteredNotifications"
            :key="notification.id"
            class="notification-item"
            :class="{ 'is-unread': !notification.isRead }"
            @click="markAsRead(notification)"
          >
            <div class="notification-item__indicator" :class="notification.priority.toLowerCase()"></div>
            <div class="notification-item__icon">
              <n-icon :size="24"><component :is="getTypeIcon(notification.type)" /></n-icon>
            </div>
            <div class="notification-item__content">
              <div class="notification-item__header">
                <strong>{{ notification.title }}</strong>
                <span class="notification-item__type">{{ getTypeLabel(notification.type) }}</span>
              </div>
              <p class="notification-item__summary">{{ notification.content }}</p>
              <div class="notification-item__meta">
                <span>{{ formatTime(notification.createTime) }}</span>
                <span v-if="notification.source" class="source-tag">{{ notification.source }}</span>
              </div>
            </div>
            <div class="notification-item__actions">
              <n-button size="small" tertiary @click.stop="markAsRead(notification)">
                {{ notification.isRead ? '标记未读' : '标记已读' }}
              </n-button>
            </div>
          </div>
        </div>

        <EmptyStateWithGlow v-else-if="!loading">
          <template #icon>
            <n-icon size="48"><NotificationsOutline /></n-icon>
          </template>
          暂无通知
        </EmptyStateWithGlow>
        <LoadingSpinner v-else />

        <div v-if="filteredNotifications.length > 0" class="pagination-bar">
          <n-pagination
            v-model:page="currentPage"
            :page-count="totalPages"
            :show-size-picker="false"
            :page-size="pageSize"
            @update:page="loadNotifications"
          />
        </div>
      </div>

      <div class="surface-panel span-4">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Status</div>
            <h3>监听器状态</h3>
          </div>
        </div>

        <div v-if="listenerStatus.length" class="listener-status-list">
          <div
            v-for="listener in listenerStatus"
            :key="listener.type"
            class="listener-status-item"
          >
            <div class="listener-status-item__icon" :class="{ 'is-running': listener.running }">
              <n-icon :size="20"><component :is="getListenerIcon(listener.type)" /></n-icon>
            </div>
            <div class="listener-status-item__info">
              <span class="listener-status-item__name">{{ listener.name }}</span>
              <span class="listener-status-item__status">{{ listener.statusDescription }}</span>
            </div>
            <div class="listener-status-item__actions">
              <n-button
                v-if="listener.running"
                size="small"
                type="error"
                tertiary
                @click="stopListener(listener.type)"
              >
                停止
              </n-button>
              <n-button
                v-else-if="listener.configured"
                size="small"
                type="primary"
                tertiary
                @click="startListener(listener.type)"
              >
                启动
              </n-button>
              <n-tag v-else size="small" type="warning">未配置</n-tag>
            </div>
          </div>
        </div>

        <div class="listener-actions">
          <n-button size="small" @click="startAllListeners">启动全部</n-button>
          <n-button size="small" tertiary @click="stopAllListeners">停止全部</n-button>
        </div>

        <div class="section-divider"></div>

        <div>
          <div class="page-eyebrow">Quick Actions</div>
          <h3>快捷操作</h3>
          <div class="quick-actions">
            <n-button block @click="goToPage('/schedule')">
              <CalendarOutline /> 查看日程
            </n-button>
            <n-button block @click="goToPage('/email')">
              <MailOutline /> 邮件配置
            </n-button>
            <n-button block @click="goToPage('/tasks')">
              <TimeOutline /> 定时任务
            </n-button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * 通知中心页面：通知列表/筛选、已读管理、监听器启停，并通过 SSE 实时接收新通知。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NIcon, NRadioGroup, NRadioButton, NTag, NPagination, useMessage } from 'naive-ui'
import {
  NotificationsOutline,
  CalendarOutline,
  MailOutline,
  TimeOutline,
  AlertCircleOutline,
  ChatbubblesOutline,
  RocketOutline,
  SettingsOutline
} from '@vicons/ionicons5'
import type { NotificationDTO, ListenerStatusDTO } from '@/types'
import { connectEmailEventStream, type EmailNotificationEvent } from '@/services/email-events'
import { emailService } from '@/services/api/email'
import EmptyStateWithGlow from '@/components/EmptyStateWithGlow.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const notifications = ref<NotificationDTO[]>([])
const listenerStatus = ref<ListenerStatusDTO[]>([])

const activeFilter = ref('all')
const currentPage = ref(1)
const pageSize = 10
let notificationSeq = 0

// 通知统计由本地列表派生（后端无 /notification/stats 接口）
const stats = computed(() => {
  const todayPrefix = new Date().toISOString().slice(0, 10)
  return {
    totalCount: notifications.value.length,
    unreadCount: notifications.value.filter((n) => !n.isRead).length,
    todayCount: notifications.value.filter((n) => (n.createTime || '').startsWith(todayPrefix)).length
  }
})

const filteredNotifications = computed(() => {
  if (activeFilter.value === 'unread') {
    return notifications.value.filter((n: NotificationDTO) => !n.isRead)
  }
  return notifications.value
})

const totalPages = computed(() => {
  return Math.ceil(filteredNotifications.value.length / pageSize)
})

const metricCards = computed(() => [
  {
    label: '全部通知',
    value: stats.value.totalCount,
    hint: '条通知',
    color: '#6366f1',
    icon: NotificationsOutline
  },
  {
    label: '未读通知',
    value: stats.value.unreadCount,
    hint: '条待处理',
    color: '#f59e0b',
    icon: AlertCircleOutline
  },
  {
    label: '运行中监听器',
    value: listenerStatus.value.filter((l: ListenerStatusDTO) => l.running).length,
    hint: '个服务',
    color: '#10b981',
    icon: RocketOutline
  }
])

function getTypeIcon(_type: string) {
  return MailOutline
}

function getTypeLabel(_type: string) {
  return '邮件'
}

function getListenerIcon(type: string) {
  const icons: Record<string, any> = {
    email: MailOutline,
    wechat: ChatbubblesOutline,
    feishu: RocketOutline
  }
  return icons[type] || SettingsOutline
}

function formatTime(time: string) {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  return time.substring(0, 10)
}

/** 刷新监听器状态。通知历史由后端 SSE 实时推送，无法回溯拉取。 */
async function loadNotifications() {
  loading.value = true
  try {
    await loadListenerStatus()
  } finally {
    loading.value = false
  }
}

async function loadListenerStatus() {
  try {
    const map = ((await emailService.getListenerStatus()) ?? {}) as unknown as Record<
      string,
      { connected?: boolean; status?: string; email?: string; host?: string }
    >
    listenerStatus.value = Object.entries(map).map(([id, v]) => ({
      type: id,
      name: v.email || `#${id}`,
      running: Boolean(v.connected),
      configured: true,
      statusDescription: v.status || (v.connected ? '已连接' : '未连接')
    }))
  } catch (error) {
    console.error('加载监听器状态失败:', error)
  }
}

function markAsRead(notification: NotificationDTO) {
  notification.isRead = !notification.isRead
  // 邮件类型通知点击直接跳详情页（如果后端推送了 messageId）
  if (notification.type === 'EMAIL' && notification.sourceId) {
    router.push(`/email/detail/${encodeURIComponent(notification.sourceId)}`)
  }
}

function markAllAsRead() {
  notifications.value.forEach((n: NotificationDTO) => (n.isRead = true))
  message.success('全部已标记为已读')
}

async function startListener(id: string | number) {
  const numericId = Number(id)
  if (!Number.isFinite(numericId)) {
    message.error('无效的监听器 ID')
    return
  }
  try {
    await emailService.startListener(numericId)
    await loadListenerStatus()
    message.success('监听器启动成功')
  } catch (error) {
    console.error('启动监听器失败:', error)
    message.error('启动监听器失败')
  }
}

async function stopListener(id: string | number) {
  const numericId = Number(id)
  if (!Number.isFinite(numericId)) {
    message.error('无效的监听器 ID')
    return
  }
  try {
    await emailService.stopListener(numericId)
    await loadListenerStatus()
    message.success('监听器已停止')
  } catch (error) {
    console.error('停止监听器失败:', error)
    message.error('停止监听器失败')
  }
}

async function startAllListeners() {
  try {
    await emailService.reloadListeners()
    await loadListenerStatus()
    message.success('已重载全部监听器')
  } catch (error) {
    console.error('重载监听器失败:', error)
    message.error('重载监听器失败')
  }
}

async function stopAllListeners() {
  try {
    await Promise.all(listenerStatus.value
        .map((l) => Number(l.type))
        .filter(Number.isFinite)
        .map((nid) => emailService.stopListener(nid)))
    await loadListenerStatus()
    message.success('已停止全部监听器')
  } catch (error) {
    console.error('停止全部监听器失败:', error)
    message.error('停止全部监听器失败')
  }
}

function goToPage(path: string) {
  router.push(path)
}

let streamController: AbortController | null = null
let reconnectTimer: number | null = null
let reconnectDelay = 1000
const MAX_DELAY = 30000

/** 把新邮件事件映射成通知列表项（沿用 NotificationDTO 形状）。 */
function toNotification(event: EmailNotificationEvent): NotificationDTO {
  const sender = event.fromName?.trim() || event.from?.trim() || '未知发件人'
  return {
    id: ++notificationSeq,
    type: 'EMAIL',
    title: event.subject?.trim() || '无主题邮件',
    content: event.accountEmail ? `${sender} → ${event.accountEmail}` : sender,
    source: event.accountEmail,
    sourceId: event.messageId,
    isRead: false,
    priority: 'MEDIUM',
    createTime: event.detectedAt || event.receivedDate || event.sentDate || ''
  }
}

/** 打开邮件通知 SSE 长连接（fetch + Authorization 头），断线时按指数退避自动重连。 */
function connectNotificationStream() {
  if (streamController) return

  streamController = connectEmailEventStream({
    onEmail: (event) => {
      const item = toNotification(event)
      const key = `${item.title}|${item.createTime}`
      const exists = notifications.value.some((n) => `${n.title}|${n.createTime}` === key)
      if (!exists) {
        notifications.value.unshift(item)
        // 限制列表长度防止长时间运行后内存膨胀
        const MAX_NOTIFICATIONS = 200
        if (notifications.value.length > MAX_NOTIFICATIONS) {
          notifications.value = notifications.value.slice(0, MAX_NOTIFICATIONS)
        }
      }
    },
    onDisconnect: scheduleReconnect
  })

  if (streamController) {
    reconnectDelay = 1000
  }
}

function scheduleReconnect() {
  streamController = null
  if (reconnectTimer != null) return
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connectNotificationStream()
  }, reconnectDelay)
  reconnectDelay = Math.min(reconnectDelay * 2, MAX_DELAY)
}

function disconnectNotificationStream() {
  if (reconnectTimer != null) {
    window.clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  streamController?.abort()
  streamController = null
}

onMounted(() => {
  loadListenerStatus()
  connectNotificationStream()
})

onUnmounted(() => {
  disconnectNotificationStream()
})
</script>

<style scoped>
.notifications-page {
  .notification-list {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
  }

  .notification-item {
    display: flex;
    align-items: flex-start;
    gap: 0.75rem;
    padding: 1rem;
    background: var(--color-card);
    border-radius: var(--radius-md);
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      background: var(--color-card-hover);
    }

    &.is-unread {
      background: var(--color-accent-muted);
    }

    .notification-item__indicator {
      width: 0.25rem;
      min-height: 2rem;
      border-radius: var(--radius-sm);
      flex-shrink: 0;

      &.high {
        background: var(--color-error);
      }

      &.medium {
        background: var(--color-warning);
      }

      &.low {
        background: var(--color-success);
      }
    }

    .notification-item__icon {
      flex-shrink: 0;
      color: var(--color-text-muted);
    }

    .notification-item__content {
      flex: 1;
      min-width: 0;

      .notification-item__header {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-bottom: 0.25rem;

        strong {
          font-weight: 500;
        }
      }

      .notification-item__type {
        font-size: 0.75rem;
        color: var(--color-text-muted);
      }

      .notification-item__summary {
        font-size: 0.875rem;
        color: var(--color-text-secondary);
        margin: 0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .notification-item__meta {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-top: 0.5rem;
        font-size: 0.75rem;
        color: var(--color-text-muted);
      }
    }

    .notification-item__actions {
      flex-shrink: 0;
    }

    & .source-tag {
      padding: 0.125rem 0.375rem;
      background: var(--color-surface);
      border-radius: var(--radius-sm);
      font-size: 0.7rem;
    }
  }

  .filter-bar {
    display: flex;
    align-items: center;
    gap: 1rem;
    padding: 0.75rem 0;
    border-bottom: 1px solid var(--color-border);
    margin-bottom: 0.75rem;

    .filter-tabs {
      display: flex;
      gap: 0.25rem;
    }

    .type-select {
      margin-left: auto;
      width: 120px;
    }
  }

  .pagination-bar {
    display: flex;
    justify-content: center;
    padding: 1rem;
    margin-top: 1rem;
  }

  .listener-status-list {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    margin-bottom: 1rem;
  }

  .listener-status-item {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 0.75rem;
    background: var(--color-surface);
    border-radius: var(--radius-md);

    .listener-status-item__icon {
      width: 2rem;
      height: 2rem;
      display: flex;
      align-items: center;
      justify-content: center;
      background: var(--color-card);
      border-radius: var(--radius-sm);
      color: var(--color-text-muted);

      &.is-running {
        background: var(--color-success-light);
        color: var(--color-success);
      }
    }

    .listener-status-item__info {
      flex: 1;

      .listener-status-item__name {
        display: block;
        font-weight: 500;
        font-size: 0.875rem;
      }

      .listener-status-item__status {
        display: block;
        font-size: 0.75rem;
        color: var(--color-text-muted);
      }
    }

    .listener-status-item__actions {
      flex-shrink: 0;
    }
  }

  .listener-actions {
    display: flex;
    gap: 0.5rem;
    margin-bottom: 1rem;
  }

  .section-divider {
    height: 1px;
    background: var(--color-border);
    margin: 1.5rem 0;
  }

  .quick-actions {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;

    :deep(.n-button) {
      justify-content: flex-start;
      gap: 0.5rem;
    }
  }
}
</style>
