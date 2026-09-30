<template>
  <div class="chat-page agent-chat-page">
    <aside id="chat-sessions" class="session-sidebar anchor-section" :class="{ collapsed: isListCollapsed }">
      <div class="session-head">
        <div v-if="!isListCollapsed">
          <span class="page-eyebrow">Conversations</span>
          <strong>会话</strong>
        </div>
        <button class="sidebar-toggle" type="button" @click="toggleSidebar" :title="isListCollapsed ? '展开会话列表' : '收起会话列表'">
          <n-icon size="17">
            <ChevronForwardIcon v-if="isListCollapsed" />
            <ChevronBackIcon v-else />
          </n-icon>
        </button>
      </div>

      <button class="new-chat-btn" type="button" :disabled="loading" @click="createNewSession">
        <n-icon size="18"><AddIcon /></n-icon>
        <span v-if="!isListCollapsed">新对话</span>
      </button>

      <div v-if="!isListCollapsed" ref="sessionListRef" class="session-list">
        <button
          v-for="session in sessions"
          :key="session.id"
          type="button"
          :disabled="loading"
          :class="['session-item', { active: currentSession?.id === session.id }]"
          @click="switchSession(session)"
        >
          <span class="session-icon"><n-icon size="15"><ChatbubbleIcon /></n-icon></span>
          <span class="session-info">
            <strong>{{ session.title }}</strong>
            <small>{{ formatSessionTime(session.lastMessageTime) }}</small>
          </span>
          <n-dropdown trigger="click" :disabled="loading" :options="sessionMenuOptions" @select="handleSessionMenu($event, session)">
            <span class="session-menu-btn" role="button" tabindex="0" @click.stop>
              <n-icon size="15"><MoreIcon /></n-icon>
            </span>
          </n-dropdown>
        </button>

        <div v-if="sessions.length === 0" class="empty-sessions">
          <n-icon size="28"><ChatbubbleIcon /></n-icon>
          <span>暂无历史会话</span>
        </div>
      </div>
    </aside>

    <section id="chat-conversation" class="chat-main anchor-section">
      <header class="chat-context-bar">
        <div class="chat-context-copy">
          <span class="page-eyebrow">Agent Conversation</span>
          <strong>{{ currentSession?.title || '新对话' }}</strong>
        </div>
        <div class="chat-context-actions">
          <span class="mode-status" :class="chatMode">
            {{ chatModeLabel }}
          </span>
        </div>
      </header>

      <div ref="messageList" class="message-list">
        <div v-if="messages.length === 0" class="welcome-area">
          <div class="welcome-mark">
            <img src="/resource/logo.png" alt="AI" @error="handleLogoError" />
          </div>
          <span class="page-eyebrow">Agent Ready</span>
          <h1>告诉 Agent 你想完成什么</h1>
          <p>可以直接描述目标，选择流式或普通方式接收 Agent 的回答。</p>
          <div class="starter-grid">
            <button v-for="starter in starters" :key="starter" type="button" @click="useStarter(starter)">
              {{ starter }}
            </button>
          </div>
        </div>

        <article
          v-for="msg in messages"
          :key="msg.id"
          :class="['message-row', msg.role]"
        >
          <div class="message-avatar">
            <span v-if="msg.role === 'user'">{{ userInitial }}</span>
            <img v-else src="/resource/logo.png" alt="AI" @error="handleLogoError" />
          </div>

          <div class="message-body">
            <div class="message-meta">
              <strong>{{ msg.role === 'user' ? 'You' : 'Agent' }}</strong>
              <span>{{ formatTime(msg.timestamp) }}</span>
              <span v-if="msg.isStreaming" class="streaming-state">生成中</span>
            </div>

            <div v-if="msg.role === 'user'" class="user-content">{{ msg.content }}</div>
            <div v-else class="assistant-content">
              <div v-if="!stripThinkContent(msg.content) && msg.isStreaming" class="thinking-row">
                <span></span><span></span><span></span>
                <small>Agent 正在处理请求</small>
              </div>
              <div v-else class="markdown-content" v-html="renderMarkdown(msg.content)"></div>
            </div>

            <div v-if="msg.role === 'assistant' && msg.content" class="message-actions">
              <button type="button" title="复制" @click="copyMessage(msg.content)">
                <n-icon><CopyIcon /></n-icon><span>复制</span>
              </button>
            </div>
          </div>
        </article>
      </div>

      <footer class="composer-area">
        <div class="composer-shell" :class="{ focused: isInputFocused }">
          <textarea
            ref="inputTextarea"
            v-model="inputText"
            class="chat-textarea"
            placeholder="给 Agent 下达任务，Ctrl + Enter 发送"
            :disabled="loading"
            @keydown="handleKeydown"
            @input="autoResize"
            @focus="isInputFocused = true"
            @blur="isInputFocused = false"
          ></textarea>

          <div class="composer-toolbar">
            <div class="mode-pills">
              <button
                v-for="mode in chatModes"
                :key="mode.value"
                type="button"
                :class="['mode-pill', { active: chatMode === mode.value }]"
                :disabled="loading"
                @click="chatMode = mode.value"
              >
                {{ mode.label }}
              </button>
            </div>

            <div class="composer-actions">
              <button type="button" class="icon-action" :class="{ active: isVoiceActive }" title="语音输入" @click="toggleVoiceInput">
                <n-icon size="18"><MicIcon /></n-icon>
              </button>
              <button v-if="loading" type="button" class="send-btn stop" title="停止生成" @click="stopStreaming">
                <n-icon size="18"><StopIcon /></n-icon>
              </button>
              <button v-else type="button" class="send-btn" :disabled="!inputText.trim()" title="发送" @click="sendMessage">
                <n-icon size="18"><SendIcon /></n-icon>
              </button>
            </div>
          </div>
        </div>
        <small class="composer-hint">对话会保存到当前账号的聊天记录中。</small>
      </footer>
    </section>

    <aside id="chat-execution" class="execution-panel anchor-section">
      <div class="execution-head">
        <span class="page-eyebrow">Execution</span>
        <strong>Agent 执行状态</strong>
        <p>展示当前请求真实生命周期，不伪造具体工具调用。</p>
      </div>

      <div class="execution-summary">
        <div>
          <span>模式</span>
          <strong>{{ chatModeLabel }}</strong>
        </div>
        <div>
          <span>模型</span>
          <strong>后端默认模型</strong>
        </div>
        <div>
          <span>会话</span>
          <strong>{{ currentSession ? `#${currentSession.id}` : '未创建' }}</strong>
        </div>
      </div>

      <div class="execution-steps">
        <div v-for="step in executionSteps" :key="step.label" class="execution-step">
          <span :class="['step-dot', step.status]"></span>
          <div>
            <strong>{{ step.label }}</strong>
            <small>{{ step.description }}</small>
          </div>
        </div>
      </div>

      <div class="execution-note">
        <n-icon><InformationIcon /></n-icon>
        <span>当前 SSE 返回回答正文，这里展示请求阶段。</span>
      </div>

      <div v-if="lastExecutionAt" class="execution-time">
        上次执行：{{ formatTime(lastExecutionAt) }}
      </div>
    </aside>

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
/**
 * Agent 对话工作台。
 *
 * 会话与消息由 Python 管理，页面展示“会话列表 + 对话 + 执行状态”。
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import {
  NButton,
  NDropdown,
  NIcon,
  NInput,
  NModal,
  useMessage
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  ChatbubbleOutline as ChatbubbleIcon,
  ChevronBackOutline as ChevronBackIcon,
  ChevronForwardOutline as ChevronForwardIcon,
  CopyOutline as CopyIcon,
  EllipsisHorizontal as MoreIcon,
  InformationCircleOutline as InformationIcon,
  MicOutline as MicIcon,
  SendOutline as SendIcon,
  StopCircleOutline as StopIcon
} from '@vicons/ionicons5'
import type { ChatMessage, ChatSession } from '@/types'
import { chatHistoryService } from '@/services/api/chat-history'
import { takeAiChatDraft } from '@/services/ai-chat-draft'
import { fetchWithAuth } from '@/services/auth-fetch'
import { renderMarkdown, stripThinkContent } from '@/utils/markdown'
import { formatSessionTime, formatTime } from '@/utils/date-format'
import { useAuthStore } from '@/stores/auth'

type ChatMode = 'stream' | 'normal'
type ExecutionStatus = 'idle' | 'running' | 'done' | 'error'

interface ExecutionStep {
  label: string
  description: string
  status: ExecutionStatus
}

const chatModes: Array<{ label: string; value: ChatMode }> = [
  { label: '流式', value: 'stream' },
  { label: '普通', value: 'normal' }
]

const starters = [
  '总结今天需要我处理的事情',
  '帮我查看最近的重要邮件',
  '根据我的日程安排今天的重点',
  '检索知识库并给出下一步建议'
]

const sessionMenuOptions = [
  { label: '编辑标题', key: 'edit' },
  { label: '清空消息', key: 'clear' },
  { label: '删除会话', key: 'delete' }
]

const message = useMessage()
const authStore = useAuthStore()
const inputText = ref('')
const isInputFocused = ref(false)
const chatMode = ref<ChatMode>('stream')
const loading = ref(false)
const messages = ref<ChatMessage[]>([])
const sessions = ref<ChatSession[]>([])
const currentSession = ref<ChatSession | null>(null)
const messageList = ref<HTMLElement | null>(null)
const sessionListRef = ref<HTMLElement | null>(null)
const inputTextarea = ref<HTMLTextAreaElement | null>(null)
const isListCollapsed = ref(false)
const showEditModal = ref(false)
const editTitle = ref('')
// 正在改标题的会话。必须与 currentSession 解耦：改标题不应把当前会话
// 切换成另一个（否则消息列表仍显示旧会话，下一次发送会写错会话）。
const titleEditingSession = ref<ChatSession | null>(null)
const isVoiceActive = ref(false)
const executionState = ref<ExecutionStatus>('idle')
const lastExecutionAt = ref('')
let abortController: AbortController | null = null

// 本地消息 id：Date.now() 在相邻两次发送（或页面内快速操作）时可能重复，
// 而 id 用作 v-for 的 key，重复会导致 Vue 复用错误的节点。
let localMessageIdSeed = 0
const nextLocalMessageId = (): string => `${Date.now()}-${++localMessageIdSeed}`
let speechRecognition: any = null

const userInitial = computed(() => {
  const name = authStore.user?.displayName || authStore.user?.username || 'U'
  return name.slice(0, 1).toUpperCase()
})
const chatModeLabel = computed(() => {
  if (chatMode.value === 'normal') return '普通对话'
  return '流式对话'
})

const executionSteps = computed<ExecutionStep[]>(() => {
  if (executionState.value === 'idle') {
    return [
      { label: '等待请求', description: '输入目标并发送后开始执行。', status: 'idle' },
      { label: '请求调度', description: '进入 Agent 对话入口。', status: 'idle' },
      { label: '返回结果', description: '接收并展示最终回答。', status: 'idle' }
    ]
  }
  if (executionState.value === 'running') {
    return [
      { label: '接收请求', description: '用户目标已进入当前会话。', status: 'done' },
      { label: 'Agent 执行', description: '后端正在处理请求并返回响应。', status: 'running' },
      { label: '返回结果', description: '等待本次响应结束。', status: 'idle' }
    ]
  }
  if (executionState.value === 'done') {
    return [
      { label: '接收请求', description: '用户目标已进入当前会话。', status: 'done' },
      { label: 'Agent 执行', description: '后端请求已完成。', status: 'done' },
      { label: '返回结果', description: '回答已展示在对话区域。', status: 'done' }
    ]
  }
  return [
    { label: '接收请求', description: '用户目标已进入当前会话。', status: 'done' },
    { label: 'Agent 执行', description: '执行过程中发生错误。', status: 'error' },
    { label: '返回结果', description: '本次请求未正常完成。', status: 'error' }
  ]
})

const toggleSidebar = () => {
  isListCollapsed.value = !isListCollapsed.value
}

const useStarter = (starter: string) => {
  inputText.value = starter
  nextTick(() => inputTextarea.value?.focus())
}

const handleLogoError = (event: Event) => {
  const target = event.target as HTMLImageElement
  target.style.display = 'none'
}

const autoResize = () => {
  if (!inputTextarea.value) return
  inputTextarea.value.style.height = 'auto'
  inputTextarea.value.style.height = `${Math.min(inputTextarea.value.scrollHeight, 180)}px`
}

/** 从后端加载当前用户的会话列表。 */
const loadSessions = async () => {
  try {
    sessions.value = await chatHistoryService.getSessions()
  } catch (error) {
    console.error('加载会话失败', error)
    message.error('加载会话失败')
  }
}

