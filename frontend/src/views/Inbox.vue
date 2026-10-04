<template>
  <UiPage class="inbox-page agent-inbox-page">
    <UiPageHeader
      eyebrow="Unified Inbox"
      title="统一收件箱"
      subtitle="把邮件、任务和日程统一收拢，优先处理需要行动的事项。"
    >
      <template #actions>
        <n-tag size="small" :bordered="false">{{ lastUpdated }}</n-tag>
        <n-button type="primary" :loading="loading" @click="loadInbox">刷新</n-button>
      </template>
    </UiPageHeader>

    <section class="metrics-grid" aria-label="收件箱概览">
      <button v-for="card in metricCards" :key="card.key" type="button" class="metric-card" @click="activeFilter = card.filter">
        <span class="metric-icon"><n-icon size="19"><component :is="card.icon" /></n-icon></span>
        <span class="metric-copy">
          <strong>{{ card.value }}</strong>
          <span>{{ card.label }}</span>
          <small>{{ card.hint }}</small>
        </span>
      </button>
    </section>

    <section class="surface-panel semantic-panel" aria-label="邮件语义搜索">
      <div class="section-head">
        <div><h3>按含义查找邮件</h3><p>描述你要找的内容，例如“客户要求延后交付的邮件”。</p></div>
        <n-button size="small" :loading="rebuilding" :disabled="!indexStatus?.enabled" @click="rebuildEmailIndex">补建邮件索引</n-button>
      </div>
      <form class="semantic-form" @submit.prevent="searchEmails">
        <n-input v-model:value="semanticQuery" aria-label="邮件搜索内容" placeholder="输入邮件内容或问题" :maxlength="2000" />
        <n-button type="primary" attr-type="submit" :loading="searching" :disabled="!semanticQuery.trim() || !indexStatus?.enabled">搜索</n-button>
        <n-button v-if="searchPerformed" @click="clearSemanticSearch">清除</n-button>
      </form>
      <div class="semantic-dates">
        <label>开始时间 <input v-model="searchFrom" type="datetime-local" /></label>
        <label>结束时间 <input v-model="searchBefore" type="datetime-local" /></label>
      </div>
      <p class="semantic-status" role="status">{{ indexMessage }}</p>
      <p v-if="searchError" class="semantic-error" role="alert">{{ searchError }}</p>
      <div v-if="searchResults.length" class="semantic-results">
        <button v-for="hit in searchResults" :key="hit.email_id" type="button" class="semantic-hit" @click="openSource(hit.email_id)">
          <strong>{{ hit.subject }}</strong>
          <small>{{ hit.sender }} · {{ hit.received_at ? formatTime(hit.received_at) : '时间未知' }}</small>
          <p>{{ hit.snippet }}</p><span>查看邮件来源</span>
        </button>
      </div>
      <p v-else-if="searchPerformed && !searching && !searchError">{{ searchResultMessage }}</p>
    </section>

    <section class="inbox-toolbar">
      <div class="filter-tabs" role="tablist" aria-label="收件箱筛选">
        <button
          v-for="filter in filters"
          :key="filter.key"
          type="button"
          :class="['filter-tab', { active: activeFilter === filter.key }]"
          @click="activeFilter = filter.key"
        >
          <n-icon size="15"><component :is="filter.icon" /></n-icon>
          <span>{{ filter.label }}</span>
          <small>{{ filterCount(filter.key) }}</small>
        </button>
      </div>
      <span class="result-count">当前 {{ filteredItems.length }} 项</span>
    </section>

    <div class="inbox-layout">
      <section class="surface-panel feed-panel">
        <div class="section-head">
          <div>
            <span class="page-eyebrow">Action Feed</span>
            <h3>{{ activeFilterLabel }}</h3>
            <p>优先完成操作，而不是在多个业务页面之间来回切换。</p>
          </div>
        </div>

        <div v-if="selectedItems.length" class="batch-bar">
          <span>已选择 {{ selectedItems.length }} 项</span>
          <div class="batch-actions">
            <n-button size="small" tertiary @click="batchCompleteSchedules">完成日程</n-button>
            <n-button size="small" tertiary @click="batchExecuteTasks">执行任务</n-button>
            <n-button size="small" quaternary @click="selectedKeys = []">清空</n-button>
          </div>
        </div>

        <div v-if="filteredItems.length" class="feed-list">
          <article v-for="item in filteredItems" :key="itemKey(item)" class="feed-item">
            <div class="feed-select">
              <n-checkbox :checked="selectedKeys.includes(itemKey(item))" @update:checked="toggleSelected(item)" />
            </div>

            <button type="button" class="feed-main" @click="goTo(item.route)">
              <span class="feed-accent" :style="{ background: item.accent || '#f97316' }"></span>
              <span class="feed-copy">
                <span class="feed-meta">
                  <n-tag size="small" :bordered="false">{{ categoryLabel(item.category) }}</n-tag>
                  <small>{{ formatTime(item.time) }}</small>
                </span>
                <strong>{{ item.title }}</strong>
                <p>{{ item.summary }}</p>
              </span>
              <span :class="['feed-status', statusTone(item.status)]">{{ item.status || '待处理' }}</span>
            </button>

            <div class="feed-actions">
              <n-button
                v-for="action in itemActions(item)"
                :key="action.label"
                size="small"
                :type="action.primary ? 'primary' : 'default'"
                :secondary="action.primary"
                :tertiary="!action.primary"
                @click="action.run"
              >
                {{ action.label }}
              </n-button>
            </div>
          </article>
        </div>

        <EmptyStateWithGlow v-else-if="!loading">
          <template #icon><n-icon size="44"><CheckIcon /></n-icon></template>
          当前筛选下没有需要处理的事项
        </EmptyStateWithGlow>
        <LoadingSpinner v-else />
      </section>

      <aside class="inbox-side">
        <section class="surface-panel side-panel">
          <div class="section-head compact">
            <div>
              <span class="page-eyebrow">Focus</span>
              <h3>处理建议</h3>
            </div>
          </div>
          <div class="focus-list">
            <button type="button" @click="activeFilter = 'pending'">
              <span>待处理事项</span>
              <strong>{{ pendingCount }}</strong>
            </button>
            <button type="button" @click="activeFilter = 'mail'">
              <span>邮件</span>
              <strong>{{ filterCount('mail') }}</strong>
            </button>
            <button type="button" @click="activeFilter = 'schedule'">
              <span>日程</span>
              <strong>{{ filterCount('schedule') }}</strong>
            </button>
            <button type="button" @click="activeFilter = 'task'">
              <span>任务</span>
              <strong>{{ filterCount('task') }}</strong>
            </button>
          </div>
        </section>

        <section class="surface-panel side-panel">
          <div class="section-head compact">
            <div>
              <span class="page-eyebrow">Warnings</span>
              <h3>系统提示</h3>
            </div>
          </div>
          <div v-if="inbox.warnings?.length" class="warning-list">
            <div v-for="warning in inbox.warnings" :key="warning" class="warning-item">
              <n-icon><AlertIcon /></n-icon>
              <span>{{ warning }}</span>
            </div>
          </div>
          <div v-else class="side-empty">当前没有额外提示</div>
        </section>

        <section class="surface-panel side-panel">
          <div class="section-head compact">
            <div>
              <span class="page-eyebrow">Agent</span>
              <h3>继续交给 Agent</h3>
            </div>
          </div>
          <p class="agent-hint">对于无法直接通过收件箱按钮处理的事项，可以进入对话让 Agent 根据上下文继续执行。</p>
          <n-button block type="primary" secondary @click="goTo('/chat')">
            <template #icon><n-icon><ChatIcon /></n-icon></template>
            打开 Agent 对话
          </n-button>
        </section>
      </aside>
    </div>
    <n-modal v-model:show="showSource" preset="card" title="邮件来源" class="email-source-modal" style="width: min(720px, 94vw)">
      <p v-if="sourceLoading">正在读取邮件…</p>
      <div v-else-if="sourceEmail">
        <h3>{{ sourceEmail.subject || '无主题' }}</h3>
        <p>{{ sourceEmail.sender }} · {{ sourceEmail.received_at ? formatTime(sourceEmail.received_at) : '时间未知' }}</p>
        <p v-if="sourceEmail.summary">{{ sourceEmail.summary }}</p>
        <pre class="source-body">{{ sourceEmail.content || '这封邮件没有纯文本正文。' }}</pre>
      </div>
      <p v-else role="alert">{{ sourceError }}</p>
    </n-modal>
  </UiPage>
