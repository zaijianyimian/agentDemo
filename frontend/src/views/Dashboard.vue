<template>
  <div class="dashboard">
    <section class="home-hero">
      <div class="hero-copy">
        <div class="page-eyebrow">Unified Management Home</div>
        <h2>统一管理工作台</h2>
        <p>集中查看今日状态、进入业务模块，并快速启动常用工作流。</p>
      </div>

      <div class="time-panel" :class="{ loading }">
        <span class="time-display">{{ formattedTime }}</span>
        <span class="date-display">{{ formattedDate }}</span>
        <span class="insight-text">{{ timeInsight }}</span>
      </div>
    </section>

    <section class="status-grid" aria-label="今日状态">
      <button
        v-for="card in statusCards"
        :key="card.key"
        class="status-card"
        type="button"
        @click="navigate(card.path)"
      >
        <span class="status-icon" :class="card.tone">
          <n-icon size="22"><component :is="card.icon" /></n-icon>
        </span>
        <span class="status-copy">
          <strong>
            <CountUp :end-val="card.value" :duration="1.2" />
          </strong>
          <span>{{ card.label }}</span>
        </span>
      </button>
    </section>

    <div class="home-layout">
      <section class="management-panel">
        <div class="panel-head">
          <div>
            <div class="page-eyebrow">Module Matrix</div>
            <h3>模块管理</h3>
          </div>
          <span class="panel-meta">{{ moduleCount }} 个模块</span>
        </div>

        <div class="module-grid">
          <article
            v-for="category in moduleCategories"
            :key="category.id"
            class="module-card"
          >
            <div class="module-head">
              <span class="module-icon" :style="{ color: category.color }">
                <n-icon size="22"><component :is="getIconComponent(category.icon)" /></n-icon>
              </span>
              <div>
                <h4>{{ category.label }}</h4>
                <p>{{ categorySummary(category.id) }}</p>
              </div>
              <span class="route-count">{{ category.routes.length }}</span>
            </div>

            <div class="route-list">
              <button
                v-for="route in category.routes"
                :key="route.name"
                class="route-chip"
                type="button"
                @click="navigate(route.path)"
              >
                <n-icon size="15"><component :is="getIconComponent(route.icon)" /></n-icon>
                <span>{{ route.label }}</span>
              </button>
            </div>
          </article>
        </div>
      </section>

      <aside class="side-stack">
        <section class="side-panel">
          <div class="panel-head compact">
            <div>
              <div class="page-eyebrow">Recent</div>
              <h3>最近访问</h3>
            </div>
          </div>
          <div v-if="recentAccess.length" class="recent-list">
            <button
              v-for="item in recentAccess"
              :key="item.name"
              class="recent-item"
              type="button"
              @click="navigate(item.path)"
            >
              <n-icon size="16"><component :is="getIconComponent(item.icon)" /></n-icon>
              <span>{{ item.label }}</span>
              <small>{{ item.accessCount }} 次</small>
            </button>
          </div>
          <div v-else class="empty-box">
            暂无最近访问
          </div>
        </section>

        <section class="side-panel">
          <div class="panel-head compact">
            <div>
              <div class="page-eyebrow">Today</div>
              <h3>今日日程</h3>
            </div>
            <button class="text-link" type="button" @click="navigate('/schedule')">查看</button>
          </div>
          <div v-if="todaySchedules.length" class="schedule-list">
            <button
              v-for="item in todaySchedules"
              :key="item.id"
              class="schedule-item"
              type="button"
              @click="navigate('/schedule')"
            >
              <strong>{{ item.title }}</strong>
              <span>{{ item.eventTime || '全天' }}</span>
            </button>
          </div>
          <div v-else class="empty-box">
            今日暂无待跟进事项
          </div>
        </section>

        <section class="side-panel">
          <div class="panel-head compact">
            <div>
              <div class="page-eyebrow">Actions</div>
              <h3>快捷操作</h3>
            </div>
          </div>
          <div class="action-grid">
            <button
              v-for="action in quickActions"
              :key="action.path"
              class="action-button"
              type="button"
              @click="navigate(action.path)"
            >
              <n-icon size="18"><component :is="action.icon" /></n-icon>
              <span>{{ action.label }}</span>
            </button>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 仪表盘主页：聚合展示各模块状态、今日日程、最近访问与快捷操作，支持本地缓存回退。
 */
