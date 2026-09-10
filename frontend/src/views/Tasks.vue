<template>
  <UiPage class="tasks-page">
    <UiPageHeader
      eyebrow="Agent Triggers"
      title="定时任务"
      subtitle="管理由时间触发的技能、AI 对话和提醒任务。这里负责触发与执行，不把 Agent 设计成可视化 Workflow。"
    >
      <template #actions>
        <n-button @click="loadTasks" :loading="loading">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          刷新
        </n-button>
        <n-button type="primary" @click="openCreateModal">
          <template #icon><n-icon><AddIcon /></n-icon></template>
          创建任务
        </n-button>
      </template>
    </UiPageHeader>

    <div class="ui-stat-grid">
      <UiStat label="任务总数" :value="tasks.length" hint="全部计划任务" />
      <UiStat label="已启用" :value="enabledCount" hint="等待定时触发" />
      <UiStat label="AI 任务" :value="aiTaskCount" hint="需要模型或 Agent 处理" />
      <UiStat label="执行失败" :value="failedExecutionCount" hint="累计失败次数" />
    </div>

    <div class="tasks-layout">
      <UiPanel class="task-list-panel" title="任务列表" subtitle="选择任务查看状态；可直接执行、编辑或启停。">
        <template #actions>
          <n-tag size="small" :bordered="false">{{ enabledCount }}/{{ tasks.length }} 启用</n-tag>
        </template>

        <LoadingSpinner v-if="loading" />
        <div v-else-if="tasks.length" class="task-list">
          <article
            v-for="task in tasks"
            :key="task.id"
            :class="['task-row', { active: currentTask?.id === task.id }]"
          >
            <button type="button" class="task-main" @click="currentTask = task">
              <span :class="['task-state-dot', { enabled: task.enabled }]"></span>
              <span class="task-copy">
                <span class="task-title-row">
                  <strong>{{ task.name }}</strong>
                  <n-tag size="small" :bordered="false">{{ taskTypeLabel(task.taskType) }}</n-tag>
                  <n-tag v-if="task.requiresAi" size="small" type="warning" :bordered="false">AI</n-tag>
                </span>
                <span class="task-description">{{ task.description || '暂无任务描述' }}</span>
                <span class="task-meta">
                  <small>Cron {{ task.cronExpression }}</small>
                  <small>下次 {{ formatTime(task.nextExecuteTime || '') }}</small>
                </span>
              </span>
              <span class="task-success-rate">{{ successRate(task) }}</span>
            </button>

            <div class="task-actions">
              <n-switch :value="task.enabled" size="small" @update:value="() => toggleTask(task)" />
              <n-button size="small" tertiary @click="executeTask(task)">
                <template #icon><n-icon><PlayIcon /></n-icon></template>
                执行
              </n-button>
              <n-button size="small" quaternary @click="editTask(task)">
                <template #icon><n-icon><EditIcon /></n-icon></template>
              </n-button>
              <n-button size="small" quaternary type="error" @click="confirmDelete(task)">
                <template #icon><n-icon><TrashIcon /></n-icon></template>
              </n-button>
            </div>
          </article>
        </div>
        <EmptyStateWithGlow v-else>
          <template #icon><n-icon size="42"><TimerIcon /></n-icon></template>
          还没有定时任务
        </EmptyStateWithGlow>
      </UiPanel>

      <aside class="task-side">
        <UiPanel title="任务详情" subtitle="当前选中任务的触发与执行状态。">
          <div v-if="currentTask" class="task-detail">
            <div class="detail-title">
              <span :class="['task-state-dot', { enabled: currentTask.enabled }]"></span>
              <div>
                <strong>{{ currentTask.name }}</strong>
                <small>{{ currentTask.enabled ? '已启用' : '已停用' }}</small>
              </div>
            </div>

            <dl class="detail-grid">
              <div><dt>任务类型</dt><dd>{{ taskTypeLabel(currentTask.taskType) }}</dd></div>
              <div><dt>Cron</dt><dd>{{ currentTask.cronExpression }}</dd></div>
              <div><dt>上次执行</dt><dd>{{ formatTime(currentTask.lastExecuteTime || '') }}</dd></div>
              <div><dt>下次执行</dt><dd>{{ formatTime(currentTask.nextExecuteTime || '') }}</dd></div>
              <div><dt>执行次数</dt><dd>{{ currentTask.executeCount || 0 }}</dd></div>
              <div><dt>成功 / 失败</dt><dd>{{ currentTask.successCount || 0 }} / {{ currentTask.failCount || 0 }}</dd></div>
            </dl>

            <div class="detail-actions">
              <n-button block type="primary" secondary @click="executeTask(currentTask)">立即执行</n-button>
              <n-button block @click="showResultModal = true">查看最近结果</n-button>
            </div>
          </div>
          <div v-else class="side-empty">从左侧选择一个任务查看详情。</div>
        </UiPanel>

        <UiPanel title="快速模板" subtitle="只做任务预填，不引入 Workflow 编排层。">
          <div class="template-list">
            <button
              v-for="template in templateCards"
              :key="template.id"
              type="button"
              class="template-row"
              @click="createByTemplate(template.id, template.name)"
            >
              <span class="template-icon"><n-icon><component :is="template.icon" /></n-icon></span>
              <span>
                <strong>{{ template.name }}</strong>
                <small>{{ template.description }}</small>
              </span>
            </button>
          </div>
        </UiPanel>

        <button v-if="lastDeletedTask" type="button" class="undo-banner" @click="undoDelete">
          <n-icon><UndoIcon /></n-icon>
          <span>撤销删除「{{ lastDeletedTask.name }}」</span>
        </button>
      </aside>
    </div>

    <n-modal v-model:show="showCreateModal" preset="card" :title="editingTask ? '编辑任务' : '创建任务'" style="width: min(680px, 92vw)">
      <n-form ref="formRef" :model="form" :rules="formRules" label-placement="top">
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="任务名称" path="name">
            <n-input v-model:value="form.name" placeholder="请输入任务名称" />
          </n-form-item-gi>
          <n-form-item-gi label="任务类型" path="taskType">
            <n-select v-model:value="form.taskType" :options="taskTypeOptions" />
          </n-form-item-gi>
          <n-form-item-gi span="2" label="任务描述" path="description">
            <n-input v-model:value="form.description" type="textarea" placeholder="说明任务目标，而不是描述编排流程" />
          </n-form-item-gi>
          <n-form-item-gi v-if="form.taskType === 'SKILL'" span="2" label="技能代码" path="skillCode">
            <n-input v-model:value="form.skillCode" placeholder="要执行的 Skill Code" />
          </n-form-item-gi>
          <n-form-item-gi span="2" label="Cron 表达式" path="cronExpression">
            <n-input v-model:value="form.cronExpression" placeholder="例如：0 0 8 * * ?">
              <template #suffix>
                <n-tooltip trigger="hover">
                  <template #trigger><n-icon><HelpIcon /></n-icon></template>
                  <div>秒 分 时 日 月 周<br />0 0 8 * * ?：每天 08:00</div>
                </n-tooltip>
              </template>
            </n-input>
          </n-form-item-gi>
          <n-form-item-gi span="2" label="任务参数" path="params">
            <n-input v-model:value="form.params" type="textarea" :rows="3" placeholder='JSON 参数，例如 {"key":"value"}' />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" :loading="submitLoading" @click="submitForm">{{ editingTask ? '更新' : '创建' }}</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="showResultModal" preset="card" title="最近执行结果" style="width: min(720px, 92vw)">
      <n-descriptions v-if="currentTask" label-placement="left" :column="1" bordered>
        <n-descriptions-item label="任务">{{ currentTask.name }}</n-descriptions-item>
        <n-descriptions-item label="上次执行">{{ formatTime(currentTask.lastExecuteTime || '') }}</n-descriptions-item>
        <n-descriptions-item label="下次执行">{{ formatTime(currentTask.nextExecuteTime || '') }}</n-descriptions-item>
      </n-descriptions>
      <n-divider>结果</n-divider>
      <n-scrollbar style="max-height: 320px">
        <pre class="result-content">{{ currentTask?.lastExecuteResult || '暂无执行结果' }}</pre>
      </n-scrollbar>
    </n-modal>
  </UiPage>
