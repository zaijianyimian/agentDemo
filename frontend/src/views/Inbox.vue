<template>
  <div class="page-shell inbox-page">
    <section class="metrics-grid">
      <article
        v-for="card in metricCards"
        :key="card.label"
        class="metric-card"
      >
        <div class="metric-card__icon" :style="{ '--metric-color': card.color }">
          <n-icon size="20"><component :is="card.icon" /></n-icon>
        </div>
        <div class="metric-card__copy">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.hint }}</small>
        </div>
      </article>
    </section>

    <section class="section-grid">
      <div class="surface-panel span-8">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Inbox Feed</div>
            <h3>统一收件箱</h3>
          </div>
          <div class="head-actions">
            <n-tag size="small" type="warning">
              {{ lastUpdated }}
            </n-tag>
            <n-button tertiary @click="loadInbox" :loading="loading">刷新</n-button>
          </div>
        </div>

        <div v-if="selectedItems.length" class="batch-bar">
          <span>已选 {{ selectedItems.length }} 项</span>
          <div class="batch-bar__actions">
            <n-button size="small" tertiary @click="batchCompleteSchedules">批量完成日程</n-button>
            <n-button size="small" tertiary @click="batchExecuteTasks">批量执行任务</n-button>
            <n-button size="small" tertiary @click="batchToggleNotes">批量切换置顶</n-button>
            <n-button size="small" tertiary @click="batchRescanAutonomy">批量重新扫描</n-button>
            <n-button size="small" quaternary @click="selectedKeys = []">清空</n-button>
          </div>
        </div>

        <div v-if="inbox.items.length" class="feed-list">
          <div
            v-for="item in inbox.items"
            :key="`${item.category}-${item.title}-${item.time}`"
            class="feed-item"
          >
            <n-checkbox
              class="feed-item__checkbox"
              :checked="selectedKeys.includes(itemKey(item))"
              @update:checked="toggleSelected(item)"
            />
            <button
              type="button"
              class="feed-item__main"
              @click="goTo(item.route)"
            >
            <div class="feed-item__accent" :style="{ '--accent': item.accent || '#f59e0b' }"></div>
            <div class="feed-item__copy">
              <div class="feed-item__meta">
                <n-tag size="small" :bordered="false">{{ categoryLabel(item.category) }}</n-tag>
                <span>{{ formatTime(item.time) }}</span>
              </div>
              <strong>{{ item.title }}</strong>
              <p>{{ item.summary }}</p>
            </div>
            <div class="feed-item__status">{{ item.status }}</div>
            </button>
            <div class="feed-item__actions">
              <n-button
                v-for="action in itemActions(item)"
                :key="action.label"
                size="small"
                tertiary
                @click="action.run"
              >
                {{ action.label }}
              </n-button>
            </div>
          </div>
        </div>
        <n-empty v-else description="收件箱还没有聚合到数据" />
      </div>

      <div class="surface-panel span-4">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Warnings</div>
            <h3>注意事项</h3>
          </div>
        </div>
        <div v-if="inbox.warnings?.length" class="warning-list">
          <div v-for="warning in inbox.warnings" :key="warning" class="warning-item">
            <n-icon size="18"><AlertIcon /></n-icon>
            <span>{{ warning }}</span>
          </div>
        </div>
        <n-empty v-else description="当前没有额外提示" />
      </div>

      <div class="surface-panel span-12">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Focus</div>
            <h3>分类视图</h3>
          </div>
        </div>
        <div class="lane-grid">
          <div
            v-for="lane in lanes"
            :key="lane.category"
            class="lane-card"
          >
            <div class="lane-card__head">
              <strong>{{ lane.label }}</strong>
              <span>{{ lane.items.length }}</span>
            </div>
            <div v-if="lane.items.length" class="lane-card__list">
              <button
                v-for="item in lane.items"
                :key="`${lane.category}-${item.title}`"
                type="button"
                class="lane-item"
                @click="goTo(item.route)"
              >
                <strong>{{ item.title }}</strong>
                <p>{{ item.summary }}</p>
              </button>
            </div>
            <n-empty v-else size="small" description="暂无内容" />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCheckbox, NEmpty, NIcon, NTag, useMessage } from 'naive-ui'
