<template>
  <div class="dashboard">
    <!-- Bento Grid Layout -->
    <div class="bento-container">
      <!-- Clock Card - Large -->
      <div class="bento-card bento-clock">
        <div class="clock-content">
          <span class="time-display">{{ formattedTime }}</span>
          <span class="date-display">{{ formattedDate }}</span>
          <span class="insight-text">{{ timeInsight }}</span>
        </div>
      </div>

      <!-- Stats Cards -->
      <div class="bento-card bento-stat" @click="$router.push('/files')">
        <div class="stat-icon files">
          <n-icon size="24"><FolderIcon /></n-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">
            <CountUp :end-val="stats.files" :duration="1.5" />
          </span>
          <span class="stat-label">份文件已入库</span>
        </div>
      </div>

      <div class="bento-card bento-stat" @click="$router.push('/models')">
        <div class="stat-icon models">
          <n-icon size="24"><ModelIcon /></n-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">
            <CountUp :end-val="stats.models" :duration="1.5" />
          </span>
          <span class="stat-label">组模型已就绪</span>
        </div>
      </div>

      <div class="bento-card bento-stat" @click="$router.push('/knowledge')">
        <div class="stat-icon knowledge">
          <n-icon size="24"><KnowledgeIcon /></n-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">
            <CountUp :end-val="stats.knowledge" :duration="1.5" />
          </span>
          <span class="stat-label">条知识已索引</span>
        </div>
      </div>

      <div class="bento-card bento-stat" @click="$router.push('/tasks')">
        <div class="stat-icon tasks">
          <n-icon size="24"><TaskIcon /></n-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">
            <CountUp :end-val="stats.tasks" :duration="1.5" />
          </span>
          <span class="stat-label">项任务在推进</span>
        </div>
      </div>

      <div class="bento-card bento-stat" @click="$router.push('/tools')">
        <div class="stat-icon tools">
          <n-icon size="24"><ToolIcon /></n-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">
            <CountUp :end-val="stats.tools" :duration="1.5" />
          </span>
          <span class="stat-label">个工具已接入</span>
        </div>
      </div>

      <div class="bento-card bento-stat" @click="$router.push('/skills')">
        <div class="stat-icon skills">
          <n-icon size="24"><SkillIcon /></n-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">
            <CountUp :end-val="stats.skills" :duration="1.5" />
          </span>
          <span class="stat-label">项技能可调用</span>
        </div>
      </div>

      <div class="bento-card bento-stat" @click="$router.push('/personal')">
        <div class="stat-icon tokens">
          <n-icon size="24"><InboxIcon /></n-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">
            <CountUp :end-val="personalInsight?.totalTokenUsage || 0" :duration="1.5" />
          </span>
          <span class="stat-label">次交互已记录</span>
        </div>
      </div>

      <div class="bento-card bento-stat" @click="$router.push('/schedule')">
        <div class="stat-icon schedules">
          <n-icon size="24"><CalendarIcon /></n-icon>
        </div>
        <div class="stat-info">
          <span class="stat-value">
            <CountUp :end-val="todaySchedules.length" :duration="1.5" />
          </span>
          <span class="stat-label">件事待跟进</span>
        </div>
      </div>

      <!-- Quick Entry Card - Medium -->
      <div class="bento-card bento-quick">
        <div class="quick-header">
          <span class="quick-title">快速入口</span>
        </div>
        <div class="quick-grid">
          <button class="quick-item" @click="$router.push('/chat')">
            <n-icon size="20"><ChatIcon /></n-icon>
            <span>开始对话</span>
          </button>
          <button class="quick-item" @click="$router.push('/inbox')">
            <n-icon size="20"><InboxIcon /></n-icon>
            <span>查看动态</span>
          </button>
          <button class="quick-item" @click="$router.push('/autonomy')">
            <n-icon size="20"><AutonomyIcon /></n-icon>
            <span>启动自治</span>
          </button>
          <button class="quick-item" @click="$router.push('/search')">
            <n-icon size="20"><SearchIcon /></n-icon>
            <span>全局搜索</span>
          </button>
          <button class="quick-item" @click="$router.push('/settings')">
            <n-icon size="20"><SettingsIcon /></n-icon>
            <span>调整参数</span>
          </button>
          <button class="quick-item" @click="$router.push('/reports')">
            <n-icon size="20"><ReportIcon /></n-icon>
            <span>生成报告</span>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, defineComponent, h } from 'vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { NIcon } from 'naive-ui'
import {
  CalendarOutline as CalendarIcon,
  FlashOutline as AutonomyIcon,
  BookOutline as KnowledgeIcon,
  ChatbubblesOutline as ChatIcon,
  CubeOutline as ModelIcon,
  FileTrayFullOutline as InboxIcon,
  FolderOutline as FolderIcon,
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
  scheduleService,
  skillService,
  taskService,
  personalService
} from '@/services/api'
import type { ScheduleEvent } from '@/types'
import { readCachedPayload, writeCachedPayload } from '@/services/user-preferences'

dayjs.locale('zh-cn')

// CountUp Component
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

// Real-time clock
const currentTime = ref(dayjs())
let clockInterval: ReturnType<typeof setInterval> | null = null

const formattedTime = computed(() => currentTime.value.format('HH:mm'))
const formattedDate = computed(() => currentTime.value.format('MM月DD日'))

