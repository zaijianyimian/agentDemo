<template>
  <div class="chat-import-page">
    <!-- 页面标题 -->
    <header class="page-header">
      <h1 class="page-title">聊天记录导入</h1>
      <p class="page-subtitle">导入微信、QQ、Telegram 等平台的聊天记录，创建个性化虚拟助手</p>
    </header>

    <!-- 主容器 - 居中布局 -->
    <div class="main-container">
      <!-- 分段控制器 (Segmented Control) -->
      <div class="segment-control">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          :class="['segment-item', { active: activeTab === tab.value }]"
          @click="activeTab = tab.value"
        >
          <n-icon size="16"><component :is="tab.icon" /></n-icon>
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <!-- 内容区域 -->
      <div class="content-area">
        <!-- 导入记录 -->
        <div v-show="activeTab === 'import'" class="tab-panel">
          <div class="content-card">
            <!-- 上传拖拽区 -->
            <div
              :class="['upload-zone', { 'is-dragging': isDragging }]"
              @dragenter.prevent="handleDragEnter"
              @dragleave.prevent="handleDragLeave"
              @dragover.prevent
              @drop.prevent="handleDrop"
              @click="triggerFileSelect"
            >
              <div class="upload-icon">
                <n-icon size="40"><CloudUploadOutline /></n-icon>
              </div>
              <div class="upload-text">
                <p class="upload-primary">点击或拖拽文件到此区域上传</p>
                <p class="upload-secondary">支持 TXT、JSON、LOG 格式的聊天记录导出文件</p>
              </div>
              <button class="upload-btn" @click.stop="triggerFileSelect">
                选择文件
              </button>
              <input
                ref="fileInputRef"
                type="file"
                accept=".txt,.json,.log"
                class="file-input-hidden"
                @change="handleFileSelect"
              />
            </div>

            <!-- 平台选择 -->
            <div class="platform-row">
              <span class="platform-label">目标平台</span>
              <div class="platform-select" @click="showPlatformMenu = true">
                <span>{{ selectedPlatformLabel }}</span>
                <n-icon size="14"><ChevronDownOutline /></n-icon>
              </div>
            </div>

            <!-- 上传进度 -->
            <div v-if="uploading" class="upload-progress">
              <div class="progress-track">
                <div class="progress-bar" :style="{ width: progressWidth }"></div>
              </div>
              <span class="progress-text">正在解析数据...</span>
            </div>

            <!-- 导入结果 -->
            <div v-if="importResult" class="result-section">
              <div :class="['result-card', importResult.success ? 'success' : 'error']">
                <div class="result-icon">
                  <n-icon size="24">
                    <CheckmarkCircleOutline v-if="importResult.success" />
                    <CloseCircleOutline v-else />
                  </n-icon>
                </div>
                <div class="result-body">
                  <div class="result-count">
                    <span class="count-num">{{ importResult.importedCount }}</span>
                    <span class="count-label">条消息已导入</span>
                  </div>
                  <div class="result-meta">
                    <span>{{ importResult.sessionCount }} 个会话</span>
                    <span>{{ importResult.platform }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 会话管理 -->
        <div v-show="activeTab === 'sessions'" class="tab-panel">
          <div class="content-card">
            <div class="card-header-row">
              <h3 class="card-title">已导入会话</h3>
              <div class="filter-select" @click="showFilterMenu = true">
                <n-icon size="14"><FilterOutline /></n-icon>
                <span>{{ filterPlatformLabel }}</span>
              </div>
            </div>

            <div class="sessions-list">
              <div
                v-for="session in sessionData"
                :key="session.sessionId"
                class="session-item"
              >
                <div class="session-icon">
                  <n-icon size="18"><ChatbubbleOutline /></n-icon>
                </div>
                <div class="session-info">
                  <div class="session-id">{{ session.sessionId }}</div>
                  <div class="session-meta">
                    <span class="platform-tag">{{ session.platform }}</span>
                    <span class="msg-count">{{ session.messageCount }} 条</span>
                  </div>
                </div>
                <div class="session-actions">
                  <button class="action-btn" title="查看" @click="viewSessionMessages(session.sessionId)">
                    <n-icon size="14"><EyeOutline /></n-icon>
                  </button>
                  <button class="action-btn danger" title="删除" @click="deleteSession(session.sessionId)">
                    <n-icon size="14"><TrashOutline /></n-icon>
                  </button>
                </div>
              </div>

              <div v-if="sessionData.length === 0" class="empty-state">
                <n-icon size="36" class="empty-icon"><FolderOpenOutline /></n-icon>
                <span>暂无导入的会话</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 虚拟助手 -->
        <div v-show="activeTab === 'assistants'" class="tab-panel">
          <div class="content-card">
            <div class="card-header-row">
              <h3 class="card-title">虚拟助手</h3>
              <button class="create-btn" @click="openCreateModal">
                <n-icon size="14"><AddOutline /></n-icon>
                <span>创建助手</span>
              </button>
            </div>

            <div class="assistants-list">
              <div
                v-for="assistant in assistants"
                :key="assistant.id"
                :class="['assistant-item', { disabled: !assistant.enabled }]"
              >
                <div class="assistant-header">
                  <div class="assistant-avatar">
                    <n-icon size="20"><PersonOutline /></n-icon>
                  </div>
                  <div class="assistant-name">{{ assistant.name }}</div>
                  <button
                    :class="['toggle-btn', { active: assistant.enabled }]"
                    @click="toggleAssistant(assistant)"
                  >
                    <span class="toggle-dot"></span>
                  </button>
                </div>
                <div class="assistant-body">
                  <div class="assistant-tag">
                    <n-icon size="12"><CallOutline /></n-icon>
                    {{ assistant.sourcePlatform }}
                  </div>
                  <div class="assistant-stat">
                    <span class="stat-value">{{ assistant.trainedMessages }}</span>
                    <span class="stat-label">训练消息</span>
                  </div>
                  <p v-if="assistant.personalitySummary" class="personality-text">
                    {{ assistant.personalitySummary }}
                  </p>
                </div>
                <div class="assistant-footer">
                  <button class="footer-btn" @click="openAssistantChat(assistant)">
                    <n-icon size="12"><ChatbubblesOutline /></n-icon>
                    对话
                  </button>
                  <button class="footer-btn" @click="analyzePersonality(assistant.id)">
                    <n-icon size="12"><AnalyticsOutline /></n-icon>
                    分析
                  </button>
                  <button class="footer-btn danger" @click="deleteAssistant(assistant.id)">
                    <n-icon size="12"><TrashOutline /></n-icon>
                  </button>
                </div>
              </div>

              <div v-if="assistants.length === 0" class="empty-state">
                <n-icon size="36" class="empty-icon"><SparklesOutline /></n-icon>
                <span>暂无虚拟助手</span>
                <button class="create-btn-inline" @click="openCreateModal">
                  立即创建
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 平台选择下拉 -->
    <Teleport to="body">
      <div v-if="showPlatformMenu" class="dropdown-overlay" @click="showPlatformMenu = false">
        <div class="dropdown-menu">
          <button
            v-for="opt in platformOptions"
            :key="opt.value"
            :class="['dropdown-item', { active: selectedPlatform === opt.value }]"
            @click="selectedPlatform = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>
    </Teleport>

    <!-- 筛选下拉 -->
    <Teleport to="body">
      <div v-if="showFilterMenu" class="dropdown-overlay" @click="showFilterMenu = false">
        <div class="dropdown-menu">
          <button
            v-for="opt in [{ label: '全部平台', value: '' }, ...platformOptions.slice(1)]"
            :key="opt.value"
            :class="['dropdown-item', { active: filterPlatform === opt.value }]"
            @click="filterPlatform = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>
    </Teleport>

    <!-- 创建助手弹窗 -->
    <Teleport to="body">
      <div v-if="showCreateAssistant" class="modal-overlay" @click.self="showCreateAssistant = false">
        <div class="modal-card">
          <div class="modal-header">
            <h3>创建虚拟助手</h3>
            <button class="modal-close" @click="showCreateAssistant = false">
              <n-icon size="18"><CloseOutline /></n-icon>
            </button>
          </div>
          <div class="modal-body">
            <div class="form-field">
              <label>名称</label>
              <input
                v-model="assistantForm.name"
                type="text"
                class="form-input"
                placeholder="为助手起个名字"
              />
            </div>
            <div class="form-field">
              <label>描述</label>
              <textarea
                v-model="assistantForm.description"
                class="form-input"
                placeholder="描述这个助手的特点"
                rows="3"
              ></textarea>
            </div>
            <div class="form-field">
              <label>平台</label>
              <div class="form-select" @click="showAssistantPlatformMenu = true">
                <span>{{ assistantPlatformLabel }}</span>
                <n-icon size="14"><ChevronDownOutline /></n-icon>
              </div>
            </div>
            <div class="form-field">
              <label>训练会话</label>
              <div class="session-tags">
                <button
                  v-for="opt in sessionSelectOptions"
                  :key="opt.value"
                  :class="['session-tag', { selected: assistantForm.sessionIds.includes(opt.value) }]"
                  @click="toggleSessionSelect(opt.value)"
                >
                  {{ opt.label }}
                </button>
              </div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn-cancel" @click="showCreateAssistant = false">取消</button>
            <button class="btn-confirm" :disabled="creating" @click="createAssistant">
              <n-icon v-if="creating" size="14" class="spin"><RefreshOutline /></n-icon>
              {{ creating ? '创建中...' : '创建' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 助手平台选择下拉 -->
    <Teleport to="body">
      <div v-if="showAssistantPlatformMenu" class="dropdown-overlay" @click="showAssistantPlatformMenu = false">
        <div class="dropdown-menu">
          <button
            v-for="opt in platformOptions.slice(1)"
            :key="opt.value"
            :class="['dropdown-item', { active: assistantForm.platform === opt.value }]"
            @click="assistantForm.platform = opt.value"
          >
            {{ opt.label }}
          </button>
        </div>
      </div>
    </Teleport>

    <!-- 助手对话弹窗 -->
    <Teleport to="body">
      <div v-if="showChatModal" class="modal-overlay" @click.self="showChatModal = false">
        <div class="modal-card chat-modal">
          <div class="modal-header">
            <div class="chat-header-left">
              <div class="chat-avatar">
                <n-icon size="18"><PersonOutline /></n-icon>
              </div>
              <span>{{ chatAssistant?.name }}</span>
            </div>
            <button class="modal-close" @click="showChatModal = false">
              <n-icon size="18"><CloseOutline /></n-icon>
            </button>
          </div>
          <div ref="chatMessagesRef" class="chat-messages">
            <div
              v-for="(msg, index) in chatMessages"
              :key="index"
              :class="['chat-bubble', msg.role]"
            >
              {{ msg.content }}
            </div>
            <div v-if="chatMessages.length === 0" class="chat-empty">
              开始与 {{ chatAssistant?.name }} 对话
            </div>
          </div>
          <div class="chat-input-area">
            <input
              v-model="chatInput"
              type="text"
              class="chat-input"
              placeholder="输入消息..."
              @keypress.enter="sendChatMessage"
            />
            <button class="send-btn" :disabled="chatting" @click="sendChatMessage">
              <n-icon size="16"><SendOutline /></n-icon>
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 会话消息详情 -->
    <Teleport to="body">
      <div v-if="showSessionMessages" class="modal-overlay" @click.self="showSessionMessages = false">
        <div class="modal-card session-modal">
          <div class="modal-header">
            <h3>{{ selectedSessionId }}</h3>
            <button class="modal-close" @click="showSessionMessages = false">
              <n-icon size="18"><CloseOutline /></n-icon>
            </button>
          </div>
          <div class="session-message-list">
            <div v-if="loadingSessionMessages" class="chat-empty">正在加载消息...</div>
            <div
              v-for="(item, index) in sessionMessages"
              :key="`${item.sender}-${item.messageTime || index}`"
              class="session-message"
            >
              <div class="session-message-meta">
                <span>{{ item.sender || '未知发送者' }}</span>
                <time>{{ formatMessageTime(item.messageTime || item.createTime) }}</time>
              </div>
              <p>{{ item.content }}</p>
            </div>
            <div v-if="!loadingSessionMessages && sessionMessages.length === 0" class="chat-empty">
              暂无消息
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { NIcon, useMessage } from 'naive-ui'
import {
  CloudUploadOutline,
  ChatbubbleOutline,
  ChatbubblesOutline,
  PersonOutline,
  CallOutline,
  ChevronDownOutline,
  FilterOutline,
  AddOutline,
  EyeOutline,
  TrashOutline,
  CloseOutline,
  CheckmarkCircleOutline,
  CloseCircleOutline,
  FolderOpenOutline,
  SparklesOutline,
  SendOutline,
  AnalyticsOutline,
  RefreshOutline
} from '@vicons/ionicons5'
import api from '@/services/api'

// Types
interface ChatImportResult {
  success: boolean
  importedCount: number
  sessionCount: number
  platform: string
  errorMessage?: string
  sessionIds?: string[]
}

interface VirtualAssistant {
  id: number
  name: string
  description?: string
  sourcePlatform: string
  trainedMessages: number
  personalitySummary?: string
  enabled: boolean
}

interface SessionRow {
  sessionId: string
  platform: string
  messageCount: number
  createTime: string
}

interface ImportedChatMessage {
  sender?: string
  content: string
  messageTime?: string
  createTime?: string
  platform?: string
}

// State
const message = useMessage()
const activeTab = ref('import')
const selectedPlatform = ref('auto')
const importResult = ref<ChatImportResult | null>(null)
const uploading = ref(false)
const progressWidth = ref('0%')
const isDragging = ref(false)
const dragCounter = ref(0)
const fileInputRef = ref<HTMLInputElement | null>(null)
const showPlatformMenu = ref(false)
const showFilterMenu = ref(false)
const loadingSessions = ref(false)
const sessionData = ref<SessionRow[]>([])
const filterPlatform = ref('')
const assistants = ref<VirtualAssistant[]>([])
const showCreateAssistant = ref(false)
const showAssistantPlatformMenu = ref(false)
const creating = ref(false)
const assistantForm = ref({
  name: '',
  description: '',
  platform: 'wechat',
  sessionIds: [] as string[]
})
const showChatModal = ref(false)
const chatAssistant = ref<VirtualAssistant | null>(null)
const chatMessages = ref<{ role: string; content: string }[]>([])
const chatInput = ref('')
const chatting = ref(false)
const chatMessagesRef = ref<HTMLElement | null>(null)
const showSessionMessages = ref(false)
const loadingSessionMessages = ref(false)
const selectedSessionId = ref('')
const sessionMessages = ref<ImportedChatMessage[]>([])

// Constants
const tabs = [
  { value: 'import', label: '导入记录', icon: CloudUploadOutline },
  { value: 'sessions', label: '会话管理', icon: FolderOpenOutline },
  { value: 'assistants', label: '虚拟助手', icon: SparklesOutline }
]

const platformOptions = [
  { label: '自动检测', value: 'auto' },
  { label: '微信', value: 'wechat' },
  { label: 'QQ', value: 'qq' },
  { label: 'Telegram', value: 'telegram' },
  { label: 'WhatsApp', value: 'whatsapp' },
  { label: '其他', value: 'other' }
]

// Computed
const selectedPlatformLabel = computed(() =>
  platformOptions.find(o => o.value === selectedPlatform.value)?.label || '自动检测'
)

const filterPlatformLabel = computed(() =>
  filterPlatform.value ? platformOptions.find(o => o.value === filterPlatform.value)?.label : '全部平台'
)

const assistantPlatformLabel = computed(() =>
  platformOptions.find(o => o.value === assistantForm.value.platform)?.label || '微信'
)

const sessionSelectOptions = computed(() =>
  sessionData.value.map(s => ({ label: `${s.sessionId} (${s.messageCount}条)`, value: s.sessionId }))
)

// Drag handling - use counter to prevent false leave events
const handleDragEnter = (e: DragEvent) => {
  e.preventDefault()
  dragCounter.value++
  if (dragCounter.value > 0) {
    isDragging.value = true
  }
}

const handleDragLeave = (e: DragEvent) => {
  e.preventDefault()
  dragCounter.value--
  if (dragCounter.value === 0) {
    isDragging.value = false
  }
}

const handleDrop = async (e: DragEvent) => {
  e.preventDefault()
  dragCounter.value = 0
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file) await uploadFile(file)
}

const triggerFileSelect = () => {
  fileInputRef.value?.click()
}

const handleFileSelect = async (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) await uploadFile(file)
}

// Upload logic
const uploadFile = async (file: File) => {
  uploading.value = true
  progressWidth.value = '30%'
  importResult.value = null
  const formData = new FormData()
  formData.append('file', file)
  formData.append('platform', selectedPlatform.value)

  try {
    progressWidth.value = '60%'
    const res = await api.post('/chatimport/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    progressWidth.value = '100%'
    importResult.value = res.data.data
    if (res.data.success) {
      message.success(`成功导入 ${res.data.data.importedCount} 条消息`)
      loadSessions()
    } else {
      message.error(res.data.message || '导入失败')
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '导入失败')
    importResult.value = {
      success: false,
      importedCount: 0,
      sessionCount: 0,
      platform: selectedPlatform.value,
      errorMessage: error.response?.data?.message || '导入失败'
    }
  } finally {
    uploading.value = false
    if (fileInputRef.value) fileInputRef.value.value = ''
    setTimeout(() => { progressWidth.value = '0%' }, 500)
  }
}

// Session management
const loadSessions = async () => {
  loadingSessions.value = true
  try {
    const params = filterPlatform.value ? { platform: filterPlatform.value } : {}
    const res = await api.get('/chatimport/sessions', { params })
    if (res.data.success) {
      const sessionIds = res.data.data as string[]
      const details = await Promise.all(
        sessionIds.slice(0, 50).map(async (id) => {
          try {
            const msgRes = await api.get(`/chatimport/session/${encodeURIComponent(id)}/messages`)
            const messages = msgRes.data.data || []
            return {
              sessionId: id,
              platform: messages[0]?.platform || 'unknown',
              messageCount: messages.length,
              createTime: messages[0]?.createTime || ''
            }
          } catch {
            return { sessionId: id, platform: 'unknown', messageCount: 0, createTime: '' }
          }
        })
      )
      sessionData.value = details
    }
  } catch (error) {
    console.error('加载会话列表失败', error)
  } finally {
    loadingSessions.value = false
  }
}

const viewSessionMessages = async (sessionId: string) => {
  selectedSessionId.value = sessionId
  sessionMessages.value = []
  showSessionMessages.value = true
  loadingSessionMessages.value = true
  try {
    const res = await api.get(`/chatimport/session/${encodeURIComponent(sessionId)}/messages`)
    if (res.data.success) {
      sessionMessages.value = res.data.data || []
    }
  } catch {
    message.error('加载会话消息失败')
  } finally {
    loadingSessionMessages.value = false
  }
}

const deleteSession = async (sessionId: string) => {
  try {
    await api.delete(`/chatimport/session/${encodeURIComponent(sessionId)}`)
    message.success('会话已删除')
    loadSessions()
  } catch {
    message.error('删除失败')
  }
}

// Assistant management
const loadAssistants = async () => {
  try {
    const res = await api.get('/chatimport/assistants')
    if (res.data.success) {
      assistants.value = res.data.data
    }
  } catch (error) {
    console.error('加载助手列表失败', error)
  }
}

const toggleSessionSelect = (sessionId: string) => {
  const idx = assistantForm.value.sessionIds.indexOf(sessionId)
  if (idx >= 0) {
    assistantForm.value.sessionIds.splice(idx, 1)
  } else {
    assistantForm.value.sessionIds.push(sessionId)
  }
}

const openCreateModal = () => {
  assistantForm.value = { name: '', description: '', platform: 'wechat', sessionIds: [] }
  showCreateAssistant.value = true
}

const createAssistant = async () => {
  if (!assistantForm.value.name || assistantForm.value.sessionIds.length === 0) {
    message.warning('请填写助手名称并选择至少一个会话')
    return
  }
  creating.value = true
  try {
    const params = new URLSearchParams()
    params.append('name', assistantForm.value.name)
    if (assistantForm.value.description) {
      params.append('description', assistantForm.value.description)
    }
    params.append('platform', assistantForm.value.platform)
    assistantForm.value.sessionIds.forEach(id => params.append('sessionIds', id))

    await api.post('/chatimport/assistant', params, {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    })
    message.success('助手创建成功，正在训练...')
    showCreateAssistant.value = false
    loadAssistants()
  } catch {
    message.error('创建失败')
  } finally {
    creating.value = false
  }
}

const toggleAssistant = async (assistant: VirtualAssistant) => {
  assistant.enabled = !assistant.enabled
  try {
    await api.put('/chatimport/assistant/' + assistant.id, null, {
      params: { enabled: assistant.enabled }
    })
    message.success(assistant.enabled ? '已启用' : '已禁用')
  } catch {
    message.error('操作失败')
    assistant.enabled = !assistant.enabled
  }
}

const deleteAssistant = async (id: number) => {
  try {
    await api.delete('/chatimport/assistant/' + id)
    message.success('助手已删除')
    loadAssistants()
  } catch {
    message.error('删除失败')
  }
}

const analyzePersonality = async (id: number) => {
  try {
    await api.post('/chatimport/assistant/' + id + '/analyze')
    message.success('人格分析完成')
    loadAssistants()
  } catch {
    message.error('分析失败')
  }
}

const openAssistantChat = (assistant: VirtualAssistant) => {
  chatAssistant.value = assistant
  chatMessages.value = []
  chatInput.value = ''
  showChatModal.value = true
}

const sendChatMessage = async () => {
  if (!chatInput.value.trim() || !chatAssistant.value) return
  const userMessage = chatInput.value.trim()
  chatMessages.value.push({ role: 'user', content: userMessage })
  chatInput.value = ''
  chatting.value = true
  try {
    const res = await api.post('/chatimport/assistant/' + chatAssistant.value.id + '/chat',
      chatMessages.value.slice(-10),
      { params: { message: userMessage } }
    )
    if (res.data.success) {
      chatMessages.value.push({ role: 'assistant', content: res.data.data })
    } else {
      chatMessages.value.push({ role: 'assistant', content: '抱歉，出现了错误' })
    }
  } catch {
    chatMessages.value.push({ role: 'assistant', content: '对话失败，请稍后重试' })
  } finally {
    chatting.value = false
    nextTick(() => {
      if (chatMessagesRef.value) {
        chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
      }
    })
  }
}

const formatMessageTime = (value?: string) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

// Lifecycle
onMounted(() => {
  loadSessions()
  loadAssistants()
})
</script>

<style scoped>
/* ============================================
 * Chat Import Page - Modern Minimal Style
 * Notion/Gemini inspired, flat and restrained
 * ============================================ */

.chat-import-page {
  padding: 0 0 40px;
  min-height: calc(100vh - 120px);
}

/* Page Header - Clean typography */
.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px;
  letter-spacing: -0.02em;
}

.page-subtitle {
  font-size: 0.9rem;
  color: var(--text-secondary);
  margin: 0;
  max-width: 520px;
  line-height: 1.5;
}

/* Main Container - Centered layout */
.main-container {
  max-width: 720px;
  margin: 0 auto;
}

/* Segmented Control - macOS/iOS style */
.segment-control {
  display: flex;
  background: var(--bg-input);
  border-radius: var(--radius-sm);
  padding: 4px;
  margin-bottom: 24px;
  width: fit-content;
}

.segment-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
  border-radius: var(--radius-xs);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.segment-item:hover:not(.active) {
  color: var(--text-primary);
}

.segment-item.active {
  background: var(--bg-elevated);
  color: var(--text-primary);
  box-shadow: var(--shadow-xs);
}

/* Content Card - Subtle, breathable */
.content-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 24px;
  box-shadow: var(--shadow-sm);
}

