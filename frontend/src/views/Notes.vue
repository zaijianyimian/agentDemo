<template>
  <div class="notes-page">
    <!-- 温暖背景装饰 -->
    <div class="warm-bg-decoration">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
    </div>

    <div class="notes-container">
      <!-- 左侧笔记列表 -->
      <div class="notes-sidebar">
        <div class="sidebar-header">
          <div class="header-title">
            <n-icon size="24" class="header-icon"><NotebookIcon /></n-icon>
            <span>笔记</span>
          </div>
          <n-button
            type="primary"
            size="small"
            @click="createNewNote"
            class="new-note-btn"
          >
            <template #icon>
              <n-icon><AddIcon /></n-icon>
            </template>
            新笔记
          </n-button>
        </div>

        <div class="notes-list">
          <div class="search-toolbar">
            <n-input
              v-model:value="semanticQuery"
              placeholder="语义搜索笔记主题或内容"
              clearable
              @keyup.enter="runSemanticSearch"
            />
            <div class="search-toolbar__actions">
              <n-button tertiary size="small" @click="runSemanticSearch" :loading="searching">
                语义搜索
              </n-button>
              <n-button tertiary size="small" @click="reindexNotes" :loading="reindexing">
                重建向量
              </n-button>
            </div>
          </div>

          <div v-if="semanticHits.length" class="semantic-results">
            <button
              v-for="hit in semanticHits"
              :key="hit.noteId"
              type="button"
              class="semantic-hit"
              @click="openSemanticHit(hit.noteId)"
            >
              <div class="semantic-hit__head">
                <strong>{{ hit.title }}</strong>
                <span>{{ formatScore(hit.score) }}</span>
              </div>
              <p>{{ hit.contentSnippet || hit.aiSummary || '暂无摘要' }}</p>
            </button>
          </div>

          <div
            v-for="note in sortedNotes"
            :key="note.id"
            :class="['note-item', { active: currentNote?.id === note.id, pinned: note.isPinned }]"
            @click="selectNote(note)"
          >
            <div class="note-header">
              <n-icon size="16" class="pin-icon" v-if="note.isPinned">
                <PinIcon />
              </n-icon>
              <span class="note-title">{{ note.title }}</span>
            </div>
            <div class="note-meta">
              <span class="note-time">{{ formatTime(note.updateTime) }}</span>
              <n-button text size="tiny" @click.stop="togglePin(note)" class="pin-btn">
                <n-icon size="14">
                  <PinIcon v-if="!note.isPinned" />
                  <PinOffIcon v-else />
                </n-icon>
              </n-button>
            </div>
          </div>

          <div v-if="notes.length === 0" class="empty-notes">
            <n-icon size="40" class="empty-icon"><NotebookIcon /></n-icon>
            <span>暂无笔记</span>
          </div>
        </div>
      </div>

      <!-- 右侧编辑+预览区域 -->
      <div class="note-main">
        <div class="note-editor">
          <div class="editor-header" v-if="currentNote">
            <n-input
              v-model:value="currentNote.title"
              placeholder="标题"
              class="title-input"
              @blur="saveNote"
            />
            <div class="header-actions">
              <n-button text @click="summarizeNote" :loading="summarizing">
                <template #icon>
                  <n-icon size="18"><SparklesIcon /></n-icon>
                </template>
                AI 总结
              </n-button>
              <n-button text @click="deleteCurrentNote">
                <template #icon>
                  <n-icon size="18"><TrashIcon /></n-icon>
                </template>
              </n-button>
            </div>
          </div>

          <div class="editor-content" v-if="currentNote">
            <n-input
              v-model:value="currentNote.content"
              type="textarea"
              placeholder="开始写笔记...支持 Markdown 格式"
              :autosize="{ minRows: 10 }"
              class="content-input"
              @blur="saveNote"
            />

            <!-- AI 总结显示 -->
            <div class="ai-summary" v-if="currentNote.aiSummary">
              <div class="summary-header">
                <n-icon size="16" class="sparkle-icon"><SparklesIcon /></n-icon>
                <span>AI 概要</span>
              </div>
              <div class="summary-content">{{ currentNote.aiSummary }}</div>
            </div>
          </div>

          <div class="empty-editor" v-else>
            <div class="empty-illustration">
              <n-icon size="60"><NotebookIcon /></n-icon>
            </div>
            <div class="empty-title">选择或创建笔记</div>
            <div class="empty-subtitle">左侧列表选择笔记，或点击「新笔记」创建</div>
          </div>
        </div>

        <!-- Markdown 预览（底部显示）-->
        <div class="note-preview" v-if="currentNote && currentNote.content">
          <div class="preview-header">
            <n-icon size="18"><EyeIcon /></n-icon>
            <span>预览</span>
          </div>
          <div class="preview-content" v-html="renderMarkdown(currentNote.content)"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  NInput,
  NButton,
  NIcon,
  useMessage
} from 'naive-ui'
import {
  DocumentTextOutline as NotebookIcon,
  AddOutline as AddIcon,
  BookmarkOutline as PinIcon,
  BookmarkSharp as PinOffIcon,
  SparklesOutline as SparklesIcon,
  TrashOutline as TrashIcon,
  EyeOutline as EyeIcon
} from '@vicons/ionicons5'
import type { Note } from '@/types'
import { noteService } from '@/services/api'
import dayjs from 'dayjs'
import { marked } from 'marked'

