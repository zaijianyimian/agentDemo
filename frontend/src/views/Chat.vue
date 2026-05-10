<template>
  <div class="chat-page">
    <!-- 会话列表侧边栏 - 可折叠 -->
    <div class="session-sidebar" :class="{ collapsed: isListCollapsed }">
      <!-- 侧边栏内容 -->
      <div class="sidebar-content">
        <!-- 新对话按钮 -->
        <button class="new-chat-btn" @click="createNewSession">
          <n-icon size="18"><AddIcon /></n-icon>
          <span>新对话</span>
        </button>

        <!-- 会话列表 -->
        <div class="session-list" ref="sessionListRef">
          <div
            v-for="session in sessions"
            :key="session.id"
            :class="['session-item', { active: currentSession?.id === session.id }]"
            @click="switchSession(session)"
          >
            <div class="session-icon">
              <n-icon size="14"><ChatbubbleIcon /></n-icon>
            </div>
            <div class="session-info">
              <div class="session-title">{{ session.title }}</div>
              <div class="session-meta">{{ formatSessionTime(session.lastMessageTime) }}</div>
            </div>
            <n-dropdown
              trigger="click"
              :options="getSessionMenuOptions()"
              @select="handleSessionMenu($event, session)"
            >
              <button class="session-menu-btn" @click.stop>
                <n-icon size="14"><MoreIcon /></n-icon>
              </button>
            </n-dropdown>
          </div>

          <div v-if="sessions.length === 0" class="empty-sessions">
            <n-icon size="32" class="empty-icon"><ChatbubbleIcon /></n-icon>
            <span>暂无会话</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 侧边栏折叠按钮 - 位于分界线上 -->
    <button
      class="divider-toggle-btn"
      @click="toggleSidebar"
      :title="isListCollapsed ? '展开列表' : '收起列表'"
      :style="{ left: isListCollapsed ? '48px' : '260px' }"
    >
      <n-icon size="18">
        <ChevronBackIcon v-if="!isListCollapsed" />
        <ChevronForwardIcon v-else />
      </n-icon>
    </button>

    <!-- 聊天主区域 -->
    <div class="chat-main">
      <!-- 消息列表 -->
      <div class="message-list" ref="messageList">
        <!-- 欢迎区域 -->
        <div v-if="messages.length === 0" class="welcome-area">
          <div class="welcome-logo">
            <img src="/resource/logo.png" alt="AI" class="ai-logo-img" @error="handleLogoError" />
            <div class="logo-glow"></div>
          </div>
          <h1 class="welcome-title">你好，有什么可以帮助你的？</h1>
          <p class="welcome-subtitle">开始一段新的对话</p>
        </div>

        <!-- 消息气泡 -->
        <div
          v-for="(msg, index) in messages"
          :key="msg.id"
          :class="['message-wrapper', msg.role]"
          :ref="el => setMessageRef(el, index)"
        >
          <!-- 用户消息 -->
          <div v-if="msg.role === 'user'" class="user-bubble">
            <div class="bubble-content">{{ msg.content }}</div>
            <div class="bubble-time">{{ formatTime(msg.timestamp) }}</div>
          </div>

          <!-- AI消息 -->
          <div v-else class="ai-bubble">
            <div class="ai-avatar">
              <img src="/resource/logo.png" alt="AI" class="ai-logo-small" @error="handleLogoError" />
              <div v-if="msg.isStreaming" class="ai-thinking-glow"></div>
            </div>
            <div class="ai-content-wrapper">
              <div class="ai-content">
                <div v-if="!msg.content && msg.isStreaming" class="thinking-indicator">
                  <span class="thinking-dot"></span>
                  <span class="thinking-dot"></span>
                  <span class="thinking-dot"></span>
                </div>
                <div v-else class="markdown-content" v-html="renderMarkdown(msg.content)"></div>
                <span v-if="msg.isStreaming && msg.content" class="cursor-blink"></span>
              </div>
              <div class="ai-footer">
                <span class="bubble-time">{{ formatTime(msg.timestamp) }}</span>
                <div class="ai-actions" v-if="msg.content">
                  <button class="action-btn" @click="copyMessage(msg.content)" title="复制">
                    <n-icon size="14"><CopyIcon /></n-icon>
                  </button>
                  <button class="action-btn" @click="captureMessage('note', msg)" title="转笔记">
                    <n-icon size="14"><DocumentIcon /></n-icon>
                  </button>
                  <button class="action-btn" @click="captureMessage('task', msg)" title="转任务">
                    <n-icon size="14"><CheckmarkIcon /></n-icon>
                  </button>
                  <button class="action-btn" @click="captureMessage('memory', msg)" title="存记忆">
                    <n-icon size="14"><BookmarksIcon /></n-icon>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部输入区 - 悬浮胶囊 -->
      <div class="input-area">
        <div class="input-capsule" :class="{ 'has-content': inputText.trim(), 'is-focused': isInputFocused }">
          <textarea
            ref="inputTextarea"
            v-model="inputText"
            class="chat-textarea"
            placeholder="输入指令... (Ctrl + Enter 发送)"
            :disabled="loading"
            @keydown="handleKeydown"
            @input="autoResize"
            @focus="isInputFocused = true"
            @blur="isInputFocused = false"
          ></textarea>

          <div class="input-right-actions">
            <!-- 模型选择器 -->
            <ModelSelector
              :is-mobile="isMobile"
              @select="handleModelSelect"
            />
            <button
              class="voice-btn"
              title="语音输入"
              @click="toggleVoiceInput"
            >
              <n-icon size="18"><MicIcon /></n-icon>
            </button>
            <button
              v-if="loading"
              class="send-btn stop"
              @click="stopStreaming"
              title="停止生成"
            >
              <n-icon size="18"><StopIcon /></n-icon>
            </button>
            <button
              v-else
              class="send-btn"
              :class="{ active: inputText.trim() }"
              :disabled="!inputText.trim()"
              @click="sendMessage"
              title="发送消息"
            >
              <n-icon size="18"><SendIcon /></n-icon>
            </button>
          </div>
        </div>

        <!-- 模式选择 -->
        <div class="input-hints">
          <div class="mode-pills">
            <button
              v-for="mode in chatModes"
              :key="mode.value"
              :class="['mode-pill', { active: chatMode === mode.value }]"
              @click="chatMode = mode.value"
              :disabled="loading"
            >
              {{ mode.label }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑标题对话框 -->
    <n-modal v-model:show="showEditModal" preset="dialog" title="编辑会话标题">
      <n-input v-model:value="editTitle" placeholder="输入新标题" />
      <template #action>
        <n-button @click="showEditModal = false">取消</n-button>
        <n-button type="primary" @click="saveSessionTitle">保存</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted, computed } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  NInput,
  NButton,
  NIcon,
  NDropdown,
  NModal,
  useMessage
} from 'naive-ui'
import {
  SendOutline as SendIcon,
  StopCircleOutline as StopIcon,
  AddOutline as AddIcon,
  ChatbubbleOutline as ChatbubbleIcon,
  EllipsisHorizontal as MoreIcon,
  CopyOutline as CopyIcon,
  DocumentTextOutline as DocumentIcon,
  CheckmarkCircleOutline as CheckmarkIcon,
  BookmarksOutline as BookmarksIcon,
  MicOutline as MicIcon,
  ChevronBackOutline as ChevronBackIcon,
  ChevronForwardOutline as ChevronForwardIcon
} from '@vicons/ionicons5'
import type { ChatMessage, ChatSession, AiModelConfig } from '@/types'
import { chatActionService, chatHistoryService } from '@/services/api'
import { fetchWithAuth } from '@/services/auth-fetch'
import { renderMarkdown } from '@/utils/markdown'
import { formatTime, formatSessionTime } from '@/utils/date-format'
import { animate as motionAnimate, type JSAnimation } from 'motion'
import ModelSelector from '@/components/ModelSelector.vue'

