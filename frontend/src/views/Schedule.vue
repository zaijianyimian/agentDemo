<template>
  <UiPage>
    <UiPageHeader
      eyebrow="Schedule Butler"
      title="日程管理"
      subtitle="查看助手安排的日程，也可以手动创建、编辑、完成或删除。"
    >
      <template #actions>
        <n-button type="primary" @click="openCreate">
          <template #icon><n-icon><AddIcon /></n-icon></template>
          添加日程
        </n-button>
        <n-button @click="loadSchedules">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          刷新
        </n-button>
      </template>
    </UiPageHeader>

    <div class="ui-stat-grid">
      <UiStat label="今日日程" :value="todaySchedules.length" hint="当天待处理事项" />
      <UiStat label="明日日程" :value="tomorrowSchedules.length" hint="次日安排" />
      <UiStat label="总日程" :value="schedules.length" hint="全部记录" />
    </div>

    <UiPanel title="日程视图" subtitle="列表适合处理事项，日历适合查看时间分布。">
      <template #actions>
        <n-radio-group v-model:value="viewMode" size="small">
          <n-radio-button value="list">列表</n-radio-button>
          <n-radio-button value="calendar">日历</n-radio-button>
        </n-radio-group>
      </template>
      <!-- 列表视图 -->
      <n-list v-if="viewMode === 'list'" bordered>
        <n-list-item v-for="event in filteredSchedules" :key="event.id">
          <n-thing :title="event.title">
            <template #header-extra>
              <n-space>
                <n-tag v-if="event.status === 'completed'" type="success" size="small">已完成</n-tag>
                <n-tag v-else-if="event.status === 'cancelled'" size="small">已取消</n-tag>
                <n-tag v-else type="warning" size="small">待处理</n-tag>
                <n-button size="small" @click="openEdit(event)">编辑</n-button>
                <n-dropdown :options="getActionOptions()" @select="(key: string) => handleAction(key, event)">
                  <n-button quaternary size="small" :aria-label="`日程操作：${event.title}`">
                    <template #icon><n-icon><EllipsisIcon /></n-icon></template>
                  </n-button>
                </n-dropdown>
              </n-space>
            </template>
            <template #description>
              <n-space>
                <span v-if="event.eventTime">
                  <n-icon><TimeIcon /></n-icon> {{ formatTime(event.eventTime) }}
                </span>
                <span v-if="event.location">
                  <n-icon><LocationIcon /></n-icon> {{ event.location }}
                </span>
                <span v-if="event.sourceEmail">
                  <n-icon><MailIcon /></n-icon> {{ event.sourceEmail }}
                </span>
              </n-space>
            </template>
          </n-thing>
        </n-list-item>
        <n-empty v-if="schedules.length === 0" description="暂无日程" />
      </n-list>

      <!-- 日历视图 -->
      <div v-else class="calendar-view">
        <n-calendar v-model:value="calendarDate" #="{ year, month, date }">
          <div class="calendar-cell">
            <div class="calendar-date">{{ date }}</div>
            <div class="calendar-events">
              <div
                v-for="event in getEventsByDate(year, month, date)"
                :key="event.id"
                class="calendar-event"
                :class="{ completed: event.status === 'completed' }"
                @click="showEventDetail(event)"
              >
                {{ event.title }}
              </div>
            </div>
          </div>
        </n-calendar>
      </div>
    </UiPanel>

    <!-- 添加日程弹窗 -->
    <n-modal v-model:show="showAddModal" preset="card" :title="editingId ? '编辑日程' : '添加日程'" style="width: min(500px, 92vw)">
      <n-form ref="formRef" :model="newEvent" label-placement="left" label-width="80">
        <n-form-item label="标题" path="title">
          <n-input v-model:value="newEvent.title" placeholder="输入日程标题" />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input v-model:value="newEvent.description" type="textarea" placeholder="输入日程描述" />
        </n-form-item>
        <n-form-item label="时间" path="eventTime">
          <n-date-picker v-model:value="newEvent.eventTime" type="datetime" clearable />
        </n-form-item>
        <n-form-item label="地点" path="location">
          <n-input v-model:value="newEvent.location" placeholder="输入地点" />
        </n-form-item>
        <n-form-item label="状态"><n-select v-model:value="newEvent.status" :options="statusOptions" /></n-form-item>
        <n-form-item label="邮件提醒"><n-switch v-model:value="newEvent.reminderEnabled" /></n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddModal = false">取消</n-button>
          <n-button type="primary" :loading="saving" @click="saveEvent">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 日程详情弹窗 -->
    <n-modal v-model:show="showDetailModal" preset="card" title="日程详情" style="width: 500px">
      <n-descriptions :column="1" label-placement="left" bordered>
        <n-descriptions-item label="标题">
          <strong>{{ currentEvent?.title }}</strong>
        </n-descriptions-item>
        <n-descriptions-item label="状态">
          <n-tag :type="currentEvent?.status === 'completed' ? 'success' : 'warning'" size="small">
            {{ currentEvent?.status === 'completed' ? '已完成' : currentEvent?.status === 'cancelled' ? '已取消' : '待处理' }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="时间">
          {{ currentEvent?.eventTime ? formatTime(currentEvent.eventTime) : '未设置' }}
        </n-descriptions-item>
        <n-descriptions-item label="日期">
          {{ currentEvent?.eventDate || '未设置' }}
        </n-descriptions-item>
        <n-descriptions-item label="地点">
          {{ currentEvent?.location || '未设置' }}
        </n-descriptions-item>
        <n-descriptions-item label="来源邮箱">
          {{ currentEvent?.sourceEmail || '无' }}
        </n-descriptions-item>
        <n-descriptions-item label="提醒状态">
          <n-tag :type="currentEvent?.reminderStatus === 'sent' ? 'success' : 'default'" size="small">
            {{ currentEvent?.reminderStatus === 'sent' ? '已发送' : '待发送' }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="描述">
          <div class="detail-description">{{ currentEvent?.description || '无' }}</div>
        </n-descriptions-item>
        <n-descriptions-item label="创建时间">
          {{ currentEvent?.createTime }}
        </n-descriptions-item>
        <n-descriptions-item label="更新时间">
          {{ currentEvent?.updateTime }}
        </n-descriptions-item>
      </n-descriptions>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showDetailModal = false">关闭</n-button>
          <n-button @click="currentEvent && openEdit(currentEvent)">编辑</n-button>
          <n-button type="error" @click="currentEvent && confirmDelete(currentEvent)">删除</n-button>
          <n-button type="primary" @click="completeCurrentEvent" :disabled="currentEvent?.status === 'completed'">
            标记完成
          </n-button>
        </n-space>
      </template>
    </n-modal>

  </UiPage>
</template>

<script setup lang="ts">
/**
 * 日程管理页面：列表/日历双视图，支持手动创建、编辑、完成与删除。
 */
import { ref, computed, onMounted } from 'vue'
import {
  NButton,
  NIcon,
  NSpace,
  NTag,
  NList,
  NListItem,
  NThing,
  NDropdown,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NDatePicker,
  NSelect,
  NSwitch,
  NEmpty,
  NDescriptions,
  NDescriptionsItem,
  NCalendar,
  NRadioButton,
  NRadioGroup,
  useDialog,
  useMessage
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  RefreshOutline as RefreshIcon,
  EllipsisVertical as EllipsisIcon,
  TimeOutline as TimeIcon,
  LocationOutline as LocationIcon,
  MailOutline as MailIcon
} from '@vicons/ionicons5'
import { scheduleService } from '@/services/api/schedule'
import type { ScheduleEvent } from '@/types'
import dayjs from 'dayjs'

import { UiPage, UiPageHeader, UiPanel, UiStat } from '@/components/ui'

const message = useMessage()
const dialog = useDialog()
const editingId = ref<number | null>(null)
const saving = ref(false)
const statusOptions = [{ label: '待处理', value: 'pending' }, { label: '已完成', value: 'completed' }, { label: '已取消', value: 'cancelled' }]
const eventTimestamp = (time: string) => dayjs(time.replace('T ', 'T')).valueOf()
const formatTime = (time: string) => dayjs(eventTimestamp(time)).format('YYYY-MM-DD HH:mm')
const viewMode = ref('list')
const schedules = ref<ScheduleEvent[]>([])
const showAddModal = ref(false)
const showDetailModal = ref(false)
const currentEvent = ref<ScheduleEvent | null>(null)
const calendarDate = ref(Date.now())
const newEvent = ref({
  title: '',
  description: '',
  eventTime: null as number | null,
  location: '',
  status: 'pending',
  reminderEnabled: false
})

// 今日日程
const todaySchedules = computed(() => {
  const today = dayjs().format('YYYY-MM-DD')
  return schedules.value.filter(s => s.eventDate === today)
})

// 明日日程
const tomorrowSchedules = computed(() => {
  const tomorrow = dayjs().add(1, 'day').format('YYYY-MM-DD')
  return schedules.value.filter(s => s.eventDate === tomorrow)
})

// 过滤后的日程
const filteredSchedules = computed(() => {
  return [...schedules.value].sort((a, b) =>
    eventTimestamp(a.eventTime) - eventTimestamp(b.eventTime)
  )
})

// 操作选项
const getActionOptions = () => [
  { label: '查看详情', key: 'detail' },
  { label: '编辑', key: 'edit' },
  { label: '标记完成', key: 'complete' },
  { label: '删除', key: 'delete' }
]

const openCreate = () => {
  editingId.value = null
  newEvent.value = { title: '', description: '', eventTime: null, location: '', status: 'pending', reminderEnabled: false }
  showAddModal.value = true
}

const openEdit = (event: ScheduleEvent) => {
  editingId.value = event.id
  newEvent.value = { title: event.title, description: event.description || '', eventTime: eventTimestamp(event.eventTime),
    location: event.location || '', status: event.status || 'pending', reminderEnabled: event.reminderEnabled ?? false }
  showDetailModal.value = false
  showAddModal.value = true
}

const confirmDelete = (event: ScheduleEvent) => {
  dialog.warning({ title: '删除日程', content: `确认删除“${event.title}”？`, positiveText: '删除', negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await scheduleService.delete(event.id)
        if (!res.success) throw new Error(res.message || '删除失败')
        showDetailModal.value = false
        message.success('删除成功')
        await loadSchedules()
      } catch (error) { message.error(error instanceof Error ? error.message : '删除失败'); return false }
    }
  })
}

