<template>
  <div class="schedule-share-page">
    <div class="share-container">
      <div class="share-header">
        <n-icon size="28" class="header-icon"><CalendarIcon /></n-icon>
        <h1>日程分享</h1>
        <span class="share-date">{{ date }}</span>
      </div>

      <div v-if="loading" class="share-loading">
        <n-spin size="large" />
        <span>加载中...</span>
      </div>

      <div v-else-if="content" class="share-content">
        <div class="markdown-body" v-html="renderedContent"></div>
      </div>

      <div v-else class="share-empty">
        <n-icon size="48"><CalendarIcon /></n-icon>
        <p>该日暂无日程安排</p>
      </div>

      <div class="share-footer">
        <span>由 AI Agent 提供</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 日程分享只读页面：根据路由参数日期拉取对应日程 Markdown 供访客查看。
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { NIcon, NSpin } from 'naive-ui'
import { CalendarOutline as CalendarIcon } from '@vicons/ionicons5'
import { scheduleService } from '@/services/api/schedule'
import { renderMarkdown } from '@/utils/markdown'

const route = useRoute()
const date = route.params.date as string
const content = ref<string | null>(null)
const loading = ref(true)

const renderedContent = computed(() => {
  return content.value ? renderMarkdown(content.value) : ''
})

onMounted(async () => {
  try {
    const res = await scheduleService.getSharedSchedule(date)
    if (res.success && res.data && res.data.found) {
      content.value = res.data.content
    }
  } catch {
    // share page fails silently
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.schedule-share-page {
  min-height: 100vh;
  background: var(--bg-base, #FFFBF0);
  position: relative;
  overflow: hidden;
}

.share-container {
  max-width: 720px;
  margin: 0 auto;
  padding: 40px 24px;
  position: relative;
  z-index: 10;
}

.share-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 32px;
}

.share-header h1 {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary, #431407);
  margin: 0;
}

.header-icon { color: #ea580c; }

.share-date {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary, #78350f);
  background: rgba(234, 88, 12, 0.1);
  padding: 2px 12px;
  border-radius: 20px;
}

.share-content {
  background: var(--bg-card, #FFFBF0);
  border: 2px solid var(--border-light, #FEF3C7);
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 4px 20px rgba(234, 88, 12, 0.08);
}

.markdown-body {
  line-height: 1.8;
  color: var(--text-primary, #431407);
}

.markdown-body :deep(h1) {
  font-size: 1.6rem;
  font-weight: 700;
  margin-bottom: 16px;
  color: var(--text-primary, #431407);
  border-bottom: 2px solid var(--border-light, #FEF3C7);
  padding-bottom: 10px;
}

.markdown-body :deep(h2) {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 20px 0 12px;
  color: var(--text-primary, #431407);
}

.markdown-body :deep(ul) { padding-left: 20px; margin: 8px 0; }
.markdown-body :deep(li) { margin: 6px 0; line-height: 1.7; }
.markdown-body :deep(strong) { color: #ea580c; font-weight: 600; }
.markdown-body :deep(hr) { border: none; border-top: 1px solid var(--border-light, #FEF3C7); margin: 16px 0; }

.share-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  gap: 16px;
  color: var(--text-muted, #E5C07B);
  font-size: 16px;
}

.share-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  gap: 16px;
  color: var(--text-muted, #E5C07B);
  opacity: 0.5;
}

.share-empty p { font-size: 16px; margin: 0; }

.share-footer {
  text-align: center;
  margin-top: 32px;
  color: var(--text-muted, #E5C07B);
  font-size: 12px;
  opacity: 0.6;
}
</style>