import { computed, onMounted, onUnmounted, ref, defineComponent, h } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { NIcon } from 'naive-ui'
import {
  BookOutline as BookIcon,
  CalendarOutline as CalendarIcon,
  ChatbubblesOutline as ChatIcon,
  CloudUploadOutline as ImportIcon,
  CodeSlashOutline as CodeIcon,
  ConstructOutline as ToolIcon,
  CubeOutline as ModelIcon,
  DocumentTextOutline as NoteIcon,
  FileTrayFullOutline as InboxIcon,
  FlashOutline as AutonomyIcon,
  FolderOutline as FolderIcon,
  GridOutline as GridIcon,
  HomeOutline as HomeIcon,
  MailOutline as MailIcon,
  NotificationsOutline as BellIcon,
  PersonOutline as PersonIcon,
  ReaderOutline as ReportIcon,
  RocketOutline as SkillIcon,
  SearchOutline as SearchIcon,
  SettingsOutline as SettingsIcon,
  TimeOutline as TaskIcon
} from '@vicons/ionicons5'
import { fileService } from '@/services/api/file'
import { knowledgeService } from '@/services/api/knowledge'
import { mcpToolService } from '@/services/api/mcp'
import { modelService } from '@/services/api/model'
import { scheduleService } from '@/services/api/schedule'
import { skillService } from '@/services/api/skill'
import { taskService } from '@/services/api/task'
import { personalService } from '@/services/api/personal'
import type { ScheduleEvent } from '@/types'
import { readCachedPayload, writeCachedPayload } from '@/services/user-preferences'
import { useMacNavStore } from '@/stores/mac-nav'

dayjs.locale('zh-cn')

const router = useRouter()
const macNavStore = useMacNavStore()

const CountUp = defineComponent({
  props: {
    endVal: { type: Number, default: 0 },
    duration: { type: Number, default: 1 }
  },
  setup(props) {
    const displayValue = ref(0)
    const startTime = ref(0)

    const animateCount = (timestamp: number) => {
      if (!startTime.value) startTime.value = timestamp
      const progress = Math.min((timestamp - startTime.value) / (props.duration * 1000), 1)
      displayValue.value = Math.floor(progress * props.endVal)
      if (progress < 1) {
        requestAnimationFrame(animateCount)
      } else {
        displayValue.value = props.endVal
      }
    }

    onMounted(() => {
      requestAnimationFrame(animateCount)
    })

    return () => h('span', displayValue.value.toLocaleString())
  }
})

const stats = ref({
  files: 0,
  schedules: 0,
  tools: 0,
  skills: 0,
  models: 0,
  tasks: 0,
  knowledge: 0
})

const todaySchedules = ref<ScheduleEvent[]>([])
const personalInsight = ref<{ totalTokenUsage?: number; avgTokensPerMessage?: number } | null>(null)
const DASHBOARD_CACHE_KEY = 'cache.dashboard.v2'
const loading = ref(true)

const currentTime = ref(dayjs())
let clockInterval: ReturnType<typeof setInterval> | null = null

const formattedTime = computed(() => currentTime.value.format('HH:mm'))
const formattedDate = computed(() => currentTime.value.format('MM月DD日'))

const moduleCategories = computed(() => macNavStore.categories)
const recentAccess = computed(() => macNavStore.topQuickAccess)
const moduleCount = computed(() =>
  moduleCategories.value.reduce((total, category) => total + category.routes.length, 0)
)

const iconMap: Record<string, any> = {
  bell: BellIcon,
  book: BookIcon,
  brain: BookIcon,
  calendar: CalendarIcon,
  chatbubbles: ChatIcon,
  code: CodeIcon,
  construct: ToolIcon,
  cpu: ModelIcon,
  cube: ModelIcon,
  folder: FolderIcon,
  grid: GridIcon,
  home: HomeIcon,
  import: ImportIcon,
  inbox: InboxIcon,
  mail: MailIcon,
  note: NoteIcon,
  person: PersonIcon,
  reader: ReportIcon,
  rocket: SkillIcon,
  search: SearchIcon,
  settings: SettingsIcon,
  sparkles: AutonomyIcon,
  time: TaskIcon,
  timer: TaskIcon
}