// Time-based insight text - 行动导向的短句
const timeInsight = computed(() => {
  const hour = currentTime.value.hour()
  const schedulesCount = todaySchedules.value.length
  const tasksCount = stats.value.tasks

  // 深夜时段 (23:00 - 05:00)
  if (hour >= 23 || hour < 5) {
    return '深夜，适合深度复盘'
  }
  // 清晨时段 (05:00 - 09:00)
  if (hour >= 5 && hour < 9) {
    if (schedulesCount > 0) {
      return `晨间，今日已有 ${schedulesCount} 项计划`
    }
    return '清晨，系统已待命'
  }
  // 上午时段 (09:00 - 12:00)
  if (hour >= 9 && hour < 12) {
    if (tasksCount > 0) {
      return `上午，${tasksCount} 项任务推进中`
    }
    return '上午，最佳产出时段'
  }
  // 午间时段 (12:00 - 14:00)
  if (hour >= 12 && hour < 14) {
    return '午间，短暂休憩'
  }
  // 下午时段 (14:00 - 18:00)
  if (hour >= 14 && hour < 18) {
    if (stats.value.files > 0) {
      return `下午，${stats.value.files} 份文件待审阅`
    }
    return '下午，专注时刻'
  }
  // 傍晚时段 (18:00 - 21:00)
  if (hour >= 18 && hour < 21) {
    return '傍晚，收尾与总结'
  }
  // 夜间时段 (21:00 - 23:00)
  return '夜间，思考与规划'
})

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
/* Bento Grid Dashboard */
.dashboard {
  min-height: calc(100vh - 200px);
  padding: 8px;
}

.bento-container {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  grid-template-rows: auto auto auto;
  gap: 16px;
  grid-template-areas:
    "clock clock stat1 stat2"
    "stat3 stat4 stat5 stat6"
    "stat7 stat8 quick quick";
}

/* Base Card Style - Warm solid instead of glass */
.bento-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: 20px;
  transition: transform var(--transition-base), box-shadow var(--transition-base), border-color var(--transition-base);
  cursor: default;
  position: relative;
  overflow: hidden;
}

.bento-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--gradient-sunset);
  opacity: 0;
  transition: opacity var(--transition-base);
}

.bento-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-lg);
  border-color: var(--primary-light);
}

.bento-card:hover::before {
  opacity: 1;
}

/* Clock Card - Large */
.bento-clock {
  grid-area: clock;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background: var(--gradient-citrus);
  border-color: var(--border-light);
}

.bento-clock::before {
  display: none;
}

.clock-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.time-display {
  font-size: 3.5rem;
  font-weight: 800;
  font-family: var(--font-display);
  color: var(--text-primary);
  letter-spacing: -0.03em;
  background: var(--gradient-sunset);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.date-display {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.insight-text {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--text-muted);
  margin-top: 8px;
}

/* Stat Cards */
.bento-stat {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  cursor: pointer;
}

.bento-stat:nth-child(2) { grid-area: stat1; }
.bento-stat:nth-child(3) { grid-area: stat2; }
.bento-stat:nth-child(4) { grid-area: stat3; }
.bento-stat:nth-child(5) { grid-area: stat4; }
.bento-stat:nth-child(6) { grid-area: stat5; }
.bento-stat:nth-child(7) { grid-area: stat6; }
.bento-stat:nth-child(8) { grid-area: stat7; }
.bento-stat:nth-child(9) { grid-area: stat8; }

.stat-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  color: white;
  flex-shrink: 0;
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  transition: transform var(--transition-fast);
}

.bento-stat:hover .stat-icon {
  transform: translateY(-1px) scale(1.025);
}

.stat-icon.files { background: linear-gradient(135deg, #FB923C, #EA580C); }
.stat-icon.models { background: linear-gradient(135deg, #F59E0B, #D97706); }
.stat-icon.knowledge { background: linear-gradient(135deg, #4ADE80, #16A34A); }
.stat-icon.tasks { background: linear-gradient(135deg, #14B8A6, #0D9488); }
.stat-icon.tools { background: linear-gradient(135deg, #C084FC, #A855F7); }
.stat-icon.skills { background: linear-gradient(135deg, #F472B6, #EC4899); }
.stat-icon.tokens { background: linear-gradient(135deg, #D97706, #B45309); }
.stat-icon.schedules { background: linear-gradient(135deg, #FBBF24, #F59E0B); }

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.stat-value {
  font-size: 1.75rem;
  font-weight: 800;
  color: var(--text-primary);
  line-height: 1;
}

.stat-label {
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--text-muted);
}

/* Quick Entry Card */
.bento-quick {
  grid-area: quick;
  padding: 24px;
}

.quick-header {
  margin-bottom: 16px;
}

.quick-title {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px;
  background: var(--warm-50);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  color: var(--text-primary);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: transform var(--transition-base), box-shadow var(--transition-base), border-color var(--transition-base), background-color var(--transition-base);
}

.quick-item:hover {
  background: var(--warm-100);
  border-color: var(--primary-light);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px var(--primary-glow);
}

.quick-item span {
  color: var(--text-secondary);
}

/* Responsive */
@media (max-width: 1024px) {
  .bento-container {
    grid-template-columns: repeat(3, 1fr);
    grid-template-areas:
      "clock clock clock"
      "stat1 stat2 stat3"
      "stat4 stat5 stat6"
      "stat7 stat8 quick";
  }
}

@media (max-width: 768px) {
  .bento-container {
    grid-template-columns: repeat(2, 1fr);
    grid-template-areas:
      "clock clock"
      "stat1 stat2"
      "stat3 stat4"
      "stat5 stat6"
      "stat7 stat8"
      "quick quick";
  }

  .time-display {
    font-size: 2.5rem;
  }

  .quick-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 480px) {
  .bento-container {
    grid-template-columns: 1fr;
    grid-template-areas:
      "clock"
      "stat1"
      "stat2"
      "stat3"
      "stat4"
      "stat5"
      "stat6"
      "stat7"
      "stat8"
      "quick";
  }
}
</style>
