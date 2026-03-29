<template>
  <div class="tasks-page">
    <!-- 操作栏 -->
    <n-card class="action-card" :bordered="false">
      <n-space>
        <n-button type="primary" @click="showCreateModal = true">
          <template #icon><n-icon><AddIcon /></n-icon></template>
          创建任务
        </n-button>
        <n-button @click="loadTasks">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          刷新
        </n-button>
      </n-space>
    </n-card>

    <!-- 任务列表 -->
    <n-card class="task-list-card" :bordered="false">
      <n-data-table
        :columns="columns"
        :data="tasks"
        :loading="loading"
        :row-key="(row: ScheduledTask) => row.id"
        striped
      />
    </n-card>

    <!-- 创建/编辑任务弹窗 -->
    <n-modal v-model:show="showCreateModal" preset="card" :title="editingTask ? '编辑任务' : '创建任务'" style="width: 600px">
      <n-form ref="formRef" :model="form" :rules="formRules">
        <n-form-item label="任务名称" path="name">
          <n-input v-model:value="form.name" placeholder="请输入任务名称" />
        </n-form-item>
        <n-form-item label="任务描述" path="description">
          <n-input v-model:value="form.description" type="textarea" placeholder="请输入任务描述" />
        </n-form-item>
        <n-form-item label="任务类型" path="taskType">
          <n-select
            v-model:value="form.taskType"
            :options="taskTypeOptions"
            placeholder="请选择任务类型"
          />
        </n-form-item>
        <n-form-item v-if="form.taskType === 'SKILL'" label="技能代码" path="skillCode">
          <n-input v-model:value="form.skillCode" placeholder="请输入要执行的技能代码" />
        </n-form-item>
        <n-form-item label="Cron表达式" path="cronExpression">
          <n-input v-model:value="form.cronExpression" placeholder="如: 0 0 8 * * ? (每天8点执行)">
            <template #suffix>
              <n-tooltip trigger="hover">
                <template #trigger>
                  <n-icon style="cursor: pointer"><HelpIcon /></n-icon>
                </template>
                <div>
                  <p>秒 分 时 日 月 周</p>
                  <p>0 0 8 * * ? 每天8点</p>
                  <p>0 0/30 * * * ? 每30分钟</p>
                  <p>0 0 20 * * ? 每天20点</p>
                </div>
              </n-tooltip>
            </template>
          </n-input>
        </n-form-item>
        <n-form-item label="任务参数" path="params">
          <n-input
            v-model:value="form.params"
            type="textarea"
            :rows="3"
            placeholder="JSON格式参数，如: {&quot;key&quot;: &quot;value&quot;}"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" @click="submitForm" :loading="submitLoading">
            {{ editingTask ? '更新' : '创建' }}
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 执行结果弹窗 -->
    <n-modal v-model:show="showResultModal" preset="card" title="执行结果" style="width: 600px">
      <n-descriptions label-placement="left" :column="1" bordered>
        <n-descriptions-item label="任务名称">{{ currentTask?.name }}</n-descriptions-item>
        <n-descriptions-item label="执行时间">{{ formatTime(currentTask?.lastExecuteTime || '') }}</n-descriptions-item>
        <n-descriptions-item label="下次执行">{{ formatTime(currentTask?.nextExecuteTime || '') }}</n-descriptions-item>
        <n-descriptions-item label="执行次数">{{ currentTask?.executeCount }} (成功: {{ currentTask?.successCount }}, 失败: {{ currentTask?.failCount }})</n-descriptions-item>
      </n-descriptions>
      <n-divider>执行结果</n-divider>
      <n-scrollbar style="max-height: 300px">
        <pre class="result-content">{{ currentTask?.lastExecuteResult }}</pre>
      </n-scrollbar>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, h, onMounted } from 'vue'
import {
  NCard,
  NSpace,
  NButton,
  NIcon,
  NDataTable,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NTag,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NScrollbar,
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
import { taskService } from '@/services/api'
import dayjs from 'dayjs'

interface ScheduledTask {
  id: number
  name: string
  description: string
  taskType: string
  cronExpression: string
  params: string
  skillCode: string
  lastExecuteTime: string | number[] | null
  lastExecuteResult: string
  nextExecuteTime: string | number[] | null
  executeCount: number
  successCount: number
  failCount: number
  enabled: boolean
  createTime: string | number[] | null
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
        const res = await taskService.delete(task.id)
        if (res.success) {
          message.success('删除成功')
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
})
</script>

<style scoped>
.tasks-page {
  display: grid;
  gap: 16px;
}

.action-card,
.task-list-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.result-content {
  background: var(--bg-input);
  padding: 16px;
  border-radius: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--text-primary);
}
</style>