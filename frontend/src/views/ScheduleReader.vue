<template>
  <div class="schedule-reader-page">
    <div class="reader-container">
      <!-- 左侧文件列表 -->
      <div class="file-sidebar">
        <div class="sidebar-header">
          <div class="header-title">
            <n-icon size="24" class="header-icon"><ReaderIcon /></n-icon>
            <span>日程文件</span>
          </div>
          <n-button tertiary size="small" @click="loadFiles" :loading="loading">
            <template #icon><n-icon><RefreshIcon /></n-icon></template>
          </n-button>
        </div>

        <div class="file-list">
          <div
            v-for="file in sortedFiles"
            :key="file.name"
            class="file-item"
            :class="{ active: selectedFile === file.name }"
            @click="selectFile(file.name)"
          >
            <div class="file-date">
              <n-icon size="16"><CalendarIcon /></n-icon>
              <span>{{ file.label }}</span>
            </div>
          </div>
          <n-empty v-if="!loading && sortedFiles.length === 0" description="暂无日程文件" />
        </div>
      </div>

      <!-- 右侧 Markdown 阅读区 -->
      <div class="reader-main">
        <div v-if="fileContent" class="content-area">
          <div class="content-header">
            <div class="content-title">
              <n-icon size="20" class="title-icon"><DocumentIcon /></n-icon>
              <span>{{ currentFileName }}</span>
            </div>
            <n-button size="small" @click="showShareModal" class="share-btn">
              <template #icon><n-icon><ShareIcon /></n-icon></template>
              分享
            </n-button>
          </div>
          <n-scrollbar class="content-scroll">
            <div class="markdown-body" v-html="renderedContent"></div>
          </n-scrollbar>
        </div>

        <div v-else class="empty-reader">
          <div class="empty-illustration">
            <n-icon size="60"><ReaderIcon /></n-icon>
          </div>
          <div class="empty-title">选择日程文件</div>
          <div class="empty-subtitle">从左侧列表选择一个文件查看内容</div>
        </div>
      </div>
    </div>

    <!-- 分享二维码弹窗 -->
    <n-modal v-model:show="shareModalVisible" preset="card" style="width: 360px" :bordered="false" class="share-modal">
      <div class="qr-modal-content">
        <h3 class="qr-title">扫码查看日程</h3>
        <p class="qr-date">{{ selectedFileDate }}</p>
        <div class="qr-wrapper">
          <canvas ref="qrCanvas" class="qr-canvas"></canvas>
        </div>
        <p class="qr-hint">扫描二维码在手机端查看日程详情</p>
        <n-button size="small" tertiary @click="copyShareLink" class="copy-btn">
          <template #icon><n-icon><CopyIcon /></n-icon></template>
          复制链接
        </n-button>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
/**
 * 日程 Markdown 阅读页面：浏览后端生成的日程文件，提供分享二维码。
 */
import { ref, computed, onMounted, nextTick } from 'vue'
import { NIcon, NButton, NScrollbar, NEmpty, NModal, useMessage } from 'naive-ui'
import {
  ReaderOutline as ReaderIcon,
  RefreshOutline as RefreshIcon,
  CalendarOutline as CalendarIcon,
  DocumentTextOutline as DocumentIcon,
  ShareSocialOutline as ShareIcon,
  CopyOutline as CopyIcon
} from '@vicons/ionicons5'
import QRCode from 'qrcode'
import { scheduleService } from '@/services/api/schedule'
import { renderMarkdown } from '@/utils/markdown'

const message = useMessage()
const loading = ref(false)
const files = ref<string[]>([])
const selectedFile = ref<string | null>(null)
const fileContent = ref<string | null>(null)
const shareModalVisible = ref(false)
const qrCanvas = ref<HTMLCanvasElement | null>(null)

interface FileEntry {
  name: string
  date: string
  label: string
}

const sortedFiles = computed<FileEntry[]>(() => {
  return files.value
    .map(name => {
      const match = name.match(/schedule-(\d{4}-\d{2}-\d{2})\.md/)
      const date = match ? match[1] : ''
      return { name, date, label: date || name }
    })
    .sort((a, b) => b.date.localeCompare(a.date))
})

const currentFileName = computed(() => {
  const match = selectedFile.value?.match(/schedule-(\d{4}-\d{2}-\d{2})\.md/)
  return match ? `日程 - ${match[1]}` : selectedFile.value || ''
})