/** 创建一个新会话并切换过去。 */
const createNewSession = async () => {
  try {
    const session = await chatHistoryService.createSession()
    sessions.value.unshift(session)
    await switchSession(session)
    message.success('已创建新会话')
  } catch {
    message.error('创建会话失败')
  }
}

/** 切换会话；旧会话为空时自动清理。 */
const switchSession = async (session: ChatSession) => {
  if (currentSession.value && messages.value.length === 0 && currentSession.value.id !== session.id) {
    try {
      await chatHistoryService.deleteSession(currentSession.value.id)
      sessions.value = sessions.value.filter(item => item.id !== currentSession.value?.id)
    } catch (error) {
      console.error('删除空会话失败', error)
    }
  }

  currentSession.value = session
  messages.value = []
  executionState.value = 'idle'
  try {
    const items = await chatHistoryService.getSessionMessages(session.id)
    if (currentSession.value?.id === session.id) {
      messages.value = items.map(item => ({
        id: item.id.toString(),
        role: item.role,
        content: item.content,
        timestamp: item.createTime,
        isStreaming: false
      }))
      await nextTick()
      scrollToBottom()
    }
  } catch (error) {
    console.error('加载消息失败', error)
    message.error('加载消息失败')
  }
}

/** 处理会话标题、清空和删除操作。 */
const handleSessionMenu = async (key: string, session: ChatSession) => {
  try {
    if (key === 'edit') {
      // 只记录待编辑的会话，不改动 currentSession，避免与消息列表脱节。
      titleEditingSession.value = session
      editTitle.value = session.title
      showEditModal.value = true
      return
    }
    if (key === 'clear') {
      Object.assign(session, await chatHistoryService.clearSessionMessages(session.id))
      sessionStorage.removeItem(pendingTurnKey(session.id))
      if (currentSession.value?.id === session.id) messages.value = []
      message.success('已清空消息')
      return
    }
    if (key === 'delete') {
      await chatHistoryService.deleteSession(session.id)
      sessionStorage.removeItem(pendingTurnKey(session.id))
      sessions.value = sessions.value.filter(item => item.id !== session.id)
      if (currentSession.value?.id === session.id) {
        currentSession.value = null
        messages.value = []
      }
      message.success('已删除会话')
    }
  } catch {
    message.error('操作失败')
  }
}