const handleAction = async (key: string, event: ScheduleEvent) => {
  if (key === 'detail') return showEventDetail(event)
  if (key === 'edit') return openEdit(event)
  if (key === 'delete') return confirmDelete(event)
  if (key === 'complete') {
    try {
      const res = await scheduleService.complete(event.id)
      if (!res.success) throw new Error(res.message || '操作失败')
      message.success('已标记完成')
      showDetailModal.value = false
      await loadSchedules()
    } catch (error) { message.error(error instanceof Error ? error.message : '操作失败') }
  }
}

const completeCurrentEvent = async () => {
  if (currentEvent.value) await handleAction('complete', currentEvent.value)
}

const loadSchedules = async () => {
  try {
    const res = await scheduleService.list()
    if (!res.success) throw new Error(res.message || '加载失败')
    schedules.value = res.data || []
  } catch (error) { message.error(error instanceof Error ? error.message : '加载失败') }
}

const saveEvent = async () => {
  if (saving.value) return
  if (!newEvent.value.title.trim()) return message.warning('请输入标题')
  if (newEvent.value.eventTime === null || !Number.isFinite(newEvent.value.eventTime)) return message.warning('请选择日程时间')
  saving.value = true
  try {
    const existing = schedules.value.find(event => event.id === editingId.value)
    const payload = { ...existing, title: newEvent.value.title.trim(), description: newEvent.value.description,
      eventTime: dayjs(newEvent.value.eventTime).format('YYYY-MM-DDTHH:mm:ss'),
      eventDate: dayjs(newEvent.value.eventTime).format('YYYY-MM-DD'), location: newEvent.value.location,
      status: newEvent.value.status, reminderEnabled: newEvent.value.reminderEnabled }
    const res = editingId.value ? await scheduleService.update(editingId.value, payload) : await scheduleService.create(payload)
    if (!res.success) throw new Error(res.message || '保存失败')
    message.success(editingId.value ? '修改成功' : '添加成功')
    showAddModal.value = false
    await loadSchedules()
  } catch (error) { message.error(error instanceof Error ? error.message : '保存失败') }
  finally { saving.value = false }
}

