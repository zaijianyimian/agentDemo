<template>
  <div class="automation-hub">
    <!-- Background Layer -->
    <div class="hub-bg"></div>

    <!-- Header Bar - macOS Breadcrumb Style -->
    <header class="hub-header">
      <div class="header-info">
        <nav class="breadcrumb">
          <span class="breadcrumb-item">自动化</span>
          <span class="breadcrumb-separator">/</span>
          <span class="breadcrumb-current">定时任务</span>
        </nav>
        <p class="header-subtitle">集中管理计划任务、执行调度与自动化流程</p>
      </div>
      <div class="header-actions">
        <button class="action-btn refresh-btn" @click="loadTasks" title="刷新">
          <n-icon size="18"><RefreshIcon /></n-icon>
        </button>
        <button class="action-btn create-btn" @click="openCreateModal" title="创建任务">
          <n-icon size="18"><AddIcon /></n-icon>
          <span class="btn-label">New Task</span>
        </button>
      </div>
    </header>

    <!-- Stats Row - Top Horizontal Cards -->
    <section class="stats-row">
      <div class="stat-card">
        <div class="stat-icon">
          <n-icon size="24"><TimerIcon /></n-icon>
        </div>
        <div class="stat-data">
          <span class="stat-label">任务总数</span>
          <strong class="stat-value">{{ tasks.length }}</strong>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon active">
          <n-icon size="24"><CheckmarkCircleIcon /></n-icon>
        </div>
        <div class="stat-data">
          <span class="stat-label">启用中</span>
          <strong class="stat-value">{{ enabledCount }}</strong>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon">
          <n-icon size="24"><LayersIcon /></n-icon>
        </div>
        <div class="stat-data">
          <span class="stat-label">任务类型</span>
          <strong class="stat-value">{{ taskTypeOptions.length }}</strong>
        </div>
      </div>
    </section>

    <!-- Main Content Grid -->
    <section class="hub-grid">
      <!-- Left: Task List Panel -->
      <div class="task-panel">
        <div class="panel-header">
          <span class="panel-tag">Overview</span>
          <h3 class="panel-title">任务列表</h3>
        </div>
        <div class="task-list">
          <div v-if="loading" class="loading-state">
            <n-icon size="32" class="loading-icon"><RefreshIcon /></n-icon>
            <span>正在加载...</span>
          </div>
          <div v-else-if="tasks.length === 0" class="empty-state">
            <n-icon size="40"><TimerIcon /></n-icon>
            <p>暂无任务</p>
            <span>点击右上角 [+] 创建第一个任务</span>
          </div>
          <div v-else class="task-items">
            <div
              v-for="task in tasks"
              :key="task.id"
              class="task-item"
              :class="{ active: currentTask?.id === task.id }"
              @click="currentTask = task"
            >
              <div class="task-status-indicator" :class="{ enabled: task.enabled }"></div>
              <div class="task-info">
                <strong class="task-name">{{ task.name }}</strong>
                <div class="task-meta">
                  <span class="task-type-badge">{{ task.taskType }}</span>
                  <span class="task-cron">{{ task.cronExpression }}</span>
                </div>
              </div>
              <div class="task-stats-mini">
                <span>{{ task.successCount || 0 }}/{{ task.executeCount || 0 }}</span>
              </div>
              <div class="task-actions-mini">
                <button class="mini-btn" @click.stop="executeTask(task)" title="执行">
                  <n-icon size="14"><PlayIcon /></n-icon>
                </button>
                <button class="mini-btn" @click.stop="editTask(task)" title="编辑">
                  <n-icon size="14"><EditIcon /></n-icon>
                </button>
                <button class="mini-btn delete" @click.stop="confirmDelete(task)" title="删除">
                  <n-icon size="14"><TrashIcon /></n-icon>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Template Gallery + Inspector -->
      <div class="side-panel">
        <!-- Template Gallery - Grid Cards -->
        <div class="template-section">
          <div class="section-header">
            <span class="section-tag">Templates</span>
            <h3 class="section-title">模板中心</h3>
          </div>
          <div class="template-grid">
            <div
              v-for="tpl in templateCards"
              :key="tpl.id"
              class="template-card"
              @click="createByTemplate(tpl.id, tpl.name)"
            >
              <div class="template-icon">
                <n-icon size="28"><component :is="tpl.icon" /></n-icon>
              </div>
              <div class="template-content">
                <strong class="template-name">{{ tpl.name }}</strong>
                <p class="template-desc">{{ tpl.description }}</p>
              </div>
              <div class="template-overlay">
                <span>Use Template</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Quick Undo -->
        <div v-if="lastDeletedTask" class="undo-banner" @click="undoDelete">
          <n-icon size="16"><UndoIcon /></n-icon>
          <span>撤销删除 "{{ lastDeletedTask.name }}"</span>
        </div>
      </div>
    </section>

    <!-- Create/Edit Modal -->
    <n-modal v-model:show="showCreateModal" preset="card" :title="editingTask ? '编辑任务' : '创建任务'" style="width: min(680px, 92vw)">
      <n-form ref="formRef" :model="form" :rules="formRules" label-placement="top">
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="任务名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入任务名称" />
          </n-form-item-gi>
          <n-form-item-gi label="任务类型" path="taskType">
            <n-select v-model:value="form.taskType" :options="taskTypeOptions" placeholder="请选择任务类型" />
          </n-form-item-gi>
          <n-form-item-gi span="2" label="任务描述" path="description">
            <n-input v-model:value="form.description" type="textarea" placeholder="请输入任务描述" />
          </n-form-item-gi>
          <n-form-item-gi v-if="form.taskType === 'SKILL'" span="2" label="技能代码" path="skillCode">
            <n-input v-model:value="form.skillCode" placeholder="请输入要执行的技能代码" />
          </n-form-item-gi>
          <n-form-item-gi span="2" label="Cron 表达式" path="cronExpression">
            <n-input v-model:value="form.cronExpression" placeholder="如: 0 0 8 * * ?">
              <template #suffix>
                <n-tooltip trigger="hover">
                  <template #trigger>
                    <n-icon style="cursor: pointer"><HelpIcon /></n-icon>
                  </template>
                  <div>
                    <p>秒 分 时 日 月 周</p>
                    <p>0 0 8 * * ? 每天8点</p>
                    <p>0 0/30 * * * ? 每30分钟</p>
                  </div>
                </n-tooltip>
              </template>
            </n-input>
          </n-form-item-gi>
          <n-form-item-gi span="2" label="任务参数" path="params">
            <n-input v-model:value="form.params" type="textarea" :rows="3" placeholder='JSON 格式参数，如 {"key":"value"}' />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" @click="submitForm" :loading="submitLoading">{{ editingTask ? '更新' : '创建' }}</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- Result Modal -->
    <n-modal v-model:show="showResultModal" preset="card" title="执行结果" style="width: min(720px, 92vw)">
      <n-descriptions label-placement="left" :column="1" bordered>
        <n-descriptions-item label="任务名称">{{ currentTask?.name }}</n-descriptions-item>
        <n-descriptions-item label="执行时间">{{ formatTime(currentTask?.lastExecuteTime || '') }}</n-descriptions-item>
        <n-descriptions-item label="下次执行">{{ formatTime(currentTask?.nextExecuteTime || '') }}</n-descriptions-item>
        <n-descriptions-item label="执行次数">{{ currentTask?.executeCount || 0 }} (成功: {{ currentTask?.successCount || 0 }})</n-descriptions-item>
      </n-descriptions>
      <n-divider>执行结果</n-divider>
      <n-scrollbar style="max-height: 300px">
        <pre class="result-content">{{ currentTask?.lastExecuteResult || '暂无执行结果' }}</pre>
      </n-scrollbar>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import {
  NButton,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NForm,
  NFormItemGi,
  NGrid,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NScrollbar,
  NSpace,
  NTooltip,
  useMessage,
  useDialog
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  RefreshOutline as RefreshIcon,
  HelpOutline as HelpIcon,
  PlayOutline as PlayIcon,
  CreateOutline as EditIcon,
  TimerOutline as TimerIcon,
  CheckmarkCircleOutline as CheckmarkCircleIcon,
  LayersOutline as LayersIcon,
  ArrowBackOutline as UndoIcon,
  CalendarOutline as CalendarIcon,
  BulbOutline as BulbIcon,
  SearchOutline as SearchIcon,
  DocumentTextOutline as DocumentIcon,
  CloudOutline as CloudIcon,
  TrashOutline as TrashIcon
} from '@vicons/ionicons5'
import { personalService, taskService } from '@/services/api'
import { formatArrayTime as formatTime } from '@/utils/date-format'
import { pushRecentAction } from '@/services/user-preferences'

