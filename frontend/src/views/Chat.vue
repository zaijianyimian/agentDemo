<template>
  <div class="chat-page">
    <!-- 会话列表侧边栏 -->
    <div class="session-sidebar">
      <div class="sidebar-header">
        <div class="logo-area">
          <div class="logo-icon">
            <n-icon size="24"><SparklesIcon /></n-icon>
          </div>
          <span class="logo-text">AI Chat</span>
        </div>
        <n-button
          type="primary"
          size="small"
          @click="createNewSession"
          class="new-session-btn"
        >
          <template #icon>
            <n-icon><AddIcon /></n-icon>
          </template>
          新会话
        </n-button>
      </div>

      <div class="session-list" ref="sessionListRef">
        <div
          v-for="session in sessions"
          :key="session.id"
          :class="['session-item', { active: currentSession?.id === session.id }]"
          @click="switchSession(session)"
        >
          <div class="session-icon">
            <n-icon size="16"><ChatbubbleIcon /></n-icon>
          </div>
          <div class="session-info">
            <div class="session-title">{{ session.title }}</div>
            <div class="session-meta">
              <span>{{ session.messageCount }} 条消息</span>
              <span>{{ formatSessionTime(session.lastMessageTime) }}</span>
            </div>
          </div>
          <n-dropdown
            trigger="click"
            :options="getSessionMenuOptions()"
            @select="handleSessionMenu($event, session)"
          >
            <n-button text size="small" class="session-menu-btn" @click.stop>
              <n-icon size="16"><MoreIcon /></n-icon>
            </n-button>
          </n-dropdown>
        </div>

        <div v-if="sessions.length === 0" class="empty-sessions">
          <n-icon size="40" class="empty-icon"><ChatbubbleIcon /></n-icon>
          <span>暂无会话记录</span>
          <span class="empty-hint">点击上方按钮开始新对话</span>
        </div>
      </div>
    </div>

    <!-- 聊天主区域 -->
    <div class="chat-main">
      <!-- 当前会话信息 -->
      <div class="chat-header" v-if="currentSession">
        <div class="header-title">
          <n-icon size="20" class="header-icon"><SparklesIcon /></n-icon>
          <span>{{ currentSession.title }}</span>
        </div>
        <div class="header-actions">
          <n-button text @click="editSessionTitle" v-if="currentSession">
            <n-icon size="18"><EditIcon /></n-icon>
          </n-button>
          <n-button text @click="clearCurrentSession">
            <n-icon size="18"><TrashIcon /></n-icon>
          </n-button>
        </div>
      </div>

      <!-- 消息列表 -->
      <div class="message-list" ref="messageList">
        <div
          v-for="msg in messages"
          :key="msg.id"
          :class="['message', msg.role]"
        >
          <!-- 消息气泡 -->
          <div :class="['message-bubble', { 'streaming': msg.isStreaming }]">
            <!-- 用户消息 -->
            <div v-if="msg.role === 'user'" class="user-message">
              <div class="message-avatar user-avatar">
                <div class="avatar-inner">
                  <n-icon size="20"><PersonIcon /></n-icon>
                </div>
              </div>
              <div class="message-body">
                <div class="message-text user-text">
                  <pre class="message-pre">{{ msg.content }}</pre>
                </div>
                <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
              </div>
            </div>

            <!-- AI消息 -->
            <div v-else class="ai-message">
              <div class="message-avatar ai-avatar">
                <div class="avatar-inner">
                  <n-icon size="20"><SparklesIcon /></n-icon>
                </div>
              </div>
              <div class="message-body">
                <div class="message-text ai-text">
                  <div v-if="!msg.content && msg.isStreaming" class="thinking-indicator">
                    <div class="thinking-dots">
                      <span class="dot"></span>
                      <span class="dot"></span>
                      <span class="dot"></span>
                    </div>
                    <span class="thinking-text">AI 正在思考...</span>
                  </div>
                  <div v-else class="text-content">
                    <div class="markdown-content" v-html="renderMarkdown(msg.content)"></div>
                    <span v-if="msg.isStreaming && msg.content" class="cursor-blink"></span>
                  </div>
                </div>
                <div class="message-footer">
                  <span class="message-time">{{ formatTime(msg.timestamp) }}</span>
                  <n-button text size="tiny" @click="copyMessage(msg.content)" v-if="msg.content">
                    <n-icon size="14"><CopyIcon /></n-icon>
                  </n-button>
                </div>
                <div class="message-actions" v-if="msg.content">
                  <n-button text size="tiny" @click="captureMessage('note', msg)">转笔记</n-button>
                  <n-button text size="tiny" @click="captureMessage('task', msg)">转任务</n-button>
                  <n-button text size="tiny" @click="captureMessage('schedule', msg)">转日程</n-button>
                  <n-button text size="tiny" @click="captureMessage('memory', msg)">存记忆</n-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 消息为空时的提示 -->
        <div v-if="messages.length === 0" class="empty-messages">
          <div class="empty-illustration">
            <div class="empty-circle">
              <n-icon size="60"><SparklesIcon /></n-icon>
            </div>
          </div>
          <div class="empty-title">开始新的对话</div>
          <div class="empty-subtitle">输入消息，与 AI 助手交流</div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="input-area">
        <div class="input-container">
          <n-input
            v-model:value="inputText"
            type="textarea"
            placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
            :autosize="{ minRows: 1, maxRows: 4 }"
            :disabled="loading"
            @keydown="handleKeydown"
            class="chat-input"
          />
          <div class="input-actions">
            <n-button
              :type="loading ? 'error' : 'primary'"
              circle
              size="large"
              :disabled="!inputText.trim() && !loading"
              @click="loading ? stopStreaming() : sendMessage()"
              class="send-btn"
            >
              <template #icon>
                <n-icon size="20">
                  <StopIcon v-if="loading" />
                  <SendIcon v-else />
                </n-icon>
              </template>
            </n-button>
          </div>
        </div>

        <div class="input-footer">
          <n-radio-group v-model:value="chatMode" size="small" :disabled="loading">
            <n-radio-button value="stream">流式聊天</n-radio-button>
            <n-radio-button value="normal">普通聊天</n-radio-button>
            <n-radio-button value="mcp">MCP Agent</n-radio-button>
          </n-radio-group>
          <div class="mode-indicator">
            <span class="mode-dot"></span>
            <span class="mode-text">
              {{ chatMode === 'stream' ? '实时响应' : chatMode === 'mcp' ? '工具调用' : '完整响应' }}
            </span>
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
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import {
  NInput,
  NButton,
  NIcon,
  NRadioGroup,
  NRadioButton,
  NDropdown,
  NModal,
  useMessage
} from 'naive-ui'
import {
  SendOutline as SendIcon,
  StopCircleOutline as StopIcon,
  PersonOutline as PersonIcon,
  SparklesOutline as SparklesIcon,
  AddOutline as AddIcon,
  ChatbubbleOutline as ChatbubbleIcon,
  EllipsisHorizontal as MoreIcon,
  CreateOutline as EditIcon,
  TrashOutline as TrashIcon,
  CopyOutline as CopyIcon
} from '@vicons/ionicons5'
import type { ChatMessage, ChatSession } from '@/types'
import { chatActionService, chatHistoryService } from '@/services/api'
import { fetchWithAuth } from '@/services/auth-fetch'
import dayjs from 'dayjs'
import { marked } from 'marked'