/** 返回指定 (year, month, date) 当天的事件列表，供日历单元格渲染。 */
const getEventsByDate = (year: number, month: number, date: number) => {
  const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(date).padStart(2, '0')}`
  return schedules.value.filter(s => s.eventDate === dateStr)
}

// 显示日程详情
const showEventDetail = (event: ScheduleEvent) => {
  currentEvent.value = event
  showDetailModal.value = true
}

onMounted(() => {
  loadSchedules()
})
</script>

<style scoped>
.schedule-page {
  display: grid;
  gap: 24px;
  padding: 4px;
}

/* Warm Solid Card Base */
.action-card,
.list-card {
  position: relative;
  background: var(--bg-card);
  border: 2px solid var(--border-light);
  border-radius: var(--radius-2xl);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.action-card::before,
.list-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--gradient-sunset);
  z-index: 1;
}

.action-card:hover,
.list-card:hover {
  border-color: var(--border-accent);
  box-shadow: var(--shadow-md);
}

/* Stat Boxes - Warm Solid */
.stat-box {
  background: var(--bg-card);
  border: 2px solid var(--border-light);
  border-radius: var(--radius-xl);
  padding: 24px;
  text-align: center;
  position: relative;
  overflow: hidden;
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  box-shadow: var(--shadow-sm);
}

.stat-box::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--gradient-sunset);
}

.stat-box.today {
  border-color: var(--border-accent);
}

.stat-box.tomorrow {
  border-left: 3px solid var(--accent-green);
}

.stat-box.total {
  border-color: var(--border-accent);
}

.stat-box:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-md);
}

.stat-title {
  font-size: 0.85rem;
  color: var(--text-muted);
  letter-spacing: 0.05em;
}

.stat-number {
  font-size: 2.5rem;
  font-weight: 700;
  margin-top: 8px;
  background: var(--gradient-sunset);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.detail-description {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

/* Calendar View Styles */
.calendar-view {
  min-height: 500px;
}

.calendar-cell {
  min-height: 80px;
  padding: 4px;
}

.calendar-date {
  font-weight: 500;
  margin-bottom: 4px;
}

.calendar-events {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.calendar-event {
  font-size: 11px;
  padding: 3px 6px;
  background: var(--gradient-sunset);
  color: var(--text-primary);
  border-radius: 6px;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
  transition: all 0.3s ease;
}

.calendar-event.completed {
  background: linear-gradient(135deg, var(--accent-green), #16A34A);
  opacity: 0.85;
}

.calendar-event:hover {
  transform: scale(1.02);
  box-shadow: var(--shadow-md);
}

/* AI Add Hint */
.ai-add-hint {
  padding: 16px;
  background: var(--bg-input);
  border-radius: 16px;
  margin-bottom: 16px;
  font-size: 0.85rem;
  color: var(--text-secondary);
  border: 1px solid var(--border-light);
}

/* NCard override */
:deep(.n-card) {
  background: transparent;
}

:deep(.n-card__content) {
  padding: 24px;
}
</style>