interface ScheduledTask {
  id: number
  name: string
  description?: string
  taskType: string
  cronExpression: string
  params?: string
  skillCode?: string
  lastExecuteTime?: string | number[] | null
  lastExecuteResult?: string
  nextExecuteTime?: string | number[] | null
  executeCount?: number
  successCount?: number
  failCount?: number
  enabled: boolean
  createTime?: string | number[] | null
}

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const tasks = ref<ScheduledTask[]>([])
const showCreateModal = ref(false)
const showResultModal = ref(false)
const editingTask = ref<ScheduledTask | null>(null)
const currentTask = ref<ScheduledTask | null>(null)
const submitLoading = ref(false)
const formRef = ref()
const templates = ref<Array<{ id: string; name: string }>>([])
const lastDeletedTask = ref<ScheduledTask | null>(null)

const form = ref({
  name: '',
  description: '',
  taskType: 'SKILL',
  cronExpression: '',
  params: '',
  skillCode: ''
})

const formRules = {
  name: { required: true, message: '请输入任务名称', trigger: 'blur' },
  taskType: { required: true, message: '请选择任务类型', trigger: 'change' },
  cronExpression: { required: true, message: '请输入Cron表达式', trigger: 'blur' }
}

const taskTypeOptions = [
  { label: '技能执行', value: 'SKILL' },
  { label: 'AI对话', value: 'CHAT' },
  { label: '提醒', value: 'REMINDER' }
]