// Type-safe animate function
const animate = motionAnimate as (elements: any, keyframes: any, options?: any) => JSAnimation<any>

// 聊天模式选项
const chatModes = [
  { label: '流式', value: 'stream' },
  { label: '普通', value: 'normal' },
  { label: 'Agent', value: 'mcp' }
]

const parseSseEvents = (rawEvent: string): string[] => {
  const lines = rawEvent.replace(/\r/g, '').split('\n')
  const chunks: string[] = []
  let currentData = ''
  for (const line of lines) {
    if (line.startsWith('data:')) {
      const data = line.slice(5).trim()
      if (data === '[DONE]') continue
      // Each data line is a separate chunk for typewriter effect
      if (currentData) chunks.push(currentData)
      currentData = data
    } else if (!line.startsWith(':') && !line.startsWith('event:') && !line.startsWith('id:') && !line.startsWith('retry:') && line.trim()) {
      // Non-standard SSE: content without data prefix
      currentData += line
    } else if (!line.trim() && currentData) {
      // Empty line signals end of current data block
      chunks.push(currentData)
      currentData = ''
    }
  }
  // Don't forget remaining data
  if (currentData) chunks.push(currentData)
  return chunks
}

const message = useMessage()
const inputText = ref('')
const isInputFocused = ref(false)
const chatMode = ref('stream')
const loading = ref(false)
const messages = ref<ChatMessage[]>([])
const sessions = ref<ChatSession[]>([])
const currentSession = ref<ChatSession | null>(null)
const messageList = ref<HTMLElement | null>(null)
const sessionListRef = ref<HTMLElement | null>(null)
const inputTextarea = ref<HTMLTextAreaElement | null>(null)
const messageRefs = ref<Map<number, HTMLElement>>(new Map())
let abortController: AbortController | null = null

