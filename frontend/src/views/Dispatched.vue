<template>
  <div class="dispatched-page">
    <n-page-header title="派发任务" subtitle="邮件产生的可执行单元的执行与历史">
      <template #extra>
        <n-space>
          <n-tag :type="availabilityTagType">Claude Code: {{ availability['claude-code'] ? '可用' : '缺失' }}</n-tag>
          <n-tag :type="availabilityTagType">Codex: {{ availability['codex'] ? '可用' : '缺失' }}</n-tag>
          <n-button @click="refresh" :loading="loading">刷新</n-button>
        </n-space>
      </template>
    </n-page-header>

    <n-card class="task-list" :bordered="false">
      <n-data-table
        :columns="columns"
        :data="rows"
        :loading="loading"
        :pagination="pagination"
        :row-key="(r: any) => r.id"
        @update:page="(p: number) => { pagination.page = p; load() }"
      />
    </n-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 派发任务页面：邮件触发的执行单元（Claude Code / Codex）历史、取消与重跑。
 */
import { computed, h, onMounted, reactive, ref } from 'vue'
import { NButton, NSpace, NTag, useMessage } from 'naive-ui'
import { dispatchedService, type DispatchedTaskSummary } from '@/services/api/dispatch'

const message = useMessage()
const rows = ref<DispatchedTaskSummary[]>([])
const loading = ref(false)
const availability = ref<Record<string, boolean>>({})

const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: false
})

const availabilityTagType = computed(() => {
  const all = Object.values(availability.value)
  if (all.length === 0) return 'default'
  return all.every(v => v) ? 'success' : 'warning'
})

const columns = [
  { title: 'ID', key: 'id', width: 70 },
  { title: '主题', key: 'subject', ellipsis: { tooltip: true } },
  {
    title: '重要性',
    key: 'importance',
    width: 90,
    render: (row: any) => h(NTag, {
      size: 'small',
      type: row.importance === 'high' ? 'error' : row.importance === 'medium' ? 'warning' : 'default'
    }, { default: () => row.importance || '-' })
  },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: (row: any) => h(NTag, {
      size: 'small',
      type: row.status === 'DONE' ? 'success'
        : row.status === 'FAILED' ? 'error'
        : row.status === 'CANCELLED' ? 'default' : 'info'
    }, { default: () => row.status || '-' })
  },
  { title: '执行器', key: 'executorUsed', width: 160 },
  { title: '重试', key: 'retries', width: 60 },
  { title: '创建时间', key: 'createdAt', width: 180 },
  {
    title: '操作',
    key: 'actions',
    width: 180,
    render: (row: any) => h(NSpace, {}, {
      default: () => [
        h(NButton, {
          size: 'tiny',
          disabled: row.status === 'RUNNING',
          onClick: () => cancel(row.id)
        }, { default: () => '取消' }),
        h(NButton, {
          size: 'tiny',
          type: 'primary',
          disabled: row.status === 'RUNNING' || row.status === 'PENDING',
          onClick: () => rerun(row.id)
        }, { default: () => '重跑' })
      ]
    })
  }
]

/** 拉取当前分页的派发任务并更新表格数据。 */
async function load() {
  loading.value = true
  try {
    const resp = await dispatchedService.list(pagination.page, pagination.pageSize)
    if (resp.success) {
      const data = resp.data as any
      rows.value = data.records || []
      pagination.itemCount = data.total || 0
    } else {
      message.error(resp.message || '加载失败')
    }
  } finally {
    loading.value = false
  }
}

async function loadAvailability() {
  try {
    const resp = await dispatchedService.executorAvailability()
    if (resp.success) {
      availability.value = (resp.data as Record<string, boolean>) || {}
    }
  } catch (e) {
    availability.value = {}
  }
}

async function refresh() {
  await Promise.all([load(), loadAvailability()])
}

async function cancel(id: number) {
  const resp = await dispatchedService.cancel(id)
  if (resp.success) {
    message.success('已取消')
    load()
  } else {
    message.error(resp.message || '取消失败')
  }
}

/** 将指定派发任务重新加入执行队列。 */
async function rerun(id: number) {
  const resp = await dispatchedService.rerun(id)
  if (resp.success) {
    message.success('已加入重跑队列')
    load()
  } else {
    message.error(resp.message || '重跑失败')
  }
}

onMounted(refresh)
</script>

<style scoped>
.dispatched-page { padding: 16px; display: flex; flex-direction: column; gap: 16px; }
.task-list { flex: 1; }
</style>