const message = useMessage()
const notes = ref<Note[]>([])
const currentNote = ref<Note | null>(null)
const summarizing = ref(false)
const semanticQuery = ref('')
const semanticHits = ref<Array<{ noteId: number; title: string; contentSnippet: string; aiSummary?: string; score: number }>>([])
const searching = ref(false)
const reindexing = ref(false)

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true
})

// 按置顶和时间排序
const sortedNotes = computed(() => {
  return [...notes.value].sort((a, b) => {
    if (a.isPinned !== b.isPinned) return b.isPinned ? 1 : -1
    return new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime()
  })
})

// 渲染 Markdown
const renderMarkdown = (content: string): string => {
  if (!content) return ''
  try {
    return marked.parse(content) as string
  } catch {
    return content
  }
}

// 加载笔记列表
const loadNotes = async () => {
  try {
    const res = await noteService.list()
    if (res.success && res.data) {
      notes.value = res.data
    }
  } catch (error) {
    message.error('加载笔记失败')
  }
}

// 创建新笔记
const createNewNote = async () => {
  try {
    const res = await noteService.create({
      title: '新笔记',
      content: ''
    })
    if (res.success && res.data) {
      notes.value.unshift(res.data)
      currentNote.value = res.data
      message.success('创建成功')
    }
  } catch (error) {
    message.error('创建失败')
  }
}

// 选择笔记
const selectNote = (note: Note) => {
  currentNote.value = { ...note }
}

// 保存笔记
const saveNote = async () => {
  if (!currentNote.value) return
  try {
    const res = await noteService.update(currentNote.value.id, {
      title: currentNote.value.title,
      content: currentNote.value.content
    })
    if (res.success && res.data) {
      const idx = notes.value.findIndex(n => n.id === currentNote.value!.id)
      if (idx >= 0) {
        notes.value[idx] = res.data
      }
      message.success('已保存')
    }
  } catch (error) {
    message.error('保存失败')
  }
}

// 切换置顶
const togglePin = async (note: Note) => {
  try {
    const res = await noteService.togglePin(note.id)
    if (res.success && res.data) {
      const idx = notes.value.findIndex(n => n.id === note.id)
      if (idx >= 0) {
        notes.value[idx] = res.data
      }
      if (currentNote.value?.id === note.id) {
        currentNote.value.isPinned = res.data.isPinned
      }
      message.success(res.data.isPinned ? '已置顶' : '已取消置顶')
    }
  } catch (error) {
    message.error('操作失败')
  }
}

