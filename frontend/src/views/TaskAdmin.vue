<template>
  <div class="task-admin">
    <!-- 头部 -->
    <header class="admin-header">
      <div class="header-info">
        <nav class="breadcrumb">
          <span class="breadcrumb-item">自动化</span>
          <span class="breadcrumb-separator">/</span>
          <span class="breadcrumb-current">调度管理</span>
        </nav>
        <p class="header-subtitle">xxl-job 风格的定时任务调度中心：任务定义、执行日志、状态一站管理</p>
      </div>
      <div class="header-actions">
        <n-button secondary @click="loadTasks" title="刷新">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          刷新
        </n-button>
        <n-button type="primary" @click="openCreateModal" title="新建任务">
          <template #icon><n-icon><AddIcon /></n-icon></template>
          新建任务
        </n-button>
      </div>
    </header>

    <!-- 统计卡片 -->
    <section class="stats-row">
      <div class="stat-card">
        <div class="stat-icon"><n-icon size="22"><TimerIcon /></n-icon></div>
        <div class="stat-data">
          <span class="stat-label">任务总数</span>
          <strong class="stat-value">{{ tasks.length }}</strong>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon active"><n-icon size="22"><CheckmarkIcon /></n-icon></div>
        <div class="stat-data">
          <span class="stat-label">启用中</span>
          <strong class="stat-value">{{ enabledCount }}</strong>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon running"><n-icon size="22"><PlayIcon /></n-icon></div>
        <div class="stat-data">
          <span class="stat-label">正在运行</span>
          <strong class="stat-value">{{ runningCount }}</strong>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon"><n-icon size="22"><StatsIcon /></n-icon></div>
        <div class="stat-data">
          <span class="stat-label">累计执行</span>
          <strong class="stat-value">{{ totalExecutes }}</strong>
        </div>
      </div>
    </section>

    <!-- 主体：左侧任务表 + 右侧日志面板 -->
    <section class="admin-grid">
      <!-- 左：任务列表 -->
      <div class="job-panel">
        <div class="panel-header">
          <span class="panel-tag">Jobs</span>
          <h3 class="panel-title">任务列表</h3>
          <span class="panel-meta">{{ filteredTasks.length }} 条</span>
        </div>

        <div class="filter-bar">
          <n-input v-model:value="filterKeyword" placeholder="搜索任务名 / 描述" clearable size="small" style="flex: 1" />
          <n-select
            v-model:value="filterType"
            :options="taskTypeFilterOptions"
            size="small"
            style="width: 140px"
            placeholder="类型"
          />
          <n-select
            v-model:value="filterStatus"
            :options="statusFilterOptions"
            size="small"
            style="width: 120px"
            placeholder="状态"
          />
        </div>

        <div class="job-table-wrapper">
          <table v-if="filteredTasks.length > 0" class="job-table">
            <thead>
              <tr>
                <th style="width: 50px">ID</th>
                <th>任务名 / 描述</th>
                <th style="width: 80px">Handler</th>
                <th style="width: 110px">Cron</th>
                <th style="width: 70px">状态</th>
                <th style="width: 80px">运行</th>
                <th style="width: 130px">上次触发</th>
                <th style="width: 130px">下次触发</th>
                <th style="width: 60px">成功/总</th>
                <th style="width: 220px">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="task in filteredTasks"
                :key="task.id"
                :class="{ active: currentTask?.id === task.id }"
                @click="selectTask(task)"
              >
                <td class="cell-id">#{{ task.id }}</td>
                <td class="cell-name">
                  <strong>{{ task.name }}</strong>
                  <span v-if="task.description" class="cell-desc">{{ task.description }}</span>
                </td>
                <td><span class="badge" :class="`badge-${task.taskType?.toLowerCase()}`">{{ task.taskType }}</span></td>
                <td class="cell-cron">{{ task.cronExpression }}</td>
                <td>
                  <span class="status-dot" :class="{ enabled: task.enabled }"></span>
                  <span class="status-text">{{ task.enabled ? '已启用' : '已停用' }}</span>
                </td>
                <td>
                  <span class="status-dot" :class="{ running: task.triggerStatus === 1 }"></span>
                  <span class="status-text">{{ task.triggerStatus === 1 ? '运行中' : '静止' }}</span>
                </td>
                <td class="cell-time">{{ formatTime(task.lastExecuteTime) }}</td>
                <td class="cell-time">{{ formatTime(task.nextExecuteTime) }}</td>
                <td class="cell-counters">{{ task.successCount || 0 }} / {{ task.executeCount || 0 }}</td>
                <td class="cell-ops" @click.stop>
                  <n-button size="tiny" quaternary @click="executeTask(task)" title="立即执行">
                    <template #icon><n-icon><PlayIcon /></n-icon></template>
                  </n-button>
                  <n-button size="tiny" quaternary @click="editTask(task)" title="编辑">
                    <template #icon><n-icon><EditIcon /></n-icon></template>
                  </n-button>
                  <n-button size="tiny" quaternary @click="toggleTask(task)" :title="task.enabled ? '停用' : '启用'">
                    <template #icon><n-icon><PauseIcon v-if="task.enabled" /><PlayIcon v-else /></n-icon></template>
                  </n-button>
                  <n-button size="tiny" quaternary type="error" @click="confirmDelete(task)" title="删除">
                    <template #icon><n-icon><TrashIcon /></n-icon></template>
                  </n-button>
                </td>
              </tr>
            </tbody>
          </table>
          <div v-else-if="loading" class="empty-state"><n-spin /></div>
          <div v-else class="empty-state">
            <n-icon size="40"><TimerIcon /></n-icon>
            <p>暂无任务，点击右上角"新建任务"开始</p>
          </div>
        </div>
      </div>

      <!-- 右：日志面板 -->
      <aside class="log-panel">
        <div class="panel-header">
          <span class="panel-tag">Logs</span>
          <h3 class="panel-title">执行日志</h3>
          <span v-if="currentTask" class="panel-meta">{{ currentTask.name }}</span>
        </div>

        <div v-if="!currentTask" class="empty-state">
          <n-icon size="40"><DocumentIcon /></n-icon>
          <p>点击左侧任务查看执行日志</p>
        </div>
        <div v-else class="log-content">
          <div class="log-toolbar">
            <n-select
              v-model:value="logPageSize"
              :options="logSizeOptions"
              size="tiny"
              style="width: 90px"
            />
            <n-button size="tiny" secondary @click="loadLogs">
              <template #icon><n-icon><RefreshIcon /></n-icon></template>
              刷新
            </n-button>
            <span class="log-total">共 {{ logTotal }} 条</span>
          </div>

          <div class="log-list">
            <div v-if="logLoading" class="empty-state"><n-spin /></div>
            <div v-else-if="logs.length === 0" class="empty-state">
              <p>暂无执行记录</p>
            </div>
            <div
              v-for="log in logs"
              v-else
              :key="log.id"
              class="log-item"
              :class="`log-${log.status.toLowerCase()}`"
            >
              <div class="log-head">
                <span class="log-status" :class="`status-${log.status.toLowerCase()}`">{{ statusLabel(log.status) }}</span>
                <span class="log-time">{{ formatTime(log.triggerTime) }}</span>
                <span class="log-trigger">{{ log.triggerType }}</span>
                <span v-if="log.durationMs != null" class="log-duration">{{ log.durationMs }}ms</span>
              </div>
              <div v-if="log.errorMessage" class="log-error">{{ truncate(log.errorMessage, 200) }}</div>
              <div v-else-if="log.result" class="log-result">{{ truncate(log.result, 200) }}</div>
            </div>
          </div>
        </div>
      </aside>
    </section>

    <!-- 创建/编辑模态框 -->
    <n-modal v-model:show="showFormModal" preset="card" :title="editingTask ? '编辑任务' : '新建任务'" style="width: min(720px, 92vw)">
      <n-form ref="formRef" :model="form" :rules="formRules" label-placement="top">
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="任务名称" path="name">
            <n-input v-model:value="form.name" placeholder="例如：每日早间提醒" />
          </n-form-item-gi>
          <n-form-item-gi label="任务类型" path="taskType">
            <n-select v-model:value="form.taskType" :options="taskTypeOptions" />
          </n-form-item-gi>
          <n-form-item-gi span="2" label="任务描述">
            <n-input v-model:value="form.description" type="textarea" placeholder="可选，便于识别" />
          </n-form-item-gi>
          <n-form-item-gi v-if="form.taskType === 'SKILL'" span="2" label="技能代码" path="skillCode">
            <n-input v-model:value="form.skillCode" placeholder="例如：DAILY_REPORT" />
          </n-form-item-gi>
          <n-form-item-gi span="2" label="Cron 表达式" path="cronExpression">
            <n-input v-model:value="form.cronExpression" placeholder="6 字段 cron，例如 0 0 8 * * ?">
              <template #suffix>
                <n-tooltip trigger="hover">
                  <template #trigger>
                    <n-icon style="cursor: pointer"><HelpIcon /></n-icon>
                  </template>
                  <div>
                    <p>秒 分 时 日 月 周（年可选）</p>
                    <p>0 0 8 * * ?&nbsp;&nbsp;每天 8 点</p>
                    <p>0 0/30 * * * ?&nbsp;&nbsp;每 30 分钟</p>
                    <p>0 0 9 * * MON&nbsp;&nbsp;每周一 9 点</p>
                  </div>
                </n-tooltip>
              </template>
            </n-input>
          </n-form-item-gi>
          <n-form-item-gi span="2" label="任务参数">
            <n-input
              v-model:value="form.params"
              type="textarea"
              :rows="3"
              :placeholder="form.taskType === 'CHAT' ? '提示词内容' : (form.taskType === 'SKILL' ? 'JSON 参数，例如 {key:value}' : '可选')"
            />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showFormModal = false">取消</n-button>
          <n-button type="primary" :loading="submitLoading" @click="submitForm">{{ editingTask ? '更新' : '创建' }}</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * 调度管理页面（xxl-job 风格）。
 * 左侧任务列表 + 右侧执行日志面板，整体设计与 Tasks.vue 互不依赖，
 * 共用同一套 REST API（/api/task/*）。
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  NButton,
  NForm,
  NFormItemGi,
  NGrid,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NSpin,
  NTooltip,
  useMessage,
  useDialog
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  RefreshOutline as RefreshIcon,
  PlayOutline as PlayIcon,
  CreateOutline as EditIcon,
  PauseOutline as PauseIcon,
  TrashOutline as TrashIcon,
  TimerOutline as TimerIcon,
  CheckmarkCircleOutline as CheckmarkIcon,
  StatsChartOutline as StatsIcon,
  DocumentTextOutline as DocumentIcon,
  HelpOutline as HelpIcon
} from '@vicons/ionicons5'
import { taskService } from '@/services/api/task'
import type { ScheduledTask, JobLog } from '@/types'
import { formatArrayTime as formatTime } from '@/utils/date-format'

const message = useMessage()
const dialog = useDialog()

const loading = ref(false)
const tasks = ref<ScheduledTask[]>([])
const currentTask = ref<ScheduledTask | null>(null)

const filterKeyword = ref('')
const filterType = ref('')
const filterStatus = ref('')

const showFormModal = ref(false)
const editingTask = ref<ScheduledTask | null>(null)
const submitLoading = ref(false)
const formRef = ref()

const form = ref({
  name: '',
  description: '',
  taskType: 'REMINDER',
  cronExpression: '',
  params: '',
  skillCode: ''
})

const formRules = {
  name: { required: true, message: '请输入任务名称', trigger: 'blur' },
  taskType: { required: true, message: '请选择任务类型', trigger: 'change' },
  cronExpression: { required: true, message: '请输入 Cron 表达式', trigger: 'blur' }
}

const taskTypeOptions = [
  { label: 'SKILL 技能执行', value: 'SKILL' },
  { label: 'CHAT AI 对话', value: 'CHAT' },
  { label: 'REMINDER 邮件提醒', value: 'REMINDER' }
]

// 过滤选项（"全部"用空字符串占位，避免 Naive UI SelectMixedOption 对 null 的类型冲突）
const taskTypeFilterOptions = [
  { label: '全部类型', value: '' },
  ...taskTypeOptions
] as unknown as Array<{ label: string; value: string }>

const statusFilterOptions = [
  { label: '全部状态', value: '' },
  { label: '已启用', value: 'enabled' },
  { label: '已停用', value: 'disabled' }
] as unknown as Array<{ label: string; value: string }>

const logLoading = ref(false)
const logs = ref<JobLog[]>([])
const logTotal = ref(0)
const logPageSize = ref(10)

const logSizeOptions = [
  { label: '10 条', value: 10 },
  { label: '20 条', value: 20 },
  { label: '50 条', value: 50 }
]

const enabledCount = computed(() => tasks.value.filter(t => t.enabled).length)
const runningCount = computed(() => tasks.value.filter(t => t.triggerStatus === 1).length)
const totalExecutes = computed(() => tasks.value.reduce((sum, t) => sum + (t.executeCount || 0), 0))

const filteredTasks = computed(() => {
  return tasks.value.filter(t => {
    if (filterType.value && t.taskType !== filterType.value) return false
    if (filterStatus.value === 'enabled' && !t.enabled) return false
    if (filterStatus.value === 'disabled' && t.enabled) return false
    if (filterKeyword.value) {
      const k = filterKeyword.value.toLowerCase()
      const hitName = t.name?.toLowerCase().includes(k)
      const hitDesc = t.description?.toLowerCase().includes(k)
      if (!hitName && !hitDesc) return false
    }
    return true
  })
})

const statusLabel = (s: string) => {
  switch (s) {
    case 'SUCCESS': return '成功'
    case 'FAILED': return '失败'
    case 'RUNNING': return '运行中'
    default: return s
  }
}

const truncate = (value: string | null | undefined, max: number) => {
  if (!value) return ''
  return value.length > max ? value.substring(0, max) + '...' : value
}

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await taskService.list()
    if (res.success) {
      tasks.value = res.data || []
      // 刷新当前任务的状态（triggerStatus 可能已变化）
      if (currentTask.value) {
        const refreshed = tasks.value.find(t => t.id === currentTask.value!.id)
        if (refreshed) currentTask.value = refreshed
      }
    } else {
      message.error(res.message || '加载任务失败')
    }
  } catch (e) {
    console.error('加载任务失败:', e)
    message.error('加载任务失败')
  } finally {
    loading.value = false
  }
}

const selectTask = async (task: ScheduledTask) => {
  currentTask.value = task
  await loadLogs()
}

const loadLogs = async () => {
  if (!currentTask.value) return
  logLoading.value = true
  try {
    const res = await taskService.logs(currentTask.value.id, 1, logPageSize.value)
    if (res.success) {
      logs.value = res.data?.records || []
      logTotal.value = res.data?.total || 0
    }
  } catch (e) {
    console.error('加载日志失败:', e)
    message.error('加载日志失败')
  } finally {
    logLoading.value = false
  }
}

const openCreateModal = () => {
  editingTask.value = null
  form.value = { name: '', description: '', taskType: 'REMINDER', cronExpression: '', params: '', skillCode: '' }
  showFormModal.value = true
}

const editTask = (task: ScheduledTask) => {
  editingTask.value = task
  form.value = {
    name: task.name,
    description: task.description || '',
    taskType: task.taskType,
    cronExpression: task.cronExpression,
    params: task.params || '',
    skillCode: task.skillCode || ''
  }
  showFormModal.value = true
}

const submitForm = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitLoading.value = true
  try {
    if (editingTask.value) {
      const res = await taskService.update(editingTask.value.id, form.value)
      if (res.success) {
        message.success('更新成功')
        showFormModal.value = false
        await loadTasks()
        if (currentTask.value?.id === editingTask.value.id) {
          currentTask.value = res.data || null
          await loadLogs()
        }
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const res = await taskService.create(form.value)
      if (res.success) {
        message.success('创建成功')
        showFormModal.value = false
        await loadTasks()
      } else {
        message.error(res.message || '创建失败')
      }
    }
  } catch (e) {
    console.error('操作失败:', e)
    message.error('操作失败')
  } finally {
    submitLoading.value = false
  }
}

const executeTask = async (task: ScheduledTask) => {
  try {
    message.loading('正在执行任务...')
    const res = await taskService.execute(task.id)
    if (res.success) {
      message.success(`执行成功：${truncate(res.data, 80)}`)
      await loadTasks()
      if (currentTask.value?.id === task.id) {
        currentTask.value = tasks.value.find(t => t.id === task.id) || currentTask.value
        await loadLogs()
      }
    } else {
      message.error(res.message || '执行失败')
    }
  } catch (e) {
    console.error('执行失败:', e)
    message.error('执行失败')
  }
}

const toggleTask = async (task: ScheduledTask) => {
  try {
    const res = await taskService.toggle(task.id)
    if (res.success) {
      message.success(res.data?.enabled ? '已启用' : '已停用')
      await loadTasks()
    } else {
      message.error(res.message || '操作失败')
    }
  } catch (e) {
    console.error('切换状态失败:', e)
    message.error('操作失败')
  }
}

const confirmDelete = (task: ScheduledTask) => {
  dialog.warning({
    title: '确认删除',
    content: `任务 "${task.name}" 及其全部执行日志将被删除，操作不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await taskService.delete(task.id)
        if (res.success) {
          message.success('删除成功')
          if (currentTask.value?.id === task.id) {
            currentTask.value = null
            logs.value = []
            logTotal.value = 0
          }
          await loadTasks()
        } else {
          message.error(res.message || '删除失败')
        }
      } catch (e) {
        console.error('删除失败:', e)
        message.error('删除失败')
      }
    }
  })
}

let refreshTimer: ReturnType<typeof setInterval> | null = null

onMounted(async () => {
  await loadTasks()
  // 每 10 秒自动刷新一次（如果开启了 SSE/WebSocket 可以替换为推送）
  refreshTimer = setInterval(() => {
    loadTasks()
  }, 10_000)
})

onUnmounted(() => {
  if (refreshTimer !== null) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.task-admin {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 0;
  min-height: calc(100vh - 200px);
}

/* Header */
.admin-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}
.header-info { display: flex; flex-direction: column; gap: 8px; }
.breadcrumb { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; }
.breadcrumb-item { color: var(--text-muted); }
.breadcrumb-separator { color: var(--text-muted); opacity: 0.5; }
.breadcrumb-current { color: var(--text-primary); font-weight: 500; }
.header-subtitle { font-size: 0.85rem; color: var(--text-secondary); letter-spacing: 0.02em; margin: 0; }
.header-actions { display: flex; gap: 12px; }