// 会话列表折叠状态
const isListCollapsed = ref(false)

// 模型选择
const selectedModel = ref<AiModelConfig | null>(null)

// 响应式检测
const isMobile = computed(() => window.innerWidth < 768)

// 处理模型选择
const handleModelSelect = (model: AiModelConfig) => {
  selectedModel.value = model
}

// 切换侧边栏折叠状态
const toggleSidebar = () => {
  isListCollapsed.value = !isListCollapsed.value
}

const showEditModal = ref(false)
const editTitle = ref('')

// Logo 加载失败处理
const handleLogoError = (e: Event) => {
  const target = e.target as HTMLImageElement
  target.style.display = 'none'
}

// 设置消息元素引用
const setMessageRef = (el: any, index: number) => {
  if (el) messageRefs.value.set(index, el)
}

// 自动调整输入框高度
const autoResize = () => {
  if (inputTextarea.value) {
    inputTextarea.value.style.height = 'auto'
    inputTextarea.value.style.height = Math.min(inputTextarea.value.scrollHeight, 200) + 'px'
  }
}

// 加载会话列表
const loadSessions = async () => {
  try {
    const res = await chatHistoryService.getSessions()
    if (res.success && res.data) sessions.value = res.data
  } catch (error) {
    console.error('加载会话失败', error)
  }
}

// 创建新会话
const createNewSession = async () => {
  try {
    const res = await chatHistoryService.createSession()
    if (res.success && res.data) {
      sessions.value.unshift(res.data)
      switchSession(res.data)
      message.success('创建新会话成功')
    }
  } catch (error) {
    message.error('创建会话失败')
  }
}

// 切换会话 - 自动清理空会话
const switchSession = async (session: ChatSession) => {
  // 如果当前会话存在且没有消息，自动删除空会话
  if (currentSession.value && messages.value.length === 0 && currentSession.value.id !== session.id) {
    try {
      await chatHistoryService.deleteSession(currentSession.value.id)
      sessions.value = sessions.value.filter(s => s.id !== currentSession.value!.id)
      // 不显示删除提示，静默清理
    } catch (error) {
      // 删除失败不影响切换
      console.error('删除空会话失败', error)
    }
  }

  currentSession.value = session
  messages.value = []
  try {
    const res = await chatHistoryService.getSessionMessages(session.id)
    if (res.success && res.data) {
      messages.value = res.data.map(m => ({
        id: m.id.toString(),
        role: m.role,
        content: m.content,
        timestamp: m.createTime,
        isStreaming: false
      }))
      await nextTick()
      scrollToBottom()
      // 入场动画
      runMessageAnimations()
    }
  } catch (error) {
    console.error('加载消息失败', error)
  }
}

// 消息入场动画
const runMessageAnimations = async () => {
  await nextTick()
  messageRefs.value.forEach((el, index) => {
    setTimeout(() => {
      animate(el, { opacity: [0, 1], transform: ['translateY(20px) scale(0.95)', 'translateY(0) scale(1)'] }, { duration: 0.4, easing: [0.34, 1.56, 0.64, 1] })
    }, index * 60)
  })
}

// 会话菜单选项
const getSessionMenuOptions = () => [
  { label: '编辑标题', key: 'edit' },
  { label: '清空消息', key: 'clear' },
  { label: '删除会话', key: 'delete' }
]

// 处理会话菜单
const handleSessionMenu = async (key: string, session: ChatSession) => {
  try {
    switch (key) {
      case 'edit':
        currentSession.value = session
        editTitle.value = session.title
        showEditModal.value = true
        break
      case 'clear':
        await chatHistoryService.clearSessionMessages(session.id)
        session.messageCount = 0
        if (currentSession.value?.id === session.id) messages.value = []
        message.success('已清空消息')
        break
      case 'delete':
        await chatHistoryService.deleteSession(session.id)
        sessions.value = sessions.value.filter(s => s.id !== session.id)
        if (currentSession.value?.id === session.id) {
          currentSession.value = null
          messages.value = []
        }
        message.success('已删除会话')
        break
    }
  } catch (error) {
    message.error('操作失败')
  }
}