/** 保存会话标题（可针对非当前会话）。 */
const saveSessionTitle = async () => {
  const target = titleEditingSession.value
  if (!target || !editTitle.value.trim()) return
  try {
    const updated = await chatHistoryService.updateSessionTitle(target.id, editTitle.value.trim())
    Object.assign(target, updated)
    const listed = sessions.value.find(item => item.id === updated.id)
    if (listed) Object.assign(listed, updated)
    if (currentSession.value?.id === target.id) {
      Object.assign(currentSession.value, updated)
    }
    message.success('标题已更新')
  } catch {
    message.error('保存失败')
  } finally {
    showEditModal.value = false
    titleEditingSession.value = null
  }
}

/** 生成一次发送动作的稳定标识。 */
const createClientRequestId = (): string => {
  const globalCrypto = window.crypto as Crypto | undefined
  if (globalCrypto?.randomUUID) {
    return globalCrypto.randomUUID()
  }
  // 保持合法 UUID 格式；客户端请求 ID 不是认证凭据。
  const bytes = new Uint8Array(16)
  if (globalCrypto?.getRandomValues) globalCrypto.getRandomValues(bytes)
  else for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256)
  bytes[6] = (bytes[6]! & 0x0f) | 0x40
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

const pendingTurnKey = (sessionId: string) =>
  `agent-demo:user:${authStore.user?.id}:chat:${sessionId}:pending-turn`