/* Stats */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 2px solid var(--border-light);
  transition: all 0.2s;
}
.stat-card:hover { transform: translateY(-1px); box-shadow: var(--shadow-sm); }
.stat-icon {
  width: 44px; height: 44px;
  display: grid; place-items: center;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  color: var(--text-secondary);
}
.stat-icon.active { background: rgba(52, 199, 89, 0.12); color: #34C759; }
.stat-icon.running { background: rgba(255, 149, 0, 0.12); color: #FF9500; }
.stat-data { display: flex; flex-direction: column; gap: 2px; }
.stat-label { font-size: 0.72rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; }
.stat-value { font-size: 1.6rem; font-weight: 700; color: var(--text-primary); line-height: 1; }

/* Grid */
.admin-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 20px;
  min-height: 0;
}

.job-panel, .log-panel {
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 2px solid var(--border-light);
  overflow: hidden;
}
.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-light);
}
.panel-tag {
  font-size: 0.7rem;
  font-weight: 600;
  color: #D97706;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}
.panel-title { font-size: 0.95rem; font-weight: 600; color: var(--text-primary); margin: 0; }
.panel-meta { font-size: 0.78rem; color: var(--text-muted); margin-left: auto; }

/* Filter */
.filter-bar {
  display: flex;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-base);
}

