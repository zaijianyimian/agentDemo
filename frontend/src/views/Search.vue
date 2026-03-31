<template>
  <div class="search-page">
    <!-- 搜索区域 -->
    <n-card class="search-card" :bordered="false">
      <div class="search-box">
        <n-input
          v-model:value="searchQuery"
          size="large"
          placeholder="输入搜索内容..."
          :disabled="loading"
          @keydown.enter="doSearch"
        >
          <template #prefix>
            <n-icon><SearchIcon /></n-icon>
          </template>
        </n-input>
        <n-button
          type="primary"
          size="large"
          :loading="loading"
          :disabled="!searchQuery.trim()"
          @click="doSearch"
        >
          搜索
        </n-button>
      </div>
      <n-space class="search-modes">
        <n-radio-group v-model:value="searchMode" :disabled="loading">
          <n-radio-button value="normal">普通搜索</n-radio-button>
          <n-radio-button value="summary">搜索+AI总结</n-radio-button>
          <n-radio-button value="stream">流式问答</n-radio-button>
        </n-radio-group>
      </n-space>
    </n-card>

    <!-- 侧边栏：历史和兴趣 -->
    <div class="sidebar" v-if="!loading && results.length === 0 && !summary && !chatAnswer">
      <!-- 热门搜索 -->
      <n-card class="sidebar-card" title="🔥 热门搜索" size="small" :bordered="false">
        <div class="hot-queries">
          <n-tag
            v-for="(item, index) in hotQueries"
            :key="index"
            type="info"
            round
            class="hot-tag"
            @click="searchFromHot(item.query)"
          >
            {{ item.query }}
            <span class="count">{{ item.count }}</span>
          </n-tag>
        </div>
      </n-card>

      <!-- 用户兴趣 -->
      <n-card class="sidebar-card" title="💡 我的兴趣" size="small" :bordered="false">
        <div class="interests">
          <div
            v-for="interest in topInterests"
            :key="interest.id"
            class="interest-item"
          >
            <div class="interest-info">
              <span class="interest-tag">{{ interest.tag }}</span>
              <n-tag :type="getCategoryColor(interest.category)" size="small">
                {{ getCategoryName(interest.category) }}
              </n-tag>
            </div>
            <div class="interest-weight">
              <n-progress
                type="line"
                :percentage="getWeightPercentage(interest.weight)"
                :show-indicator="false"
                :height="4"
              />
              <span class="weight-text">{{ interest.searchCount }}次</span>
            </div>
          </div>
        </div>
        <n-button text type="primary" @click="showInterestReport = true">
          查看完整分析 →
        </n-button>
      </n-card>

      <!-- 搜索历史 -->
      <n-card class="sidebar-card" title="📜 搜索历史" size="small" :bordered="false">
        <div class="history-list">
          <div
            v-for="item in searchHistory"
            :key="item.id"
            class="history-item"
            @click="searchFromHistory(item.query)"
          >
            <span class="history-query">{{ item.query }}</span>
            <span class="history-time">{{ formatTime(item.createTime) }}</span>
          </div>
        </div>
        <n-button text type="error" @click="confirmClearHistory">
          清空历史
        </n-button>
      </n-card>
    </div>

    <!-- 搜索结果 -->
    <n-card v-if="results.length > 0" title="搜索结果" class="result-card" :bordered="false">
      <n-list>
        <n-list-item v-for="(result, index) in results" :key="index">
          <a :href="result.url" target="_blank" class="result-title">
            {{ result.title }}
          </a>
          <div class="result-url">{{ result.url }}</div>
          <div class="result-snippet">{{ result.snippet }}</div>
        </n-list-item>
      </n-list>
    </n-card>

    <!-- AI总结 -->
    <n-card v-if="summary" title="AI总结" class="summary-card" :bordered="false">
      <div class="summary-content markdown-body" v-html="renderMarkdown(summary)"></div>
      <span v-if="isStreamingSummary" class="cursor-blink">|</span>
    </n-card>

    <!-- AI回答 -->
    <n-card v-if="chatAnswer" title="AI回答" class="answer-card" :bordered="false">
      <div class="answer-content markdown-body" v-html="renderMarkdown(chatAnswer)"></div>
      <span v-if="isStreamingChat" class="cursor-blink">|</span>
    </n-card>

    <!-- 兴趣分析报告弹窗 -->
    <n-modal v-model:show="showInterestReport" preset="card" title="📊 兴趣分析报告" style="width: 600px; max-width: 90vw;">
      <div class="interest-report" v-if="interestReport">
        <n-statistic label="兴趣标签总数" :value="interestReport.totalInterests" />
        <n-statistic label="搜索总权重" :value="interestReport.totalSearchWeight" />

        <n-divider>兴趣领域分布</n-divider>
        <div class="category-distribution">
          <div v-for="(entry, index) in interestReport.categoryDistribution" :key="index" class="category-bar">
            <span class="category-name">{{ getCategoryName(entry[0]) }}</span>
            <n-progress
              type="line"
              :percentage="(entry[1] / interestReport.totalSearchWeight) * 100"
              :show-indicator="true"
            />
          </div>
        </div>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import {
  NCard,
  NInput,
  NButton,
  NIcon,
  NSpace,
  NRadioGroup,
  NRadioButton,
  NList,
  NListItem,
  NTag,
  NProgress,
  NStatistic,
  NDivider,
  NModal,
  useMessage,
  useDialog
} from 'naive-ui'
import { SearchOutline as SearchIcon } from '@vicons/ionicons5'
import { searchService } from '@/services/api'
import { fetchWithAuth } from '@/services/auth-fetch'
import type { SearchResult } from '@/types'
import { marked } from 'marked'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import bash from 'highlight.js/lib/languages/bash'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('js', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('ts', typescript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('shell', bash)

// 自定义渲染器添加代码高亮
const renderer = new marked.Renderer()
renderer.code = function({ text, lang }: { text: string; lang?: string }) {
  const language = lang && hljs.getLanguage(lang) ? lang : 'plaintext'
  const highlighted = hljs.highlight(text, { language }).value
  return `<pre><code class="hljs language-${language}">${highlighted}</code></pre>`
}

// 配置 marked
marked.setOptions({
  renderer,
  breaks: true,
  gfm: true
})

// 渲染 Markdown
const renderMarkdown = (text: string) => {
  return marked.parse(text) as string
}

const message = useMessage()
const dialog = useDialog()
const searchQuery = ref('')
const searchMode = ref('normal')
const loading = ref(false)
const results = ref<SearchResult[]>([])
const summary = ref('')
const chatAnswer = ref('')
const isStreamingSummary = ref(false)
const isStreamingChat = ref(false)

// 搜索历史和兴趣
const searchHistory = ref<any[]>([])
const hotQueries = ref<any[]>([])
const topInterests = ref<any[]>([])
const interestReport = ref<any>(null)
const showInterestReport = ref(false)

let abortController: AbortController | null = null

// 加载侧边栏数据
const loadSidebarData = async () => {
  try {
    const [historyRes, hotRes, interestsRes] = await Promise.all([
      searchService.getHistory(10),
      searchService.getHotQueries(10),
      searchService.getTopInterests(8)
    ])

    if (historyRes.success) searchHistory.value = historyRes.data || []
    if (hotRes.success) hotQueries.value = hotRes.data || []
    if (interestsRes.success) topInterests.value = interestsRes.data || []
  } catch (error) {
    console.error('加载侧边栏数据失败', error)
  }
}

// 加载兴趣报告
const loadInterestReport = async () => {
  try {
    const res = await searchService.getInterestReport()
    if (res.success) interestReport.value = res.data
  } catch (error) {
    console.error('加载兴趣报告失败', error)
  }
}

// 执行搜索
const doSearch = async () => {
  if (!searchQuery.value.trim()) {
    message.warning('请输入搜索内容')
    return
  }

  loading.value = true
  results.value = []
  summary.value = ''
  chatAnswer.value = ''
  isStreamingSummary.value = false
  isStreamingChat.value = false

  try {
    switch (searchMode.value) {
      case 'normal':
        const normalRes = await searchService.search(searchQuery.value)
        results.value = normalRes.results || []
        loadSidebarData() // 刷新侧边栏
        break
      case 'summary':
        await doStreamSummary()
        break
      case 'stream':
        await doStreamChat()
        break
    }
  } catch (error: any) {
    if (error.name !== 'AbortError') {
      message.error('搜索失败')
    }
  } finally {
    loading.value = false
    isStreamingSummary.value = false
    isStreamingChat.value = false
  }
}

// 从热门搜索点击
const searchFromHot = (query: string) => {
  searchQuery.value = query
  doSearch()
}

// 从历史点击
const searchFromHistory = (query: string) => {
  searchQuery.value = query
  doSearch()
}

// AI总结（SSE流式）
const doStreamSummary = async () => {
  abortController = new AbortController()
  isStreamingSummary.value = true

  const response = await fetchWithAuth(`/api/search/summary?query=${encodeURIComponent(searchQuery.value)}`, {
    signal: abortController.signal,
    headers: {
      'Accept': 'text/event-stream'
    }
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) throw new Error('No reader available')

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (data === '[DONE]') {
          continue
        }
        try {
          const parsed = JSON.parse(data)
          // 搜索结果
          if (parsed.searchResults) {
            results.value = parsed.searchResults
          }
          // AI总结流式内容
          if (parsed.summary) {
            summary.value += parsed.summary
          }
        } catch {
          // 忽略解析错误
        }
      }
    }
  }

  loadSidebarData()
}