import {
  AlertCircleOutline as AlertIcon,
  CalendarOutline as CalendarIcon,
  DocumentTextOutline as NoteIcon,
  MailOutline as MailIcon,
  SearchOutline as SearchIcon,
  SparklesOutline as AutonomyIcon,
  TimeOutline as TaskIcon
} from '@vicons/ionicons5'
import type { InboxSummary } from '@/types'
import { autonomyService, inboxService, noteService, scheduleService, taskService } from '@/services/api'
import dayjs from 'dayjs'
import { readCachedPayload, writeCachedPayload } from '@/services/user-preferences'

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const selectedKeys = ref<string[]>([])
const inbox = ref<InboxSummary>({
  generatedAt: '',
  counts: {},
  items: [],
  warnings: []
})
const INBOX_CACHE_KEY = 'cache.inbox.v1'

const categoryMap: Record<string, { label: string; icon: any; color: string }> = {
  schedule: { label: '日程', icon: CalendarIcon, color: '#f59e0b' },
  task: { label: '任务', icon: TaskIcon, color: '#fb923c' },
  note: { label: '笔记', icon: NoteIcon, color: '#fbbf24' },
  search: { label: '搜索', icon: SearchIcon, color: '#f97316' },
  mail: { label: '邮件', icon: MailIcon, color: '#fdba74' },
  autonomy: { label: '自治', icon: AutonomyIcon, color: '#f59e0b' }
}

const metricCards = computed(() => [
  { label: '今日日程', value: inbox.value.counts.todaySchedules || 0, hint: '今天需要跟进的安排', icon: CalendarIcon, color: '#f59e0b' },
  { label: '启用任务', value: inbox.value.counts.enabledTasks || 0, hint: '自动化执行中的任务', icon: TaskIcon, color: '#fb923c' },
  { label: '置顶笔记', value: inbox.value.counts.pinnedNotes || 0, hint: '当前重点知识卡片', icon: NoteIcon, color: '#fbbf24' },
  { label: '自治发现', value: inbox.value.counts.autonomyFindings || 0, hint: '最近扫描出的结构问题', icon: AutonomyIcon, color: '#f97316' }
])

const lastUpdated = computed(() => {
  if (!inbox.value.generatedAt) return '尚未刷新'
  return `更新于 ${dayjs(inbox.value.generatedAt).format('MM-DD HH:mm')}`
})

const lanes = computed(() => Object.entries(categoryMap).map(([category, config]) => ({
  category,
  label: config.label,
  items: inbox.value.items.filter(item => item.category === category).slice(0, 3)
})))
const selectedItems = computed(() => inbox.value.items.filter(item => selectedKeys.value.includes(itemKey(item))))

const loadInbox = async () => {
  loading.value = true
  try {
    const res = await inboxService.summary(18)
    if (res.success && res.data) {
      inbox.value = res.data
      writeCachedPayload(INBOX_CACHE_KEY, res.data)
    }
  } catch (_error) {
    const cached = readCachedPayload<InboxSummary>(INBOX_CACHE_KEY)
    if (cached) {
      inbox.value = cached
      message.info('当前使用离线缓存收件箱数据')
    }
  } finally {
    loading.value = false
  }
}

const categoryLabel = (category: string) => categoryMap[category]?.label || '收件'

const formatTime = (time?: string) => time ? dayjs(time).format('MM-DD HH:mm') : '未记录时间'

const goTo = (path?: string) => {
  if (path) router.push(path)
}

const itemKey = (item: any) => `${item.category}-${item.title}-${item.time}`

const toggleSelected = (item: any) => {
  const key = itemKey(item)
  if (selectedKeys.value.includes(key)) {
    selectedKeys.value = selectedKeys.value.filter(itemKey => itemKey !== key)
  } else {
    selectedKeys.value = [...selectedKeys.value, key]
  }
}

const itemActions = (item: any) => {
  const id = item?.meta?.id
  if (item.category === 'task' && id) {
    return [{ label: '立即执行', run: () => executeTask(id) }]
  }
  if (item.category === 'schedule' && id && item.status !== 'completed') {
    return [{ label: '标记完成', run: () => completeSchedule(id) }]
  }
  if (item.category === 'note' && id) {
    return [{ label: '置顶切换', run: () => toggleNotePin(id) }]
  }
  if (item.category === 'autonomy') {
    return [{ label: '重新扫描', run: runAutonomyScan }]
  }
  return [{ label: '打开', run: () => goTo(item.route) }]
}

const executeTask = async (id: number) => {
  const res = await taskService.execute(id)
  message.success(res.success ? '任务已执行' : (res.message || '执行失败'))
  await loadInbox()
}

const completeSchedule = async (id: number) => {
  await scheduleService.complete(id)
  message.success('日程已标记完成')
  await loadInbox()
}

