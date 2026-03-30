<template>
  <div class="page-shell reports-page">
    <section class="metrics-grid">
      <article class="metric-card">
        <span>历史报告</span>
        <strong>{{ history.length }}</strong>
        <small>已生成的日报与周报</small>
      </article>
      <article class="metric-card">
        <span>当前预览</span>
        <strong>{{ activePeriodLabel }}</strong>
        <small>最近一次打开的报告周期</small>
      </article>
    </section>

    <section class="section-grid">
      <div class="surface-panel span-4">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Generate</div>
            <h3>生成报告</h3>
          </div>
        </div>
        <div class="report-actions">
          <n-button type="primary" size="large" @click="generate('daily')" :loading="generatingDaily">生成日报</n-button>
          <n-button size="large" @click="generate('weekly')" :loading="generatingWeekly">生成周报</n-button>
        </div>
      </div>

      <div class="surface-panel span-8">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">History</div>
            <h3>报告历史</h3>
          </div>
          <n-button tertiary @click="loadHistory">刷新</n-button>
        </div>
        <div v-if="history.length" class="history-list">
          <button
            v-for="item in history"
            :key="item.path"
            type="button"
            class="history-card"
            @click="openArtifact(item)"
          >
            <div class="history-card__meta">
              <n-tag size="small" :type="item.period === 'weekly' ? 'warning' : 'info'">{{ item.period }}</n-tag>
              <span>{{ formatTime(item.time) }}</span>
            </div>
            <strong>{{ item.name }}</strong>
            <p>{{ item.preview }}</p>
          </button>
        </div>
        <n-empty v-else description="还没有报告历史" />
      </div>

      <div class="surface-panel span-12">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Preview</div>
            <h3>报告预览</h3>
          </div>
          <n-tag size="small" type="warning">{{ currentReport?.path || '未选择报告' }}</n-tag>
        </div>
        <div v-if="currentReport" class="report-preview">
          <div class="report-metrics">
            <div v-for="entry in metricEntries" :key="entry.label" class="report-metric">
              <span>{{ entry.label }}</span>
              <strong>{{ entry.value }}</strong>
            </div>
          </div>
          <pre>{{ currentReport.content }}</pre>
        </div>
        <n-empty v-else description="生成或打开一份报告开始查看" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NButton, NEmpty, NTag, useMessage } from 'naive-ui'
import type { GeneratedReport, ReportArtifact } from '@/types'
import { reportService } from '@/services/api'
import dayjs from 'dayjs'

const message = useMessage()
const history = ref<ReportArtifact[]>([])
const currentReport = ref<GeneratedReport | null>(null)
const generatingDaily = ref(false)
const generatingWeekly = ref(false)

const activePeriodLabel = computed(() => currentReport.value?.period || '未打开')
const metricEntries = computed(() => {
  const metrics = currentReport.value?.metrics || {}
  return Object.entries(metrics).map(([label, value]) => ({ label, value }))
})

const loadHistory = async () => {
  const res = await reportService.history(12)
  if (res.success && res.data) {
    history.value = res.data
  }
}

const generate = async (period: 'daily' | 'weekly') => {
  const loadingRef = period === 'daily' ? generatingDaily : generatingWeekly
  loadingRef.value = true
  try {
    const res = await reportService.generate(period)
    if (res.success && res.data) {
      currentReport.value = res.data
      await loadHistory()
      message.success(`${period === 'daily' ? '日报' : '周报'}已生成`)
    }
  } finally {
    loadingRef.value = false
  }
}

const openArtifact = async (item: ReportArtifact) => {
  const res = await reportService.readArtifact(item.path)
  if (res.success && res.data) {
    currentReport.value = {
      period: item.period,
      generatedAt: item.time || '',
      path: item.path,
      content: res.data,
      metrics: currentReport.value?.period === item.period ? currentReport.value.metrics : {}
    }
  }
}

const formatTime = (time?: string) => time ? dayjs(time).format('MM-DD HH:mm') : '未知'

onMounted(loadHistory)
</script>

<style scoped>
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.metric-card,
.history-card,
.report-metric {
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.05);
}

.metric-card {
  border-radius: 24px;
  padding: 18px;
}

.metric-card span,
.metric-card small,
.history-card p,
.history-card__meta span,
.report-metric span {
  color: var(--text-secondary);
}

.metric-card strong {
  display: block;
  margin: 6px 0;
  font-size: 1.8rem;
}

.report-actions,
.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-card {
  border-radius: 20px;
  padding: 16px;
  text-align: left;
  cursor: pointer;
}

.history-card__meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.report-preview {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.report-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.report-metric {
  border-radius: 18px;
  padding: 14px;
}

.report-metric strong {
  display: block;
  margin-top: 6px;
}

.report-preview pre {
  margin: 0;
  padding: 18px;
  border-radius: 20px;
  background: rgba(20, 16, 11, 0.78);
  color: #f8e9d4;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 520px;
  overflow: auto;
}

@media (max-width: 900px) {
  .metrics-grid,
  .report-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