// 流式问答
const doStreamChat = async () => {
  abortController = new AbortController()
  isStreamingChat.value = true

  const response = await fetchWithAuth(`/api/search/chat/stream?message=${encodeURIComponent(searchQuery.value)}`, {
    signal: abortController.signal,
    headers: {
      'Accept': 'text/event-stream'
    }
  })

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) throw new Error('No reader available')

  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })

    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const data = line.slice(5).trim()
        if (data && data !== '[DONE]') {
          try {
            const parsed = JSON.parse(data)
            chatAnswer.value += parsed.content || parsed.text || data
          } catch {
            chatAnswer.value += data
          }
        }
      } else if (line.trim() && !line.startsWith(':')) {
        chatAnswer.value += line
      }
    }
  }

  if (buffer.trim()) {
    chatAnswer.value += buffer
  }

  loadSidebarData()
}

// 停止流式响应
const stopStreaming = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
}

// 清空历史确认
const confirmClearHistory = () => {
  dialog.warning({
    title: '确认清空',
    content: '确定要清空所有搜索历史吗？',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      await searchService.clearHistory()
      searchHistory.value = []
      message.success('已清空搜索历史')
    }
  })
}

// 格式化时间
const formatTime = (time: string) => {
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  return `${Math.floor(diff / 86400000)}天前`
}