</template>

<script setup lang="ts">
/**
 * Agent 定时触发任务页面。
 *
 * 负责 Cron 任务的创建、启停、手动执行、模板预填和执行结果查看。该页面只管理触发器，
 * 不承担 Agent 内部的决策编排。
 */
import { computed, onMounted, ref } from 'vue'
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
  NScrollbar,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  NTooltip,
  useDialog,
  useMessage
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  ArrowBackOutline as UndoIcon,
  BulbOutline as BulbIcon,
  CalendarOutline as CalendarIcon,
  CloudOutline as CloudIcon,
  CreateOutline as EditIcon,
  DocumentTextOutline as DocumentIcon,
  HelpOutline as HelpIcon,
  PlayOutline as PlayIcon,
  RefreshOutline as RefreshIcon,
  SearchOutline as SearchIcon,
  TimerOutline as TimerIcon,
  TrashOutline as TrashIcon
} from '@vicons/ionicons5'
import type { ScheduledTask } from '@/types'
import EmptyStateWithGlow from '@/components/EmptyStateWithGlow.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { UiPage, UiPageHeader, UiPanel, UiStat } from '@/components/ui'
import { personalService } from '@/services/api/personal'
import { taskService } from '@/services/api/task'
import { pushRecentAction } from '@/services/user-preferences'
import { formatArrayTime as formatTime } from '@/utils/date-format'

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
  cronExpression: { required: true, message: '请输入 Cron 表达式', trigger: 'blur' }
}