const getIconComponent = (icon: string) => iconMap[icon] || GridIcon

const statusCards = computed(() => [
  { key: 'schedules', label: '今日待跟进', value: todaySchedules.value.length, path: '/schedule', icon: CalendarIcon, tone: 'schedules' },
  { key: 'files', label: '文件已入库', value: stats.value.files, path: '/files', icon: FolderIcon, tone: 'files' },
  { key: 'knowledge', label: '知识库条目', value: stats.value.knowledge, path: '/knowledge', icon: BookIcon, tone: 'knowledge' },
  { key: 'models', label: '模型配置', value: stats.value.models, path: '/models', icon: ModelIcon, tone: 'models' },
  { key: 'tasks', label: '任务在推进', value: stats.value.tasks, path: '/tasks', icon: TaskIcon, tone: 'tasks' },
  { key: 'tools', label: '工具已接入', value: stats.value.tools, path: '/tools', icon: ToolIcon, tone: 'tools' },
  { key: 'skills', label: '技能可调用', value: stats.value.skills, path: '/skills', icon: SkillIcon, tone: 'skills' },
  { key: 'interactions', label: '交互已记录', value: personalInsight.value?.totalTokenUsage || 0, path: '/personal', icon: PersonIcon, tone: 'personal' }
])

const quickActions = [
  { label: '开始对话', path: '/chat', icon: ChatIcon },
  { label: '上传文件', path: '/files', icon: FolderIcon },
  { label: '添加日程', path: '/schedule', icon: CalendarIcon },
  { label: '全局搜索', path: '/search', icon: SearchIcon },
  { label: '生成报告', path: '/reports', icon: ReportIcon },
  { label: '系统设置', path: '/settings', icon: SettingsIcon }
]

const categoryDescriptions: Record<string, string> = {
  workspace: '动态、报告和自治任务入口',
  engine: '模型、系统和基础参数管理',
  knowledge: '知识、文件、笔记和聊天资产',
  automation: '日程、通知、邮件和任务流程',
  tools: 'MCP、技能、代码与搜索工具链',
  personal: '个人效率、模板和备份恢复'
}

const categorySummary = (categoryId: string) => categoryDescriptions[categoryId] || '业务模块入口'

const navigate = (path: string) => {
  router.push(path)
}

const timeInsight = computed(() => {
  const hour = currentTime.value.hour()
  const schedulesCount = todaySchedules.value.length
  const tasksCount = stats.value.tasks

  if (hour >= 23 || hour < 5) {
    return '深夜，适合深度复盘'
  }
  if (hour >= 5 && hour < 9) {
    if (schedulesCount > 0) {
      return `晨间，今日已有 ${schedulesCount} 项计划`
    }
    return '清晨，系统已待命'
  }
  if (hour >= 9 && hour < 12) {
    if (tasksCount > 0) {
      return `上午，${tasksCount} 项任务推进中`
    }
    return '上午，最佳产出时段'
  }
  if (hour >= 12 && hour < 14) {
    return '午间，短暂休憩'
  }
  if (hour >= 14 && hour < 18) {
    if (stats.value.files > 0) {
      return `下午，${stats.value.files} 份文件待审阅`
    }
    return '下午，专注时刻'
  }
  if (hour >= 18 && hour < 21) {
    return '傍晚，收尾与总结'
  }
  return '夜间，思考与规划'
})