</template>

<script setup lang="ts">
/**
 * 统一收件箱。
 *
 * 聚合各业务域产生的事项，并提供过滤、单项动作和批量动作。页面不自行构造业务数据，所有
 * 操作继续复用原有 service 接口。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCheckbox, NIcon, NTag, NInput, NModal, useMessage } from 'naive-ui'
import {
  AlertCircleOutline as AlertIcon,
  CalendarOutline as CalendarIcon,
  ChatbubblesOutline as ChatIcon,
  CheckmarkCircleOutline as CheckIcon,
  FileTrayFullOutline as InboxIcon,
  MailOutline as MailIcon,
  TimeOutline as TaskIcon
} from '@vicons/ionicons5'
import type { InboxItem, InboxSummary } from '@/types'
import { emailAnalysisService, type EmailSearchHit, type EmailIndexStatus, type AiEmailAnalysis } from '@/services/api/email-analysis'
import { inboxService } from '@/services/api/inbox'
import { scheduleService } from '@/services/api/schedule'
import { taskService } from '@/services/api/task'
import { formatShortDateTime as formatTime } from '@/utils/date-format'
import { readCachedPayload, writeCachedPayload } from '@/services/user-preferences'
import EmptyStateWithGlow from '@/components/EmptyStateWithGlow.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { UiPage, UiPageHeader } from '@/components/ui'

type InboxFilter = 'all' | 'pending' | 'mail' | 'schedule' | 'task'

interface ItemAction {
  label: string
  primary?: boolean
  run: () => void | Promise<void>
}

const router = useRouter()
const route = useRoute()
const message = useMessage()
const loading = ref(false)
const selectedKeys = ref<string[]>([])
const activeFilter = ref<InboxFilter>('pending')
const INBOX_CACHE_KEY = 'cache.inbox.v2'
const inbox = ref<InboxSummary>({
  generatedAt: '',
  counts: {},
  items: [],
  warnings: []
})

const categoryMap: Record<string, { label: string; icon: any }> = {
  schedule: { label: '日程', icon: CalendarIcon },
  task: { label: '任务', icon: TaskIcon },
  mail: { label: '邮件', icon: MailIcon }
}

const filters: Array<{ key: InboxFilter; label: string; icon: any }> = [
  { key: 'all', label: '全部', icon: InboxIcon },
  { key: 'pending', label: '待处理', icon: CheckIcon },
  { key: 'mail', label: '邮件', icon: MailIcon },
  { key: 'schedule', label: '日程', icon: CalendarIcon },
  { key: 'task', label: '任务', icon: TaskIcon }
]

const inboxFilterKeys: InboxFilter[] = ['all', 'pending', 'mail', 'schedule', 'task']

watch(
  () => route.query.filter,
  value => {
    const key = Array.isArray(value) ? value[0] : value
    if (key && inboxFilterKeys.includes(key as InboxFilter)) {
      activeFilter.value = key as InboxFilter
    }
  },
  { immediate: true }
)

watch(activeFilter, value => {
  if (route.query.filter === value) return
  void router.replace({ query: { ...route.query, filter: value } })
})

const isCompleted = (status?: string) => {
  const normalized = (status || '').toLowerCase()
  return ['completed', 'complete', 'done', 'success', 'finished', '已完成'].includes(normalized)
}

const visibleItems = computed(() => inbox.value.items.filter(item => item.category !== 'autonomy'))
const pendingCount = computed(() => visibleItems.value.filter(item => !isCompleted(item.status)).length)
const filteredItems = computed(() => {
  if (activeFilter.value === 'all') return visibleItems.value
  if (activeFilter.value === 'pending') return visibleItems.value.filter(item => !isCompleted(item.status))
  return visibleItems.value.filter(item => item.category === activeFilter.value)
})
const selectedItems = computed(() => visibleItems.value.filter(item => selectedKeys.value.includes(itemKey(item))))
const activeFilterLabel = computed(() => filters.find(item => item.key === activeFilter.value)?.label || '全部事项')

const metricCards = computed(() => [
  { key: 'pending', label: '待处理', value: pendingCount.value, hint: '尚未完成的聚合事项', icon: InboxIcon, filter: 'pending' as InboxFilter },
  { key: 'schedule', label: '今日日程', value: Number(inbox.value.counts.todaySchedules || 0), hint: '今天需要跟进的安排', icon: CalendarIcon, filter: 'schedule' as InboxFilter },
  { key: 'task', label: '启用任务', value: Number(inbox.value.counts.enabledTasks || 0), hint: '当前可执行任务', icon: TaskIcon, filter: 'task' as InboxFilter }
])

const lastUpdated = computed(() => inbox.value.generatedAt ? `更新于 ${formatTime(inbox.value.generatedAt)}` : '尚未刷新')

const filterCount = (filter: InboxFilter) => {
  if (filter === 'all') return visibleItems.value.length
  if (filter === 'pending') return pendingCount.value
  return visibleItems.value.filter(item => item.category === filter).length
}

const categoryLabel = (category: string) => categoryMap[category]?.label || 'Workspace'
const itemKey = (item: InboxItem) => `${item.category}-${item.title}-${item.time}`

const statusTone = (status?: string) => {
  const normalized = (status || '').toLowerCase()
  if (isCompleted(status)) return 'success'
  if (normalized.includes('fail') || normalized.includes('error')) return 'error'
  if (normalized.includes('run') || normalized.includes('process')) return 'running'
  return 'pending'
}

const goTo = (path?: string) => {
  if (path) router.push(path)
}

const toggleSelected = (item: InboxItem) => {
  const key = itemKey(item)
  selectedKeys.value = selectedKeys.value.includes(key)
    ? selectedKeys.value.filter(itemKey => itemKey !== key)
    : [...selectedKeys.value, key]
}

/** 加载统一收件箱摘要；失败时回退到最近缓存。 */
const loadInbox = async () => {
  loading.value = true
  try {
    const response = await inboxService.summary(30)
    if (response.success && response.data) {
      inbox.value = response.data
      writeCachedPayload(INBOX_CACHE_KEY, response.data)
    }
  } catch {
    const cached = readCachedPayload<InboxSummary>(INBOX_CACHE_KEY)
    if (cached) {
      inbox.value = cached
      message.info('当前使用离线缓存收件箱数据')
    }
  } finally {
    loading.value = false
  }
}

