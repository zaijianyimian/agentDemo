<template>
  <aside class="context-sidebar" :class="{ collapsed }" aria-label="上下文导航">
    <header class="context-header">
      <div v-if="!collapsed" class="context-heading">
        <span>{{ eyebrow }}</span>
        <strong>{{ title }}</strong>
      </div>
      <button
        type="button"
        class="context-toggle"
        :title="collapsed ? '展开上下文导航' : '收起上下文导航'"
        @click="toggleCollapsed"
      >
        <n-icon size="17">
          <ChevronForwardOutline v-if="collapsed" />
          <ChevronBackOutline v-else />
        </n-icon>
      </button>
    </header>

    <nav class="context-nav">
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        :class="['context-entry', { active: isActive(item) }]"
        :title="collapsed ? item.label : item.description"
        @click="selectItem(item)"
      >
        <span class="context-icon">
          <n-icon size="17"><component :is="iconFor(item.icon)" /></n-icon>
        </span>
        <span v-if="!collapsed" class="context-copy">
          <strong>{{ item.label }}</strong>
          <small v-if="item.description">{{ item.description }}</small>
        </span>
        <span v-if="!collapsed && item.badge" class="context-badge">{{ item.badge }}</span>
      </button>
    </nav>

    <footer v-if="!collapsed" class="context-footer">
      <span class="health-dot"></span>
      <span>工作区服务已连接</span>
    </footer>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import { NIcon } from 'naive-ui'
import {
  ArchiveOutline,
  BookOutline,
  CalendarOutline,
  ChatbubbleEllipsesOutline,
  CheckmarkDoneOutline,
  ChevronBackOutline,
  ChevronForwardOutline,
  CloudOutline,
  CloudUploadOutline,
  CodeSlashOutline,
  ConstructOutline,
  CubeOutline,
  DocumentTextOutline,
  FileTrayFullOutline,
  FolderOutline,
  GridOutline,
  HomeOutline,
  MailOutline,
  NotificationsOutline,
  PlayCircleOutline,
  PersonOutline,
  ReaderOutline,
  RocketOutline,
  SearchOutline,
  SendOutline,
  SettingsOutline,
  ShieldCheckmarkOutline,
  SparklesOutline,
  TimeOutline
} from '@vicons/ionicons5'
import { useMacNavStore } from '@/stores/mac-nav'

interface ContextItem {
  key: string
  label: string
  description?: string
  icon: string
  path?: string
  hash?: string
  query?: LocationQueryRaw
  badge?: string | number
}

const route = useRoute()
const router = useRouter()
const navStore = useMacNavStore()
const collapsed = ref(localStorage.getItem('workspace.context.collapsed') === 'true')

const dashboardItems: ContextItem[] = [
  { key: 'overview', label: '今日总览', description: '当前工作摘要', icon: 'home', hash: '#dashboard-overview' },
  { key: 'attention', label: '待处理事项', description: '需要你的判断', icon: 'inbox', hash: '#dashboard-attention' },
  { key: 'activity', label: '运行记录', description: 'Agent 最近动态', icon: 'play', hash: '#dashboard-activity' },
  { key: 'health', label: '系统健康', description: '服务连接与提示', icon: 'shield', hash: '#dashboard-health' }
]

const chatItems: ContextItem[] = [
  { key: 'sessions', label: '全部会话', description: '浏览与管理会话', icon: 'chat', hash: '#chat-sessions' },
  { key: 'conversation', label: '当前对话', description: '与 Agent 协作', icon: 'sparkles', hash: '#chat-conversation' },
  { key: 'execution', label: '执行状态', description: '请求生命周期', icon: 'play', hash: '#chat-execution' }
]

const inboxItems: ContextItem[] = [
  { key: 'all', label: '全部', description: '全部聚合事项', icon: 'inbox', query: { filter: 'all' } },
  { key: 'pending', label: '需要行动', description: '尚未完成的事项', icon: 'check', query: { filter: 'pending' } },
  { key: 'mail', label: '邮件', description: '邮件与跟进', icon: 'mail', query: { filter: 'mail' } },
  { key: 'task', label: '任务', description: '计划与执行任务', icon: 'time', query: { filter: 'task' } },
  { key: 'schedule', label: '日程', description: '今天与即将开始', icon: 'calendar', query: { filter: 'schedule' } }
]

const settingsItems: ContextItem[] = [
  { key: 'system', label: '外观与系统', description: '站点与主题', icon: 'settings', query: { section: 'system' } },
  { key: 'executor', label: '执行基础设施', description: '明确执行器能力', icon: 'construct', query: { section: 'executor' } },
  { key: 'model', label: '模型', description: '模型参数', icon: 'cube', query: { section: 'model' } },
  { key: 'qdrant', label: '知识与记忆', description: '向量检索连接', icon: 'cloud', query: { section: 'qdrant' } },
  { key: 'search', label: '集成与搜索', description: '外部搜索服务', icon: 'search', query: { section: 'search' } },
  { key: 'schedule', label: '通知与日程', description: '提醒和摘要', icon: 'calendar', query: { section: 'schedule' } },
  { key: 'file', label: '文件', description: '上传与存储', icon: 'folder', query: { section: 'file' } },
  { key: 'backup', label: '数据归档', description: '受控备份入口', icon: 'archive', query: { section: 'backup' } }
]

const routeName = computed(() => String(route.name || ''))
const currentCategory = computed(() => navStore.getCategoryForRoute(routeName.value))

const eyebrow = computed(() => currentCategory.value?.label || 'Workspace')
const title = computed(() => String(route.meta.title || currentCategory.value?.label || '工作区'))

