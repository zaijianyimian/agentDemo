<template>
  <div class="page-shell dashboard-page">
    <section class="metrics-grid">
      <button
        v-for="card in metricCards"
        :key="card.label"
        class="metric-card"
        type="button"
        @click="$router.push(card.path)"
      >
        <div class="metric-card__icon" :style="{ '--icon-color': card.color }">
          <n-icon size="22"><component :is="card.icon" /></n-icon>
        </div>
        <div class="metric-card__copy">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.hint }}</small>
        </div>
      </button>
    </section>

    <section class="section-grid">
      <div class="surface-panel span-8">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Quick Entry</div>
            <h3>模块总览</h3>
          </div>
        </div>
        <div class="module-grid">
          <button
            v-for="module in modules"
            :key="module.name"
            type="button"
            class="module-card"
            @click="$router.push(module.path)"
          >
            <div class="module-card__icon" :style="{ '--module-color': module.color }">
              <n-icon size="20"><component :is="module.icon" /></n-icon>
            </div>
            <div>
              <strong>{{ module.name }}</strong>
              <p>{{ module.description }}</p>
            </div>
          </button>
        </div>
      </div>

      <div class="surface-panel span-4">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Today</div>
            <h3>今日日程</h3>
          </div>
          <n-button text @click="$router.push('/schedule')">查看全部</n-button>
        </div>
        <div v-if="todaySchedules.length" class="timeline-list">
          <div v-for="event in todaySchedules" :key="event.id" class="timeline-item">
            <span class="timeline-dot"></span>
            <div class="timeline-copy">
              <strong>{{ event.title }}</strong>
              <p>{{ formatTime(event.eventTime) }}<span v-if="event.location"> · {{ event.location }}</span></p>
            </div>
            <n-tag size="small" :type="event.status === 'completed' ? 'success' : 'warning'">
              {{ event.status === 'completed' ? '已完成' : '待处理' }}
            </n-tag>
          </div>
        </div>
        <n-empty v-else description="今天没有待跟进的日程" />
      </div>

      <div class="surface-panel span-12">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Latest Files</div>
            <h3>最近文件</h3>
          </div>
          <n-button text @click="$router.push('/files')">文件中心</n-button>
        </div>
        <div v-if="recentFiles.length" class="activity-list">
          <div v-for="file in recentFiles" :key="file.id" class="activity-row">
            <div>
              <strong>{{ file.fileName }}</strong>
              <p>{{ file.fileType.toUpperCase() }} · {{ formatSize(file.fileSize) }}</p>
            </div>
            <n-tag size="small" :type="getImportanceType(file.importance)">
              {{ file.importance ?? 0 }}
            </n-tag>
          </div>
        </div>
        <n-empty v-else description="暂无文件数据" />
      </div>

    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  NButton,
  NEmpty,
  NIcon,
  NTag
} from 'naive-ui'
import {
  FlashOutline as AutonomyIcon,
  BookOutline as KnowledgeIcon,
  ChatbubblesOutline as ChatIcon,
  CubeOutline as ModelIcon,
  FileTrayFullOutline as InboxIcon,
  FolderOutline as FolderIcon,
  MailOutline as MailIcon,
  ReaderOutline as ReportIcon,
  RocketOutline as SkillIcon,
  SearchOutline as SearchIcon,
  SettingsOutline as SettingsIcon,
  TimeOutline as TaskIcon,
  ConstructOutline as ToolIcon
} from '@vicons/ionicons5'
import {
  fileService,
  knowledgeService,
  mcpToolService,
  modelService,
  personalService,
  scheduleService,
  skillService,
  taskService
} from '@/services/api'
import type { Document, ScheduleEvent } from '@/types'
import dayjs from 'dayjs'
import { readCachedPayload, writeCachedPayload } from '@/services/user-preferences'

const stats = ref({
  files: 0,
  schedules: 0,
  tools: 0,
  skills: 0,
  models: 0,
  tasks: 0,
  knowledge: 0
})

const recentFiles = ref<Document[]>([])
const todaySchedules = ref<ScheduleEvent[]>([])
const personalInsight = ref<{ totalTokenUsage?: number; avgTokensPerMessage?: number } | null>(null)
const DASHBOARD_CACHE_KEY = 'cache.dashboard.v1'