/** 根据事项类型返回当前可执行动作。 */
const itemActions = (item: InboxItem): ItemAction[] => {
  const id = item.meta?.id
  if (item.category === 'task' && id) {
    return [{ label: '立即执行', primary: true, run: () => executeTask(Number(id)) }, { label: '打开', run: () => goTo(item.route) }]
  }
  if (item.category === 'schedule' && id && !isCompleted(item.status)) {
    return [{ label: '标记完成', primary: true, run: () => completeSchedule(Number(id)) }, { label: '打开', run: () => goTo(item.route) }]
  }
  return [{ label: '打开', primary: item.category === 'mail', run: () => goTo(item.route) }]
}

const executeTask = async (id: number) => {
  const response = await taskService.execute(id)
  if (response.success) message.success('任务已执行')
  else message.error(response.message || '执行失败')
  await loadInbox()
}

const completeSchedule = async (id: number) => {
  await scheduleService.complete(id)
  message.success('日程已标记完成')
  await loadInbox()
}

/** 批量完成选中日程。 */
const batchCompleteSchedules = async () => {
  for (const item of selectedItems.value.filter(item => item.category === 'schedule' && item.meta?.id)) {
    await scheduleService.complete(Number(item.meta?.id))
  }
  selectedKeys.value = []
  message.success('已批量完成所选日程')
  await loadInbox()
}

