<template>
  <div class="dashboard workspace-dashboard">
    <section class="dashboard-hero">
      <div class="hero-copy">
        <span class="page-eyebrow">Agent Workspace</span>
        <h2>{{ greeting }}，{{ displayName }}</h2>
        <p>{{ heroSummary }}</p>
        <div class="hero-actions">
          <n-button type="primary" size="large" @click="navigate('/chat')">
            <template #icon><n-icon><ChatIcon /></n-icon></template>
            Ask Agent
          </n-button>
          <n-button size="large" secondary @click="navigate('/inbox')">
            查看待处理事项
          </n-button>
        </div>
      </div>

      <div class="hero-context">
        <span>{{ formattedDate }}</span>
        <strong>{{ formattedTime }}</strong>
        <small>{{ loading ? '正在同步工作区状态…' : 'Workspace 已同步' }}</small>
      </div>
    </section>

    <section class="overview-grid" aria-label="今日概览">
      <button
        v-for="card in overviewCards"
        :key="card.key"
        type="button"
        class="overview-card"
        @click="navigate(card.path)"
      >
        <span class="overview-icon">
          <n-icon size="20"><component :is="card.icon" /></n-icon>
        </span>
        <span class="overview-copy">
          <strong>{{ card.value }}</strong>
          <span>{{ card.label }}</span>
          <small>{{ card.hint }}</small>
        </span>
      </button>
    </section>

    <div class="dashboard-grid">
      <section class="dashboard-panel attention-panel">
        <div class="panel-head">
          <div>
            <span class="page-eyebrow">Needs Attention</span>
            <h3>需要你处理</h3>
            <p>优先展示尚未完成的邮件、任务、日程和 Agent 发现项。</p>
          </div>
          <n-button tertiary size="small" @click="navigate('/inbox')">查看全部</n-button>
        </div>

        <div v-if="attentionItems.length" class="attention-list">
          <button
            v-for="item in attentionItems"
            :key="itemKey(item)"
            type="button"
            class="attention-item"
            @click="navigate(item.route || '/inbox')"
          >
            <span class="attention-accent" :style="{ background: item.accent || '#f97316' }"></span>
            <span class="attention-main">
              <span class="attention-meta">
                <n-tag size="small" :bordered="false">{{ categoryLabel(item.category) }}</n-tag>
                <small>{{ formatItemTime(item.time) }}</small>
              </span>
              <strong>{{ item.title }}</strong>
              <p>{{ item.summary }}</p>
            </span>
            <span :class="['attention-status', statusTone(item.status)]">{{ item.status || '待处理' }}</span>
          </button>
        </div>

        <div v-else class="dashboard-empty">
          <n-icon size="32"><CheckIcon /></n-icon>
          <strong>当前没有高优先级事项</strong>
          <span>你可以直接向 Agent 下达新的任务。</span>
        </div>
      </section>

      <aside class="dashboard-side">
        <section class="dashboard-panel">
          <div class="panel-head compact">
            <div>
              <span class="page-eyebrow">Recent Activity</span>
              <h3>最近工作动态</h3>
            </div>
          </div>
          <div v-if="activityItems.length" class="activity-list">
            <button
              v-for="item in activityItems"
              :key="`activity-${itemKey(item)}`"
              type="button"
              class="activity-item"
              @click="navigate(item.route || '/inbox')"
            >
              <span class="activity-dot" :style="{ background: item.accent || '#f97316' }"></span>
              <span>
                <strong>{{ item.title }}</strong>
                <small>{{ categoryLabel(item.category) }} · {{ formatItemTime(item.time) }}</small>
              </span>
            </button>
          </div>
          <div v-else class="mini-empty">暂无工作动态</div>
        </section>

        <section class="dashboard-panel">
          <div class="panel-head compact">
            <div>
              <span class="page-eyebrow">Quick Access</span>
              <h3>最近访问</h3>
            </div>
          </div>
          <div v-if="recentAccess.length" class="quick-list">
            <button
              v-for="item in recentAccess"
              :key="item.name"
              type="button"
              class="quick-item"
              @click="navigate(item.path)"
            >
              <span>{{ item.label }}</span>
              <small>{{ item.accessCount }} 次</small>
            </button>
          </div>
          <div v-else class="mini-empty">开始使用后会记录常用入口</div>
        </section>
      </aside>
    </div>

    <section v-if="inbox.warnings?.length" class="dashboard-panel warning-panel">
      <div class="panel-head compact">
        <div>
          <span class="page-eyebrow">System Hints</span>
          <h3>需要关注的系统提示</h3>
        </div>
      </div>
      <div class="warning-list">
        <div v-for="warning in inbox.warnings" :key="warning" class="warning-item">
          <n-icon><AlertIcon /></n-icon>
          <span>{{ warning }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * Agent Workspace 首页。
 *
 * 首页只聚焦“今天需要处理什么”和最近工作动态，统一复用 Inbox Summary 接口，避免为了
 * 展示模块数量而并行调用多个业务接口。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { NButton, NIcon, NTag, useMessage } from 'naive-ui'
import {
  AlertCircleOutline as AlertIcon,
  CalendarOutline as CalendarIcon,
  ChatbubblesOutline as ChatIcon,
  CheckmarkCircleOutline as CheckIcon,
  FileTrayFullOutline as InboxIcon,
  MailOutline as MailIcon,
  SparklesOutline as AgentIcon,
  TimeOutline as TaskIcon
} from '@vicons/ionicons5'
import type { InboxItem, InboxSummary } from '@/types'
import { inboxService } from '@/services/api/inbox'
import { readCachedPayload, writeCachedPayload } from '@/services/user-preferences'
import { useAuthStore } from '@/stores/auth'
import { useMacNavStore } from '@/stores/mac-nav'

dayjs.locale('zh-cn')

const router = useRouter()
const message = useMessage()
const authStore = useAuthStore()
const navStore = useMacNavStore()
const loading = ref(false)
const currentTime = ref(dayjs())
let clockTimer: ReturnType<typeof setInterval> | null = null

const DASHBOARD_CACHE_KEY = 'cache.dashboard.workspace.v1'
const inbox = ref<InboxSummary>({
  generatedAt: '',
  counts: {},
  items: [],
  warnings: []
})

const displayName = computed(() => authStore.user?.displayName || authStore.user?.username || 'User')
const formattedTime = computed(() => currentTime.value.format('HH:mm'))
const formattedDate = computed(() => currentTime.value.format('M月D日 dddd'))
const recentAccess = computed(() => navStore.topQuickAccess.slice(0, 4))

const greeting = computed(() => {
  const hour = currentTime.value.hour()
  if (hour < 6) return '夜深了'
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const mailCount = computed(() => inbox.value.items.filter(item => item.category === 'mail').length)
const pendingCount = computed(() => inbox.value.items.filter(item => !isCompleted(item.status)).length)

const heroSummary = computed(() => {
  const scheduleCount = Number(inbox.value.counts.todaySchedules || 0)
  const taskCount = Number(inbox.value.counts.enabledTasks || 0)
  if (pendingCount.value === 0) {
    return '当前没有集中待处理事项，可以直接向 Agent 下达新的任务。'
  }
  return `当前聚合到 ${pendingCount.value} 项待处理内容，其中今天有 ${scheduleCount} 项日程、${taskCount} 个启用任务。`
})

const overviewCards = computed(() => [
  {
    key: 'pending',
    label: '待处理',
    value: pendingCount.value,
    hint: '统一收件箱中的未完成事项',
    path: '/inbox',
    icon: InboxIcon
  },
  {
    key: 'schedule',
    label: '今日日程',
    value: Number(inbox.value.counts.todaySchedules || 0),
    hint: '今天需要跟进的安排',
    path: '/schedule',
    icon: CalendarIcon
  },
  {
    key: 'task',
    label: '启用任务',
    value: Number(inbox.value.counts.enabledTasks || 0),
    hint: '当前可触发的自动任务',
    path: '/tasks',
    icon: TaskIcon
  },
  {
    key: 'mail',
    label: '邮件事项',
    value: mailCount.value,
    hint: '已聚合到收件箱的邮件内容',
    path: '/email',
    icon: MailIcon
  },
  {
    key: 'agent',
    label: 'Agent 发现',
    value: Number(inbox.value.counts.autonomyFindings || 0),
    hint: '最近自治扫描发现的问题',
    path: '/autonomy',
    icon: AgentIcon
  }
])

const attentionItems = computed(() =>
  inbox.value.items.filter(item => !isCompleted(item.status)).slice(0, 7)
)
const activityItems = computed(() => inbox.value.items.slice(0, 6))

const categoryLabels: Record<string, string> = {
  schedule: '日程',
  task: '任务',
  note: '笔记',
  search: '搜索',
  mail: '邮件',
  autonomy: 'Agent'
}

const categoryLabel = (category: string) => categoryLabels[category] || 'Workspace'

const isCompleted = (status?: string) => {
  const normalized = (status || '').toLowerCase()
  return ['completed', 'complete', 'done', 'success', 'finished', '已完成'].includes(normalized)
}

const statusTone = (status?: string) => {
  const normalized = (status || '').toLowerCase()
  if (isCompleted(status)) return 'success'
  if (normalized.includes('fail') || normalized.includes('error')) return 'error'
  if (normalized.includes('run') || normalized.includes('process')) return 'running'
  return 'pending'
}

const formatItemTime = (value?: string) => {
  if (!value) return '刚刚'
  const date = dayjs(value)
  return date.isValid() ? date.format('MM-DD HH:mm') : value
}

const itemKey = (item: InboxItem) => `${item.category}-${item.title}-${item.time}`

const navigate = (path?: string) => {
  if (path) router.push(path)
}

/** 加载统一收件箱摘要；失败时使用最近缓存。 */
const loadDashboard = async () => {
  loading.value = true
  try {
    const response = await inboxService.summary(24)
    if (response.success && response.data) {
      inbox.value = response.data
      writeCachedPayload(DASHBOARD_CACHE_KEY, response.data)
    }
  } catch {
    const cached = readCachedPayload<InboxSummary>(DASHBOARD_CACHE_KEY)
    if (cached) {
      inbox.value = cached
      message.info('首页当前使用最近缓存数据')
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  navStore.loadQuickAccess()
  loadDashboard()
  clockTimer = setInterval(() => {
    currentTime.value = dayjs()
  }, 30000)
})

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
.workspace-dashboard {
  display: grid;
  gap: 20px;
  width: min(100%, 1480px);
  margin: 0 auto;
  color: var(--text-primary);
}

.dashboard-hero,
.dashboard-panel,
.overview-card {
  border: 1px solid var(--workspace-border, var(--border-light));
  background: var(--bg-card);
  box-shadow: none;
}

.dashboard-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  align-items: stretch;
  gap: 22px;
  padding: 28px;
  border-radius: 18px;
}

.hero-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.page-eyebrow {
  color: var(--primary-color);
  font-size: 0.68rem;
  font-weight: 750;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.hero-copy h2 {
  margin: 7px 0 0;
  font-size: clamp(1.8rem, 3vw, 2.5rem);
  letter-spacing: -0.04em;
}

.hero-copy p {
  max-width: 760px;
  margin: 12px 0 0;
  color: var(--text-secondary);
  line-height: 1.75;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.hero-context {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 5px;
  min-height: 156px;
  padding: 18px;
  border-radius: 14px;
  background: var(--bg-input);
  text-align: center;
}

.hero-context span,
.hero-context small {
  color: var(--text-muted);
}

.hero-context strong {
  color: var(--text-primary);
  font-size: 2.35rem;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.05em;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.overview-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-height: 108px;
  padding: 16px;
  border-radius: 14px;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 140ms ease, background 140ms ease;
}

.overview-card:hover {
  border-color: color-mix(in srgb, var(--primary-color) 32%, var(--workspace-border, var(--border-light)));
  background: color-mix(in srgb, var(--primary-color) 3%, var(--bg-card));
}

.overview-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border-radius: 11px;
  background: color-mix(in srgb, var(--primary-color) 12%, var(--bg-input));
  color: var(--primary-color);
}

.overview-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.overview-copy strong {
  color: var(--text-primary);
  font-size: 1.45rem;
  line-height: 1;
}

.overview-copy span {
  margin-top: 4px;
  color: var(--text-primary);
  font-size: 0.78rem;
  font-weight: 650;
}

.overview-copy small {
  color: var(--text-muted);
  font-size: 0.66rem;
  line-height: 1.45;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(290px, 360px);
  gap: 16px;
  align-items: start;
}

.dashboard-side {
  display: grid;
  gap: 16px;
}

.dashboard-panel {
  padding: 20px;
  border-radius: 16px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.panel-head.compact {
  margin-bottom: 12px;
}

.panel-head h3 {
  margin: 5px 0 0;
  color: var(--text-primary);
  font-size: 1rem;
}

.panel-head p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 0.76rem;
  line-height: 1.55;
}

.attention-list,
.activity-list,
.quick-list,
.warning-list {
  display: grid;
}

.attention-item {
  display: grid;
  grid-template-columns: 3px minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  width: 100%;
  padding: 14px 0;
  border: 0;
  border-top: 1px solid var(--workspace-border, var(--border-light));
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.attention-item:first-child {
  border-top: 0;
}

.attention-accent {
  width: 3px;
  height: 44px;
  border-radius: 99px;
}

.attention-main {
  display: grid;
  min-width: 0;
}

.attention-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
}

.attention-meta small,
.activity-item small {
  color: var(--text-muted);
  font-size: 0.66rem;
}

.attention-main strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 0.82rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.attention-main p {
  display: -webkit-box;
  margin: 5px 0 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 0.74rem;
  line-height: 1.5;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.attention-status {
  padding: 4px 7px;
  border-radius: 99px;
  background: var(--bg-input);
  color: var(--text-muted);
  font-size: 0.64rem;
  white-space: nowrap;
}

.attention-status.success { color: #16a34a; }
.attention-status.error { color: #dc2626; }
.attention-status.running { color: #0284c7; }
.attention-status.pending { color: var(--primary-color); }

.dashboard-empty {
  display: grid;
  place-items: center;
  gap: 6px;
  min-height: 220px;
  border-radius: 14px;
  background: var(--bg-input);
  color: var(--text-muted);
  text-align: center;
}

.dashboard-empty strong {
  color: var(--text-primary);
}

.activity-item,
.quick-item {
  width: 100%;
  border: 0;
  border-top: 1px solid var(--workspace-border, var(--border-light));
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.activity-item:first-child,
.quick-item:first-child {
  border-top: 0;
}

.activity-item {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 11px 2px;
}

.activity-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.activity-item span:last-child {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.activity-item strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 0.75rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.quick-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 2px;
  color: var(--text-secondary);
  font-size: 0.76rem;
}

.quick-item small {
  color: var(--text-muted);
}

.warning-panel {
  padding-bottom: 14px;
}

.warning-item {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 10px 0;
  border-top: 1px solid var(--workspace-border, var(--border-light));
  color: var(--text-secondary);
  font-size: 0.76rem;
  line-height: 1.5;
}

.warning-item:first-child {
  border-top: 0;
}

.warning-item :deep(svg) {
  color: var(--primary-color);
}

.mini-empty {
  padding: 18px 0;
  color: var(--text-muted);
  font-size: 0.74rem;
  text-align: center;
}

@media (max-width: 1200px) {
  .overview-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 920px) {
  .dashboard-hero,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .hero-context {
    min-height: 116px;
  }

  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .dashboard-hero,
  .dashboard-panel {
    padding: 16px;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }

  .overview-card {
    min-height: 86px;
  }

  .attention-item {
    grid-template-columns: 3px minmax(0, 1fr);
  }

  .attention-status {
    display: none;
  }
}
</style>