// 保存标题
const saveSessionTitle = async () => {
  if (!currentSession.value) return
  try {
    const res = await chatHistoryService.updateSessionTitle(currentSession.value.id, editTitle.value)
    if (res.success && res.data) {
      currentSession.value.title = editTitle.value
      const idx = sessions.value.findIndex(s => s.id === currentSession.value!.id)
      if (idx >= 0) sessions.value[idx].title = editTitle.value
      message.success('标题已更新')
    }
  } catch (error) {
    message.error('保存失败')
  }
  showEditModal.value = false
}

// 发送消息
const sendMessage = async () => {
  if (!inputText.value.trim() || loading.value) return

  if (!currentSession.value) {
    try {
      const res = await chatHistoryService.createSession()
      if (res.success && res.data) {
        sessions.value.unshift(res.data)
        currentSession.value = res.data
      } else {
        message.error('无法创建会话')
        return
      }
    } catch (error) {
      message.error('创建会话失败')
      return
    }
  }

  const userMessage: ChatMessage = {
    id: Date.now().toString(),
    role: 'user',
    content: inputText.value,
    timestamp: new Date().toISOString()
  }

  messages.value.push(userMessage)
  const queryText = inputText.value
  inputText.value = ''
  loading.value = true

  if (inputTextarea.value) {
    inputTextarea.value.style.height = 'auto'
  }

  await nextTick()
  scrollToBottom()
  animateNewMessage(messages.value.length - 1)

  const assistantMessage: ChatMessage = {
    id: (Date.now() + 1).toString(),
    role: 'assistant',
    content: '',
    timestamp: new Date().toISOString(),
    isStreaming: true
  }
  messages.value.push(assistantMessage)

  await nextTick()
  scrollToBottom()
  animateNewMessage(messages.value.length - 1)

  try {
    if (chatMode.value === 'stream') {
      await streamChat(queryText, assistantMessage)
    } else {
      await normalChat(queryText, assistantMessage)
    }
    currentSession.value.messageCount += 2
    currentSession.value.lastMessageTime = new Date().toISOString()
  } catch (error: any) {
    if (error.name !== 'AbortError') {
      message.error('发送失败，请重试')
      assistantMessage.content = '抱歉，发生了错误，请重试。'
    }
  } finally {
    loading.value = false
    assistantMessage.isStreaming = false
    await nextTick()
    scrollToBottom()
  }
}

// 新消息入场动画 - enhanced with spring bounce
const animateNewMessage = (index: number) => {
  const el = messageRefs.value.get(index)
  if (el) {
    // Spring bounce animation
    animate(
      el,
      {
        opacity: [0, 1],
        transform: ['translateY(20px) scale(0.92)', 'translateY(-4px) scale(1.02)', 'translateY(0) scale(1)']
      },
      {
        duration: 0.5,
        easing: [0.34, 1.56, 0.64, 1]
      }
    )
  }
}

// 流式聊天
const streamChat = async (query: string, messageObj: ChatMessage) => {
  abortController = new AbortController()
  const apiPath = chatMode.value === 'mcp'
    ? `/api/mcp/agent/chat/stream?message=${encodeURIComponent(query)}`
    : `/api/chat/stream/session?message=${encodeURIComponent(query)}&sessionId=${currentSession.value!.id}`

  try {
    const response = await fetchWithAuth(apiPath, {
      signal: abortController.signal,
      headers: { 'Accept': 'text/event-stream', 'Cache-Control': 'no-cache', 'Connection': 'keep-alive' }
    })

    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)

    const reader = response.body?.getReader()
    if (!reader) throw new Error('No reader available')

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() || ''

      for (const event of events) {
        if (!event.trim()) continue
        const chunks = parseSseEvents(event)
        for (const chunk of chunks) {
          // Typewriter effect: add small delay between chunks
          messageObj.content += chunk
          await nextTick()
          scrollToBottom()
          // Small delay for typewriter feel (optional, can be removed for instant streaming)
          await new Promise(resolve => setTimeout(resolve, 8))
        }
      }
    }

    if (buffer.trim()) {
      const chunks = parseSseEvents(buffer)
      for (const chunk of chunks) messageObj.content += chunk
    }
  } catch (error: any) {
    if (error.name !== 'AbortError') throw error
  }
}

