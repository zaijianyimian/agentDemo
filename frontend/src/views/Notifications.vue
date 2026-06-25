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
            <n-radio-button value="high">高优先级</n-radio-button>
          </n-radio-group>
          <n-select
            v-model="selectedType"
            placeholder="筛选类型"
            class="type-select"
          >
            <n-option value="">全部类型</n-option>
            <n-option v-for="type in notificationTypes" :key="type.value" :value="type.value">
              {{ type.label }}
            </n-option>
          </n-select>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NIcon, NRadioGroup, NRadioButton, NSelect, NTag, NPagination, useMessage } from 'naive-ui'
import NOption from 'naive-ui'
import {
  NotificationsOutline,
  CalendarOutline,
  MailOutline,
  TimeOutline,
  AlertCircleOutline,
  ChatbubblesOutline,
  DocumentTextOutline,
  PinOutline,
  RocketOutline,
  SettingsOutline
} from '@vicons/ionicons5'
import type { NotificationDTO, NotificationStatsDTO, ListenerStatusDTO } from '@/types'
import api from '@/services/api'
import { getAccessToken } from '@/services/auth-token'
import EmptyStateWithGlow from '@/components/EmptyStateWithGlow.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const notifications = ref<NotificationDTO[]>([])
const stats = ref<NotificationStatsDTO>({ totalCount: 0, unreadCount: 0, todayCount: 0 })
const listenerStatus = ref<ListenerStatusDTO[]>([])

const activeFilter = ref('all')
const selectedType = ref('')
const currentPage = ref(1)
const pageSize = 10

const notificationTypes = [
  { value: 'SCHEDULE', label: '日程' },
  { value: 'EMAIL', label: '邮件' },
  { value: 'TASK', label: '任务' },
  { value: 'SYSTEM', label: '系统' },
  { value: 'CHAT', label: '聊天' },
  { value: 'KNOWLEDGE', label: '知识库' }
]

const filteredNotifications = computed(() => {
  let result = notifications.value

  if (activeFilter.value === 'unread') {
    result = result.filter((n: NotificationDTO) => !n.isRead)
  } else if (activeFilter.value === 'high') {
    result = result.filter((n: NotificationDTO) => n.priority === 'HIGH')
  }

  if (selectedType.value) {
    result = result.filter((n: NotificationDTO) => n.type === selectedType.value)
  }

  return result
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

function getTypeIcon(type: string) {
  const icons: Record<string, any> = {
    SCHEDULE: CalendarOutline,
    EMAIL: MailOutline,
    TASK: TimeOutline,
    SYSTEM: SettingsOutline,
    CHAT: ChatbubblesOutline,
    KNOWLEDGE: DocumentTextOutline
  }
  return icons[type] || PinOutline
}

function getTypeLabel(type: string) {
  const labels: Record<string, string> = {
    SCHEDULE: '日程',
    EMAIL: '邮件',
    TASK: '任务',
    SYSTEM: '系统',
    CHAT: '聊天',
    KNOWLEDGE: '知识库'
  }
  return labels[type] || type
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

async function loadNotifications() {
  loading.value = true
  try {
    const response = await api.get('/notification', { params: { page: 1, size: 100 } })
    if (response.data.success && response.data.data) {
      notifications.value = response.data.data.content || []
    }
    await loadStats()
  } catch (error) {
    console.error('加载通知失败:', error)
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  try {
    const response = await api.get('/notification/stats')
    if (response.data.success && response.data.data) {
      stats.value = response.data.data
    }
  } catch (error) {
    console.error('加载统计失败:', error)
  }
}

async function loadListenerStatus() {
  try {
    const response = await api.get('/listener/status')
    if (response.data.success && response.data.data) {
      listenerStatus.value = response.data.data
    }
  } catch (error) {
    console.error('加载监听器状态失败:', error)
  }
}

async function markAsRead(notification: NotificationDTO) {
  try {
    await api.put(`/notification/${notification.id}/read`)
    notification.isRead = true
    stats.value.unreadCount = Math.max(0, stats.value.unreadCount - 1)
    message.success('已标记为已读')
  } catch (error) {
    console.error('标记已读失败:', error)
    message.error('标记已读失败')
  }
}

async function markAllAsRead() {
  try {
    await api.put('/notification/all/read')
    notifications.value.forEach((n: NotificationDTO) => n.isRead = true)
    stats.value.unreadCount = 0
    message.success('全部已标记为已读')
  } catch (error) {
    console.error('全部已读失败:', error)
    message.error('全部已读失败')
  }
}

async function startListener(type: string) {
  try {
    const response = await api.post(`/listener/${type}/start`)
    if (response.data.success) {
      await loadListenerStatus()
      message.success('监听器启动成功')
    }
  } catch (error) {
    console.error('启动监听器失败:', error)
    message.error('启动监听器失败')
  }
}

async function stopListener(type: string) {
  try {
    const response = await api.post(`/listener/${type}/stop`)
    if (response.data.success) {
      await loadListenerStatus()
      message.success('监听器已停止')
    }
  } catch (error) {
    console.error('停止监听器失败:', error)
    message.error('停止监听器失败')
  }
}

async function startAllListeners() {
  try {
    await api.post('/listener/start-all')
    await loadListenerStatus()
    message.success('已启动全部监听器')
  } catch (error) {
    console.error('启动全部监听器失败:', error)
    message.error('启动全部监听器失败')
  }
}

async function stopAllListeners() {
  try {
    await api.post('/listener/stop-all')
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

let eventSource: EventSource | null = null
let reconnectDelay = 1000
const MAX_DELAY = 30000

function connectNotificationStream() {
  if (eventSource) return

  const token = getAccessToken()
  if (!token) return

  const base = api.defaults.baseURL || '/api'
  const url = `${base}/notification/stream?token=${encodeURIComponent(token)}`
  eventSource = new EventSource(url)

  eventSource.addEventListener('connected', () => {
    console.log('[通知SSE] 连接已建立')
    reconnectDelay = 1000 // 重置重连延迟
  })

  eventSource.addEventListener('notification', (event: MessageEvent) => {
    try {
      const notification: NotificationDTO = JSON.parse(event.data)
      // 避免重复添加通知
      const exists = notifications.value.some((n: NotificationDTO) => n.id === notification.id)
      if (!exists) {
        notifications.value.unshift(notification)
        stats.value.unreadCount += 1
        stats.value.totalCount += 1
      }
    } catch (e) {
      console.error('[通知SSE] 解析通知数据失败', e)
    }
  })

  eventSource.addEventListener('unread-count', (event: MessageEvent) => {
    try {
      const data = JSON.parse(event.data)
      stats.value.unreadCount = data.unreadCount ?? stats.value.unreadCount
    } catch (e) {
      console.error('[通知SSE] 解析未读数失败', e)
    }
  })

  eventSource.addEventListener('ping', () => {
    // heartbeat, no action needed
  })

  eventSource.onerror = () => {
    console.warn('[通知SSE] 连接断开，将在', reconnectDelay / 1000, '秒后重连...')
    eventSource?.close()
    eventSource = null
    setTimeout(connectNotificationStream, reconnectDelay)
    // 指数退避
    reconnectDelay = Math.min(reconnectDelay * 2, MAX_DELAY)
  }
}

function disconnectNotificationStream() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
    console.log('[通知SSE] 连接已关闭')
  }
}

onMounted(() => {
  loadNotifications()
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