// AI 总结
const summarizeNote = async () => {
  if (!currentNote.value || !currentNote.value.content) {
    message.warning('请先编写笔记内容')
    return
  }
  summarizing.value = true
  try {
    const res = await noteService.summarize(currentNote.value.id)
    if (res.success && res.data) {
      currentNote.value.aiSummary = res.data.aiSummary
      const idx = notes.value.findIndex(n => n.id === currentNote.value!.id)
      if (idx >= 0) {
        notes.value[idx].aiSummary = res.data.aiSummary
      }
      message.success('总结完成')
    }
  } catch (error) {
    message.error('总结失败')
  } finally {
    summarizing.value = false
  }
}

const runSemanticSearch = async () => {
  if (!semanticQuery.value.trim()) {
    semanticHits.value = []
    return
  }
  searching.value = true
  try {
    const res = await noteService.semanticSearch(semanticQuery.value.trim(), 5)
    if (res.success && res.data) {
      semanticHits.value = res.data
      if (!res.data.length) {
        message.info('没有找到语义相近的笔记')
      }
    }
  } catch (error) {
    message.error('语义搜索失败')
  } finally {
    searching.value = false
  }
}

const reindexNotes = async () => {
  reindexing.value = true
  try {
    const res = await noteService.reindex()
    if (res.success) {
      message.success(`已重建 ${res.data || 0} 篇笔记的向量索引`)
    }
  } catch (error) {
    message.error('向量重建失败')
  } finally {
    reindexing.value = false
  }
}

const openSemanticHit = async (id: number) => {
  const existing = notes.value.find(item => item.id === id)
  if (existing) {
    currentNote.value = { ...existing }
    return
  }
  try {
    const res = await noteService.get(id)
    if (res.success && res.data) {
      currentNote.value = res.data
    }
  } catch (error) {
    message.error('打开笔记失败')
  }
}

// 删除笔记
const deleteCurrentNote = async () => {
  if (!currentNote.value) return
  try {
    await noteService.delete(currentNote.value.id)
    notes.value = notes.value.filter(n => n.id !== currentNote.value!.id)
    currentNote.value = notes.value.length > 0 ? notes.value[0] : null
    message.success('已删除')
  } catch (error) {
    message.error('删除失败')
  }
}

// 格式化时间
const formatTime = (time: string) => {
  const d = dayjs(time)
  const now = dayjs()
  if (now.diff(d, 'day') === 0) return d.format('HH:mm')
  if (now.diff(d, 'day') === 1) return '昨天'
  if (now.diff(d, 'day') < 7) return `${now.diff(d, 'day')}天前`
  return d.format('MM-DD')
}

const formatScore = (score = 0) => `相似度 ${(score * 100).toFixed(0)}%`

onMounted(() => {
  loadNotes()
})
</script>

<style scoped>
.notes-page {
  width: 100%;
  height: calc(100vh - 112px);
  position: relative;
  overflow: hidden;
}

.warm-bg-decoration {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  overflow: hidden;
  z-index: 0;
}

.gradient-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.12;
  animation: float 20s ease-in-out infinite;
}

.orb-1 {
  width: 300px;
  height: 300px;
  background: var(--primary-color);
  top: -50px;
  right: -50px;
}

.orb-2 {
  width: 200px;
  height: 200px;
  background: var(--primary-light);
  bottom: 0;
  left: 30%;
  animation-delay: -10s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(20px, -20px); }
}

.notes-container {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 0;
  height: 100%;
  width: 100%;
  z-index: 10;
  position: relative;
}

/* 左侧列表 */
.notes-sidebar {
  background: var(--bg-elevated);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 20px;
  border-bottom: 1px solid var(--border-color);
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

.header-icon {
  color: var(--primary-color);
}

.new-note-btn {
  background: var(--gradient-warm) !important;
  border: none !important;
  box-shadow: var(--shadow-md);
  font-weight: 600;
}

.notes-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.search-toolbar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}

.search-toolbar__actions {
  display: flex;
  gap: 8px;
}

.semantic-results {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 14px;
}