const getPendingTurn = (sessionId: string): { id: string; message: string } | null => {
  try { return JSON.parse(sessionStorage.getItem(pendingTurnKey(sessionId)) || 'null') }
  catch { return null }
}

/** 发送用户消息，并按当前模式调用 Python 普通或流式接口。 */
const sendMessage = async () => {
  const queryText = inputText.value.trim()
  if (!queryText || loading.value) return

  if (!currentSession.value) {
    try {
      const session = await chatHistoryService.createSession()
      sessions.value.unshift(session)
      currentSession.value = session
    } catch {
      message.error('创建会话失败')
      return
    }
  }

  const sendingSessionId = currentSession.value!.id
  const pending = getPendingTurn(sendingSessionId)
  if (pending && pending.message !== queryText) {
    inputText.value = pending.message
    message.warning('上一条请求尚未确认完成，请重新发送该消息以恢复，或先清空会话。')
    return
  }

  const userMessage: ChatMessage = {
    id: nextLocalMessageId(),
    role: 'user',
    content: queryText,
    timestamp: new Date().toISOString()
  }
  messages.value.push(userMessage)
  inputText.value = ''
  loading.value = true
  executionState.value = 'running'
  if (inputTextarea.value) inputTextarea.value.style.height = 'auto'

  // 本次发送的稳定标识：同一次发送的重试复用同一个值，
  // 让后端派生的业务幂等键（例如创建日程）在重试时保持一致。
  const clientRequestId = pending?.id || createClientRequestId()
  sessionStorage.setItem(pendingTurnKey(sendingSessionId), JSON.stringify({ id: clientRequestId, message: queryText }))

  const assistantMessage: ChatMessage = {
    id: nextLocalMessageId(),
    role: 'assistant',
    content: '',
    timestamp: new Date().toISOString(),
    isStreaming: true
  }
  messages.value.push(assistantMessage)
  await nextTick()
  scrollToBottom()

  let turnFailed = false
  try {
    if (chatMode.value === 'normal') {
      await normalChat(queryText, assistantMessage, clientRequestId)
    } else {
      await streamChat(queryText, assistantMessage, clientRequestId)
    }
    executionState.value = 'done'
    sessionStorage.removeItem(pendingTurnKey(sendingSessionId))
  } catch (error: any) {
    inputText.value = queryText
    turnFailed = true
    if (error?.name === 'AbortError') {
      executionState.value = 'idle'
    } else {
      turnFailed = true
      // 已经渲染出来的内容不能被错误文案覆盖：用户已经读到的回复应当保留。
      if (assistantMessage.content) {
        assistantMessage.content += '\n\n> 本次响应未完整结束，以上为已接收到的内容。'
      } else {
        assistantMessage.content = '抱歉，本次请求执行失败，请稍后重试。'
      }
      message.error(error?.message || '发送失败，请重试')
      executionState.value = 'error'
    }
  } finally {
    loading.value = false
    abortController = null
    assistantMessage.isStreaming = false
    lastExecutionAt.value = new Date().toISOString()
    await nextTick()
    scrollToBottom()
    // 失败的回合不回拉服务端列表：后端可能没有记录该回合，
    // 覆盖会把用户刚看到的问题和错误提示一起抹掉。
    if (currentSession.value && !turnFailed) {
      await refreshCurrentSession().catch(() => {})
    }
  }
}