/** 批量执行选中任务。 */
const batchExecuteTasks = async () => {
  for (const item of selectedItems.value.filter(item => item.category === 'task' && item.meta?.id)) {
    await taskService.execute(Number(item.meta?.id))
  }
  selectedKeys.value = []
  message.success('已批量执行所选任务')
  await loadInbox()
}

const semanticQuery = ref('')
const searchFrom = ref('')
const searchBefore = ref('')
const searching = ref(false)
const rebuilding = ref(false)
const searchPerformed = ref(false)
const searchResults = ref<EmailSearchHit[]>([])
const searchError = ref('')
const searchResultMessage = ref('')
const indexStatus = ref<EmailIndexStatus | null>(null)
const indexError = ref('')
const showSource = ref(false)
const sourceLoading = ref(false)
const sourceEmail = ref<AiEmailAnalysis | null>(null)
const sourceError = ref('')
let sourceRequest = 0
const indexMessage = computed(() => {
  if (indexError.value) return indexError.value
  const status = indexStatus.value
  if (!status) return '正在读取索引状态…'
  if (!status.enabled) return status.message
  const counts = status.counts
  return `已索引 ${counts.ready || 0} / ${status.total_emails || 0} 封 · 等待 ${(counts.pending || 0) + (counts.retry || 0)} 封 · 失败 ${counts.failed || 0} 封。${status.message}`
})
const loadIndexStatus = async () => {
  try {
    indexStatus.value = await emailAnalysisService.indexStatus()
    indexError.value = ''
  } catch (error) {
    indexError.value = error instanceof Error ? error.message : '索引状态不可用'
  }
}
const searchEmails = async () => {
  if (!semanticQuery.value.trim() || searching.value) return
  searching.value = true
  searchPerformed.value = true
  searchResults.value = []
  searchError.value = ''
  try {
    const lower = searchFrom.value ? new Date(searchFrom.value).toISOString() : undefined
    const upper = searchBefore.value ? new Date(searchBefore.value).toISOString() : undefined
    if (lower && upper && upper <= lower) throw new Error('结束时间必须晚于开始时间')
    const result = await emailAnalysisService.search(semanticQuery.value.trim(), lower, upper)
    searchResults.value = result.results
    searchResultMessage.value = result.message
  } catch (error) {
    searchError.value = error instanceof Error ? error.message : '搜索失败'
  } finally {
    searching.value = false
    void loadIndexStatus()
  }
}
const clearSemanticSearch = () => {
  semanticQuery.value = ''
  searchResults.value = []
  searchError.value = ''
  searchPerformed.value = false
}
const rebuildEmailIndex = async () => {
  rebuilding.value = true
  try {
    const result = await emailAnalysisService.rebuildIndex()
    message.success(result.message)
    await loadIndexStatus()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '补建失败')
  } finally {
    rebuilding.value = false
  }
}
const openSource = async (id: number) => {
  const request = ++sourceRequest
  showSource.value = true
  sourceLoading.value = true
  sourceEmail.value = null
  sourceError.value = ''
  try {
    const email = await emailAnalysisService.getById(id)
    if (request === sourceRequest) sourceEmail.value = email
  } catch (error) {
    if (request === sourceRequest) sourceError.value = error instanceof Error ? error.message : '邮件来源不可用'
  } finally {
    if (request === sourceRequest) sourceLoading.value = false
  }
}
watch(() => route.query.email, value => {
  if (typeof value === 'string' && /^[1-9]\d*$/.test(value)) void openSource(Number(value))
}, { immediate: true })
onMounted(() => { void loadInbox(); void loadIndexStatus() })
</script>

