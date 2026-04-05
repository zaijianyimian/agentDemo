<template>
  <div class="schedule-page">
    <!-- 操作栏 -->
    <n-card class="action-card" :bordered="false">
      <n-space>
        <n-button type="primary" @click="showAddModal = true">
          <template #icon><n-icon><AddIcon /></n-icon></template>
          添加日程
        </n-button>
        <n-button @click="showAiAddModal = true">
          <template #icon><n-icon><AddIcon /></n-icon></template>
          AI添加
        </n-button>
        <n-button @click="loadSchedules">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          刷新
        </n-button>
        <n-radio-group v-model:value="viewMode" size="small">
          <n-radio-button value="list">列表</n-radio-button>
          <n-radio-button value="calendar">日历</n-radio-button>
        </n-radio-group>
      </n-space>
    </n-card>

    <!-- 日程统计 -->
    <n-grid :cols="3" :x-gap="16">
      <n-gi>
        <div class="stat-box today">
          <div class="stat-title">今日日程</div>
          <div class="stat-number">{{ todaySchedules.length }}</div>
        </div>
      </n-gi>
      <n-gi>
        <div class="stat-box tomorrow">
          <div class="stat-title">明日日程</div>
          <div class="stat-number">{{ tomorrowSchedules.length }}</div>
        </div>
      </n-gi>
      <n-gi>
        <div class="stat-box total">
          <div class="stat-title">总日程</div>
          <div class="stat-number">{{ schedules.length }}</div>
        </div>
      </n-gi>
    </n-grid>

    <!-- 日程列表 -->
    <n-card class="list-card" :bordered="false">
      <!-- 列表视图 -->
      <n-list v-if="viewMode === 'list'" bordered>
        <n-list-item v-for="event in filteredSchedules" :key="event.id">
          <n-thing :title="event.title">
            <template #header-extra>
              <n-space>
                <n-tag v-if="event.status === 'completed'" type="success" size="small">已完成</n-tag>
                <n-tag v-else type="warning" size="small">待处理</n-tag>
                <n-dropdown :options="getActionOptions()" @select="(key: string) => handleAction(key, event)">
                  <n-button quaternary size="small">
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
    </n-card>

    <!-- 添加日程弹窗 -->
    <n-modal v-model:show="showAddModal" preset="card" title="添加日程" style="width: 500px">
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
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddModal = false">取消</n-button>
          <n-button type="primary" @click="addEvent">确定</n-button>
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
            {{ currentEvent?.status === 'completed' ? '已完成' : '待处理' }}
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
          <n-button type="primary" @click="completeCurrentEvent" :disabled="currentEvent?.status === 'completed'">
            标记完成
          </n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- AI添加日程弹窗 -->
    <n-modal v-model:show="showAiAddModal" preset="card" title="AI添加日程" style="width: 500px">
      <div class="ai-add-hint">
        用自然语言描述您的日程，AI会自动提取时间、地点等信息
      </div>
      <n-input
        v-model:value="aiInput"
        type="textarea"
        :rows="4"
        placeholder="例如：明天下午3点在会议室A开会讨论项目进度"
        :disabled="aiLoading"
      />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAiAddModal = false">取消</n-button>
          <n-button type="primary" :loading="aiLoading" @click="aiAddSchedule">
            AI创建
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  NCard,
  NGrid,
  NGi,
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
  NEmpty,
  NDescriptions,
  NDescriptionsItem,
  NCalendar,
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
import { scheduleService } from '@/services/api'
import type { ScheduleEvent } from '@/types'
import dayjs from 'dayjs'

const message = useMessage()
const viewMode = ref('list')
const schedules = ref<ScheduleEvent[]>([])
const showAddModal = ref(false)
const showDetailModal = ref(false)
const showAiAddModal = ref(false)
const currentEvent = ref<ScheduleEvent | null>(null)
const calendarDate = ref(Date.now())
const aiInput = ref('')
const aiLoading = ref(false)
const newEvent = ref({
  title: '',
  description: '',
  eventTime: null as number | null,
  location: ''
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
  return schedules.value.sort((a, b) =>
    new Date(a.eventTime).getTime() - new Date(b.eventTime).getTime()
  )
})