const taskTypeOptions = [
  { label: '技能执行', value: 'SKILL' },
  { label: 'AI 对话', value: 'CHAT' },
  { label: '提醒', value: 'REMINDER' }
]

const enabledCount = computed(() => tasks.value.filter(item => item.enabled).length)
const aiTaskCount = computed(() => tasks.value.filter(item => item.requiresAi || item.taskType === 'CHAT').length)
const failedExecutionCount = computed(() => tasks.value.reduce((total, item) => total + Number(item.failCount || 0), 0))

const templateCards = computed(() => [
  { id: 'daily-report', name: '日报生成', description: '每天固定时间生成工作摘要', icon: CalendarIcon },
  { id: 'knowledge-check', name: '知识库巡检', description: '定时检查知识库状态', icon: SearchIcon },
  { id: 'note-summary', name: '笔记总结', description: '让 AI 定时总结笔记', icon: DocumentIcon },
  { id: 'model-health', name: '模型健康检查', description: '检查模型连接与可用状态', icon: CloudIcon },
  ...templates.value.map((template, index) => ({
    id: template.id,
    name: template.name,
    description: '自定义任务模板',
    icon: index % 2 === 0 ? BulbIcon : TimerIcon
  }))
])

const taskTypeLabel = (type: string) => taskTypeOptions.find(option => option.value === type)?.label || type

const successRate = (task: ScheduledTask) => {
  const total = Number(task.executeCount || 0)
  if (total === 0) return '未执行'
  return `${Math.round((Number(task.successCount || 0) / total) * 100)}%`
}

/** 加载当前用户的定时任务列表。 */
const loadTasks = async () => {
  loading.value = true
  try {
    const response = await taskService.list()
    if (response.success) {
      tasks.value = response.data || []
      if (currentTask.value) {
        currentTask.value = tasks.value.find(item => item.id === currentTask.value?.id) || null
      }
    } else {
      message.error(response.message || '加载任务失败')
    }
  } catch (error) {
    console.error('加载任务失败:', error)
    message.error('加载任务失败')
  } finally {
    loading.value = false
  }
}