const enabledCount = computed(() => tasks.value.filter(item => item.enabled).length)

// Template Cards with icons
const templateCards = computed(() => [
  { id: 'daily-report', name: '日报生成', description: '每日自动生成工作报告', icon: CalendarIcon },
  { id: 'knowledge-check', name: '知识库巡检', description: '定期检查知识库状态', icon: SearchIcon },
  { id: 'note-summary', name: '笔记总结', description: '智能总结笔记内容', icon: DocumentIcon },
  { id: 'model-health', name: '模型健康检查', description: '监控模型运行状态', icon: CloudIcon },
  ...templates.value.map((t, i) => ({
    id: t.id,
    name: t.name,
    description: '自定义任务模板',
    icon: i % 2 === 0 ? BulbIcon : TimerIcon
  }))
])

const loadTasks = async () => {
  loading.value = true
  try {
    const res = await taskService.list()
    if (res.success) {
      tasks.value = res.data || []
    } else {
      message.error(res.message || '加载任务失败')
    }
  } catch (error) {
    console.error('加载任务失败:', error)
    message.error('加载任务失败，请检查数据库是否已初始化')
  } finally {
    loading.value = false
  }
}

const submitForm = async () => {
  try {
    await formRef.value?.validate()
    submitLoading.value = true

    if (editingTask.value) {
      const res = await taskService.update(editingTask.value.id, form.value)
      if (res.success) {
        message.success('更新成功')
        showCreateModal.value = false
        loadTasks()
      } else {
        message.error(res.message || '更新失败')
      }
    } else {
      const res = await taskService.create(form.value)
      if (res.success) {
        message.success('创建成功')
        showCreateModal.value = false
        loadTasks()
      } else {
        message.error(res.message || '创建失败')
      }
    }
  } catch (error) {
    console.error('操作失败:', error)
    message.error('操作失败')
  } finally {
    submitLoading.value = false
  }
}

const loadTemplates = async () => {
  try {
    const res = await personalService.listTaskTemplates()
    if (res.success && res.data) {
      templates.value = res.data.map(item => ({ id: item.id, name: item.name }))
    }
  } catch (error) {
    console.error('加载任务模板失败:', error)
  }
}

const openCreateModal = () => {
  editingTask.value = null
  form.value = {
    name: '',
    description: '',
    taskType: 'SKILL',
    cronExpression: '',
    params: '',
    skillCode: ''
  }
  showCreateModal.value = true
}

const executeTask = async (task: ScheduledTask) => {
  try {
    message.loading('正在执行任务...')
    const res = await taskService.execute(task.id)
    if (res.success) {
      message.success('执行成功')
      loadTasks()
    } else {
      message.error(res.message || '执行失败')
    }
  } catch (error) {
    console.error('执行失败:', error)
    message.error('执行失败')
  }
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
  showCreateModal.value = true
}