const metricCards = computed(() => [
  { label: '文件', value: stats.value.files, hint: '内容分析与检索', path: '/files', icon: FolderIcon, color: '#f59e0b' },
  { label: '模型', value: stats.value.models, hint: '默认模型与连接测试', path: '/models', icon: ModelIcon, color: '#fb923c' },
  { label: '知识库', value: stats.value.knowledge, hint: 'RAG 与文档向量化', path: '/knowledge', icon: KnowledgeIcon, color: '#fbbf24' },
  { label: '任务', value: stats.value.tasks, hint: '自动化执行与计划流程', path: '/tasks', icon: TaskIcon, color: '#f97316' },
  { label: 'Token 用量', value: personalInsight.value?.totalTokenUsage || 0, hint: `均值 ${personalInsight.value?.avgTokensPerMessage || 0}`, path: '/personal', icon: InboxIcon, color: '#fdba74' }
])

const modules = [
  { name: 'AI 聊天', path: '/chat', description: '会话、流式与 MCP Agent 对话', icon: ChatIcon, color: '#f59e0b' },
  { name: '统一收件箱', path: '/inbox', description: '聚合日程、任务、笔记、搜索与自治发现项', icon: InboxIcon, color: '#fb923c' },
  { name: '自治中心', path: '/autonomy', description: '执行项目扫描、验证与补全草稿生成', icon: AutonomyIcon, color: '#f97316' },
  { name: '日报周报', path: '/reports', description: '生成日报、周报并沉淀历史报告', icon: ReportIcon, color: '#fdba74' },
  { name: '知识库', path: '/knowledge', description: '创建库、上传文档、检索片段', icon: KnowledgeIcon, color: '#fbbf24' },
  { name: '定时任务', path: '/tasks', description: 'Cron、技能执行与手动触发', icon: TaskIcon, color: '#fb923c' },
  { name: '网络搜索', path: '/search', description: '搜索、总结、历史与兴趣画像', icon: SearchIcon, color: '#fdba74' },
  { name: '工具管理', path: '/tools', description: 'MCP 工具配置、验证与执行', icon: ToolIcon, color: '#f97316' },
  { name: '技能管理', path: '/skills', description: '分类、绑定工具、执行与导出', icon: SkillIcon, color: '#f59e0b' },
  { name: '邮件配置', path: '/email', description: '邮箱、监听、测试与模板', icon: MailIcon, color: '#fb923c' },
  { name: '系统设置', path: '/settings', description: '模型、Qdrant、搜索与文件参数', icon: SettingsIcon, color: '#fbbf24' },
  { name: '单用户中心', path: '/personal', description: '专注模式、备份恢复、模板与离线增强', icon: ReportIcon, color: '#f97316' }
]

const loadDashboard = async () => {
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

    const allSchedules = schedulesRes || []
    const today = dayjs().format('YYYY-MM-DD')
    const toolList = Array.isArray(toolsRes) ? toolsRes : (toolsRes.data || [])
    const skillList = Array.isArray(skillsRes) ? skillsRes : (skillsRes.data || [])

    stats.value = {
      files: filesRes.data?.length || filesRes.total || 0,
      schedules: allSchedules.length || 0,
      tools: toolList.length || toolsRes.total || 0,
      skills: skillList.length || skillsRes.total || 0,
      models: modelsRes.data?.length || 0,
      tasks: tasksRes.data?.length || 0,
      knowledge: knowledgeRes.data?.length || 0
    }

    recentFiles.value = (filesRes.data || []).slice(0, 4)
    todaySchedules.value = allSchedules.filter(item => item.eventDate === today).slice(0, 4)
    personalInsight.value = insightRes.data || null

    writeCachedPayload(DASHBOARD_CACHE_KEY, {
      stats: stats.value,
      recentFiles: recentFiles.value,
      todaySchedules: todaySchedules.value,
      personalInsight: personalInsight.value
    })
  } catch (error) {
    console.error('加载仪表盘失败:', error)
    const cached = readCachedPayload<{
      stats: typeof stats.value
      recentFiles: Document[]
      todaySchedules: ScheduleEvent[]
      personalInsight: { totalTokenUsage?: number; avgTokensPerMessage?: number } | null
    }>(DASHBOARD_CACHE_KEY)
    if (cached) {
      stats.value = cached.stats
      recentFiles.value = cached.recentFiles
      todaySchedules.value = cached.todaySchedules
      personalInsight.value = cached.personalInsight
    }
  }
}