/** 加载用户保存的任务模板。 */
const loadTemplates = async () => {
  try {
    const response = await personalService.listTaskTemplates()
    if (response.success && response.data) {
      templates.value = response.data.map(item => ({ id: item.id, name: item.name }))
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

/** 创建或更新定时任务。 */
const submitForm = async () => {
  try {
    await formRef.value?.validate()
    submitLoading.value = true
    const response = editingTask.value
      ? await taskService.update(editingTask.value.id, form.value)
      : await taskService.create(form.value)

    if (response.success) {
      message.success(editingTask.value ? '更新成功' : '创建成功')
      showCreateModal.value = false
      await loadTasks()
    } else {
      message.error(response.message || '保存失败')
    }
  } catch (error) {
    console.error('保存任务失败:', error)
    message.error('保存任务失败')
  } finally {
    submitLoading.value = false
  }
}

/** 手动执行一次任务。 */
const executeTask = async (task: ScheduledTask) => {
  try {
    const response = await taskService.execute(task.id)
    if (response.success) {
      message.success('任务已触发')
      await loadTasks()
    } else {
      message.error(response.message || '执行失败')
    }
  } catch (error) {
    console.error('执行任务失败:', error)
    message.error('执行失败')
  }
}

/** 切换任务启用状态。 */
const toggleTask = async (task: ScheduledTask) => {
  try {
    const response = await taskService.toggle(task.id)
    if (response.success) {
      await loadTasks()
    } else {
      message.error(response.message || '状态更新失败')
    }
  } catch {
    message.error('状态更新失败')
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
    content: `确定删除任务「${task.name}」吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      lastDeletedTask.value = { ...task }
      const response = await taskService.delete(task.id)
      if (response.success) {
        pushRecentAction({ time: new Date().toLocaleString(), title: '删除任务', detail: task.name })
        currentTask.value = null
        message.success('删除成功')
        await loadTasks()
      } else {
        message.error(response.message || '删除失败')
      }
    }
  })
}

/** 恢复最近一次删除的任务。 */
const undoDelete = async () => {
  if (!lastDeletedTask.value) return
  const { id: _id, ...payload } = lastDeletedTask.value
  const response = await taskService.create(payload)
  if (response.success) {
    pushRecentAction({ time: new Date().toLocaleString(), title: '撤销删除任务', detail: lastDeletedTask.value.name })
    lastDeletedTask.value = null
    message.success('任务已恢复')
    await loadTasks()
  } else {
    message.error(response.message || '恢复失败')
  }
}

/** 通过模板预填任务；自定义模板继续使用后端模板接口。 */
const createByTemplate = async (templateId: string, templateName: string) => {
  if (templateId === 'daily-report' || templateId === 'knowledge-check' || templateId === 'note-summary' || templateId === 'model-health') {
    openCreateModal()
    form.value = {
      name: templateName,
      description: '',
      taskType: templateId === 'note-summary' ? 'CHAT' : 'SKILL',
      cronExpression: '0 0 8 * * ?',
      params: '',
      skillCode: templateId === 'daily-report'
        ? 'DAILY_REPORT'
        : templateId === 'knowledge-check'
          ? 'KNOWLEDGE_CHECK'
          : templateId === 'model-health'
            ? 'MODEL_HEALTH'
            : ''
    }
    return
  }

  const response = await personalService.createTaskFromTemplate(templateId)
  if (response.success) {
    pushRecentAction({ time: new Date().toLocaleString(), title: '应用任务模板', detail: templateName })
    message.success(`模板任务已创建: ${templateName}`)
    await loadTasks()
  } else {
    message.error(response.message || '模板创建失败')
  }
}

onMounted(() => {
  loadTasks()
  loadTemplates()
})
</script>

<style scoped>
.tasks-page {
  gap: 18px;
}

.tasks-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 16px;
  align-items: start;
}

.task-side {
  display: grid;
  gap: 14px;
}

.task-list {
  display: grid;
}

.task-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid var(--workspace-border, var(--border-light));
}

.task-row:first-child {
  border-top: 0;
}

.task-row.active {
  margin-inline: -8px;
  padding-inline: 8px;
  border-radius: 10px;
  background: var(--workspace-soft, var(--bg-input));
}

.task-main,
.template-row,
.undo-banner {
  border: 0;
  color: inherit;
  font: inherit;
  cursor: pointer;
}

.task-main {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
  background: transparent;
  text-align: left;
}

.task-state-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
}

.task-state-dot.enabled {
  background: #22c55e;
}

.task-copy {
  display: grid;
  min-width: 0;
}

.task-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.task-title-row strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 0.78rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.task-description {
  margin-top: 4px;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 0.68rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 5px;
  color: var(--text-muted);
}

.task-meta small {
  font-size: 0.61rem;
}

.task-success-rate {
  color: var(--text-muted);
  font-size: 0.66rem;
}

.task-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.task-detail {
  display: grid;
  gap: 14px;
}

.detail-title {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr);
  gap: 9px;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--workspace-border, var(--border-light));
}

.detail-title > div {
  display: grid;
  gap: 2px;
}

.detail-title strong {
  font-size: 0.8rem;
}

.detail-title small {
  color: var(--text-muted);
  font-size: 0.62rem;
}

.detail-grid {
  display: grid;
  gap: 7px;
  margin: 0;
}

.detail-grid > div {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px;
  padding: 7px 8px;
  border-radius: 8px;
  background: var(--workspace-soft, var(--bg-input));
}

.detail-grid dt,
.detail-grid dd {
  margin: 0;
  font-size: 0.66rem;
}

.detail-grid dt {
  color: var(--text-muted);
}

.detail-grid dd {
  overflow-wrap: anywhere;
  color: var(--text-primary);
  text-align: right;
}

.detail-actions {
  display: grid;
  gap: 7px;
}

.side-empty {
  padding: 30px 8px;
  color: var(--text-muted);
  font-size: 0.7rem;
  text-align: center;
}

.template-list {
  display: grid;
  gap: 5px;
}

.template-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 9px;
  align-items: center;
  width: 100%;
  min-height: 48px;
  padding: 7px;
  border-radius: 9px;
  background: transparent;
  text-align: left;
}

.template-row:hover {
  background: var(--workspace-soft, var(--bg-input));
}

.template-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--primary-color) 10%, var(--bg-input));
  color: var(--primary-color);
}

.template-row > span:last-child {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.template-row strong {
  font-size: 0.7rem;
}

.template-row small {
  color: var(--text-muted);
  font-size: 0.61rem;
}

.undo-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 40px;
  padding: 8px 10px;
  border: 1px solid color-mix(in srgb, var(--primary-color) 25%, var(--workspace-border, var(--border-light)));
  border-radius: 10px;
  background: color-mix(in srgb, var(--primary-color) 8%, var(--bg-card));
  color: var(--primary-color);
  font-size: 0.68rem;
}

.result-content {
  margin: 0;
  padding: 12px;
  border-radius: 10px;
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 0.72rem;
  white-space: pre-wrap;
}

@media (max-width: 1080px) {
  .tasks-layout {
    grid-template-columns: 1fr;
  }

  .task-side {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .undo-banner {
    grid-column: 1 / -1;
  }
}

@media (max-width: 720px) {
  .task-row {
    grid-template-columns: 1fr;
  }

  .task-actions {
    justify-content: flex-end;
  }

  .task-side {
    grid-template-columns: 1fr;
  }

  .task-main {
    grid-template-columns: 10px minmax(0, 1fr);
  }

  .task-success-rate {
    display: none;
  }
}
</style>