const refreshCurrentSession = async () => {
  const session = currentSession.value
  if (!session) return
  const [updated, items] = await Promise.all([
    chatHistoryService.getSessions(),
    chatHistoryService.getSessionMessages(session.id)
  ])
  sessions.value = updated
  if (currentSession.value?.id !== session.id) return
  currentSession.value = updated.find(item => item.id === session.id) || session
  messages.value = items.map(item => ({
    id: String(item.id), role: item.role, content: item.content,
    timestamp: item.createTime, isStreaming: false
  }))
}

/** 判断一个原始 SSE 事件是否为结束标记（兼容 data: 与 event: 两种写法）。 */
const isDoneEvent = (rawEvent: string): boolean =>
  /(?:^|\n)event:\s*done\s*(?:\n|$)/i.test(rawEvent) ||
  /(?:^|\n)data:\s*\[DONE\]\s*(?:\n|$)/i.test(rawEvent)

/** 解析 SSE 事件中的 data 内容，忽略事件元信息和结束标记。 */
const parseSseEvents = (rawEvent: string): string[] => {
  const dataLines: string[] = []
  for (const line of rawEvent.replace(/\r/g, '').split('\n')) {
    if (line.startsWith('data:')) {
      const rawData = line.slice(5)
      // SSE 只移除一个分隔空格，保留模型片段自身的前导空格。
      const data = rawData.startsWith(' ') ? rawData.slice(1) : rawData
      if (data !== '[DONE]') dataLines.push(data)
    } else if (line && !line.startsWith(':') && !line.startsWith('event:') && !line.startsWith('id:') && !line.startsWith('retry:')) {
      dataLines.push(line)
    }
  }
  const data = dataLines.join('\n')
  return data ? [data] : []
}

/**
 * 从 Python 读取流式 Agent 回复。
 *
 * 只有收到 [DONE] 才确认服务端已完整写入回复。EOF/断开保留可见片段和
 * 请求标识，供重试恢复；尾部残留帧同样识别 [DONE]。
 */