const confirmDelete = (task: ScheduledTask) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除任务 "${task.name}" 吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        lastDeletedTask.value = { ...task }
        const res = await taskService.delete(task.id)
        if (res.success) {
          message.success('删除成功')
          pushRecentAction({ time: new Date().toLocaleString(), title: '删除任务', detail: task.name })
          currentTask.value = null
          loadTasks()
        } else {
          message.error(res.message || '删除失败')
        }
      } catch (error) {
        console.error('删除失败:', error)
        message.error('删除失败')
      }
    }
  })
}

const undoDelete = async () => {
  if (!lastDeletedTask.value) {
    message.warning('当前没有可撤销的删除项')
    return
  }

  const payload = { ...lastDeletedTask.value }
  delete (payload as any).id
  const res = await taskService.create(payload)
  if (res.success) {
    message.success('任务已恢复')
    pushRecentAction({ time: new Date().toLocaleString(), title: '撤销删除任务', detail: lastDeletedTask.value.name })
    lastDeletedTask.value = null
    await loadTasks()
  } else {
    message.error(res.message || '恢复失败')
  }
}

const createByTemplate = async (templateId: string, templateName: string) => {
  // For built-in templates, create directly
  if (templateId.startsWith('daily-report') || templateId.startsWith('knowledge-check')) {
    openCreateModal()
    form.value = {
      name: templateName,
      description: '',
      taskType: 'SKILL',
      cronExpression: '0 0 8 * * ?',
      params: '',
      skillCode: templateId === 'daily-report' ? 'DAILY_REPORT' : 'KNOWLEDGE_CHECK'
    }
    return
  }

  const res = await personalService.createTaskFromTemplate(templateId)
  if (res.success) {
    message.success(`模板任务已创建: ${templateName}`)
    pushRecentAction({ time: new Date().toLocaleString(), title: '应用任务模板', detail: templateName })
    await loadTasks()
  } else {
    message.error(res.message || '模板创建失败')
  }
}

onMounted(() => {
  loadTasks()
  loadTemplates()
})
</script>

<style scoped>
/* ============================================
 * Automation Hub - macOS Control Center Style
 * Clean, minimal, glassmorphism
 * ============================================ */

.automation-hub {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: calc(100vh - 200px);
}

/* Background Layer - Warm neutral tone */
.hub-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: var(--bg-base);
  border-radius: var(--radius-lg);
}

:global([data-theme="dark"]) .hub-bg {
  background: var(--bg-base);
}

/* Header Bar - macOS Breadcrumb */
.hub-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.header-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.85rem;
}

.breadcrumb-item {
  color: var(--text-muted);
}

.breadcrumb-separator {
  color: var(--text-muted);
  opacity: 0.5;
}

.breadcrumb-current {
  color: var(--text-primary);
  font-weight: 500;
}

.header-subtitle {
  font-size: 0.85rem;
  color: var(--text-secondary);
  letter-spacing: 0.02em;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: var(--bg-card);
  border: 2px solid var(--border-light);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.action-btn:hover {
  background: var(--bg-elevated);
  transform: translateY(-1px);
}

.create-btn {
  background: var(--gradient-sunset);
  border: none;
  color: white;
  box-shadow: 0 2px 8px var(--primary-glow);
  text-shadow: 0 1px 2px rgba(0,0,0,0.1);
}

.create-btn:hover {
  transform: translateY(-1px) scale(1.02);
  box-shadow: var(--shadow-md);
}

.btn-label {
  font-size: 0.85rem;
  font-weight: 500;
}

/* Stats Row - Top Horizontal Cards with Amber Accent */
.stats-row {
  display: flex;
  gap: 20px;
}

.stat-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  padding-left: 28px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 2px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  flex: 1;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

/* Amber accent line - left side */
.stat-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 4px;
  border-radius: 2px;
  background: var(--gradient-sunset);
  opacity: 0.8;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--primary-light);
}

.stat-card:hover::before {
  opacity: 1;
  top: 8px;
  bottom: 8px;
}

.stat-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background: var(--warm-100);
  color: var(--primary-color);
  border: 1.5px solid var(--border-light);
}