const selectedFileDate = computed(() => {
  const match = selectedFile.value?.match(/schedule-(\d{4}-\d{2}-\d{2})\.md/)
  return match ? match[1] : ''
})

const shareUrl = computed(() => {
  if (!selectedFileDate.value) return ''
  return `${window.location.origin}/schedule-share/${selectedFileDate.value}`
})

const renderedContent = computed(() => {
  return fileContent.value ? renderMarkdown(fileContent.value) : ''
})

const loadFiles = async () => {
  loading.value = true
  try {
    const res = await scheduleService.listFiles()
    files.value = res
  } catch {
    message.error('加载文件列表失败')
  } finally {
    loading.value = false
  }
}

const selectFile = async (fileName: string) => {
  selectedFile.value = fileName
  try {
    const res = await scheduleService.getFileByName(fileName)
    if (res.success && res.data) {
      fileContent.value = res.data.content || null
    }
  } catch {
    message.error('加载文件内容失败')
    fileContent.value = null
  }
}

/** 打开分享弹窗并基于当前选中文件渲染二维码到 canvas。 */
const showShareModal = async () => {
  shareModalVisible.value = true
  await nextTick()
  if (qrCanvas.value && shareUrl.value) {
    try {
      await QRCode.toCanvas(qrCanvas.value, shareUrl.value, {
        width: 200,
        margin: 2,
        color: { dark: '#431407', light: '#FFFBF0' }
      })
    } catch {
      message.error('生成二维码失败')
    }
  }
}

const copyShareLink = async () => {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    message.success('链接已复制')
  } catch {
    message.error('复制失败')
  }
}

onMounted(() => {
  loadFiles()
})
</script>

<style scoped>
.schedule-reader-page {
  width: 100%;
  height: calc(100vh - 112px);
  position: relative;
  overflow: hidden;
}

.reader-container {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 0;
  height: 100%;
  z-index: 10;
  position: relative;
}

/* 左侧文件列表 */
.file-sidebar {
  background: var(--bg-card);
  border-right: 2px solid var(--border-light);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 2px solid var(--border-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-icon { color: var(--primary-color); }

.file-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.file-item {
  padding: 14px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 6px;
  background: transparent;
  border: 1px solid transparent;
}

.file-item:hover {
  background: var(--bg-hover);
  border-color: var(--border-color);
}

.file-item.active {
  background: var(--bg-active);
  border-color: var(--primary-color);
  box-shadow: var(--shadow-glow);
}

.file-date {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

.file-item.active .file-date { color: var(--primary-color); }

/* 右侧阅读区 */
.reader-main {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content-area {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.content-header {
  padding: 16px 24px;
  border-bottom: 2px solid var(--border-light);
  background: var(--bg-card);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.content-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.title-icon { color: var(--primary-color); }

.share-btn {
  background: var(--gradient-warm) !important;
  border: none !important;
  color: white !important;
  font-weight: 600;
}

.content-scroll { flex: 1; }

.markdown-body {
  padding: 24px;
  line-height: 1.8;
  color: var(--text-primary);
}

.markdown-body :deep(h1) {
  font-size: 1.6rem;
  font-weight: 700;
  margin-bottom: 16px;
  color: var(--text-primary);
  border-bottom: 2px solid var(--border-light);
  padding-bottom: 10px;
}

.markdown-body :deep(h2) {
  font-size: 1.25rem;
  font-weight: 600;
  margin: 20px 0 12px;
  color: var(--text-primary);
}

.markdown-body :deep(ul) { padding-left: 20px; margin: 8px 0; }
.markdown-body :deep(li) { margin: 6px 0; line-height: 1.7; }
.markdown-body :deep(strong) { color: var(--primary-color); font-weight: 600; }
.markdown-body :deep(hr) { border: none; border-top: 1px solid var(--border-light); margin: 16px 0; }

/* 空状态 */
.empty-reader {
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

/* 二维码弹窗 */
.qr-modal-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.qr-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.qr-date {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
  background: rgba(234, 88, 12, 0.1);
  padding: 2px 14px;
  border-radius: 20px;
}

.qr-wrapper {
  padding: 16px;
  background: #FFFBF0;
  border-radius: 12px;
  border: 2px solid var(--border-light);
}

.qr-canvas {
  display: block;
  border-radius: 8px;
}

.qr-hint {
  font-size: 13px;
  color: var(--text-muted);
  margin: 4px 0 0;
}

.copy-btn {
  margin-top: 4px;
}
</style>