const streamChat = async (query: string, messageObj: ChatMessage, clientRequestId: string) => {
  abortController = new AbortController()
  const apiPath = '/api/chat/turn/stream'

  const response = await fetchWithAuth(apiPath, {
    method: 'POST',
    signal: abortController.signal,
    headers: { Accept: 'text/event-stream', 'Content-Type': 'application/json', 'Cache-Control': 'no-cache' },
    body: JSON.stringify({
      session_id: currentSession.value!.id,
      message: query,
      client_request_id: clientRequestId
    })
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)

  const reader = response.body?.getReader()
  if (!reader) throw new Error('响应流不可用')

  const decoder = new TextDecoder()
  let buffer = ''
  let rawContent = ''
  let completed = false

  const appendVisibleChunk = async (chunk: string) => {
    rawContent += chunk
    const visibleContent = stripThinkContent(rawContent)
    if (visibleContent === messageObj.content) return
    messageObj.content = visibleContent
    await nextTick()
    scrollToBottom()
  }

  readStream: while (true) {
    const { done, value } = await reader.read()
    if (done) {
      buffer += decoder.decode()
      // 上游正常关闭连接：尾部可能还残留未成帧的数据。
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const events = buffer.split(/\r?\n\r?\n/)
    buffer = events.pop() || ''

    for (const event of events) {
      if (!event.trim()) continue
      if (isDoneEvent(event)) {
        completed = true
        buffer = ''
        break readStream
      }
      for (const chunk of parseSseEvents(event)) await appendVisibleChunk(chunk)
    }
  }

  // 尾部残留同样要识别结束标记：否则最后一帧 [DONE] 会被当成普通内容丢掉，
  // 并且因为没有置 completed 而被误判为「响应流提前结束」。
  if (buffer.trim() && !completed) {
    if (isDoneEvent(buffer)) {
      completed = true
    } else {
      for (const chunk of parseSseEvents(buffer)) await appendVisibleChunk(chunk)
    }
  }

  if (!completed) {
    // Only DONE proves the server committed the complete reply. Keep the request
    // identity on EOF so replay can recover a result whose response was lost.
    throw new Error('响应流提前结束，请重试以恢复本次请求')
  }
}

/** 调用普通非流式聊天接口。 */
const normalChat = async (query: string, messageObj: ChatMessage, clientRequestId: string) => {
  abortController = new AbortController()
  const response = await fetchWithAuth('/api/chat/turn', {
    method: 'POST',
    signal: abortController.signal,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      session_id: currentSession.value!.id,
      message: query,
      client_request_id: clientRequestId
    })
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  const payload = await response.json()
  messageObj.content = payload.content
}

/** 中止当前流式响应。 */
const stopStreaming = () => {
  abortController?.abort()
  executionState.value = 'idle'
  const lastMessage = messages.value[messages.value.length - 1]
  if (lastMessage?.isStreaming) {
    lastMessage.isStreaming = false
    if (!lastMessage.content) lastMessage.content = '[已停止]'
  }
}

const copyMessage = async (content: string) => {
  try {
    await navigator.clipboard.writeText(content)
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && event.ctrlKey) {
    event.preventDefault()
    loading.value ? stopStreaming() : sendMessage()
  }
}

/** 启动或停止浏览器语音识别。 */
const toggleVoiceInput = () => {
  const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
  if (!SpeechRecognition) {
    message.warning('当前浏览器不支持语音输入')
    return
  }

  if (isVoiceActive.value && speechRecognition) {
    speechRecognition.stop()
    return
  }

  speechRecognition = new SpeechRecognition()
  speechRecognition.lang = 'zh-CN'
  speechRecognition.interimResults = true
  speechRecognition.continuous = false
  isVoiceActive.value = true
  speechRecognition.onresult = (event: any) => {
    const transcript = Array.from(event.results)
      .map((result: any) => result[0]?.transcript || '')
      .join('')
      .trim()
    if (transcript) {
      inputText.value = transcript
      nextTick(autoResize)
    }
  }
  speechRecognition.onerror = () => {
    isVoiceActive.value = false
    message.error('语音识别失败，请检查麦克风权限')
  }
  speechRecognition.onend = () => {
    isVoiceActive.value = false
  }
  speechRecognition.start()
}

const scrollToBottom = () => {
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}

onBeforeRouteLeave(async () => {
  if (currentSession.value && messages.value.length === 0) {
    try {
      await chatHistoryService.deleteSession(currentSession.value.id)
    } catch (error) {
      console.error('离开页面时删除空会话失败', error)
    }
  }
  return true
})

onMounted(async () => {
  await loadSessions()
  if (sessions.value.length > 0) {
    await switchSession(sessions.value[0])
  }
  // 读取日程页转交的描述：只预填输入框，不自动发送，
  // 避免用户还没确认就把内容发出去。
  const draft = takeAiChatDraft()
  if (draft) {
    inputText.value = draft
    await nextTick()
    inputTextarea.value?.focus()
  }
})

onUnmounted(() => {
  abortController?.abort()
  speechRecognition?.stop?.()
  abortController = null
})
</script>

<style scoped>
.agent-chat-page {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr) 280px;
  gap: 14px;
  width: 100%;
  height: calc(100vh - 124px);
  min-height: 560px;
  overflow: hidden;
  color: var(--text-primary);
}

.anchor-section {
  scroll-margin-top: 18px;
}

.page-eyebrow {
  color: var(--primary-color);
  font-size: 0.64rem;
  font-weight: 750;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

.session-sidebar,
.chat-main,
.execution-panel {
  min-width: 0;
  border: 1px solid var(--workspace-border, var(--border-light));
  border-radius: 14px;
  background: var(--bg-card);
  box-shadow: none;
}

.session-sidebar {
  display: flex;
  flex-direction: column;
  width: 248px;
  min-width: 248px;
  padding: 12px;
  overflow: hidden;
  transition: width 160ms ease, min-width 160ms ease;
}

.session-sidebar.collapsed {
  width: 58px;
  min-width: 58px;
  padding-inline: 8px;
}

.session-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  gap: 8px;
  padding: 0 2px 10px;
  border-bottom: 1px solid var(--workspace-border, var(--border-light));
}

.session-head > div {
  display: grid;
  gap: 2px;
}

.session-head strong {
  font-size: 0.9rem;
}

.sidebar-toggle,
.new-chat-btn,
.session-item,
.message-actions button,
.icon-action,
.send-btn,
.starter-grid button,
.mode-pill {
  border: 0;
  font: inherit;
  cursor: pointer;
}

.sidebar-toggle {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--bg-input);
  color: var(--text-muted);
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 38px;
  margin: 12px 0;
  padding: 0 12px;
  border-radius: 9px;
  background: var(--primary-color);
  color: white;
  font-size: 0.76rem;
  font-weight: 700;
}

.session-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.session-item {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) 26px;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 52px;
  padding: 7px 8px;
  border-radius: 9px;
  background: transparent;
  color: inherit;
  text-align: left;
}

.session-item + .session-item {
  margin-top: 3px;
}

.session-item:hover,
.session-item.active {
  background: var(--bg-input);
}