/* Upload Zone - Clean, dashed border */
.upload-zone {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 40px 24px;
  background: var(--bg-base);
  border: 2px dashed var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-base);
  min-height: 180px;
}

.upload-zone:hover {
  border-color: var(--border-accent);
  background: var(--bg-hover);
}

.upload-zone.is-dragging {
  border-color: var(--primary-color);
  background: var(--bg-active);
}

.upload-icon {
  color: var(--text-muted);
  transition: color var(--transition-fast);
}

.upload-zone:hover .upload-icon,
.upload-zone.is-dragging .upload-icon {
  color: var(--primary-color);
}

.upload-text {
  text-align: center;
}

.upload-primary {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.upload-secondary {
  font-size: 0.78rem;
  color: var(--text-muted);
  margin: 0;
}

.upload-btn {
  padding: 8px 20px;
  border: 1px solid var(--primary-color);
  background: transparent;
  color: var(--primary-color);
  font-size: 0.85rem;
  font-weight: 500;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.upload-btn:hover {
  background: var(--bg-menu-item-active);
}

.file-input-hidden {
  position: absolute;
  inset: 0;
  opacity: 0;
  pointer-events: none;
}

/* Platform Row */
.platform-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.platform-label {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.platform-select {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 0.85rem;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.platform-select:hover {
  border-color: var(--border-accent);
}

/* Upload Progress */
.upload-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.progress-track {
  flex: 1;
  height: 4px;
  background: var(--bg-input);
  border-radius: var(--radius-full);
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: var(--primary-color);
  border-radius: var(--radius-full);
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 0.8rem;
  color: var(--text-secondary);
}

/* Result Section */
.result-section {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-light);
}

.result-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border-radius: var(--radius-md);
}

.result-card.success {
  background: rgba(34, 197, 94, 0.08);
  border: 1px solid rgba(34, 197, 94, 0.2);
}

.result-card.error {
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.2);
}

.result-icon {
  color: var(--success);
}

.result-card.error .result-icon {
  color: var(--error);
}

.result-body {
  flex: 1;
}

.result-count {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.count-num {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
}

.count-label {
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.result-meta {
  display: flex;
  gap: 12px;
  margin-top: 4px;
  font-size: 0.78rem;
  color: var(--text-muted);
}

/* Card Header Row */
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.card-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.filter-select {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-input);
  color: var(--text-secondary);
  font-size: 0.8rem;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.filter-select:hover {
  border-color: var(--border-accent);
  color: var(--text-primary);
}

/* Create Button */
.create-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border: 1px solid var(--primary-color);
  background: transparent;
  color: var(--primary-color);
  font-size: 0.8rem;
  font-weight: 500;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.create-btn:hover {
  background: var(--bg-menu-item-active);
}

/* Sessions List */
.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: var(--bg-input);
  border-radius: var(--radius-sm);
  transition: background var(--transition-fast);
}

.session-item:hover {
  background: var(--bg-hover);
}

.session-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  background: var(--bg-glass);
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
}

.session-info {
  flex: 1;
  min-width: 0;
}

.session-id {
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  gap: 8px;
  margin-top: 2px;
}

.platform-tag,
.msg-count {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.session-actions {
  display: flex;
  gap: 4px;
}

.action-btn {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border: none;
  background: transparent;
  color: var(--text-muted);
  border-radius: var(--radius-xs);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.action-btn:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.action-btn.danger:hover {
  color: var(--error);
}

/* Assistants List */
.assistants-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.assistant-item {
  padding: 16px;
  background: var(--bg-input);
  border-radius: var(--radius-md);
  transition: background var(--transition-fast);
}

.assistant-item:hover {
  background: var(--bg-hover);
}

.assistant-item.disabled {
  opacity: 0.6;
}

.assistant-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.assistant-avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  background: var(--gradient-warm);
  border-radius: var(--radius-xs);
  color: var(--text-primary);
}

.assistant-name {
  flex: 1;
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
}

/* Toggle Button */
.toggle-btn {
  width: 44px;
  height: 24px;
  padding: 0;
  border: none;
  background: var(--bg-glass);
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: background var(--transition-fast);
  position: relative;
}

.toggle-btn.active {
  background: var(--primary-color);
}

.toggle-dot {
  position: absolute;
  top: 4px;
  left: 4px;
  width: 16px;
  height: 16px;
  background: var(--bg-elevated);
  border-radius: 50%;
  transition: transform var(--transition-fast);
}

.toggle-btn.active .toggle-dot {
  transform: translateX(20px);
}

.assistant-body {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}

.assistant-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: var(--bg-glass);
  border-radius: var(--radius-xs);
  font-size: 0.72rem;
  color: var(--text-secondary);
}

.assistant-stat {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat-value {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
}

.stat-label {
  font-size: 0.72rem;
  color: var(--text-muted);
}

.personality-text {
  font-size: 0.8rem;
  color: var(--text-secondary);
  line-height: 1.5;
  margin: 0;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
}

.assistant-footer {
  display: flex;
  gap: 8px;
}

.footer-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.78rem;
  border-radius: var(--radius-xs);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.footer-btn:hover {
  border-color: var(--border-accent);
  color: var(--text-primary);
}

.footer-btn.danger:hover {
  border-color: rgba(239, 68, 68, 0.3);
  color: var(--error);
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 20px;
  color: var(--text-muted);
}

.empty-icon {
  opacity: 0.4;
}

.empty-state span {
  font-size: 0.85rem;
}

.create-btn-inline {
  padding: 8px 16px;
  border: 1px solid var(--primary-color);
  background: transparent;
  color: var(--primary-color);
  font-size: 0.8rem;
  font-weight: 500;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.create-btn-inline:hover {
  background: var(--bg-menu-item-active);
}

/* Dropdown Overlay & Menu */
.dropdown-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.3);
}

.dropdown-menu {
  background: var(--bg-panel-strong);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 8px;
  min-width: 160px;
  box-shadow: var(--shadow-lg);
}

.dropdown-item {
  display: block;
  width: 100%;
  padding: 10px 14px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.85rem;
  border-radius: var(--radius-xs);
  cursor: pointer;
  text-align: left;
  transition: all var(--transition-fast);
}

.dropdown-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.dropdown-item.active {
  background: var(--bg-menu-item-active);
  color: var(--text-accent);
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1001;
  display: grid;
  place-items: center;
  background: rgba(0, 0, 0, 0.4);
}

.modal-card {
  width: min(480px, 90vw);
  background: var(--bg-panel-strong);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}

.modal-header h3 {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.modal-close {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: none;
  background: transparent;
  color: var(--text-muted);
  border-radius: var(--radius-xs);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.modal-close:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.modal-body {
  padding: 20px;
}

.form-field {
  margin-bottom: 16px;
}

.form-field:last-child {
  margin-bottom: 0;
}

.form-field label {
  display: block;
  font-size: 0.85rem;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.form-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 0.85rem;
  border-radius: var(--radius-sm);
  transition: all var(--transition-fast);
}

.form-input:focus {
  outline: none;
  border-color: var(--border-focus);
  box-shadow: var(--shadow-focus);
}

.form-input::placeholder {
  color: var(--text-muted);
}

textarea.form-input {
  resize: vertical;
  min-height: 80px;
}

.form-select {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 0.85rem;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.form-select:hover {
  border-color: var(--border-accent);
}

.session-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.session-tag {
  padding: 6px 12px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.78rem;
  border-radius: var(--radius-xs);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.session-tag:hover {
  border-color: var(--border-accent);
  color: var(--text-primary);
}

.session-tag.selected {
  border-color: var(--primary-color);
  background: var(--bg-menu-item-active);
  color: var(--text-accent);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid var(--border-light);
}

.btn-cancel {
  padding: 8px 16px;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.85rem;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btn-cancel:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.btn-confirm {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  background: var(--primary-color);
  color: var(--text-primary);
  font-size: 0.85rem;
  font-weight: 500;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.btn-confirm:hover {
  background: var(--primary-hover);
}

.btn-confirm:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* Chat Modal */
.chat-modal {
  height: 480px;
  display: flex;
  flex-direction: column;
}

.session-modal {
  width: min(720px, 92vw);
  max-height: min(720px, 86vh);
  display: flex;
  flex-direction: column;
}

.session-message-list {
  flex: 1;
  padding: 16px 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.session-message {
  padding: 12px 14px;
  border: 1px solid var(--border-light);
  background: var(--bg-input);
  border-radius: var(--radius-sm);
}

.session-message-meta {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 6px;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.session-message-meta time {
  flex-shrink: 0;
}

.session-message p {
  margin: 0;
  color: var(--text-primary);
  font-size: 0.86rem;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chat-avatar {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  background: var(--gradient-warm);
  border-radius: var(--radius-xs);
  color: var(--text-primary);
}

.chat-messages {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chat-bubble {
  max-width: 80%;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  font-size: 0.85rem;
  line-height: 1.5;
}

.chat-bubble.user {
  background: var(--primary-color);
  color: var(--text-primary);
  align-self: flex-end;
}

.chat-bubble.assistant {
  background: var(--bg-input);
  color: var(--text-primary);
  align-self: flex-start;
}

.chat-empty {
  color: var(--text-muted);
  font-size: 0.85rem;
  text-align: center;
  padding: 40px;
}

.chat-input-area {
  display: flex;
  gap: 8px;
  padding: 16px;
  border-top: 1px solid var(--border-light);
}

.chat-input {
  flex: 1;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 0.85rem;
  border-radius: var(--radius-sm);
}

.chat-input:focus {
  outline: none;
  border-color: var(--border-focus);
}

.send-btn {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border: none;
  background: var(--primary-color);
  color: var(--text-primary);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.send-btn:hover {
  background: var(--primary-hover);
}

.send-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Scrollbar */
.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: var(--border-color);
  border-radius: var(--radius-full);
}

/* Responsive */
@media (max-width: 640px) {
  .main-container {
    max-width: 100%;
  }

  .segment-control {
    width: 100%;
    justify-content: center;
  }

  .content-card {
    padding: 16px;
  }

  .upload-zone {
    padding: 32px 16px;
    min-height: 160px;
  }
}
</style>