<style scoped>
.semantic-panel { padding: 20px; }
.semantic-form { display: flex; gap: 10px; }
.semantic-form .n-input { flex: 1; }
.semantic-dates { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 12px; }
.semantic-dates label { display: flex; align-items: center; gap: 8px; color: var(--text-secondary); font-size: 0.8rem; }
.semantic-dates input { max-width: 100%; padding: 7px; border: 1px solid var(--border-light); border-radius: 8px; background: var(--bg-input); color: var(--text-primary); }
.semantic-status { margin-top: 12px; font-size: 0.8rem; color: var(--text-secondary); }
.semantic-error { color: var(--error-color, #dc2626); }
.semantic-results { display: grid; gap: 10px; margin-top: 14px; }
.semantic-hit { display: grid; gap: 6px; padding: 14px; text-align: left; color: var(--text-primary); background: var(--bg-input); border: 1px solid var(--border-light); border-radius: 10px; cursor: pointer; overflow-wrap: anywhere; }
.semantic-hit small { color: var(--text-secondary); }
.semantic-hit p { white-space: pre-wrap; max-height: 7rem; overflow: hidden; margin: 0; }
.semantic-hit span { color: var(--primary-color); }
.source-body { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 60vh; overflow-y: auto; font: inherit; }
@media (max-width: 600px) { .semantic-form { flex-wrap: wrap; } .semantic-form .n-input { flex-basis: 100%; } .semantic-panel .section-head { flex-wrap: wrap; } }

.agent-inbox-page {
  gap: 18px;
}

.page-eyebrow {
  color: var(--primary-color);
  font-size: 0.66rem;
  font-weight: 750;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric-card {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-height: 102px;
  padding: 15px;
  border: 1px solid var(--workspace-border, var(--border-light));
  border-radius: 14px;
  background: var(--bg-card);
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.metric-card:hover {
  border-color: color-mix(in srgb, var(--primary-color) 34%, var(--workspace-border, var(--border-light)));
}

.metric-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--primary-color) 11%, var(--bg-input));
  color: var(--primary-color);
}

.metric-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.metric-copy strong {
  font-size: 1.4rem;
  line-height: 1;
}

.metric-copy span {
  margin-top: 4px;
  font-size: 0.76rem;
  font-weight: 650;
}

.metric-copy small {
  color: var(--text-muted);
  font-size: 0.64rem;
  line-height: 1.4;
}

.inbox-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.filter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 9px;
  border: 1px solid transparent;
  border-radius: 9px;
  background: var(--bg-input);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.7rem;
  cursor: pointer;
}