// 操作选项
const getActionOptions = () => [
  { label: '查看详情', key: 'detail' },
  { label: '标记完成', key: 'complete' },
  { label: '删除', key: 'delete' }
]

// 处理操作
const handleAction = async (key: string, event: ScheduleEvent) => {
  switch (key) {
    case 'detail':
      currentEvent.value = event
      showDetailModal.value = true
      break
    case 'complete':
      await scheduleService.complete(event.id)
      message.success('已标记完成')
      loadSchedules()
      break
    case 'delete':
      await scheduleService.delete(event.id)
      message.success('删除成功')
      loadSchedules()
      break
  }
}

// 完成当前日程
const completeCurrentEvent = async () => {
  if (!currentEvent.value) return
  await scheduleService.complete(currentEvent.value.id)
  message.success('已标记完成')
  showDetailModal.value = false
  loadSchedules()
}

// 加载日程
const loadSchedules = async () => {
  try {
    const res = await scheduleService.list()
    schedules.value = res.data || []
  } catch (error) {
    message.error('加载失败')
  }
}

// 添加日程
const addEvent = async () => {
  if (!newEvent.value.title) {
    message.warning('请输入标题')
    return
  }

  try {
    await scheduleService.create({
      title: newEvent.value.title,
      description: newEvent.value.description,
      eventTime: newEvent.value.eventTime ? dayjs(newEvent.value.eventTime).format('YYYY-MM-DD HH:mm:ss') : undefined,
      eventDate: newEvent.value.eventTime ? dayjs(newEvent.value.eventTime).format('YYYY-MM-DD') : undefined,
      location: newEvent.value.location
    })
    message.success('添加成功')
    showAddModal.value = false
    loadSchedules()
  } catch (error) {
    message.error('添加失败')
  }
}

// 格式化时间
const formatTime = (time: string) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

// 根据日期获取日程
const getEventsByDate = (year: number, month: number, date: number) => {
  const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(date).padStart(2, '0')}`
  return schedules.value.filter(s => s.eventDate === dateStr)
}

// 显示日程详情
const showEventDetail = (event: ScheduleEvent) => {
  currentEvent.value = event
  showDetailModal.value = true
}

// AI添加日程
const aiAddSchedule = async () => {
  if (!aiInput.value.trim()) {
    message.warning('请输入日程描述')
    return
  }

  aiLoading.value = true
  try {
    const res = await scheduleService.parseAndSave({
      subject: aiInput.value,
      from: 'user',
      content: aiInput.value
    })

    if (res.success && res.data) {
      message.success(`已创建日程: ${res.data.title}`)
      showAiAddModal.value = false
      aiInput.value = ''
      loadSchedules()
    } else {
      message.error(res.message || '无法从描述中提取日程信息')
    }
  } catch (error) {
    message.error('无法从描述中提取日程信息')
  } finally {
    aiLoading.value = false
  }
}

onMounted(() => {
  loadSchedules()
})
</script>

<style scoped>
.schedule-page {
  display: grid;
  gap: 16px;
}

.action-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.stat-box {
  background: linear-gradient(135deg, var(--bg-card) 0%, #1A2332 100%);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  text-align: center;
}

.stat-box.today {
  border-color: var(--primary-color);
}

.stat-box.tomorrow {
  border-color: var(--success);
}

.stat-box.total {
  border-color: var(--info);
}

.stat-title {
  font-size: 14px;
  color: var(--text-secondary);
}

.stat-number {
  font-size: 36px;
  font-weight: 700;
  color: var(--text-primary);
  margin-top: 8px;
}

.list-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.detail-description {
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

/* 日历视图样式 */
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
  padding: 2px 4px;
  background: var(--primary-color);
  color: white;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.calendar-event.completed {
  background: var(--success);
  opacity: 0.7;
}

.calendar-event:hover {
  opacity: 0.8;
}

/* AI添加提示 */
.ai-add-hint {
  padding: 12px;
  background: var(--bg-input);
  border-radius: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-secondary);
}
</style>
