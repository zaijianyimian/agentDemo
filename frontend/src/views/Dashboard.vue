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

      <div class="surface-panel span-6">
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

      <div class="surface-panel span-6">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Coverage</div>
            <h3>接口入口覆盖</h3>
          </div>
        </div>
        <div class="coverage-list">
          <div class="coverage-item">
            <span>已接入主模块</span>
            <strong>14 / 14</strong>
          </div>
          <div class="coverage-item">
            <span>服务层封装</span>
            <strong>统一 `api.ts`</strong>
          </div>
          <div class="coverage-item">
            <span>主题方向</span>
            <strong>Amber Citrus</strong>
          </div>
          <div class="coverage-item">
            <span>设计语言</span>
            <strong>暖橙玻璃面板</strong>
          </div>
        </div>
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
  BookOutline as KnowledgeIcon,
  ChatbubblesOutline as ChatIcon,
  CubeOutline as ModelIcon,
  FolderOutline as FolderIcon,
  MailOutline as MailIcon,
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
  scheduleService,
  skillService,
  taskService
} from '@/services/api'
import type { Document, ScheduleEvent } from '@/types'
import dayjs from 'dayjs'

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

const metricCards = computed(() => [
  { label: '文件', value: stats.value.files, hint: '内容分析与检索', path: '/files', icon: FolderIcon, color: '#f59e0b' },
  { label: '模型', value: stats.value.models, hint: '默认模型与连接测试', path: '/models', icon: ModelIcon, color: '#fb923c' },
  { label: '知识库', value: stats.value.knowledge, hint: 'RAG 与文档向量化', path: '/knowledge', icon: KnowledgeIcon, color: '#fbbf24' },
  { label: '任务', value: stats.value.tasks, hint: '自动化执行与计划流程', path: '/tasks', icon: TaskIcon, color: '#f97316' }
])

const modules = [
  { name: 'AI 聊天', path: '/chat', description: '会话、流式与 MCP Agent 对话', icon: ChatIcon, color: '#f59e0b' },
  { name: '知识库', path: '/knowledge', description: '创建库、上传文档、检索片段', icon: KnowledgeIcon, color: '#fbbf24' },
  { name: '定时任务', path: '/tasks', description: 'Cron、技能执行与手动触发', icon: TaskIcon, color: '#fb923c' },
  { name: '网络搜索', path: '/search', description: '搜索、总结、历史与兴趣画像', icon: SearchIcon, color: '#fdba74' },
  { name: '工具管理', path: '/tools', description: 'MCP 工具配置、验证与执行', icon: ToolIcon, color: '#f97316' },
  { name: '技能管理', path: '/skills', description: '分类、绑定工具、执行与导出', icon: SkillIcon, color: '#f59e0b' },
  { name: '邮件配置', path: '/email', description: '邮箱、监听、测试与模板', icon: MailIcon, color: '#fb923c' },
  { name: '系统设置', path: '/settings', description: '模型、Qdrant、搜索与文件参数', icon: SettingsIcon, color: '#fbbf24' }
]

const loadDashboard = async () => {
  try {
    const [filesRes, schedulesRes, toolsRes, skillsRes, modelsRes, tasksRes, knowledgeRes] = await Promise.all([
      fileService.list(),
      scheduleService.list(),
      mcpToolService.list(),
      skillService.list(),
      modelService.list(),
      taskService.list(),
      knowledgeService.list()
    ])

    const allSchedules = schedulesRes || []
    const today = dayjs().format('YYYY-MM-DD')

    stats.value = {
      files: filesRes.data?.length || filesRes.total || 0,
      schedules: allSchedules.length || 0,
      tools: toolsRes.data?.length || toolsRes.total || 0,
      skills: skillsRes.data?.length || skillsRes.total || 0,
      models: modelsRes.data?.length || 0,
      tasks: tasksRes.data?.length || 0,
      knowledge: knowledgeRes.data?.length || 0
    }

    recentFiles.value = (filesRes.data || []).slice(0, 4)
    todaySchedules.value = allSchedules.filter(item => item.eventDate === today).slice(0, 4)
  } catch (error) {
    console.error('加载仪表盘失败:', error)
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
.activity-row,
.coverage-item {
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.04);
}

.mini-stat {
  padding: 16px;
  border-radius: 20px;
}

.mini-stat span {
  display: block;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.mini-stat strong {
  font-size: 1.4rem;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px;
  border-radius: 24px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
  box-shadow: var(--shadow-sm);
}

.metric-card:hover,
.module-card:hover {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--primary-color) 55%, white 12%);
  box-shadow: var(--shadow-glow);
}

.metric-card__icon,
.module-card__icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: #fff7ed;
  background: linear-gradient(135deg, var(--icon-color, #f59e0b), color-mix(in srgb, var(--icon-color, #f59e0b) 72%, #fde68a 28%));
}

.metric-card__icon {
  width: 54px;
  height: 54px;
  border-radius: 18px;
}

.metric-card__copy {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.metric-card__copy span,
.module-card p,
.activity-row p,
.timeline-copy p,
.coverage-item span {
  color: var(--text-secondary);
}

.metric-card__copy strong {
  font-size: 1.8rem;
  line-height: 1;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.section-head h3 {
  font-size: 1.2rem;
}

.module-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.module-card {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px;
  border-radius: 22px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}

.module-card__icon {
  width: 46px;
  height: 46px;
  border-radius: 16px;
  --icon-color: var(--module-color);
}

.module-card strong,
.timeline-copy strong,
.activity-row strong {
  display: block;
  margin-bottom: 4px;
}

.timeline-list,
.activity-list,
.coverage-list {
  display: grid;
  gap: 12px;
}

.timeline-item,
.activity-row,
.coverage-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 20px;
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
  box-shadow: 0 0 0 8px rgba(245, 158, 11, 0.12);
}

.timeline-copy {
  flex: 1;
}

.coverage-item strong {
  color: var(--text-primary);
}

@media (max-width: 1100px) {
  .metrics-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 768px) {
  .metrics-grid,
  .module-grid {
    grid-template-columns: 1fr;
  }
}
</style>