/** 并行拉取各模块统计数据并写入本地缓存，请求失败时回退到缓存快照。 */
const loadDashboard = async () => {
  loading.value = true
  try {
    const [filesRes, schedulesRes, toolsRes, skillsRes, modelsRes, tasksRes, knowledgeRes, insightRes] = await Promise.all([
      fileService.list(),
      scheduleService.list(),
      mcpToolService.list(),
      skillService.list(),
      modelService.list(),
      taskService.list(),
      knowledgeService.list(),
      personalService.insights()
    ])

    const allSchedules = schedulesRes.data || []
    const today = dayjs().format('YYYY-MM-DD')

    stats.value = {
      files: filesRes.data?.length || 0,
      schedules: allSchedules.length || 0,
      tools: toolsRes.data?.length || 0,
      skills: skillsRes.data?.length || 0,
      models: modelsRes.data?.length || 0,
      tasks: tasksRes.data?.length || 0,
      knowledge: knowledgeRes.data?.length || 0
    }

    todaySchedules.value = allSchedules.filter(item => item.eventDate === today).slice(0, 5)
    personalInsight.value = insightRes.data || null

    writeCachedPayload(DASHBOARD_CACHE_KEY, {
      stats: stats.value,
      todaySchedules: todaySchedules.value,
      personalInsight: personalInsight.value
    })
  } catch (error) {
    console.error('加载仪表盘失败:', error)
    const cached = readCachedPayload<{
      stats: typeof stats.value
      todaySchedules: ScheduleEvent[]
      personalInsight: { totalTokenUsage?: number; avgTokensPerMessage?: number } | null
    }>(DASHBOARD_CACHE_KEY)
    if (cached) {
      stats.value = cached.stats
      todaySchedules.value = cached.todaySchedules
      personalInsight.value = cached.personalInsight
    }
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadDashboard()
  macNavStore.loadQuickAccess()
  clockInterval = setInterval(() => {
    currentTime.value = dayjs()
  }, 1000)
})

onUnmounted(() => {
  if (clockInterval) {
    clearInterval(clockInterval)
    clockInterval = null
  }
})
</script>

<style scoped>
.dashboard {
  display: grid;
  gap: 16px;
  min-height: calc(100vh - 180px);
  color: var(--text-primary);
}

.home-hero,
.management-panel,
.side-panel,
.status-card,
.module-card {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-lg);
  background:
    var(--gradient-card),
    var(--bg-panel);
  box-shadow:
    inset 0 1px 0 var(--border-hairline),
    var(--shadow-card);
}

.home-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 300px);
  gap: 18px;
  align-items: stretch;
  padding: 22px;
  background:
    radial-gradient(circle at 90% 8%, var(--primary-glow), transparent 30%),
    var(--gradient-accent),
    var(--gradient-workbench),
    var(--bg-panel);
}

.hero-copy {
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.hero-copy h2,
.panel-head h3,
.module-head h4 {
  margin: 0;
  color: var(--text-primary);
}

.hero-copy h2 {
  font-size: clamp(1.6rem, 3vw, 2.25rem);
  line-height: 1.15;
}

.hero-copy p {
  max-width: 58ch;
  margin: 10px 0 0;
  color: var(--text-secondary);
}

.time-panel {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 6px;
  min-height: 148px;
  padding: 20px;
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-md);
  background: var(--surface-hover);
}

.time-panel.loading {
  border-color: var(--border-accent);
}

.time-display {
  font-family: var(--font-display);
  font-size: clamp(2.4rem, 5vw, 3.6rem);
  font-weight: 800;
  line-height: 1;
  color: var(--text-primary);
  background: var(--gradient-sunset);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.date-display {
  color: var(--text-secondary);
  font-weight: 700;
}

.insight-text {
  color: var(--text-muted);
  font-size: 0.88rem;
  text-align: center;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.status-card {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  min-height: 86px;
  padding: 16px;
  border-color: var(--surface-border);
  color: inherit;
  cursor: pointer;
  transition: transform var(--transition-base), border-color var(--transition-base), box-shadow var(--transition-base);
}

.status-card:hover,
.module-card:hover,
.side-panel:hover {
  transform: translateY(-2px);
  border-color: var(--surface-border-strong);
  box-shadow:
    inset 0 1px 0 var(--border-hairline),
    var(--shadow-card-hover);
}

.status-icon,
.module-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.status-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  color: white;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.10);
}