// 获取分类名称
const getCategoryName = (category: string) => {
  const names: Record<string, string> = {
    technology: '科技',
    business: '商业',
    entertainment: '娱乐',
    health: '健康',
    education: '教育',
    travel: '旅行',
    news: '新闻',
    other: '其他'
  }
  return names[category] || category
}

// 获取分类颜色
const getCategoryColor = (category: string): 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error' => {
  const colors: Record<string, 'default' | 'primary' | 'info' | 'success' | 'warning' | 'error'> = {
    technology: 'primary',
    business: 'warning',
    entertainment: 'error',
    health: 'success',
    education: 'info',
    travel: 'success',
    news: 'default',
    other: 'default'
  }
  return colors[category] || 'default'
}

// 计算权重百分比
const maxWeight = computed(() => Math.max(...topInterests.value.map(i => i.weight), 1))
const getWeightPercentage = (weight: number) => {
  return Math.round((weight / maxWeight.value) * 100)
}

// 组件卸载时清理
onUnmounted(() => {
  stopStreaming()
})

// 初始化
onMounted(() => {
  loadSidebarData()
  loadInterestReport()
})
</script>

<style scoped>
.search-page {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 16px;
}

@media (max-width: 900px) {
  .search-page {
    grid-template-columns: 1fr;
  }
  .sidebar {
    order: -1;
  }
}