const toggleNotePin = async (id: number) => {
  const res = await noteService.togglePin(id)
  message.success(res.success ? '笔记状态已更新' : '操作失败')
  await loadInbox()
}

const runAutonomyScan = async () => {
  await autonomyService.scan()
  message.success('已重新扫描项目')
  await loadInbox()
}

const batchCompleteSchedules = async () => {
  for (const item of selectedItems.value.filter(item => item.category === 'schedule' && item.meta?.id)) {
    const id = item.meta?.id
    if (id) await scheduleService.complete(id)
  }
  selectedKeys.value = []
  message.success('已批量完成所选日程')
  await loadInbox()
}

const batchExecuteTasks = async () => {
  for (const item of selectedItems.value.filter(item => item.category === 'task' && item.meta?.id)) {
    const id = item.meta?.id
    if (id) await taskService.execute(id)
  }
  selectedKeys.value = []
  message.success('已批量执行所选任务')
  await loadInbox()
}

const batchToggleNotes = async () => {
  for (const item of selectedItems.value.filter(item => item.category === 'note' && item.meta?.id)) {
    const id = item.meta?.id
    if (id) await noteService.togglePin(id)
  }
  selectedKeys.value = []
  message.success('已批量切换所选笔记置顶状态')
  await loadInbox()
}

const batchRescanAutonomy = async () => {
  const autonomyCount = selectedItems.value.filter(item => item.category === 'autonomy').length
  if (autonomyCount > 0) {
    await autonomyService.scan()
    message.success(`已根据 ${autonomyCount} 条自治发现重新扫描项目`)
    selectedKeys.value = []
    await loadInbox()
  }
}

onMounted(loadInbox)
</script>

<style scoped>
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.metric-card,
.feed-item,
.lane-card,
.warning-item {
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.05);
}

.metric-card {
  display: flex;
  gap: 16px;
  align-items: center;
  border-radius: var(--radius-xl);
  padding: 18px;
  box-shadow: var(--shadow-sm);
}

.metric-card__icon {
  width: 52px;
  height: 52px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  color: #fff7ed;
  background: linear-gradient(135deg, var(--metric-color, #f59e0b), color-mix(in srgb, var(--metric-color, #f59e0b) 70%, #fde68a 30%));
}

.metric-card__copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-card__copy span,
.metric-card__copy small,
.feed-item__meta span,
.feed-item__copy p,
.warning-item span,
.lane-item p {
  color: var(--text-secondary);
}

.metric-card__copy strong {
  font-size: 1.8rem;
}

.head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.feed-list,
.warning-list,
.lane-card__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.feed-item,
.lane-item {
  text-align: left;
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: transform var(--transition-base), border-color var(--transition-base), box-shadow var(--transition-base);
}

.feed-item:hover,
.lane-item:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--primary-color) 45%, white 18%);
  box-shadow: var(--shadow-sm);
}

.feed-item {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  position: relative;
}

.feed-item__checkbox {
  position: absolute;
  top: 16px;
  right: 16px;
}

.feed-item__main {
  width: 100%;
  display: grid;
  grid-template-columns: 8px 1fr auto;
  gap: 16px;
  align-items: stretch;
  background: transparent;
  border: 0;
  padding: 0;
  text-align: left;
}

.feed-item__accent {
  border-radius: 999px;
  background: linear-gradient(180deg, var(--accent, #f59e0b), color-mix(in srgb, var(--accent, #f59e0b) 65%, #fde68a 35%));
}

.feed-item__copy {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.feed-item__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.feed-item__status {
  align-self: center;
  color: var(--text-primary);
  font-weight: 700;
  text-transform: capitalize;
}

.feed-item__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
}

.batch-bar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.05);
}

.batch-bar__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.warning-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 14px 16px;
  border-radius: var(--radius-lg);
}

.lane-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.lane-card {
  border-radius: var(--radius-xl);
  padding: 18px;
}

.lane-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.lane-card__head span {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(245, 158, 11, 0.12);
  color: var(--primary-color);
  font-weight: 700;
}

.lane-item {
  padding: 14px 16px;
}

@media (max-width: 1100px) {
  .metrics-grid,
  .lane-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .metrics-grid,
  .lane-grid {
    grid-template-columns: 1fr;
  }

  .feed-item {
    padding: 14px;
  }

  .feed-item__main {
    grid-template-columns: 8px 1fr;
  }

  .feed-item__status {
    grid-column: 2;
    justify-self: start;
  }

  .feed-item__actions {
    justify-content: flex-start;
  }

  .batch-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>

