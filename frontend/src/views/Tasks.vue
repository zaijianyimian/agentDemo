<template>
  <div class="page-shell">
    <section class="page-hero hero-grid">
      <div>
        <div class="page-eyebrow">Automation Board</div>
        <h2>把计划任务、执行结果和技能调度放到一个面板里。</h2>
        <p>前端直接承接任务增删改查、手动执行和类型选择，适合集中维护自动化流程。</p>
      </div>
      <div class="hero-side">
        <div class="hero-stat"><span>任务总数</span><strong>{{ tasks.length }}</strong></div>
        <div class="hero-stat"><span>启用中</span><strong>{{ enabledCount }}</strong></div>
        <div class="hero-stat"><span>任务类型</span><strong>{{ taskTypeOptions.length }}</strong></div>
      </div>
    </section>

    <section class="section-grid">
      <div class="surface-panel span-4">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Actions</div>
            <h3>快速操作</h3>
          </div>
        </div>
        <div class="quick-actions">
          <n-button type="primary" size="large" @click="openCreateModal">
            <template #icon><n-icon><AddIcon /></n-icon></template>
            创建任务
          </n-button>
          <n-button size="large" @click="loadTasks">
            <template #icon><n-icon><RefreshIcon /></n-icon></template>
            刷新
          </n-button>
          <div class="task-type-list">
            <span v-for="item in taskTypeOptions" :key="item.value" class="task-type-pill">{{ item.label }}</span>
          </div>
          <n-divider style="margin: 8px 0;" />
          <div class="template-block">
            <strong>模板中心</strong>
            <n-button
              v-for="item in templates"
              :key="item.id"
              size="small"
              tertiary
              @click="createByTemplate(item.id, item.name)"
            >
              {{ item.name }}
            </n-button>
            <n-button size="small" type="warning" secondary @click="undoDelete">撤销删除</n-button>
          </div>
        </div>
      </div>

      <div class="surface-panel span-8">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Overview</div>
            <h3>任务列表</h3>
          </div>
        </div>
        <n-data-table
          :columns="columns"
          :data="tasks"
          :loading="loading"
          :row-key="(row: ScheduledTask) => row.id"
          striped
        />
      </div>
    </section>

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

    <n-modal v-model:show="showResultModal" preset="card" title="执行结果" style="width: min(720px, 92vw)">
      <n-descriptions label-placement="left" :column="1" bordered>
        <n-descriptions-item label="任务名称">{{ currentTask?.name }}</n-descriptions-item>
        <n-descriptions-item label="执行时间">{{ formatTime(currentTask?.lastExecuteTime || '') }}</n-descriptions-item>
        <n-descriptions-item label="下次执行">{{ formatTime(currentTask?.nextExecuteTime || '') }}</n-descriptions-item>
        <n-descriptions-item label="执行次数">{{ currentTask?.executeCount || 0 }} (成功: {{ currentTask?.successCount || 0 }}, 失败: {{ currentTask?.failCount || 0 }})</n-descriptions-item>
      </n-descriptions>
      <n-divider>执行结果</n-divider>
      <n-scrollbar style="max-height: 300px">
        <pre class="result-content">{{ currentTask?.lastExecuteResult || '暂无执行结果' }}</pre>
      </n-scrollbar>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, h, onMounted } from 'vue'
import {
  NButton,
  NDataTable,
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
  NTag,
  NTooltip,
  useMessage,
  useDialog,
  type DataTableColumns
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  RefreshOutline as RefreshIcon,
  HelpOutline as HelpIcon,
  PlayOutline as PlayIcon,
  EyeOutline as EyeIcon,
  CreateOutline as EditIcon,
  TrashOutline as TrashIcon
} from '@vicons/ionicons5'
import { personalService, taskService } from '@/services/api'
import dayjs from 'dayjs'
import { computed } from 'vue'
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