.stat-icon.active {
  background: linear-gradient(180deg, #34C759 0%, #22C55E 100%);
  color: white;
}

:global([data-theme="dark"]) .stat-icon {
  background: var(--warm-100);
  color: var(--primary-color);
}

.stat-data {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-label {
  font-size: 0.75rem;
  font-weight: 500;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.stat-value {
  font-size: 2rem;
  font-weight: 700;
  line-height: 1;
  color: var(--text-primary);
}

/* Main Grid Layout */
.hub-grid {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 24px;
}

/* Task Panel */
.task-panel {
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border-radius: var(--radius-xl);
  border: 2px solid var(--border-light);
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}

.panel-tag {
  font-size: 0.7rem;
  font-weight: 600;
  color: #D97706;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.panel-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.task-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 48px;
  color: var(--text-muted);
}

.loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.empty-state span {
  font-size: 0.85rem;
}

.task-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: transparent;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.task-item:hover {
  background: var(--bg-menu-item-hover);
}

.task-item.active {
  background: var(--bg-menu-item-active);
  /* Subtle amber left indicator */
  box-shadow: inset 2px 0 0 rgba(217, 119, 6, 0.6);
}

.task-status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
  flex-shrink: 0;
  transition: background 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.task-status-indicator.enabled {
  background: #34C759;
}

.task-info {
  flex: 1;
  min-width: 0;
}

.task-name {
  display: block;
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--text-primary);
}

.task-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
}

.task-type-badge {
  font-size: 0.7rem;
  padding: 2px 8px;
  border-radius: 100px;
  background: var(--bg-input);
  color: var(--text-secondary);
}

.task-cron {
  font-size: 0.75rem;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.task-stats-mini {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

.task-actions-mini {
  display: flex;
  gap: 4px;
}

.mini-btn {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
}

.mini-btn:hover {
  background: var(--bg-menu-item-hover);
  color: #D97706;
}

.mini-btn.delete:hover {
  background: rgba(255, 59, 48, 0.1);
  color: #FF3B30;
}

/* Side Panel */
.side-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.template-section {
  background: var(--bg-card);
  border-radius: var(--radius-xl);
  border: 2px solid var(--border-light);
  padding: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.section-tag {
  font-size: 0.7rem;
  font-weight: 600;
  color: #D97706;
  text-transform: uppercase;
  letter-spacing: 0.1em;
}

.section-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.template-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px 16px;
  background: var(--warm-50);
  border-radius: var(--radius-lg);
  border: 2px solid var(--border-light);
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  box-shadow: var(--shadow-xs);
}

.template-card:hover {
  background: var(--warm-100);
  border-color: var(--primary-light);
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

:global([data-theme="dark"]) .template-card {
  background: var(--bg-hover);
  border-color: var(--border-color);
}

:global([data-theme="dark"]) .template-card:hover {
  background: var(--bg-active);
  border-color: var(--primary-light);
}

.template-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  background: var(--gradient-sunset);
  color: white;
  box-shadow: 0 2px 8px var(--primary-glow);
}

:global([data-theme="dark"]) .template-icon {
  background: var(--gradient-sunset);
  color: white;
}

.template-content {
  text-align: center;
}

.template-name {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-primary);
}

.template-desc {
  font-size: 0.7rem;
  color: var(--text-muted);
  margin-top: 4px;
}

.template-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  /* Amber overlay */
  background: rgba(217, 119, 6, 0.92);
  color: #FFFBEB;
  font-size: 0.8rem;
  font-weight: 500;
  opacity: 0;
  transition: opacity 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.template-card:hover .template-overlay {
  opacity: 1;
}

/* Undo Banner */
.undo-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--warm-100);
  border: 2px solid var(--primary-light);
  border-radius: var(--radius-md);
  color: var(--primary-color);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.undo-banner:hover {
  background: var(--warm-200);
}

/* Result Content */
.result-content {
  background: var(--bg-input);
  padding: 16px;
  border-radius: var(--radius-md);
  font-size: 0.85rem;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--text-primary);
}

/* Responsive */
@media (max-width: 1024px) {
  .hub-grid {
    grid-template-columns: 1fr;
  }

  .side-panel {
    order: -1;
  }
}

@media (max-width: 768px) {
  .stats-row {
    flex-direction: column;
  }

  .template-grid {
    grid-template-columns: 1fr;
  }

  .header-actions .btn-label {
    display: none;
  }
}
</style>