.session-item.active {
  box-shadow: inset 3px 0 0 var(--primary-color);
}

.session-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--primary-color) 9%, var(--bg-card));
  color: var(--primary-color);
}

.session-info {
  display: grid;
  min-width: 0;
}

.session-info strong,
.session-info small {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.session-info strong {
  font-size: 0.74rem;
}

.session-info small {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 0.62rem;
}

.session-menu-btn {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  color: var(--text-muted);
}

.session-menu-btn:hover {
  background: var(--bg-card);
  color: var(--text-primary);
}

.empty-sessions {
  display: grid;
  place-items: center;
  gap: 8px;
  padding: 44px 12px;
  color: var(--text-muted);
  font-size: 0.72rem;
}

.chat-main {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.chat-context-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 62px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--workspace-border, var(--border-light));
}

.chat-context-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.chat-context-copy strong {
  overflow: hidden;
  font-size: 0.86rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.chat-context-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mode-status {
  padding: 4px 8px;
  border-radius: 99px;
  background: var(--bg-input);
  color: var(--text-muted);
  font-size: 0.64rem;
}

.mode-status.mcp {
  background: color-mix(in srgb, var(--primary-color) 11%, var(--bg-card));
  color: var(--primary-color);
}

.message-list {
  flex: 1;
  min-height: 0;
  padding: 22px clamp(18px, 4vw, 56px);
  overflow-y: auto;
}

.welcome-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100%;
  max-width: 760px;
  margin: 0 auto;
  text-align: center;
}

.welcome-mark {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin-bottom: 16px;
  border-radius: 15px;
  background: color-mix(in srgb, var(--primary-color) 13%, var(--bg-input));
}

.welcome-mark img {
  width: 32px;
  height: 32px;
  object-fit: contain;
}

.welcome-area h1 {
  margin: 9px 0 0;
  font-size: clamp(1.45rem, 3vw, 2rem);
  letter-spacing: -0.035em;
}

.welcome-area p {
  max-width: 600px;
  margin: 10px 0 0;
  color: var(--text-secondary);
  font-size: 0.8rem;
  line-height: 1.7;
}

.starter-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  width: 100%;
  margin-top: 24px;
}

.starter-grid button {
  min-height: 54px;
  padding: 10px 12px;
  border: 1px solid var(--workspace-border, var(--border-light));
  border-radius: 10px;
  background: var(--bg-input);
  color: var(--text-secondary);
  font-size: 0.72rem;
  text-align: left;
}

.starter-grid button:hover {
  border-color: color-mix(in srgb, var(--primary-color) 38%, var(--workspace-border, var(--border-light)));
  color: var(--text-primary);
}

.message-row {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 12px;
  max-width: 920px;
  margin: 0 auto;
  padding: 18px 0;
  border-bottom: 1px solid var(--workspace-border, var(--border-light));
}

.message-row:last-child {
  border-bottom: 0;
}

.message-avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: var(--bg-input);
  color: var(--text-secondary);
  font-size: 0.72rem;
  font-weight: 750;
}

.message-row.assistant .message-avatar {
  background: color-mix(in srgb, var(--primary-color) 12%, var(--bg-input));
}

.message-avatar img {
  width: 20px;
  height: 20px;
  object-fit: contain;
}

.message-body {
  min-width: 0;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.message-meta strong {
  font-size: 0.74rem;
}

.message-meta span {
  color: var(--text-muted);
  font-size: 0.62rem;
}

.streaming-state {
  color: var(--primary-color) !important;
}

.user-content,
.assistant-content {
  color: var(--text-primary);
  font-size: 0.86rem;
  line-height: 1.8;
  overflow-wrap: anywhere;
}

.user-content {
  white-space: pre-wrap;
}

.markdown-content :deep(p) {
  margin: 0 0 0.8em;
}

.markdown-content :deep(p:last-child) {
  margin-bottom: 0;
}

.markdown-content :deep(pre) {
  overflow-x: auto;
  padding: 12px;
  border-radius: 10px;
  background: var(--bg-input);
}

.thinking-row {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--text-muted);
}

.thinking-row > span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary-color);
  animation: pulse 1s infinite ease-in-out;
}

.thinking-row > span:nth-child(2) { animation-delay: 120ms; }
.thinking-row > span:nth-child(3) { animation-delay: 240ms; }

.thinking-row small {
  margin-left: 4px;
  font-size: 0.68rem;
}

@keyframes pulse {
  0%, 100% { opacity: 0.3; transform: translateY(0); }
  50% { opacity: 1; transform: translateY(-2px); }
}

.message-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 10px;
}

.message-actions button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  padding: 0 7px;
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.62rem;
}

.message-actions button:hover {
  background: var(--bg-input);
  color: var(--text-primary);
}

.composer-area {
  padding: 12px 18px 14px;
  border-top: 1px solid var(--workspace-border, var(--border-light));
  background: var(--bg-card);
}