.status-icon.files { background: linear-gradient(135deg, #FB923C, #EA580C); }
.status-icon.models { background: linear-gradient(135deg, #F59E0B, #D97706); }
.status-icon.knowledge { background: linear-gradient(135deg, #4ADE80, #16A34A); }
.status-icon.tasks { background: linear-gradient(135deg, #14B8A6, #0D9488); }
.status-icon.tools { background: linear-gradient(135deg, #C084FC, #A855F7); }
.status-icon.skills { background: linear-gradient(135deg, #F472B6, #EC4899); }
.status-icon.schedules { background: linear-gradient(135deg, #FBBF24, #F59E0B); }
.status-icon.personal { background: linear-gradient(135deg, #6366F1, #A855F7); }

.status-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
  text-align: left;
}

.status-copy strong {
  color: var(--text-primary);
  font-size: clamp(1.3rem, 2vw, 1.7rem);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.status-copy span {
  color: var(--text-muted);
  font-size: 0.84rem;
}

.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
  gap: 16px;
  align-items: start;
}

.management-panel,
.side-panel {
  padding: 18px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head.compact {
  align-items: center;
}

.panel-meta,
.route-count {
  color: var(--text-muted);
  font-size: 0.78rem;
}

.panel-meta {
  padding: 5px 10px;
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-full);
  background: var(--surface-hover);
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.module-card {
  padding: 16px;
  transition: transform var(--transition-base), border-color var(--transition-base), box-shadow var(--transition-base);
}

.module-head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 12px;
  align-items: start;
  margin-bottom: 14px;
}

.module-icon {
  width: 38px;
  height: 38px;
  border: 1px solid var(--surface-border);
  border-radius: var(--radius-md);
  background: var(--surface-hover);
}

.module-head p {
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 0.82rem;
}

.route-count {
  display: grid;
  place-items: center;
  min-width: 28px;
  height: 28px;
  border-radius: var(--radius-full);
  background: var(--bg-active);
  color: var(--text-accent);
  font-weight: 700;
}

.route-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.route-chip,
.recent-item,
.schedule-item,
.action-button,
.text-link {
  border: 1px solid transparent;
  background: transparent;
  color: inherit;
  cursor: pointer;
  transition: background var(--transition-base), border-color var(--transition-base), color var(--transition-base), transform var(--transition-base);
}

.route-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 7px 10px;
  border-color: var(--surface-border);
  border-radius: var(--radius-full);
  background: var(--surface-hover);
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.route-chip:hover,
.recent-item:hover,
.schedule-item:hover,
.action-button:hover {
  background: var(--bg-active);
  border-color: var(--border-accent);
  color: var(--text-primary);
  transform: translateY(-1px);
}

.side-stack {
  display: grid;
  gap: 16px;
}

.recent-list,
.schedule-list {
  display: grid;
  gap: 8px;
}

.recent-item,
.schedule-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-height: 42px;
  padding: 10px 12px;
  border-color: var(--surface-border);
  border-radius: var(--radius-md);
  background: var(--surface-hover);
  text-align: left;
}

.recent-item span,
.schedule-item strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-item small,
.schedule-item span {
  color: var(--text-muted);
  font-size: 0.75rem;
}

.schedule-item {
  grid-template-columns: minmax(0, 1fr) auto;
}

.empty-box {
  display: grid;
  place-items: center;
  min-height: 76px;
  border: 1px dashed var(--surface-border);
  border-radius: var(--radius-md);
  color: var(--text-muted);
  background: var(--surface-hover);
  font-size: 0.86rem;
}

.text-link {
  padding: 6px 10px;
  border-radius: var(--radius-full);
  color: var(--text-accent);
  font-size: 0.82rem;
  font-weight: 700;
}

.text-link:hover {
  background: var(--bg-active);
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.action-button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 42px;
  padding: 10px;
  border-color: var(--surface-border);
  border-radius: var(--radius-md);
  background: var(--surface-hover);
  color: var(--text-secondary);
  font-weight: 700;
  font-size: 0.82rem;
}

@media (max-width: 1180px) {
  .status-grid,
  .module-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .home-layout {
    grid-template-columns: 1fr;
  }

  .side-stack {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 820px) {
  .home-hero {
    grid-template-columns: 1fr;
  }

  .side-stack {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .dashboard {
    gap: 12px;
  }

  .home-hero,
  .management-panel,
  .side-panel {
    padding: 14px;
  }

  .status-grid,
  .module-grid,
  .action-grid {
    grid-template-columns: 1fr;
  }

  .status-card {
    min-height: 76px;
  }

  .module-head {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .route-count {
    grid-column: 2;
    justify-self: start;
  }
}
</style>
