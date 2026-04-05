<template>
  <div class="snippets-page">
    <!-- 温暖背景装饰 -->
    <div class="warm-bg-decoration">
      <div class="gradient-orb orb-1"></div>
      <div class="gradient-orb orb-2"></div>
    </div>

    <div class="snippets-container">
      <!-- 左侧片段列表 -->
      <div class="snippets-sidebar">
        <div class="sidebar-header">
          <div class="header-title">
            <n-icon size="24" class="header-icon"><CodeIcon /></n-icon>
            <span>代码片段</span>
          </div>
          <n-button
            type="primary"
            size="small"
            @click="createNewSnippet"
            class="new-snippet-btn"
          >
            <template #icon>
              <n-icon><AddIcon /></n-icon>
            </template>
            新片段
          </n-button>
        </div>

        <!-- 搜索框 -->
        <div class="search-area">
          <n-input
            v-model:value="searchKeyword"
            placeholder="搜索片段..."
            clearable
            @clear="loadSnippets"
            @keyup.enter="searchSnippets"
          >
            <template #prefix>
              <n-icon><SearchIcon /></n-icon>
            </template>
          </n-input>
        </div>

        <div class="snippets-list">
          <div
            v-for="snippet in snippets"
            :key="snippet.id"
            :class="['snippet-item', { active: currentSnippet?.id === snippet.id }]"
            @click="selectSnippet(snippet)"
          >
            <div class="snippet-header">
              <span class="snippet-lang">{{ snippet.language || 'text' }}</span>
              <span class="snippet-title">{{ snippet.title }}</span>
            </div>
            <div class="snippet-meta">
              <span class="snippet-time">{{ formatTime(snippet.updateTime) }}</span>
            </div>
          </div>

          <div v-if="snippets.length === 0" class="empty-snippets">
            <n-icon size="40" class="empty-icon"><CodeIcon /></n-icon>
            <span>暂无代码片段</span>
          </div>
        </div>
      </div>

      <!-- 右侧编辑区域 -->
      <div class="snippet-editor">
        <div class="editor-header" v-if="currentSnippet">
          <n-input
            v-model:value="currentSnippet.title"
            placeholder="片段标题"
            class="title-input"
            @blur="saveSnippet"
          />
          <n-select
            v-model:value="currentSnippet.language"
            :options="languageOptions"
            placeholder="语言"
            class="lang-select"
            @update:value="saveSnippet"
          />
          <div class="header-actions">
            <n-button text @click="copyCode">
              <template #icon>
                <n-icon size="18"><CopyIcon /></n-icon>
              </template>
              复制
            </n-button>
            <n-button text @click="explainSnippet" :loading="explaining">
              <template #icon>
                <n-icon size="18"><SparklesIcon /></n-icon>
              </template>
              AI 解释
            </n-button>
            <n-button text @click="deleteCurrentSnippet">
              <template #icon>
                <n-icon size="18"><TrashIcon /></n-icon>
              </template>
            </n-button>
          </div>
        </div>

        <div class="editor-content" v-if="currentSnippet">
          <div class="code-editor">
            <n-input
              v-model:value="currentSnippet.code"
              type="textarea"
              placeholder="粘贴代码..."
              :autosize="{ minRows: 15 }"
              class="code-input"
              @blur="saveSnippet"
            />
          </div>

          <!-- 描述输入 -->
          <div class="description-area">
            <n-input
              v-model:value="currentSnippet.description"
              placeholder="添加描述..."
              @blur="saveSnippet"
            />
          </div>

          <!-- AI 解释显示 -->
          <div class="ai-explanation" v-if="aiExplanation">
            <div class="explanation-header">
              <n-icon size="16" class="sparkle-icon"><SparklesIcon /></n-icon>
              <span>AI 解释</span>
            </div>
            <div class="explanation-content" v-html="renderMarkdown(aiExplanation)"></div>
          </div>
        </div>

        <div class="empty-editor" v-else>
          <div class="empty-illustration">
            <n-icon size="60"><CodeIcon /></n-icon>
          </div>
          <div class="empty-title">选择或创建代码片段</div>
          <div class="empty-subtitle">左侧选择片段，或点击「新片段」创建</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  NInput,
  NButton,
  NIcon,
  NSelect,
  useMessage
} from 'naive-ui'
import {
  CodeSlashOutline as CodeIcon,
  AddOutline as AddIcon,
  SearchOutline as SearchIcon,
  CopyOutline as CopyIcon,
  SparklesOutline as SparklesIcon,
  TrashOutline as TrashIcon
} from '@vicons/ionicons5'
import type { CodeSnippet } from '@/types'
import { snippetService } from '@/services/api'
import { sanitizeHtml } from '@/utils/sanitize-html'
import dayjs from 'dayjs'
import { marked } from 'marked'