const getImportanceType = (importance = 0) => {
  if (importance >= 8) return 'error'
  if (importance >= 5) return 'warning'
  return 'success'
}

const formatSize = (size: number) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

const formatTime = (time?: string) => {
  if (!time) return '未设置'
  return dayjs(time).format('HH:mm')
}

onMounted(loadDashboard)
</script>

<style scoped>
.mini-stat,
.metric-card,
.module-card,
.timeline-item,
.activity-row {
  border: 1px solid var(--border-color);
  background: var(--gradient-card);
  position: relative;
  overflow: hidden;
}

.mini-stat,
.metric-card,
.module-card {
  transition: all var(--transition-base);
}

.mini-stat {
  padding: 18px;
  border-radius: var(--radius-xl);
}

.mini-stat span {
  display: block;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.mini-stat strong {
  font-size: 1.5rem;
  font-weight: 700;
  background: var(--text-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 18px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px;
  border-radius: var(--radius-xl);
  text-align: left;
  cursor: pointer;
  box-shadow: var(--shadow-xs);
}

.metric-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--icon-color, var(--primary-color)), transparent);
  opacity: 0;
  transition: opacity var(--transition-base);
}

.metric-card:hover {
  transform: translateY(-1px);
  border-color: var(--border-glow);
  box-shadow: var(--shadow-xs);
}

.metric-card:hover::before {
  opacity: 1;
}

.metric-card:active {
  transform: translateY(-2px) scale(1);
}

.metric-card__icon,
.module-card__icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: #fff7ed;
  background: linear-gradient(135deg, var(--icon-color, #f59e0b), color-mix(in srgb, var(--icon-color, #f59e0b) 72%, #fde68a 28%));
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-base);
}

.metric-card:hover .metric-card__icon,
.module-card:hover .module-card__icon {
  transform: scale(1.08);
  box-shadow: var(--shadow-sm);
}

.metric-card__icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
}

.metric-card__copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-card__copy span,
.module-card p,
.activity-row p,
.timeline-copy p {
  color: var(--text-secondary);
  font-size: 0.88rem;
}

.metric-card__copy strong {
  font-size: 2rem;
  font-weight: 700;
  line-height: 1;
  background: var(--text-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.metric-card__copy small {
  color: var(--text-muted);
  font-size: 0.82rem;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.section-head h3 {
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--text-primary);
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
}

.module-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 20px;
  border-radius: var(--radius-lg);
  text-align: left;
  cursor: pointer;
  box-shadow: var(--shadow-xs);
}

.module-card:hover {
  transform: translateY(-1px);
  border-color: var(--border-glow);
  box-shadow: var(--shadow-sm);
}

.module-card:active {
  transform: translateY(-1px);
}

.module-card__icon {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  --icon-color: var(--module-color);
}

.module-card strong,
.timeline-copy strong,
.activity-row strong {
  display: block;
  margin-bottom: 4px;
  font-weight: 600;
  color: var(--text-primary);
}

.timeline-list,
.activity-list {
  display: grid;
  gap: 12px;
}

.timeline-item,
.activity-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 18px;
  border-radius: var(--radius-lg);
  transition: all var(--transition-base);
}

.timeline-item:hover,
.activity-row:hover {
  border-color: var(--border-glow);
  background: var(--bg-hover);
  transform: translateX(1px);
}

.timeline-item {
  align-items: flex-start;
}

.timeline-dot {
  width: 10px;
  height: 10px;
  margin-top: 8px;
  border-radius: 50%;
  background: var(--primary-color);
  box-shadow: 0 0 0 5px rgba(245, 158, 11, 0.15);
  flex-shrink: 0;
  animation: pulse 2s ease-in-out infinite;
}

.timeline-copy {
  flex: 1;
}

@media (max-width: 1100px) {
  .metric-card__copy strong {
    font-size: 1.7rem;
  }
}

@media (max-width: 768px) {
  .metrics-grid,
  .module-grid {
    grid-template-columns: 1fr;
  }

  .metric-card__copy strong {
    font-size: 1.6rem;
  }
}
</style>