/* Job Table */
.job-table-wrapper {
  flex: 1;
  overflow: auto;
}
.job-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}
.job-table thead {
  position: sticky;
  top: 0;
  background: var(--bg-base);
  z-index: 1;
}
.job-table th {
  text-align: left;
  padding: 10px 12px;
  font-weight: 600;
  color: var(--text-secondary);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-bottom: 1px solid var(--border-light);
}
.job-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-light);
  color: var(--text-primary);
  vertical-align: middle;
}
.job-table tbody tr {
  cursor: pointer;
  transition: background 0.15s;
}
.job-table tbody tr:hover { background: var(--bg-menu-item-hover); }
.job-table tbody tr.active {
  background: var(--bg-menu-item-active);
  box-shadow: inset 2px 0 0 #D97706;
}
.cell-id { font-family: var(--font-mono); color: var(--text-muted); font-size: 0.8rem; }
.cell-name { display: flex; flex-direction: column; gap: 2px; }
.cell-desc { font-size: 0.75rem; color: var(--text-muted); }
.cell-cron { font-family: var(--font-mono); font-size: 0.78rem; color: var(--text-secondary); }
.cell-time { font-size: 0.78rem; color: var(--text-secondary); white-space: nowrap; }
.cell-counters { font-family: var(--font-mono); font-size: 0.85rem; }
.cell-ops { white-space: nowrap; }

.badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 100px;
  font-size: 0.7rem;
  font-weight: 500;
  background: var(--bg-input);
  color: var(--text-secondary);
}
.badge-skill { background: rgba(99, 102, 241, 0.12); color: #6366F1; }
.badge-chat { background: rgba(168, 85, 247, 0.12); color: #A855F7; }
.badge-reminder { background: rgba(217, 119, 6, 0.12); color: #D97706; }

.status-dot {
  display: inline-block;
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  margin-right: 6px;
  vertical-align: middle;
}
.status-dot.enabled { background: #34C759; }
.status-dot.running {
  background: #FF9500;
  animation: pulse 1.2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}
.status-text { font-size: 0.78rem; color: var(--text-secondary); }

/* Log Panel */
.log-content { display: flex; flex-direction: column; flex: 1; overflow: hidden; }
.log-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg-base);
}
.log-total { font-size: 0.78rem; color: var(--text-muted); margin-left: auto; }
.log-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.log-item {
  padding: 10px 12px;
  border-radius: var(--radius-md);
  border-left: 3px solid var(--text-muted);
  background: var(--bg-base);
  margin-bottom: 6px;
  transition: all 0.15s;
}
.log-item:hover { background: var(--bg-menu-item-hover); }
.log-success { border-left-color: #34C759; }
.log-failed { border-left-color: #FF3B30; background: rgba(255, 59, 48, 0.04); }
.log-running { border-left-color: #FF9500; }

.log-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.78rem;
  flex-wrap: wrap;
}
.log-status {
  padding: 1px 8px;
  border-radius: 100px;
  font-size: 0.7rem;
  font-weight: 600;
}
.status-success { background: rgba(52, 199, 89, 0.15); color: #34C759; }
.status-failed { background: rgba(255, 59, 48, 0.15); color: #FF3B30; }
.status-running { background: rgba(255, 149, 0, 0.15); color: #FF9500; }
.log-time { font-family: var(--font-mono); color: var(--text-secondary); }
.log-trigger {
  font-size: 0.7rem;
  padding: 1px 6px;
  border-radius: 100px;
  background: var(--bg-input);
  color: var(--text-muted);
}
.log-duration { font-family: var(--font-mono); color: var(--text-muted); margin-left: auto; }
.log-error {
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 0.78rem;
  color: #FF3B30;
  white-space: pre-wrap;
  word-break: break-all;
}
.log-result {
  margin-top: 6px;
  font-family: var(--font-mono);
  font-size: 0.78rem;
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 60px 20px;
  color: var(--text-muted);
}

@media (max-width: 1200px) {
  .admin-grid { grid-template-columns: 1fr; }
  .stats-row { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 768px) {
  .stats-row { grid-template-columns: 1fr; }
  .job-table { font-size: 0.78rem; }
}
</style>