// 配置 marked 选项
marked.setOptions({
  breaks: true,
  gfm: true
})

// 渲染 Markdown 并高亮代码
const renderMarkdown = (content: string): string => {
  if (!content) return ''
  try {
    const html = marked.parse(content) as string
    // 后处理：为代码块添加高亮
    const highlighted = html.replace(/<pre><code class="language-(\w+)">/g, '<pre class="hljs"><code class="language-$1">')
    return sanitizeHtml(highlighted)
  } catch {
    return sanitizeHtml(content)
  }
}

const sanitizeHtml = (html: string): string => {
  const parser = new DOMParser()
  const doc = parser.parseFromString(html, 'text/html')

  const blockedTags = ['script', 'iframe', 'object', 'embed', 'link', 'meta', 'style']
  blockedTags.forEach(tag => {
    doc.querySelectorAll(tag).forEach(node => node.remove())
  })

  doc.querySelectorAll('*').forEach(el => {
    const attrs = Array.from(el.attributes)
    attrs.forEach(attr => {
      const name = attr.name.toLowerCase()
      const value = attr.value
      if (name.startsWith('on')) {
        el.removeAttribute(attr.name)
        return
      }
      if ((name === 'href' || name === 'src') && /^\s*javascript:/i.test(value)) {
        el.removeAttribute(attr.name)
        return
      }
      if (name === 'style') {
        el.removeAttribute('style')
      }
    })
    if (el.tagName.toLowerCase() === 'a') {
      const href = el.getAttribute('href')
      if (href && !/^(https?:|mailto:|\/|#)/i.test(href)) {
        el.removeAttribute('href')
      } else {
        el.setAttribute('rel', 'noopener noreferrer nofollow')
        el.setAttribute('target', '_blank')
      }
    }
  })

  return doc.body.innerHTML
}

const parseSseEvents = (rawEvent: string): string[] => {
  const lines = rawEvent.replace(/\r/g, '').split('\n')
  const chunks: string[] = []
  let dataBuffer = ''
  for (const line of lines) {
    if (line.startsWith('data:')) {
      const data = line.slice(5).trim()
      if (data && data !== '[DONE]') {
        dataBuffer += data
      }
      continue
    }
    if (line.startsWith(':') || line.startsWith('event:') || line.startsWith('id:') || line.startsWith('retry:')) {
      continue
    }
    if (line.trim()) {
      dataBuffer += line
    }
  }
  if (dataBuffer) {
    chunks.push(dataBuffer)
  }
  return chunks
}

const message = useMessage()
const inputText = ref('')
const chatMode = ref('stream')
const loading = ref(false)
const messages = ref<ChatMessage[]>([])
const sessions = ref<ChatSession[]>([])
const currentSession = ref<ChatSession | null>(null)
const messageList = ref<HTMLElement | null>(null)
const sessionListRef = ref<HTMLElement | null>(null)
let abortController: AbortController | null = null

// 编辑标题
const showEditModal = ref(false)
const editTitle = ref('')

// 加载会话列表
const loadSessions = async () => {
  try {
    const res = await chatHistoryService.getSessions()
    if (res.success && res.data) {
      sessions.value = res.data
    }
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

// 切换会话
const switchSession = async (session: ChatSession) => {
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
    }
  } catch (error) {
    console.error('加载消息失败', error)
  }
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
        if (currentSession.value?.id === session.id) {
          messages.value = []
        }
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

// 编辑标题
const editSessionTitle = () => {
  if (currentSession.value) {
    editTitle.value = currentSession.value.title
    showEditModal.value = true
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
      if (idx >= 0) {
        sessions.value[idx].title = editTitle.value
      }
      message.success('标题已更新')
    }
  } catch (error) {
    message.error('保存失败')
  }
  showEditModal.value = false
}

// 清空当前会话
const clearCurrentSession = async () => {
  if (!currentSession.value) return
  try {
    await chatHistoryService.clearSessionMessages(currentSession.value.id)
    currentSession.value.messageCount = 0
    messages.value = []
    message.success('已清空消息')
  } catch (error) {
    message.error('操作失败')
  }
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

  // 注意：不再手动保存消息，后端在 /session 接口中会自动保存

  await nextTick()
  scrollToBottom()

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

  try {
    if (chatMode.value === 'stream') {
      await streamChat(queryText, assistantMessage)
    } else {
      await normalChat(queryText, assistantMessage)
    }

    // 注意：不再手动保存AI消息，后端已经保存了

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

// 流式聊天
const streamChat = async (query: string, messageObj: ChatMessage) => {
  abortController = new AbortController()

  // 使用带 sessionId 的流式接口，后端会自动选择当前启用模型并保存消息和记忆
  let apiPath = ''
  if (chatMode.value === 'mcp') {
    apiPath = `/api/mcp/agent/chat/stream?message=${encodeURIComponent(query)}`
  } else {
    apiPath = `/api/chat/stream/session?message=${encodeURIComponent(query)}&sessionId=${currentSession.value!.id}`
  }

  try {
    const response = await fetchWithAuth(apiPath, {
      signal: abortController.signal,
      headers: {
        'Accept': 'text/event-stream',
        'Cache-Control': 'no-cache',
        'Connection': 'keep-alive'
      }
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('No reader available')

    const decoder = new TextDecoder()
    let buffer = ''

    // 强制UI刷新的辅助函数
    const flushUI = async () => {
      await nextTick()
      scrollToBottom()
      // 使用 requestAnimationFrame 确保渲染完成
      await new Promise(resolve => requestAnimationFrame(resolve))
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // SSE event 以空行分隔，兼容 \r\n\r\n 与 \n\n
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() || ''

      for (const event of events) {
        if (!event.trim()) continue
        const chunks = parseSseEvents(event)
        for (const chunk of chunks) {
          messageObj.content += chunk
          await flushUI()
        }
      }
    }

    // 处理剩余缓冲区
    if (buffer.trim()) {
      const chunks = parseSseEvents(buffer)
      for (const chunk of chunks) {
        messageObj.content += chunk
      }
    }

  } catch (error: any) {
    if (error.name !== 'AbortError') {
      throw error
    }
  }
}

// 普通聊天
const normalChat = async (query: string, messageObj: ChatMessage) => {
  // 使用带 sessionId 的接口，后端会自动选择当前启用模型并保存消息和记忆
  let apiPath = ''
  if (chatMode.value === 'mcp') {
    apiPath = `/api/mcp/agent/chat?message=${encodeURIComponent(query)}`
  } else {
    apiPath = `/api/chat/complete/session?message=${encodeURIComponent(query)}&sessionId=${currentSession.value!.id}`
  }

  const response = await fetchWithAuth(apiPath)
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`)
  }

  const text = await response.text()
  messageObj.content = text
}

// 停止流式响应
const stopStreaming = () => {
  if (abortController) {
    abortController.abort()
    abortController = null
    loading.value = false
    const lastMsg = messages.value[messages.value.length - 1]
    if (lastMsg && lastMsg.isStreaming) {
      lastMsg.isStreaming = false
      if (!lastMsg.content) {
        lastMsg.content = '[已停止]'
      }
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

const captureMessage = async (target: 'note' | 'task' | 'schedule' | 'memory', msg: ChatMessage) => {
  try {
    const payload = {
      sessionId: currentSession.value?.id,
      content: msg.content,
      role: msg.role,
      titleHint: currentSession.value?.title
    }
    const action = target === 'note'
      ? chatActionService.createNote
      : target === 'task'
        ? chatActionService.createTask
        : target === 'schedule'
          ? chatActionService.createSchedule
          : chatActionService.storeMemory
    const res = await action(payload)
    if (res.success && res.data) {
      message.success(res.data.message || '处理成功')
    }
  } catch (error) {
    message.error('转换失败')
  }
}

// 键盘事件处理
const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    if (loading.value) {
      stopStreaming()
    } else {
      sendMessage()
    }
  }
}

// 滚动到底部
const scrollToBottom = () => {
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight
  }
}

// 格式化时间
const formatTime = (time: string) => {
  return dayjs(time).format('HH:mm')
}

// 格式化会话时间
const formatSessionTime = (time?: string) => {
  if (!time) return ''
  const d = dayjs(time)
  const now = dayjs()
  if (now.diff(d, 'day') === 0) return d.format('HH:mm')
  if (now.diff(d, 'day') === 1) return '昨天'
  if (now.diff(d, 'day') < 7) return `${now.diff(d, 'day')}天前`
  return d.format('MM-DD')
}

// 组件卸载时清理
onUnmounted(() => {
  stopStreaming()
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
.chat-page {
  display: grid;
  grid-template-columns: 312px 1fr;
  gap: 0;
  height: calc(100vh - 120px);
  position: relative;
  overflow: hidden;
  background: linear-gradient(180deg, color-mix(in srgb, var(--bg-base) 97%, #fff 3%), var(--bg-base));
  border: 1px solid var(--border-color);
  border-radius: var(--radius-2xl);
}

/* 会话侧边栏 */
.session-sidebar {
  background: color-mix(in srgb, var(--bg-elevated) 96%, transparent);
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  z-index: 10;
  position: relative;
}

.session-sidebar::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 1px;
  background: linear-gradient(180deg, transparent, var(--border-light), transparent);
}

.sidebar-header {
  padding: 22px;
  border-bottom: 1px solid var(--border-color);
  background: var(--gradient-card);
}

.logo-area {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 18px;
}

.logo-icon {
  width: 48px;
  height: 48px;
  background: var(--gradient-warm);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-base);
}

.logo-icon:hover {
  transform: scale(1.05);
  box-shadow: var(--shadow-sm);
}

.logo-text {
  font-size: 22px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.5px;
}

.new-session-btn {
  width: 100%;
  background: var(--gradient-warm) !important;
  border: none !important;
  box-shadow: var(--shadow-xs);
  transition: all var(--transition-base);
  font-weight: 600;
  height: 44px;
  border-radius: var(--radius-md) !important;
}

.new-session-btn:hover {
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.new-session-btn:active {
  transform: translateY(0);
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--primary-color) 30%, transparent) transparent;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all var(--transition-base);
  margin-bottom: 8px;
  background: transparent;
  border: 1px solid transparent;
  position: relative;
}

.session-item::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  background: var(--primary-color);
  border-radius: 0 var(--radius-xs) var(--radius-xs) 0;
  transition: height var(--transition-base);
}

.session-item:hover {
  background: var(--bg-hover);
  border-color: var(--border-color);
}

.session-item:hover::before {
  height: 40%;
}

.session-item.active {
  background: color-mix(in srgb, var(--bg-active) 72%, transparent);
  border-color: var(--primary-color);
  box-shadow: var(--shadow-xs);
}

.session-item.active::before {
  height: 60%;
}

.session-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  flex-shrink: 0;
  transition: all var(--transition-base);
}

.session-item:hover .session-icon,
.session-item.active .session-icon {
  background: var(--gradient-warm);
  color: white;
  box-shadow: var(--shadow-xs);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  font-size: 12px;
  color: var(--text-muted);
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.session-menu-btn {
  opacity: 0;
  transition: opacity var(--transition-fast);
  color: var(--text-muted);
}

.session-item:hover .session-menu-btn {
  opacity: 1;
}

.empty-sessions {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: var(--text-muted);
  text-align: center;
}

.empty-icon {
  color: var(--border-light);
  margin-bottom: 14px;
  opacity: 0.6;
}

.empty-hint {
  font-size: 12px;
  margin-top: 8px;
  color: var(--text-secondary);
}

/* 聊天主区域 */
.chat-main {
  display: flex;
  flex-direction: column;
  position: relative;
  background: transparent;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.chat-header {
  padding: 18px 26px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--bg-card);
  backdrop-filter: blur(var(--blur-md));
  position: sticky;
  top: 0;
  z-index: 20;
}

.chat-header::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 26px;
  right: 26px;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--border-light), transparent);
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.header-icon {
  color: var(--primary-color);
}

.header-actions {
  display: flex;
  gap: 8px;
}

.header-actions .n-button:hover {
  color: var(--primary-color);
}

/* 消息列表 */
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 26px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.message {
  animation: messageSlideIn 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes messageSlideIn {
  from {
    opacity: 0;
    transform: translateY(20px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.message-bubble {
  position: relative;
}

/* 用户消息 */
.user-message {
  display: flex;
  gap: 14px;
}

.message-avatar {
  flex-shrink: 0;
}

.avatar-inner {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-base);
}

.user-avatar .avatar-inner {
  background: var(--gradient-warm);
  box-shadow: var(--shadow-sm);
}

.ai-avatar .avatar-inner {
  background: linear-gradient(135deg, #10B981, #059669);
}

.message-body {
  flex: 1;
  min-width: 0;
}

.message-text {
  padding: 16px 20px;
  border-radius: var(--radius-lg);
  line-height: 1.7;
  position: relative;
  max-width: min(78%, 860px);
  box-shadow: var(--shadow-sm);
}

.user-text {
  background: var(--gradient-warm);
  color: white;
  border-bottom-left-radius: var(--radius-xs);
  box-shadow: var(--shadow-sm);
}

.ai-text {
  background: color-mix(in srgb, var(--bg-card) 96%, #fff 4%);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
  border-bottom-right-radius: var(--radius-xs);
}

.message-bubble.streaming .ai-text {
  border-color: var(--primary-color);
  box-shadow: var(--shadow-xs);
}

.message-pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  margin: 0;
}

/* Markdown 内容样式 */
.markdown-content {
  line-height: 1.75;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3),
.markdown-content :deep(h4) {
  margin: 18px 0 10px 0;
  font-weight: 600;
  color: var(--text-primary);
}

.markdown-content :deep(h1) { font-size: 1.4em; }
.markdown-content :deep(h2) { font-size: 1.25em; }
.markdown-content :deep(h3) { font-size: 1.1em; }

.markdown-content :deep(p) {
  margin: 10px 0;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 10px 0;
  padding-left: 22px;
}

.markdown-content :deep(li) {
  margin: 6px 0;
}

.markdown-content :deep(code) {
  background: var(--bg-input);
  padding: 3px 7px;
  border-radius: var(--radius-xs);
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 0.88em;
  color: var(--primary-color);
  border: 1px solid var(--border-color);
}

.markdown-content :deep(pre) {
  background: var(--bg-input);
  padding: 14px 18px;
  border-radius: var(--radius-md);
  margin: 14px 0;
  overflow-x: auto;
  border: 1px solid var(--border-color);
}

.markdown-content :deep(pre code) {
  background: transparent;
  padding: 0;
  color: var(--text-primary);
  font-size: 0.85em;
  line-height: 1.6;
  border: none;
}

.markdown-content :deep(a) {
  color: var(--primary-color);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.markdown-content :deep(strong) {
  font-weight: 600;
  color: var(--text-primary);
}

.markdown-content :deep(em) {
  font-style: italic;
}

.markdown-content :deep(blockquote) {
  margin: 14px 0;
  padding: 10px 18px;
  border-left: 3px solid var(--primary-color);
  background: var(--bg-input);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
}

.markdown-content :deep(table) {
  margin: 14px 0;
  border-collapse: collapse;
  width: 100%;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  text-align: left;
}

.markdown-content :deep(th) {
  background: var(--bg-input);
  font-weight: 600;
}

.markdown-content :deep(hr) {
  margin: 18px 0;
  border: none;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--border-color), transparent);
}

.message-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.message-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.message-actions .n-button {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
}

.message-actions .n-button:hover {
  background: var(--bg-active);
  color: var(--primary-color);
}

.message-time {
  font-size: 11px;
  color: var(--text-muted);
}

/* AI消息 */
.ai-message {
  display: flex;
  gap: 14px;
}

.message.assistant .ai-message {
  flex-direction: row-reverse;
}

/* 思考指示器 */
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 10px;
}

.thinking-dots {
  display: flex;
  gap: 5px;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary-color);
  animation: bounce 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) { animation-delay: -0.32s; }
.dot:nth-child(2) { animation-delay: -0.16s; }
.dot:nth-child(3) { animation-delay: 0s; }

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1.2);
    opacity: 1;
  }
}

.thinking-text {
  font-size: 13px;
  color: var(--text-secondary);
}

/* 光标闪烁 */
.cursor-blink {
  display: inline-block;
  width: 2px;
  height: 18px;
  background: var(--primary-color);
  animation: blink 1s infinite;
  margin-left: 2px;
  vertical-align: text-bottom;
  box-shadow: none;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* 空消息提示 */
.empty-messages {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 72px 24px;
}

.empty-illustration {
  position: relative;
  margin-bottom: 28px;
}

.empty-circle {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: var(--gradient-card);
  border: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
  animation: float 4s ease-in-out infinite;
  box-shadow: var(--shadow-sm);
}

.empty-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  margin-bottom: 10px;
}

.empty-subtitle {
  font-size: 15px;
  color: var(--text-secondary);
}

/* 输入区域 */
.input-area {
  padding: 22px 28px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-card);
  backdrop-filter: blur(var(--blur-sm));
  position: relative;
}

.input-area::before {
  content: '';
  position: absolute;
  top: 0;
  left: 28px;
  right: 28px;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--border-light), transparent);
}

.input-container {
  display: flex;
  gap: 14px;
  align-items: flex-end;
  padding: 16px;
  border-radius: var(--radius-lg);
  background: var(--bg-input);
  border: 1px solid color-mix(in srgb, var(--border-color) 84%, transparent);
  transition: all var(--transition-base);
}

.input-container:focus-within {
  border-color: var(--primary-color);
  box-shadow: var(--shadow-focus), 0 8px 20px rgba(0, 0, 0, 0.08);
}

.chat-input {
  flex: 1;
  background: transparent !important;
}

.chat-input :deep(.n-input__textarea-el) {
  font-size: 15px;
  color: var(--text-primary);
  background: transparent;
  line-height: 1.6;
}

.chat-input :deep(.n-input__placeholder) {
  color: var(--text-muted);
}

.send-btn {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  background: var(--gradient-warm) !important;
  border: none !important;
  box-shadow: var(--shadow-xs);
  transition: all var(--transition-base);
}

.send-btn:hover:not(:disabled) {
  transform: scale(1.02);
  box-shadow: var(--shadow-sm);
}

.send-btn:active:not(:disabled) {
  transform: scale(0.95);
}

.send-btn:disabled {
  opacity: 0.5;
}

.input-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  gap: 14px;
}

.mode-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary-color);
  animation: modeDotPulse 1.5s ease-in-out infinite;
  box-shadow: none;
}

@keyframes modeDotPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.mode-text {
  font-size: 12px;
  color: var(--text-muted);
}

/* 响应式 */
@media (max-width: 900px) {
  .chat-page {
    grid-template-columns: 1fr;
    border-radius: var(--radius-xl);
  }

  .session-sidebar {
    display: none;
  }
}
</style>