.composer-shell {
  max-width: 920px;
  margin: 0 auto;
  border: 1px solid var(--workspace-border, var(--border-light));
  border-radius: 13px;
  background: var(--bg-input);
  transition: border-color 120ms ease, box-shadow 120ms ease;
}

.composer-shell.focused {
  border-color: color-mix(in srgb, var(--primary-color) 55%, var(--workspace-border, var(--border-light)));
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--primary-color) 10%, transparent);
}

.chat-textarea {
  display: block;
  width: 100%;
  min-height: 54px;
  max-height: 180px;
  resize: none;
  padding: 14px 14px 8px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-size: 0.82rem;
  line-height: 1.55;
}

.composer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 8px 8px;
}

.mode-pills,
.composer-actions {
  display: flex;
  align-items: center;
  gap: 5px;
}

.mode-pill {
  min-height: 28px;
  padding: 0 8px;
  border-radius: 7px;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.64rem;
}

.mode-pill:hover,
.mode-pill.active {
  background: var(--bg-card);
  color: var(--text-primary);
}

.mode-pill.active {
  color: var(--primary-color);
}

.icon-action,
.send-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
}

.icon-action {
  background: transparent;
  color: var(--text-muted);
}

.icon-action:hover,
.icon-action.active {
  background: var(--bg-card);
  color: var(--primary-color);
}

.send-btn {
  background: var(--primary-color);
  color: white;
}

.send-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.send-btn.stop {
  background: #ef4444;
}

.composer-hint {
  display: block;
  max-width: 920px;
  margin: 7px auto 0;
  color: var(--text-muted);
  font-size: 0.6rem;
  text-align: center;
}

.execution-panel {
  padding: 16px;
  overflow-y: auto;
}

.execution-head {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--workspace-border, var(--border-light));
}

.execution-head strong {
  display: block;
  margin-top: 5px;
  font-size: 0.9rem;
}

.execution-head p {
  margin: 6px 0 0;
  color: var(--text-muted);
  font-size: 0.68rem;
  line-height: 1.5;
}

.execution-summary {
  display: grid;
  grid-template-columns: 1fr;
  gap: 7px;
  padding: 14px 0;
  border-bottom: 1px solid var(--workspace-border, var(--border-light));
}

.execution-summary > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 7px 9px;
  border-radius: 8px;
  background: var(--bg-input);
}

.execution-summary span {
  color: var(--text-muted);
  font-size: 0.64rem;
}

.execution-summary strong {
  overflow: hidden;
  max-width: 150px;
  font-size: 0.68rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.execution-steps {
  display: grid;
  gap: 14px;
  padding: 16px 2px;
}

.execution-step {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr);
  gap: 9px;
}

.step-dot {
  width: 8px;
  height: 8px;
  margin-top: 4px;
  border-radius: 50%;
  background: #94a3b8;
}

.step-dot.running { background: var(--primary-color); box-shadow: 0 0 0 4px color-mix(in srgb, var(--primary-color) 10%, transparent); }
.step-dot.done { background: #22c55e; }
.step-dot.error { background: #ef4444; }

.execution-step div {
  display: grid;
  gap: 3px;
}

.execution-step strong {
  font-size: 0.72rem;
}

.execution-step small {
  color: var(--text-muted);
  font-size: 0.64rem;
  line-height: 1.5;
}

.execution-note {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 8px;
  padding: 10px;
  border-radius: 9px;
  background: var(--bg-input);
  color: var(--text-muted);
  font-size: 0.62rem;
  line-height: 1.55;
}

.execution-note :deep(svg) {
  color: var(--primary-color);
}

.execution-time {
  margin-top: 12px;
  color: var(--text-muted);
  font-size: 0.6rem;
}

@media (max-width: 1280px) {
  .agent-chat-page {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .execution-panel {
    display: none;
  }
}

@media (max-width: 900px) {
  .agent-chat-page {
    grid-template-columns: 64px minmax(0, 1fr);
  }

  .session-sidebar {
    width: 64px;
    min-width: 64px;
    padding-inline: 8px;
  }

  .session-sidebar:not(.collapsed) .session-list,
  .session-sidebar:not(.collapsed) .session-head > div,
  .session-sidebar:not(.collapsed) .new-chat-btn span {
    display: none;
  }
}

@media (max-width: 767px) {
  .agent-chat-page {
    display: block;
    height: calc(100vh - 164px);
    min-height: 480px;
  }

  .session-sidebar {
    display: none;
  }

  .chat-main {
    height: 100%;
  }

  .chat-context-bar {
    min-height: 54px;
    padding: 8px 10px;
  }

  .message-list {
    padding: 14px 12px;
  }

  .starter-grid {
    grid-template-columns: 1fr;
  }

  .composer-area {
    padding: 9px 10px 10px;
  }
}
</style>