.filter-tab small {
  display: grid;
  place-items: center;
  min-width: 18px;
  height: 18px;
  padding: 0 4px;
  border-radius: 99px;
  background: var(--bg-card);
  font-size: 0.6rem;
}

.filter-tab.active {
  border-color: color-mix(in srgb, var(--primary-color) 30%, transparent);
  background: color-mix(in srgb, var(--primary-color) 10%, var(--bg-card));
  color: var(--primary-color);
}

.result-count {
  flex: 0 0 auto;
  color: var(--text-muted);
  font-size: 0.68rem;
}

.inbox-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;
}

.surface-panel {
  border: 1px solid var(--workspace-border, var(--border-light));
  border-radius: 15px;
  background: var(--bg-card);
  box-shadow: none;
}

.feed-panel,
.side-panel {
  padding: 18px;
}

.inbox-side {
  display: grid;
  gap: 14px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-head.compact {
  margin-bottom: 10px;
}

.section-head h3 {
  margin: 4px 0 0;
  font-size: 0.95rem;
}

.section-head p {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 0.7rem;
}

.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--primary-color) 7%, var(--bg-input));
  color: var(--text-secondary);
  font-size: 0.7rem;
}

.batch-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.feed-list {
  display: grid;
}

.feed-item {
  position: relative;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 8px;
  padding: 14px 0;
  border-top: 1px solid var(--workspace-border, var(--border-light));
}

.feed-item:first-child {
  border-top: 0;
}

.feed-select {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}

.feed-main {
  display: grid;
  grid-template-columns: 3px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.feed-accent {
  width: 3px;
  height: 48px;
  border-radius: 99px;
}

.feed-copy {
  display: grid;
  min-width: 0;
}

.feed-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
}

.feed-meta small {
  color: var(--text-muted);
  font-size: 0.62rem;
}

.feed-copy strong {
  overflow: hidden;
  font-size: 0.78rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.feed-copy p {
  display: -webkit-box;
  margin: 5px 0 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 0.7rem;
  line-height: 1.5;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.feed-status {
  padding: 4px 7px;
  border-radius: 99px;
  background: var(--bg-input);
  color: var(--text-muted);
  font-size: 0.6rem;
  white-space: nowrap;
}

.feed-status.success { color: #16a34a; }
.feed-status.error { color: #dc2626; }
.feed-status.running { color: #0284c7; }
.feed-status.pending { color: var(--primary-color); }

.feed-actions {
  grid-column: 2;
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 8px;
}

.focus-list {
  display: grid;
}

.focus-list button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 42px;
  padding: 0 4px;
  border: 0;
  border-top: 1px solid var(--workspace-border, var(--border-light));
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 0.72rem;
  cursor: pointer;
}

.focus-list button:first-child {
  border-top: 0;
}

.focus-list strong {
  color: var(--text-primary);
}

.warning-list {
  display: grid;
}

.warning-item {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 8px;
  padding: 9px 0;
  border-top: 1px solid var(--workspace-border, var(--border-light));
  color: var(--text-secondary);
  font-size: 0.68rem;
  line-height: 1.5;
}

.warning-item:first-child {
  border-top: 0;
}

.warning-item :deep(svg) {
  color: var(--primary-color);
}

.side-empty,
.agent-hint {
  color: var(--text-muted);
  font-size: 0.68rem;
  line-height: 1.6;
}

.agent-hint {
  margin: 0 0 12px;
}

@media (max-width: 1100px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .inbox-layout {
    grid-template-columns: 1fr;
  }

  .inbox-side {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .metrics-grid,
  .inbox-side {
    grid-template-columns: 1fr;
  }

  .inbox-toolbar,
  .batch-bar {
    align-items: flex-start;
    flex-direction: column;
  }

  .feed-panel,
  .side-panel {
    padding: 14px;
  }

  .feed-main {
    grid-template-columns: 3px minmax(0, 1fr);
  }

  .feed-status {
    display: none;
  }

  .feed-actions {
    justify-content: flex-start;
  }
}
</style>