// 普通聊天
const normalChat = async (query: string, messageObj: ChatMessage) => {
  const apiPath = chatMode.value === 'mcp'
    ? `/api/mcp/agent/chat?message=${encodeURIComponent(query)}`
    : `/api/chat/complete/session?message=${encodeURIComponent(query)}&sessionId=${currentSession.value!.id}`

  const response = await fetchWithAuth(apiPath)
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`)
  messageObj.content = await response.text()
}

// 停止流式响应
const stopStreaming = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
    loading.value = false
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg?.isStreaming) {
      lastMsg.isStreaming = false
      if (!lastMsg.content) lastMsg.content = '[已停止]'
    }
  }
}

// 复制消息
const copyMessage = async (content: string) => {
  try {
    await navigator.clipboard.writeText(content)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

// 转换消息
const captureMessage = async (target: 'note' | 'task' | 'schedule' | 'memory', msg: ChatMessage) => {
  try {
    const payload = {
      sessionId: currentSession.value?.id,
      content: msg.content,
      role: msg.role,
      titleHint: currentSession.value?.title
    }
    const action = target === 'note' ? chatActionService.createNote
      : target === 'task' ? chatActionService.createTask
      : target === 'schedule' ? chatActionService.createSchedule
      : chatActionService.storeMemory
    const res = await action(payload)
    if (res.success && res.data) message.success(res.data.message || '处理成功')
  } catch (error) {
    message.error('转换失败')
  }
}

// 键盘事件
const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && e.ctrlKey) {
    e.preventDefault()
    loading.value ? stopStreaming() : sendMessage()
  }
}

// 语音输入（占位）
const isVoiceActive = ref(false)
const toggleVoiceInput = () => {
  isVoiceActive.value = !isVoiceActive.value
  if (isVoiceActive.value) {
    message.info('语音输入功能开发中...')
    setTimeout(() => {
      isVoiceActive.value = false
    }, 1500)
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight
  }
}

onUnmounted(() => {
  stopStreaming()
  // 清理资源
  messageRefs.value.clear()
  abortController = null
})

// 离开页面时清理空会话
onBeforeRouteLeave(async () => {
  // 如果当前会话存在且没有消息，静默删除空会话
  if (currentSession.value && messages.value.length === 0) {
    try {
      await chatHistoryService.deleteSession(currentSession.value.id)
      // 不需要更新本地 sessions 列表，因为组件即将卸载
    } catch (error) {
      // 删除失败不影响路由跳转
      console.error('离开时删除空会话失败', error)
    }
  }
  return true
})

onMounted(async () => {
  await loadSessions()
  if (sessions.value.length > 0) {
    await switchSession(sessions.value[0])
  } else {
    messages.value.push({
      id: '0',
      role: 'assistant',
      content: '你好！我是AI助手，有什么可以帮助你的吗？',
      timestamp: new Date().toISOString()
    })
  }
})
</script>

<style scoped>
/* ============================================
 * Dashboard-Style Chat Interface
 * Soft shadows, large rounded corners, card-based layout
 * ============================================ */

/* Warm Citrus Chat Variables */
.chat-page {
  --ds-accent: #F59E0B;
  --ds-bg: var(--bg-base);
  --ds-card: var(--bg-card);
  --ds-text: var(--text-primary);
  --ds-text-secondary: var(--text-secondary);
  --ds-text-muted: var(--text-muted);
  --ds-border: var(--border-light);
  --ds-radius: var(--radius-xl);
  --ds-radius-sm: var(--radius-lg);
  --ds-radius-xs: var(--radius-md);
  --ds-shadow: var(--shadow-sm);
  --ds-shadow-lg: var(--shadow-lg);
  --ds-shadow-hover: 0 6px 24px rgba(234, 88, 12, 0.12);
}

.chat-page {
  position: relative;
  display: flex;
  height: 100%;
  background: var(--ds-bg);
  overflow: hidden;
  gap: 24px;
  padding: 24px;
}

/* ========== 侧边栏卡片 ========== */
.session-sidebar {
  display: flex;
  flex-direction: column;
  width: 280px;
  min-width: 0;
  flex-shrink: 0;
  background: var(--ds-card);
  border: 2px solid var(--ds-border);
  border-radius: var(--ds-radius);
  height: 100%;
  overflow: hidden;
  position: relative;
  box-shadow: var(--ds-shadow);
  transition: width 0.35s cubic-bezier(0.4, 0, 0.2, 1),
              min-width 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.session-sidebar.collapsed {
  width: 72px;
  min-width: 72px;
}

/* 折叠按钮 - 仪表盘风格 */
.divider-toggle-btn {
  position: absolute;
  top: 50%;
  right: -12px;
  transform: translateY(-50%);
  width: 24px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: var(--ds-card);
  color: var(--ds-text-secondary);
  border-radius: var(--ds-radius-xs);
  cursor: pointer;
  z-index: 1000;
  transition: all 0.25s ease;
  box-shadow: var(--ds-shadow);
}

.divider-toggle-btn:hover {
  background: var(--ds-accent);
  color: var(--ds-text);
  box-shadow: var(--ds-shadow-hover);
}

/* 侧边栏内容 */
.sidebar-content {
  display: flex;
  flex-direction: column;
  padding: 20px;
  height: 100%;
  overflow: hidden;
  opacity: 1;
  visibility: visible;
  transition: opacity 0.25s ease, visibility 0.25s ease;
}

.session-sidebar.collapsed .sidebar-content {
  opacity: 0;
  visibility: hidden;
}

/* 新对话按钮 - 仪表盘风格 */
.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  height: 48px;
  padding: 0 20px;
  border: none;
  border-radius: var(--ds-radius-sm);
  background: var(--gradient-sunset);
  color: white;
  font-weight: 700;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 2px 8px rgba(234, 88, 12, 0.25);
  margin-bottom: 16px;
  text-shadow: 0 1px 2px rgba(0,0,0,0.1);
}

.new-chat-btn:hover {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 4px 16px rgba(234, 88, 12, 0.35);
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 4px;
}

/* Scrollbar - 柔和风格 */
.session-list::-webkit-scrollbar {
  width: 6px;
}

.session-list::-webkit-scrollbar-track {
  background: transparent;
}

.session-list::-webkit-scrollbar-thumb {
  background: var(--ds-border);
  border-radius: 6px;
}

.session-list::-webkit-scrollbar-thumb:hover {
  background: var(--ds-accent);
}

/* 会话项 - 卡片风格 */
.session-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: var(--ds-radius-xs);
  cursor: pointer;
  transition: all 0.2s ease;
  background: transparent;
  border: none;
  white-space: nowrap;
  overflow: hidden;
}

.session-item:hover {
  background: var(--ds-bg);
}

.session-item.active {
  background: var(--bg-menu-item-active);
  border: 1.5px solid var(--border-accent);
}

.session-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--ds-radius-xs);
  background: var(--warm-100);
  color: var(--text-muted);
  flex-shrink: 0;
  transition: all 0.2s ease;
  border: 1.5px solid var(--border-light);
}

.session-item.active .session-icon {
  background: var(--gradient-sunset);
  color: white;
  border-color: transparent;
}

.session-item:hover .session-icon {
  color: var(--text-primary);
  background: var(--warm-200);
}

.session-info {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.session-title {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--ds-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  font-size: 0.75rem;
  color: var(--ds-text-muted);
  margin-top: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-menu-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--ds-text-muted);
  border-radius: var(--ds-radius-xs);
  opacity: 0;
  transition: all 0.2s ease;
  cursor: pointer;
  flex-shrink: 0;
}

.session-item:hover .session-menu-btn { opacity: 1; }
.session-menu-btn:hover {
  background: var(--ds-bg);
  color: var(--ds-text);
}

.empty-sessions {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 20px;
  color: var(--ds-text-muted);
  font-size: 0.85rem;
}

.empty-icon {
  margin-bottom: 16px;
  opacity: 0.6;
}

/* ========== 主聊天区域卡片 ========== */
.chat-main {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--ds-card);
  border-radius: var(--ds-radius);
  box-shadow: var(--ds-shadow);
  position: relative;
}

/* ========== 消息列表 - 结构化卡片流 ========== */
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* 欢迎区域 - 仪表盘风格 */
.welcome-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 32px;
  text-align: center;
}

.welcome-logo {
  position: relative;
  width: 80px;
  height: 80px;
  margin-bottom: 28px;
  border-radius: var(--ds-radius-sm);
  background: var(--gradient-sunset);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 24px rgba(234, 88, 12, 0.2);
}

.ai-logo-img {
  width: 48px;
  height: 48px;
  object-fit: contain;
  border-radius: var(--ds-radius-xs);
}

.logo-glow { display: none; }

.welcome-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--ds-text);
  margin-bottom: 12px;
  letter-spacing: -0.02em;
}

.welcome-subtitle {
  font-size: 1rem;
  color: var(--ds-text-secondary);
  font-weight: 500;
}

/* ========== 消息卡片流 - 无气泡设计 ========== */
.message-wrapper {
  display: flex;
  padding: 20px 0;
  border-bottom: 1px solid var(--ds-border);
  animation: cardFadeIn 0.4s ease-out;
}

.message-wrapper:last-child {
  border-bottom: none;
}

.message-wrapper.user {
  flex-direction: row-reverse;
}

.message-wrapper.assistant {
  flex-direction: row;
}

@keyframes cardFadeIn {
  0% {
    opacity: 0;
    transform: translateY(12px);
  }
  100% {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 用户消息卡片 */
.user-bubble {
  max-width: 65%;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.bubble-content {
  padding: 16px 20px;
  border-radius: var(--ds-radius-sm);
  background: var(--gradient-sunset);
  color: white;
  font-size: 0.95rem;
  font-weight: 600;
  line-height: 1.7;
  word-wrap: break-word;
  box-shadow: 0 2px 12px rgba(234, 88, 12, 0.2);
  text-shadow: 0 1px 2px rgba(0,0,0,0.1);
}

.bubble-time {
  font-size: 0.75rem;
  color: var(--ds-text-muted);
  font-weight: 400;
}

/* AI消息卡片 */
.ai-bubble {
  display: flex;
  gap: 16px;
  max-width: 85%;
}

.ai-avatar {
  position: relative;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: var(--ds-radius-xs);
  overflow: hidden;
  background: var(--ds-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--ds-shadow);
}

.ai-logo-small {
  width: 24px;
  height: 24px;
  object-fit: contain;
}

.ai-thinking-glow { display: none; }

.ai-content-wrapper {
  flex: 1;
  min-width: 0;
}

.ai-content {
  padding: 16px 20px;
  border-radius: var(--ds-radius-sm);
  background: var(--warm-50);
  color: var(--ds-text);
  font-size: 0.95rem;
  font-weight: 400;
  line-height: 1.7;
  box-shadow: none;
  border: 1.5px solid var(--ds-border);
}

/* 思考指示器 - 柔和风格 */
.thinking-indicator {
  display: flex;
  gap: 8px;
  padding: 8px 0;
}

.thinking-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ds-accent);
  animation: softPulse 1.2s ease-in-out infinite;
}

.thinking-dot:nth-child(1) { animation-delay: 0s; }
.thinking-dot:nth-child(2) { animation-delay: 0.15s; }
.thinking-dot:nth-child(3) { animation-delay: 0.3s; }

@keyframes softPulse {
  0%, 100% { transform: scale(0.8); opacity: 0.4; }
  50% { transform: scale(1); opacity: 1; }
}

/* 光标闪烁 */
.cursor-blink {
  display: inline-block;
  width: 2px;
  height: 18px;
  background: var(--ds-accent);
  margin-left: 2px;
  vertical-align: text-bottom;
  animation: cursorBlink 0.7s infinite;
}

@keyframes cursorBlink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* AI底部 */
.ai-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  padding-left: 0;
}

.ai-actions {
  display: flex;
  gap: 8px;
}

.action-btn {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: none;
  background: transparent;
  color: var(--ds-text-muted);
  border-radius: var(--ds-radius-xs);
  cursor: pointer;
  transition: all 0.2s ease;
}

.action-btn:hover {
  background: var(--warm-100);
  color: var(--primary-color);
}

/* Markdown content */
.markdown-content {
  line-height: 1.8;
  font-weight: 400;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  margin: 20px 0 12px;
  font-weight: 700;
  color: var(--ds-text);
}

.markdown-content :deep(h1) { font-size: 1.25em; }
.markdown-content :deep(h2) { font-size: 1.1em; }
.markdown-content :deep(p) { margin: 12px 0; }
.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 12px 0;
  padding-left: 24px;
}
.markdown-content :deep(li) { margin: 6px 0; }

.markdown-content :deep(code) {
  background: var(--warm-100);
  padding: 3px 8px;
  border-radius: 6px;
  font-family: var(--font-mono);
  font-size: 0.88em;
  color: var(--primary-dark);
}

.markdown-content :deep(pre) {
  background: var(--warm-50);
  padding: 16px 20px;
  border-radius: var(--ds-radius-sm);
  margin: 16px 0;
  overflow-x: auto;
  border: 1.5px solid var(--ds-border);
}

.markdown-content :deep(pre code) {
  background: transparent;
  padding: 0;
}

.markdown-content :deep(a) {
  color: var(--primary-color);
  text-decoration: none;
  border-bottom: 2px solid var(--primary-light);
  font-weight: 600;
}

.markdown-content :deep(strong) { font-weight: 700; }

.markdown-content :deep(blockquote) {
  margin: 16px 0;
  padding: 12px 20px;
  border-left: 3px solid var(--primary-color);
  background: var(--warm-50);
  border-radius: 0 var(--ds-radius-xs) var(--ds-radius-xs) 0;
}

/* ========== 输入区域 - 悬浮卡片 ========== */
.input-area {
  flex-shrink: 0;
  padding: 24px;
  background: transparent;
}

.input-capsule {
  position: relative;
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-radius: var(--ds-radius-sm);
  background: var(--ds-card);
  border: 2px solid var(--ds-border);
  transition: all 0.25s ease;
  box-shadow: var(--ds-shadow);
}

.input-capsule::before { display: none; }

.input-capsule:focus-within {
  border-color: var(--primary-light);
  box-shadow: 0 4px 20px rgba(234, 88, 12, 0.1);
}

.input-right-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.chat-textarea {
  flex: 1;
  border: none;
  background: transparent;
  resize: none;
  font-size: 0.95rem;
  line-height: 1.7;
  color: var(--ds-text);
  font-family: inherit;
  font-weight: 400;
  max-height: 180px;
  min-height: 24px;
  outline: none;
  padding: 8px 4px;
}

.chat-textarea::placeholder {
  color: var(--ds-text-muted);
}

/* Send button - warm gradient */
.send-btn {
  width: 48px;
  height: 48px;
  display: grid;
  place-items: center;
  border: none;
  background: var(--warm-100);
  color: var(--text-muted);
  border-radius: var(--ds-radius-xs);
  cursor: pointer;
  transition: all 0.25s ease;
}

.send-btn:hover:not(:disabled) {
  background: var(--warm-200);
  color: var(--text-primary);
}

.send-btn.active {
  background: var(--gradient-sunset);
  color: white;
  box-shadow: 0 4px 16px rgba(234, 88, 12, 0.25);
}

.send-btn.active:hover {
  transform: translateY(-2px) scale(1.05);
  box-shadow: 0 6px 20px rgba(234, 88, 12, 0.35);
}

.send-btn.stop {
  background: var(--ds-card);
  color: #EF4444;
  border: 1.5px solid #EF4444;
}

.send-btn.stop:hover {
  background: #EF4444;
  color: var(--ds-card);
}

/* 语音按钮 */
.voice-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: var(--ds-text-muted);
  border-radius: var(--ds-radius-xs);
  cursor: pointer;
  transition: all 0.2s ease;
}

.voice-btn:hover {
  background: var(--warm-100);
  color: var(--text-primary);
}

/* Mode pills - warm style */
.input-hints {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 16px;
}

.mode-pills {
  display: flex;
  gap: 10px;
}

.mode-pill {
  height: 36px;
  padding: 0 18px;
  border: 1.5px solid var(--ds-border);
  background: var(--ds-card);
  color: var(--ds-text-secondary);
  font-size: 0.8rem;
  font-weight: 600;
  border-radius: var(--ds-radius-xs);
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: none;
}

.mode-pill:hover {
  background: var(--warm-50);
  color: var(--ds-text);
  border-color: var(--primary-light);
}

.mode-pill.active {
  background: var(--warm-100);
  border-color: var(--primary-color);
  color: var(--primary-dark);
}

.mode-pill:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* ========== 响应式布局 ========== */
@media (max-width: 900px) {
  .chat-page {
    flex-direction: column;
    height: 100%;
    padding: 16px;
    gap: 16px;
  }

  .session-sidebar {
    width: 100%;
    height: auto;
    max-height: 240px;
    border-radius: var(--ds-radius-sm);
  }

  .session-sidebar.collapsed {
    width: 100%;
    height: 72px;
    max-height: 72px;
  }

  .divider-toggle-btn {
    top: auto;
    bottom: 24px;
    right: 24px;
    transform: none;
  }

  .message-list {
    padding: 20px;
  }

  .input-area {
    padding: 16px;
  }

  .user-bubble, .ai-bubble {
    max-width: 85%;
  }

  .input-hints {
    flex-wrap: wrap;
    gap: 8px;
  }
}

@media (max-width: 768px) {
  .chat-page {
    padding: 12px;
    gap: 12px;
  }

  .session-sidebar,
  .chat-main {
    border-radius: var(--ds-radius-sm);
  }

  .input-capsule {
    padding: 12px 16px;
    gap: 10px;
  }

  .input-right-actions {
    gap: 8px;
  }

  .send-btn {
    width: 44px;
    height: 44px;
  }

  .voice-btn {
    width: 36px;
    height: 36px;
  }
}

@media (max-width: 640px) {
  .chat-page {
    padding: 8px;
    gap: 8px;
  }

  .welcome-title {
    font-size: 1.35rem;
  }

  .bubble-content, .ai-content {
    font-size: 0.9rem;
    padding: 12px 16px;
  }

  .chat-textarea {
    font-size: 0.9rem;
  }

  .mode-pills {
    width: 100%;
    justify-content: center;
  }

  .mode-pill {
    flex: 1;
    max-width: 100px;
    justify-content: center;
    padding: 0 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
</style>