const message = useMessage()
const snippets = ref<CodeSnippet[]>([])
const currentSnippet = ref<CodeSnippet | null>(null)
const searchKeyword = ref('')
const explaining = ref(false)
const aiExplanation = ref('')

// 语言选项
const languageOptions = [
  { label: 'Java', value: 'java' },
  { label: 'Python', value: 'python' },
  { label: 'JavaScript', value: 'javascript' },
  { label: 'TypeScript', value: 'typescript' },
  { label: 'Vue', value: 'vue' },
  { label: 'HTML', value: 'html' },
  { label: 'CSS', value: 'css' },
  { label: 'SQL', value: 'sql' },
  { label: 'Go', value: 'go' },
  { label: 'Rust', value: 'rust' },
  { label: 'C++', value: 'cpp' },
  { label: 'Shell', value: 'shell' },
  { label: 'JSON', value: 'json' },
  { label: 'YAML', value: 'yaml' },
  { label: 'Markdown', value: 'markdown' },
  { label: '其他', value: 'text' }
]

// 配置 marked
marked.setOptions({
  breaks: true,
  gfm: true
})

// 渲染 Markdown
const renderMarkdown = (content: string): string => {
  if (!content) return ''
  try {
    return sanitizeHtml(marked.parse(content) as string)
  } catch {
    return sanitizeHtml(content)
  }
}

// 加载片段列表
const loadSnippets = async () => {
  try {
    const res = await snippetService.list()
    if (res.success && res.data) {
      snippets.value = res.data
    }
  } catch (error) {
    message.error('加载失败')
  }
}

// 搜索片段
const searchSnippets = async () => {
  if (!searchKeyword.value.trim()) {
    loadSnippets()
    return
  }
  try {
    const res = await snippetService.search(searchKeyword.value.trim())
    if (res.success && res.data) {
      snippets.value = res.data
    }
  } catch (error) {
    message.error('搜索失败')
  }
}

// 创建新片段
const createNewSnippet = async () => {
  try {
    const res = await snippetService.create({
      title: '新代码片段',
      code: '',
      language: 'java'
    })
    if (res.success && res.data) {
      snippets.value.unshift(res.data)
      currentSnippet.value = res.data
      aiExplanation.value = ''
      message.success('创建成功')
    }
  } catch (error) {
    message.error('创建失败')
  }
}

// 选择片段
const selectSnippet = (snippet: CodeSnippet) => {
  currentSnippet.value = { ...snippet }
  aiExplanation.value = ''
}

// 保存片段
const saveSnippet = async () => {
  if (!currentSnippet.value) return
  try {
    const res = await snippetService.update(currentSnippet.value.id, {
      title: currentSnippet.value.title,
      code: currentSnippet.value.code,
      language: currentSnippet.value.language,
      description: currentSnippet.value.description
    })
    if (res.success && res.data) {
      const idx = snippets.value.findIndex(s => s.id === currentSnippet.value!.id)
      if (idx >= 0) {
        snippets.value[idx] = res.data
      }
      message.success('已保存')
    }
  } catch (error) {
    message.error('保存失败')
  }
}