const columns: DataTableColumns<ScheduledTask> = [
  { title: '任务名称', key: 'name', ellipsis: { tooltip: true } },
  {
    title: '类型',
    key: 'taskType',
    width: 100,
    render: row => h(NTag, { type: getTaskTypeColor(row.taskType), size: 'small' }, { default: () => row.taskType })
  },
  { title: 'Cron表达式', key: 'cronExpression', width: 140 },
  {
    title: '状态',
    key: 'enabled',
    width: 80,
    render: row => h(NTag, { type: row.enabled ? 'success' : 'default', size: 'small' }, { default: () => row.enabled ? '启用' : '禁用' })
  },
  { title: '执行次数', key: 'executeCount', width: 100, render: row => `${row.successCount}/${row.executeCount}` },
  { title: '最后执行', key: 'lastExecuteTime', width: 160, render: row => formatTime(row.lastExecuteTime) },
  { title: '下次执行', key: 'nextExecuteTime', width: 160, render: row => formatTime(row.nextExecuteTime) },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render: row => h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', quaternary: true, type: 'primary', onClick: () => executeTask(row) }, { icon: () => h(NIcon, null, { default: () => h(PlayIcon) }) }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => viewResult(row) }, { icon: () => h(NIcon, null, { default: () => h(EyeIcon) }) }),
        h(NButton, { size: 'small', quaternary: true, onClick: () => editTask(row) }, { icon: () => h(NIcon, null, { default: () => h(EditIcon) }) }),
        h(NButton, { size: 'small', quaternary: true, type: 'error', onClick: () => confirmDelete(row) }, { icon: () => h(NIcon, null, { default: () => h(TrashIcon) }) })
      ]
    })
  }
]

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

const viewResult = (task: ScheduledTask) => {
  currentTask.value = task
  showResultModal.value = true
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
          message.success('删除成功，可点击“撤销删除”恢复')
          pushRecentAction({ time: new Date().toLocaleString(), title: '删除任务', detail: task.name })
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
  const res = await personalService.createTaskFromTemplate(templateId)
  if (res.success) {
    message.success(`模板任务已创建: ${templateName}`)
    pushRecentAction({ time: new Date().toLocaleString(), title: '应用任务模板', detail: templateName })
    await loadTasks()
  } else {
    message.error(res.message || '模板创建失败')
  }
}

function getTaskTypeColor(type: string) {
  if (type === 'SKILL') return 'info'
  if (type === 'CHAT') return 'warning'
  return 'default'
}

function formatTime(time: string | number[] | null | undefined) {
  if (!time) return '-'
  // Handle array format [year, month, day, hour, minute, second]
  if (Array.isArray(time)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = time
    return dayjs(new Date(year, month - 1, day, hour, minute, second)).format('YYYY-MM-DD HH:mm')
  }
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

onMounted(() => {
  loadTasks()
  loadTemplates()
})
</script>

<style scoped>
.hero-grid { display:grid; grid-template-columns:minmax(0,1.35fr) minmax(260px,.85fr); gap:20px; }
.hero-side, .quick-actions { display:grid; gap:12px; }
.hero-stat, .task-type-pill { border:1px solid var(--border-color); background:rgba(255,255,255,.05); }
.hero-stat { padding:16px 18px; border-radius:20px; }
.hero-stat span { color:var(--text-secondary); font-size:.86rem; }
.hero-stat strong { display:block; margin-top:6px; font-size:1.3rem; }
.task-type-list { display:flex; flex-wrap:wrap; gap:10px; }
.template-block { display:grid; gap:8px; }
.task-type-pill { padding:8px 12px; border-radius:999px; color:var(--text-secondary); font-size:.86rem; }
.result-content { background: var(--bg-input); padding:16px; border-radius:14px; font-size:12px; white-space:pre-wrap; word-break:break-all; color:var(--text-primary); }
@media (max-width: 900px) { .hero-grid { grid-template-columns:1fr; } }
</style>