.semantic-hit {
  border: 1px solid rgba(245, 158, 11, 0.18);
  background: rgba(255, 247, 237, 0.56);
  border-radius: 18px;
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.semantic-hit:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 28px rgba(245, 158, 11, 0.12);
}

.semantic-hit__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 6px;
}

.semantic-hit p {
  margin: 0;
  color: #7c4a14;
  font-size: 13px;
  line-height: 1.55;
}

.note-item {
  padding: 14px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 6px;
  background: transparent;
  border: 1px solid transparent;
}

.note-item:hover {
  background: var(--bg-hover);
  border-color: var(--border-color);
}

.note-item.active {
  background: var(--bg-active);
  border-color: var(--primary-color);
  box-shadow: var(--shadow-glow);
}

.note-item.pinned {
  background: rgba(249, 115, 22, 0.05);
}

.note-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pin-icon {
  color: var(--primary-color);
}

.note-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.note-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
}

.note-time {
  font-size: 12px;
  color: var(--text-muted);
}

.pin-btn {
  opacity: 0;
  transition: opacity 0.2s;
}

.note-item:hover .pin-btn {
  opacity: 1;
}

.empty-notes {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: var(--text-muted);
}

.empty-icon {
  margin-bottom: 12px;
  color: var(--border-light);
}

/* 右侧主区域（编辑+预览）*/
.note-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  overflow: hidden;
}

/* 编辑区域 */
.note-editor {
  display: flex;
  flex-direction: column;
  flex: 1;
  background: var(--bg-card);
  overflow: hidden;
}

.editor-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 16px;
  flex-shrink: 0;
}

.title-input {
  flex: 1;
}

.title-input :deep(.n-input__input-el) {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.editor-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.content-input {
  flex: 1;
  min-height: 200px;
}

.content-input :deep(.n-input__textarea-el) {
  font-size: 15px;
  color: var(--text-primary);
  line-height: 1.7;
}

.ai-summary {
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px;
  flex-shrink: 0;
}

.summary-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 8px;
}

.sparkle-icon {
  color: var(--primary-color);
}

.summary-content {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.empty-editor {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
}

.empty-illustration {
  color: var(--border-light);
  margin-bottom: 20px;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.empty-subtitle {
  font-size: 14px;
}

/* 预览区域 */
.note-preview {
  background: var(--bg-base);
  border-top: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  max-height: 40%;
  flex-shrink: 0;
}

.preview-header {
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  background: var(--bg-elevated);
}

.preview-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  line-height: 1.7;
}

.preview-content :deep(h1),
.preview-content :deep(h2),
.preview-content :deep(h3) {
  margin: 16px 0 8px 0;
  font-weight: 600;
  color: var(--text-primary);
}

.preview-content :deep(h1) { font-size: 1.4em; }
.preview-content :deep(h2) { font-size: 1.2em; }

.preview-content :deep(p) {
  margin: 8px 0;
  color: var(--text-primary);
}

.preview-content :deep(ul),
.preview-content :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.preview-content :deep(li) {
  margin: 4px 0;
  color: var(--text-primary);
}

.preview-content :deep(code) {
  background: var(--bg-input);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 0.9em;
  color: var(--primary-color);
}

.preview-content :deep(pre) {
  background: var(--bg-input);
  padding: 12px 16px;
  border-radius: 12px;
  margin: 12px 0;
  overflow-x: auto;
  border: 1px solid var(--border-color);
}

.preview-content :deep(pre code) {
  background: transparent;
  padding: 0;
  color: var(--text-primary);
}

.preview-content :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 3px solid var(--primary-color);
  background: var(--bg-input);
  border-radius: 8px;
}

/* 响应式 */
@media (max-width: 900px) {
  .notes-container {
    grid-template-columns: 1fr;
  }

  .notes-sidebar {
    display: none;
  }
}

@media (min-width: 1400px) {
  .notes-container {
    grid-template-columns: 300px 1fr;
  }

  .note-preview {
    max-height: 50%;
  }
}
</style>