const items = computed<ContextItem[]>(() => {
  if (routeName.value === 'Dashboard') return dashboardItems
  if (routeName.value === 'Chat') return chatItems
  if (routeName.value === 'Inbox') return inboxItems
  if (routeName.value === 'Settings') return settingsItems

  return (currentCategory.value?.routes || []).map(item => ({
    key: item.name,
    label: item.label,
    description: item.description,
    icon: item.icon,
    path: item.path
  }))
})

const iconMap: Record<string, any> = {
  archive: ArchiveOutline,
  bell: NotificationsOutline,
  book: BookOutline,
  brain: CloudOutline,
  calendar: CalendarOutline,
  chat: ChatbubbleEllipsesOutline,
  chatbubbles: ChatbubbleEllipsesOutline,
  check: CheckmarkDoneOutline,
  cloud: CloudOutline,
  code: CodeSlashOutline,
  construct: ConstructOutline,
  cube: CubeOutline,
  dispatch: SendOutline,
  document: DocumentTextOutline,
  folder: FolderOutline,
  grid: GridOutline,
  home: HomeOutline,
  inbox: FileTrayFullOutline,
  import: CloudUploadOutline,
  mail: MailOutline,
  notifications: NotificationsOutline,
  person: PersonOutline,
  play: PlayCircleOutline,
  reader: ReaderOutline,
  rocket: RocketOutline,
  search: SearchOutline,
  settings: SettingsOutline,
  shield: ShieldCheckmarkOutline,
  sparkles: SparklesOutline,
  push: NotificationsOutline,
  cpu: CubeOutline,
  time: TimeOutline,
  timer: TimeOutline
}

const iconFor = (name: string) => iconMap[name] || GridOutline

const isActive = (item: ContextItem) => {
  if (item.path) return route.path === item.path
  if (item.hash) return route.hash === item.hash || (!route.hash && item === items.value[0])
  if (item.query?.filter) return (route.query.filter || 'pending') === item.query.filter
  if (item.query?.section) return (route.query.section || 'system') === item.query.section
  return false
}

const selectItem = async (item: ContextItem) => {
  if (item.path) {
    await router.push(item.path)
    return
  }

  if (item.query) {
    await router.replace({ query: { ...route.query, ...item.query } })
    return
  }

  if (item.hash) {
    await router.replace({ hash: item.hash, query: route.query })
    await nextTick()
    document.querySelector(item.hash)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const toggleCollapsed = () => {
  collapsed.value = !collapsed.value
  localStorage.setItem('workspace.context.collapsed', String(collapsed.value))
}
</script>

<style scoped>
.context-sidebar {
  display: flex;
  flex: 0 0 220px;
  width: 220px;
  min-width: 220px;
  height: 100vh;
  flex-direction: column;
  overflow: hidden;
  border-right: 1px solid var(--workspace-border, var(--border-light));
  background: var(--bg-card);
  transition: width 180ms ease, min-width 180ms ease, flex-basis 180ms ease;
}

.context-sidebar.collapsed {
  flex-basis: 58px;
  width: 58px;
  min-width: 58px;
}

.context-header {
  display: flex;
  min-height: 68px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--workspace-border, var(--border-light));
}

.context-heading {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.context-heading span {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 0.62rem;
  font-weight: 750;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-heading strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 0.86rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-toggle,
.context-entry {
  border: 0;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.context-toggle {
  display: grid;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  place-items: center;
  border-radius: 8px;
  background: var(--bg-input);
  color: var(--text-muted);
}

.collapsed .context-header {
  justify-content: center;
  padding-inline: 8px;
}

.context-nav {
  flex: 1;
  min-height: 0;
  padding: 12px 10px;
  overflow-y: auto;
  scrollbar-width: thin;
}

.context-entry {
  position: relative;
  display: flex;
  width: 100%;
  min-height: 46px;
  align-items: center;
  gap: 10px;
  padding: 7px 9px;
  border-radius: 9px;
  background: transparent;
  text-align: left;
}

.context-entry + .context-entry {
  margin-top: 3px;
}

.context-entry:hover {
  background: var(--bg-input);
}

.context-entry.active {
  background: color-mix(in srgb, var(--primary-color) 9%, var(--bg-card));
  color: var(--primary-color);
}

.context-icon {
  display: grid;
  width: 26px;
  height: 26px;
  flex: 0 0 26px;
  place-items: center;
  color: var(--text-muted);
}

.context-entry.active .context-icon {
  color: var(--primary-color);
}

.context-copy {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 2px;
}

.context-copy strong,
.context-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.context-copy strong {
  color: var(--text-primary);
  font-size: 0.78rem;
  font-weight: 650;
}

.context-copy small {
  color: var(--text-muted);
  font-size: 0.64rem;
}

.context-entry.active .context-copy strong {
  color: var(--primary-color);
}

.context-badge {
  min-width: 19px;
  padding: 2px 5px;
  border-radius: 999px;
  background: var(--bg-input);
  color: var(--text-muted);
  font-size: 0.62rem;
  font-weight: 700;
  text-align: center;
}

.collapsed .context-entry {
  justify-content: center;
  padding-inline: 6px;
}

.context-footer {
  display: flex;
  min-height: 46px;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--workspace-border, var(--border-light));
  color: var(--text-muted);
  font-size: 0.66rem;
}

.health-dot {
  width: 7px;
  height: 7px;
  flex: 0 0 7px;
  border-radius: 50%;
  background: var(--success);
}

@media (max-width: 1023px) {
  .context-sidebar {
    flex-basis: 196px;
    width: 196px;
    min-width: 196px;
  }
}

@media (max-width: 767px) {
  .context-sidebar {
    display: none;
  }
}
</style>