// 复制代码
const copyCode = async () => {
  if (!currentSnippet.value?.code) {
    message.warning('没有代码内容')
    return
  }
  try {
    await navigator.clipboard.writeText(currentSnippet.value.code)
    message.success('已复制到剪贴板')
  } catch {
    message.error('复制失败')
  }
}

// AI 解释
const explainSnippet = async () => {
  if (!currentSnippet.value || !currentSnippet.value.code) {
    message.warning('请先添加代码')
    return
  }
  explaining.value = true
  try {
    const res = await snippetService.explain(currentSnippet.value.id)
    if (res.success && res.data) {
      aiExplanation.value = res.data
      message.success('解释完成')
    }
  } catch (error) {
    message.error('解释失败')
  } finally {
    explaining.value = false
  }
}

// 删除片段
const deleteCurrentSnippet = async () => {
  if (!currentSnippet.value) return
  try {
    await snippetService.delete(currentSnippet.value.id)
    snippets.value = snippets.value.filter(s => s.id !== currentSnippet.value!.id)
    currentSnippet.value = snippets.value.length > 0 ? snippets.value[0] : null
    aiExplanation.value = ''
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

onMounted(() => {
  loadSnippets()
})
</script>

<style scoped>
.snippets-page {
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
  right: 20%;
}

.orb-2 {
  width: 200px;
  height: 200px;
  background: var(--primary-light);
  bottom: 0;
  left: -50px;
  animation-delay: -10s;
}

@keyframes float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(20px, -20px); }
}

.snippets-container {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 0;
  height: 100%;
  z-index: 10;
  position: relative;
}

/* 左侧列表 */
.snippets-sidebar {
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

.new-snippet-btn {
  background: var(--gradient-warm) !important;
  border: none !important;
  box-shadow: var(--shadow-md);
  font-weight: 600;
}

.search-area {
  padding: 12px 20px;
}

.snippets-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.snippet-item {
  padding: 14px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 6px;
  background: transparent;
  border: 1px solid transparent;
}

.snippet-item:hover {
  background: var(--bg-hover);
  border-color: var(--border-color);
}

.snippet-item.active {
  background: var(--bg-active);
  border-color: var(--primary-color);
  box-shadow: var(--shadow-glow);
}

.snippet-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.snippet-lang {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: rgba(249, 115, 22, 0.1);
  color: var(--primary-color);
  font-weight: 600;
  text-transform: uppercase;
}

.snippet-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.snippet-meta {
  margin-top: 6px;
}

.snippet-time {
  font-size: 12px;
  color: var(--text-muted);
}

.empty-snippets {
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

/* 编辑区域 */
.snippet-editor {
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
}

.editor-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-input {
  flex: 1;
}

.title-input :deep(.n-input__input-el) {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.lang-select {
  width: 120px;
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

.code-editor {
  flex: 1;
  min-height: 200px;
}

.code-input {
  height: 100%;
}

.code-input :deep(.n-input__textarea-el) {
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 14px;
  line-height: 1.5;
  color: var(--text-primary);
  background: var(--bg-input);
}

.code-input :deep(.n-input-wrapper) {
  background: var(--bg-input);
  border-radius: 12px;
}

.description-area {
  margin-top: 8px;
}

.ai-explanation {
  background: var(--bg-input);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px;
  margin-top: 16px;
}

.explanation-header {
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

.explanation-content {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.explanation-content :deep(code) {
  background: var(--bg-hover);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.explanation-content :deep(pre) {
  background: var(--bg-hover);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
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

/* 响应式 */
@media (max-width: 900px) {
  .snippets-container {
    grid-template-columns: 1fr;
  }

  .snippets-sidebar {
    display: none;
  }
}
</style>