.search-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  transition: background-color 0.3s ease, border-color 0.3s ease;
}

.search-box {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.search-modes {
  display: flex;
  justify-content: center;
}

/* 侧边栏 */
.sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.sidebar-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

/* 热门搜索 */
.hot-queries {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hot-tag {
  cursor: pointer;
  transition: transform 0.2s;
}

.hot-tag:hover {
  transform: scale(1.05);
}

.hot-tag .count {
  margin-left: 4px;
  opacity: 0.7;
  font-size: 11px;
}

/* 用户兴趣 */
.interests {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.interest-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px;
  background: var(--bg-hover);
  border-radius: 8px;
}

.interest-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.interest-tag {
  font-weight: 500;
  color: var(--text-primary);
}

.interest-weight {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100px;
}

.weight-text {
  font-size: 11px;
  color: var(--text-muted);
  white-space: nowrap;
}

/* 搜索历史 */
.history-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
  max-height: 200px;
  overflow-y: auto;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.history-item:hover {
  background: var(--bg-hover);
}

.history-query {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 150px;
}

.history-time {
  font-size: 11px;
  color: var(--text-muted);
}

/* 搜索结果 */
.result-card,
.summary-card,
.answer-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  transition: background-color 0.3s ease, border-color 0.3s ease;
  grid-column: span 2;
}

@media (max-width: 900px) {
  .result-card,
  .summary-card,
  .answer-card {
    grid-column: span 1;
  }
}

.result-title {
  font-size: 16px;
  font-weight: 500;
  color: var(--primary-color);
  text-decoration: none;
}

.result-title:hover {
  text-decoration: underline;
}

.result-url {
  font-size: 12px;
  color: var(--text-muted);
  margin: 4px 0;
}

.result-snippet {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.summary-content,
.answer-content {
  line-height: 1.8;
  color: var(--text-primary);
  transition: color 0.3s ease;
}

/* Markdown 渲染样式 */
.markdown-body {
  font-size: 14px;
  line-height: 1.8;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3 {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 600;
}

.markdown-body h1 { font-size: 20px; }
.markdown-body h2 { font-size: 18px; }
.markdown-body h3 { font-size: 16px; }

.markdown-body p {
  margin: 8px 0;
}

.markdown-body ul,
.markdown-body ol {
  margin: 8px 0;
  padding-left: 24px;
}

.markdown-body li {
  margin: 4px 0;
}

.markdown-body a {
  color: var(--primary-color);
  text-decoration: none;
}

.markdown-body a:hover {
  text-decoration: underline;
}

.markdown-body code {
  background: var(--bg-input);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}

.markdown-body pre {
  background: var(--bg-input);
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-body pre code {
  background: transparent;
  padding: 0;
}

.markdown-body blockquote {
  border-left: 4px solid var(--primary-color);
  padding-left: 16px;
  margin: 12px 0;
  color: var(--text-secondary);
}

.markdown-body strong {
  font-weight: 600;
}

.markdown-body table {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid var(--border-color);
  padding: 8px 12px;
}

.markdown-body th {
  background: var(--bg-input);
  font-weight: 600;
}

/* 兴趣报告 */
.interest-report {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.category-distribution {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.category-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.category-name {
  width: 60px;
  font-size: 13px;
  color: var(--text-secondary);
}

/* 光标闪烁动画 */
.cursor-blink {
  animation: blink 1s infinite;
  font-weight: bold;
  color: var(--primary-color);
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
</style>
