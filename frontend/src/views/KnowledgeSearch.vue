<template>
  <div class="knowledge-search-page">
    <div class="search-container">
      <!-- 搜索栏 -->
      <div class="search-bar">
        <div class="search-bar-inner">
          <n-select
            v-model:value="selectedBaseId"
            :options="baseOptions"
            placeholder="选择知识库"
            style="width: 220px"
            size="large"
          />
          <n-input
            v-model:value="searchQuery"
            placeholder="输入搜索内容..."
            clearable
            size="large"
            @keyup.enter="doSearch"
            style="flex: 1"
          />
          <n-select
            v-model:value="topK"
            :options="topKOptions"
            style="width: 100px"
            size="large"
          />
          <n-button
            type="primary"
            size="large"
            @click="doSearch"
            :loading="searching"
            :disabled="!selectedBaseId || !searchQuery.trim()"
          >
            <template #icon><n-icon><SearchIcon /></n-icon></template>
            搜索
          </n-button>
        </div>
      </div>

      <!-- 结果区域 -->
      <div class="results-area">
        <div v-if="results.length > 0" class="results-list">
          <div class="results-header">
            <span class="results-count">找到 {{ results.length }} 条结果</span>
          </div>
          <div
            v-for="(item, idx) in results"
            :key="idx"
            class="result-card"
          >
            <div class="result-header">
              <div class="result-doc">
                <n-icon size="16"><DocumentIcon /></n-icon>
                <span>{{ item.docName || '未知文档' }}</span>
              </div>
              <div class="result-score" :class="scoreClass(item.score)">
                {{ (item.score * 100).toFixed(1) }}%
              </div>
            </div>
            <div class="result-text">{{ item.text }}</div>
          </div>
        </div>

        <div v-else-if="searched" class="empty-state">
          <div class="empty-illustration">
            <n-icon size="60"><SearchIcon /></n-icon>
          </div>
          <div class="empty-title">未找到匹配结果</div>
          <div class="empty-subtitle">尝试使用不同的关键词或选择其他知识库</div>
        </div>

        <div v-else class="empty-state">
          <div class="empty-illustration">
            <n-icon size="60"><SearchIcon /></n-icon>
          </div>
          <div class="empty-title">语义搜索</div>
          <div class="empty-subtitle">选择知识库并输入搜索内容，通过向量相似度匹配文档片段</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 知识库语义检索页面：在指定知识库内通过向量相似度匹配 topK 个片段。
 */
import { ref, computed, onMounted } from 'vue'
import { NIcon, NButton, NInput, NSelect, useMessage } from 'naive-ui'
import {
  SearchOutline as SearchIcon,
  DocumentTextOutline as DocumentIcon
} from '@vicons/ionicons5'
import { knowledgeService } from '@/services/api/knowledge'
import type { KnowledgeBase } from '@/types'

const message = useMessage()

const bases = ref<KnowledgeBase[]>([])
const selectedBaseId = ref<number | null>(null)
const searchQuery = ref('')
const topK = ref(5)
const searching = ref(false)
const searched = ref(false)
const results = ref<Array<{ score: number; text: string; docName?: string; docId?: number }>>([])

const baseOptions = computed(() =>
  bases.value
    .filter(b => b.enabled)
    .map(b => ({ label: b.name, value: b.id }))
)

const topKOptions = [
  { label: '3 条', value: 3 },
  { label: '5 条', value: 5 },
  { label: '10 条', value: 10 }
]

const scoreClass = (score: number) => {
  if (score >= 0.8) return 'score-high'
  if (score >= 0.5) return 'score-mid'
  return 'score-low'
}

const loadBases = async () => {
  try {
    const res = await knowledgeService.list()
    if (res.success && res.data) {
      bases.value = res.data
      if (!selectedBaseId.value && bases.value.length) {
        selectedBaseId.value = bases.value[0].id
      }
    }
  } catch {
    message.error('加载知识库列表失败')
  }
}

const doSearch = async () => {
  if (!selectedBaseId.value || !searchQuery.value.trim()) return
  searching.value = true
  searched.value = true
  results.value = []
  try {
    const res = await knowledgeService.search(selectedBaseId.value, searchQuery.value.trim(), topK.value)
    if (res.success && res.data) {
      results.value = res.data
      if (!res.data.length) {
        message.info('没有找到匹配的内容')
      }
    }
  } catch {
    message.error('搜索失败')
  } finally {
    searching.value = false
  }
}

onMounted(() => {
  loadBases()
})
</script>

<style scoped>
.knowledge-search-page {
  width: 100%;
  height: calc(100vh - 112px);
  position: relative;
  overflow: hidden;
}

.search-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  z-index: 10;
  position: relative;
}

/* 搜索栏 */
.search-bar {
  padding: 20px 24px;
  background: var(--bg-card);
  border-bottom: 2px solid var(--border-light);
}

.search-bar-inner {
  display: flex;
  gap: 12px;
  align-items: center;
}

/* 结果区域 */
.results-area {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.results-header {
  margin-bottom: 16px;
}

.results-count {
  font-size: 14px;
  color: var(--text-muted);
  font-weight: 500;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.result-card {
  background: var(--bg-card);
  border: 2px solid var(--border-light);
  border-radius: 14px;
  padding: 18px 20px;
  transition: all 0.2s ease;
}

.result-card:hover {
  border-color: var(--primary-light);
  box-shadow: var(--shadow-md);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.result-doc {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.result-doc .n-icon { color: var(--primary-color); }

.result-score {
  font-size: 13px;
  font-weight: 700;
  padding: 2px 10px;
  border-radius: 20px;
}

.score-high { background: rgba(34, 197, 94, 0.12); color: #16a34a; }
.score-mid { background: rgba(234, 88, 12, 0.12); color: #ea580c; }
.score-low { background: rgba(156, 163, 175, 0.12); color: #9ca3af; }

.result-text {
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-secondary);
  white-space: pre-wrap;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
}

.empty-illustration { color: var(--text-muted); opacity: 0.4; }
.empty-title { font-size: 18px; font-weight: 600; color: var(--text-muted); }
.empty-subtitle { font-size: 14px; color: var(--text-muted); opacity: 0.7; }
</style>
