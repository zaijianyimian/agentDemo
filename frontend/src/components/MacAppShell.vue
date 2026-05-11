<template>
  <div class="mac-shell" :data-theme="actualTheme">
    <!-- Skip Link for accessibility -->
    <a href="#main-content" class="skip-link">跳转到主要内容</a>

    <!-- Global Rail - Layer 1 -->
    <MacGlobalRail
      :compact="railCompact"
      @categorySelect="handleCategorySelect"
      @userAction="handleUserAction"
    />

    <!-- Functional Pane - Layer 2 -->
    <MacFunctionalPane
      @routeSelect="handleRouteSelect"
      @inspectorToggle="handleInspectorToggle"
      @close="handlePaneClose"
    />

    <!-- Inspector Panel - Layer 3 (optional, per page) -->
    <slot name="inspector"></slot>

    <!-- Built-in Inspector Panel for routes with hasInspector flag -->
    <aside
      v-if="showInspector && currentRouteHasInspector"
      class="inspector-panel"
      :class="{ open: showInspector }"
    >
      <div class="inspector-header">
        <span class="inspector-title">{{ inspectorTitle }}</span>
        <button class="inspector-close-btn" @click="closeInspectorPanel" title="关闭">
          <n-icon size="16"><ChevronForwardOutline /></n-icon>
        </button>
      </div>
      <div class="inspector-content">
        <slot name="inspector-content">
          <div class="inspector-placeholder">
            <p>在此页面添加 Inspector 内容</p>
          </div>
        </slot>
      </div>
    </aside>

    <!-- Main Content Area -->
    <main class="main-canvas" id="main-content">
      <!-- Top Header Bar -->
      <header class="canvas-header">
        <!-- Left: Current page info -->
        <div class="header-left">
          <div class="page-info">
            <span class="page-category">{{ currentCategoryLabel }}</span>
            <h1 class="page-title">{{ currentTitle }}</h1>
          </div>
        </div>

        <!-- Center: Control Center -->
        <MacControlCenter
          :modelStatus="systemStatus.model"
          :qdrantStatus="systemStatus.qdrant"
          :searchStatus="systemStatus.search"
          :notificationCount="notificationCount"
          @statusClick="handleStatusClick"
          @searchClick="openCommandPalette"
        />

        <!-- Right: User menu -->
        <div class="header-right">
          <n-dropdown :options="userMenuOptions" @select="handleUserMenuSelect">
            <button class="user-menu-btn">
              <span class="user-avatar">{{ userInitial }}</span>
              <span class="user-name">{{ userDisplayName }}</span>
            </button>
          </n-dropdown>
        </div>
      </header>

      <!-- Content Frame -->
      <div class="content-frame">
        <slot></slot>
      </div>
    </main>

    <!-- Command Palette Modal -->
    <n-modal v-model:show="showCommandPalette" :mask-closable="true" :close-on-esc="true">
      <div class="command-palette" @click.stop>
        <div class="command-header">
          <n-icon size="18"><SearchOutline /></n-icon>
          <input
            ref="commandInputRef"
            v-model="commandQuery"
            type="text"
            class="command-input"
            placeholder="搜索页面或操作..."
            @keydown="handleCommandKeydown"
          />
          <kbd>ESC</kbd>
        </div>
        <div class="command-list">
          <button
            v-for="(item, index) in filteredCommands"
            :key="item.id"
            :class="['command-item', { selected: selectedIndex === index }]"
            @click="executeCommand(item)"
            @mouseenter="selectedIndex = index"
          >
            <n-icon size="16"><component :is="getIconComponent(item.icon)" /></n-icon>
            <span class="command-label">{{ item.label }}</span>
            <span class="command-desc">{{ item.description }}</span>
          </button>
        </div>
        <div class="command-footer">
          <span><kbd>↑↓</kbd> 导航</span>
          <span><kbd>Enter</kbd> 执行</span>
        </div>
      </div>
    </n-modal>

    <!-- Password Modal -->
    <n-modal v-model:show="showPasswordModal" preset="card" title="修改密码" style="width: min(480px, 90vw)">
      <n-form label-placement="top">
        <n-form-item label="当前密码">
          <n-input v-model:value="passwordForm.currentPassword" type="password" show-password-on="mousedown" />
        </n-form-item>
        <n-form-item label="新密码">
          <n-input v-model:value="passwordForm.newPassword" type="password" show-password-on="mousedown" />
        </n-form-item>
        <n-form-item label="确认新密码">
          <n-input v-model:value="passwordForm.confirmPassword" type="password" show-password-on="mousedown" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showPasswordModal = false">取消</n-button>
          <n-button type="primary" :loading="changingPassword" @click="submitPasswordChange">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- Mobile Bottom Nav (if needed) -->
    <nav v-if="isMobile" class="mobile-nav">
      <button v-for="item in mobileNavItems" :key="item.key" :class="['mobile-nav-item', { active: item.active }]" @click="handleMobileNav(item)">
        <n-icon size="20"><component :is="item.icon" /></n-icon>
        <span>{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, nextTick, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useWindowSize } from '@vueuse/core'
import {
  NModal,
  NDropdown,
  NIcon,
  NInput,
  NButton,
  NForm,
  NFormItem,
  NSpace,
  createDiscreteApi
} from 'naive-ui'
import {
  SearchOutline,
  HomeOutline,
  ChatbubblesOutline,
  FileTrayFullOutline,
  DocumentTextOutline,
  SettingsOutline,
  CubeOutline,
  CloudOutline,
  GridOutline,
  BookOutline,
  TimerOutline,
  ConstructOutline,
  PersonOutline,
  SparklesOutline,
  ReaderOutline,
  FolderOutline,
  CalendarOutline,
  MailOutline,
  RocketOutline,
  CodeSlashOutline,
  CloudUploadOutline,
  ChevronForwardOutline
} from '@vicons/ionicons5'
import { useMacNavStore } from '@/stores/mac-nav'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { authService, inboxService, modelService, searchService, settingsService } from '@/services/api'
import MacGlobalRail from '@/components/MacGlobalRail.vue'
import MacFunctionalPane from '@/components/MacFunctionalPane.vue'
import MacControlCenter from '@/components/MacControlCenter.vue'

const router = useRouter()
const route = useRoute()
const { width: windowWidth } = useWindowSize()
const macNavStore = useMacNavStore()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const { message } = createDiscreteApi(['message'])

// Responsive
const isMobile = computed(() => windowWidth.value < 768)
const railCompact = computed(() => windowWidth.value < 1024)

// Theme
const actualTheme = computed(() => {
  if (themeStore.mode === 'auto') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return themeStore.mode
})

// User info
const userDisplayName = computed(() => authStore.user?.displayName || authStore.user?.username || 'User')
const userInitial = computed(() => userDisplayName.value.slice(0, 1).toUpperCase())
const userMenuOptions = computed(() => [
  { key: 'email', label: authStore.user?.email || '-' },
  { key: 'role', label: `角色: ${authStore.user?.role || 'USER'}` },
  { type: 'divider', key: 'd1' },
  { key: 'password', label: '修改密码' },
  { key: 'logout', label: '退出登录' }
])

// Current page info
const currentTitle = computed(() => route.meta.title as string || '仪表盘')
const currentCategoryLabel = computed(() => {
  const category = macNavStore.getCategoryForRoute(route.name as string)
  return category?.label || 'Console'
})

// Inspector Panel - Layer 3
const showInspector = computed(() => macNavStore.inspectorOpen)
const currentRouteHasInspector = computed(() => {
  const routeInfo = macNavStore.getRouteByName(route.name as string)
  return routeInfo?.hasInspector || false
})
const inspectorTitle = computed(() => currentTitle.value + ' 参数')

const closeInspectorPanel = () => {
  macNavStore.closeInspector()
}

type ServiceStatus = 'active' | 'inactive' | 'error'

// System status
const systemStatus = ref<{
  model: ServiceStatus
  qdrant: ServiceStatus
  search: ServiceStatus
}>({
  model: 'inactive',
  qdrant: 'inactive',
  search: 'inactive'
})
const notificationCount = ref(0)
let statusRefreshTimer: number | undefined

const loadSystemStatus = async () => {
  const [modelResult, qdrantResult, searchResult, inboxResult] = await Promise.allSettled([
    modelService.health(),
    settingsService.getAll(),
    searchService.test(),
    inboxService.summary(18)
  ])

  if (modelResult.status === 'fulfilled' && modelResult.value.success) {
    const models = modelResult.value.data || []
    systemStatus.value.model = models.some((item: Record<string, any>) =>
      item.isAvailable === true
      || item.available === true
      || item.healthAvailable === true
      || item.status === 'available'
    ) ? 'active' : 'inactive'
  } else {
    systemStatus.value.model = 'error'
  }

  if (qdrantResult.status === 'fulfilled' && qdrantResult.value.success) {
    const qdrant = qdrantResult.value.data?.qdrant || {}
    systemStatus.value.qdrant = qdrant.host || qdrant.port ? 'active' : 'inactive'
  } else {
    systemStatus.value.qdrant = 'error'
  }

  systemStatus.value.search =
    searchResult.status === 'fulfilled' && searchResult.value.success ? 'active' : 'error'

  if (inboxResult.status === 'fulfilled' && inboxResult.value.success) {
    notificationCount.value = inboxResult.value.data?.items?.length || 0
  }
}

// Command palette
const showCommandPalette = ref(false)
const commandQuery = ref('')
const selectedIndex = ref(0)
const commandInputRef = ref<HTMLInputElement | null>(null)

const allCommands = computed(() => {
  const commands: any[] = []
  macNavStore.categories.forEach(cat => {
    cat.routes.forEach(r => {
      commands.push({
        id: r.name,
        label: r.label,
        description: r.description,
        icon: r.icon,
        type: 'route',
        path: r.path
      })
    })
  })
  return commands
})

const filteredCommands = computed(() => {
  const q = commandQuery.value.trim().toLowerCase()
  if (!q) return allCommands.value.slice(0, 10)
  return allCommands.value.filter(c =>
    c.label.toLowerCase().includes(q) || c.description.toLowerCase().includes(q)
  )
})

// Password modal
const showPasswordModal = ref(false)
const changingPassword = ref(false)
const passwordForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// Mobile nav
const mobileNavItems = computed(() => [
  { key: 'home', label: '首页', icon: HomeOutline, path: '/', active: route.path === '/' },
  { key: 'chat', label: '对话', icon: ChatbubblesOutline, path: '/chat', active: route.path.startsWith('/chat') },
  { key: 'inbox', label: '收件箱', icon: FileTrayFullOutline, path: '/inbox', active: route.path.startsWith('/inbox') },
  { key: 'notes', label: '笔记', icon: DocumentTextOutline, path: '/notes', active: route.path.startsWith('/notes') },
  { key: 'settings', label: '设置', icon: SettingsOutline, path: '/settings', active: route.path.startsWith('/settings') }
])

// Icon map
const iconMap: Record<string, any> = {
  grid: GridOutline, home: HomeOutline, inbox: FileTrayFullOutline, sparkles: SparklesOutline,
  reader: ReaderOutline, cube: CubeOutline, settings: SettingsOutline, book: BookOutline,
  chatbubbles: ChatbubblesOutline, import: CloudUploadOutline, note: DocumentTextOutline, folder: FolderOutline,
  time: TimerOutline, calendar: CalendarOutline, mail: MailOutline, rocket: RocketOutline,
  code: CodeSlashOutline, search: SearchOutline, construct: ConstructOutline, person: PersonOutline,
  cpu: CubeOutline, brain: CloudOutline, timer: TimerOutline
}
const getIconComponent = (icon: string) => iconMap[icon] || GridOutline

// Handlers
const handleCategorySelect = (_categoryId: string) => {
  // Pane will auto-open via store
}

const handleRouteSelect = (_routeInfo: any) => {
  // Route navigation handled in pane
}

const handleInspectorToggle = (_routeInfo: any) => {
  // Inspector toggle handled in pane
}

const handlePaneClose = () => {
  macNavStore.closePane()
}

const handleUserAction = () => {
  // Show user dropdown or navigate to profile
}

const handleUserMenuSelect = async (key: string) => {
  if (key === 'password') {
    passwordForm.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
    showPasswordModal.value = true
  } else if (key === 'logout') {
    await authStore.logout()
    await router.replace('/login')
    message.success('已退出登录')
  }
}

const handleStatusClick = (type: string) => {
  if (type === 'model') router.push('/models')
  else if (type === 'qdrant') router.push('/settings')
  else if (type === 'search') router.push('/search')
  else if (type === 'notifications') router.push('/inbox')
}

const openCommandPalette = () => {
  showCommandPalette.value = true
  selectedIndex.value = 0
  commandQuery.value = ''
  nextTick(() => commandInputRef.value?.focus())
}

const handleCommandKeydown = (e: KeyboardEvent) => {
  if (e.key === 'ArrowDown') {
    selectedIndex.value = Math.min(selectedIndex.value + 1, filteredCommands.value.length - 1)
  } else if (e.key === 'ArrowUp') {
    selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
  } else if (e.key === 'Enter' && filteredCommands.value[selectedIndex.value]) {
    executeCommand(filteredCommands.value[selectedIndex.value])
  } else if (e.key === 'Escape') {
    showCommandPalette.value = false
  }
}

const executeCommand = (cmd: any) => {
  showCommandPalette.value = false
  if (cmd.type === 'route') {
    router.push(cmd.path)
  }
}

const submitPasswordChange = async () => {
  if (!passwordForm.value.currentPassword || !passwordForm.value.newPassword) {
    message.warning('请填写完整密码信息')
    return
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    message.warning('两次输入的新密码不一致')
    return
  }
  changingPassword.value = true
  try {
    const res = await authService.changePassword({
      currentPassword: passwordForm.value.currentPassword,
      newPassword: passwordForm.value.newPassword
    })
    if (res.success) {
      message.success(res.message || '密码修改成功')
      showPasswordModal.value = false
    } else {
      message.error(res.message || '密码修改失败')
    }
  } catch {
    message.error('密码修改失败')
  } finally {
    changingPassword.value = false
  }
}

const handleMobileNav = (item: any) => {
  router.push(item.path)
}

// Track route access
watch(() => route.name, (name) => {
  if (name) {
    const routeInfo = macNavStore.getRouteByName(name as string)
    if (routeInfo) {
      macNavStore.trackAccess(name as string, routeInfo.path, routeInfo.label, routeInfo.icon)
    }
  }
})

// Global keyboard handler
const handleGlobalKeydown = (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    openCommandPalette()
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown)
  // Apply theme to document
  document.documentElement.setAttribute('data-theme', actualTheme.value)
  loadSystemStatus()
  statusRefreshTimer = window.setInterval(loadSystemStatus, 60000)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  if (statusRefreshTimer) {
    window.clearInterval(statusRefreshTimer)
  }
})

// Watch theme changes
watch(actualTheme, (theme) => {
  document.documentElement.setAttribute('data-theme', theme)
})
</script>

<style scoped>
/* ============================================
 * macOS Shell - Root Container
 * Strict 100vh lock, proper flex hierarchy
 * ============================================ */
.mac-shell {
  display: flex;
  height: 100vh;
  width: 100vw;
  overflow: hidden;
  background: var(--bg-base);
  transition: background var(--transition-base);
}

/* Main Canvas - Content area (flex: 1, fills remaining space) */
.main-canvas {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  position: relative;
  overflow: hidden;
  padding: 0;
  gap: 0;
}

/* Canvas Header - macOS style (fixed at top, left offset for rail) */
.canvas-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 20px;
  margin-left: 96px;
  margin-right: 24px;
  margin-top: 24px;
  background: var(--bg-card);
  border-radius: var(--radius-xl);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
  z-index: 10;
  min-width: min(480px, calc(100vw - 160px));
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.page-category {
  font-size: 0.7rem;
  font-weight: 500;
  color: var(--text-muted) !important;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.page-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--text-primary) !important;
  margin: 0;
}

.user-name {
  font-size: 0.85rem;
  color: var(--text-secondary) !important;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-menu-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-base), color var(--transition-base);
}

.user-menu-btn:hover {
  background: var(--bg-menu-item-hover);
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  background: var(--gradient-sunset);
  color: white;
  font-weight: 700;
  font-size: 0.85rem;
  text-shadow: 0 1px 2px rgba(0,0,0,0.15);
}

/* Content Frame - Scrollable main content area
 * Prevents squeezing when pane/sidebar opens */
.content-frame {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 24px;
  padding-left: 120px; /* 72px rail + 48px gap */
  padding-right: 24px;
  scrollbar-width: thin;
  position: relative;
  z-index: 1;
  /* Critical: Minimum width to prevent card deformation */
  min-width: min(600px, calc(100vw - 144px));
  /* macOS style scrollbar overlay */
  scrollbar-color: rgba(142, 142, 147, 0.24) transparent;
}

/* macOS floating scrollbar */
.content-frame::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.content-frame::-webkit-scrollbar-track {
  background: transparent;
}

.content-frame::-webkit-scrollbar-thumb {
  background: rgba(142, 142, 147, 0.24);
  border-radius: 4px;
  transition: background 0.2s ease;
}

.content-frame::-webkit-scrollbar-thumb:hover {
  background: rgba(142, 142, 147, 0.40);
}

/* Dark mode scrollbar */
:global([data-theme="dark"]) .content-frame::-webkit-scrollbar-thumb {
  background: rgba(142, 142, 147, 0.32);
}

:global([data-theme="dark"]) .content-frame::-webkit-scrollbar-thumb:hover {
  background: rgba(142, 142, 147, 0.48);
}

/* ============================================
 * Inspector Panel - Layer 3
 * Right sidebar for detailed parameters
 * ============================================ */
  .inspector-panel {
  position: fixed;
  right: 0;
  top: 0;
  height: 100vh;
  width: var(--inspector-width);
  z-index: 30;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border-left: 1px solid var(--border-light);
  box-shadow: var(--shadow-sidebar);
  transform: translateX(100%);
  opacity: 0;
  visibility: hidden;
  transition:
    transform var(--transition-slide),
    opacity var(--transition-base),
    visibility 0s 0.25s;
}

.inspector-panel.open {
  transform: translateX(0);
  opacity: 1;
  visibility: visible;
  transition:
    transform var(--transition-slide),
    opacity var(--transition-base),
    visibility 0s 0s;
}

.inspector-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}

.inspector-title {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-primary);
}

.inspector-close-btn {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: background-color var(--transition-base), color var(--transition-base);
}

.inspector-close-btn:hover {
  background: var(--bg-menu-item-hover);
  color: var(--text-secondary);
}

.inspector-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  scrollbar-width: thin;
}

.inspector-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  font-size: 0.85rem;
}

/* Command Palette - macOS Spotlight style */
.command-palette {
  width: min(560px, 90vw);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-xl);
  overflow: hidden;
}

.command-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-light);
}

.command-header .n-icon {
  color: var(--text-muted);
}

.command-input {
  flex: 1;
  background: transparent;
  border: none;
  font-size: 1rem;
  color: var(--text-primary);
  outline: none;
}

.command-input::placeholder {
  color: var(--text-muted);
}

.command-header kbd {
  padding: 4px 8px;
  background: var(--bg-input);
  border-radius: var(--radius-xs);
  font-size: 0.75rem;
  color: var(--text-muted);
}

.command-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  max-height: 320px;
  overflow-y: auto;
}

.command-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-base);
}

.command-item:hover,
.command-item.selected {
  background: var(--bg-menu-item-hover);
}

.command-item.selected {
  background: var(--bg-menu-item-active);
}

.command-label {
  font-size: 0.9rem;
  color: var(--text-primary);
}

.command-desc {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-left: auto;
}

.command-footer {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-light);
  font-size: 0.75rem;
  color: var(--text-muted);
}

.command-footer kbd {
  padding: 2px 6px;
  background: var(--bg-input);
  border-radius: var(--radius-xs);
}

/* Mobile Bottom Nav */
.mobile-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-around;
  padding: 8px 16px;
  background: var(--bg-card);
  border-top: 1px solid var(--border-light);
  z-index: 200;
}

.mobile-nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 12px;
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  transition: color var(--transition-base), background-color var(--transition-base);
}

.mobile-nav-item.active {
  color: var(--primary-color);
}

.mobile-nav-item span {
  font-size: 0.7rem;
}

/* Skip Link */
.skip-link {
  position: absolute;
  top: -40px;
  left: 0;
  padding: 8px 16px;
  background: var(--primary-color);
  color: white;
  text-decoration: none;
  border-radius: var(--radius-md);
  z-index: 9999;
  transition: top 0.2s ease;
}

.skip-link:focus {
  top: 8px;
}

/* Responsive adjustments */
@media (max-width: 1024px) {
  .canvas-header {
    margin-left: 88px;
    margin-right: 16px;
    min-width: 360px;
  }

  .content-frame {
    padding-left: 100px;
    padding-right: 16px;
    min-width: 480px;
  }

  .functional-pane {
    left: 88px;
  }
}

@media (max-width: 768px) {
  .canvas-header {
    margin-left: 0;
    margin-right: 0;
    margin-top: 16px;
    border-radius: var(--radius-lg);
    min-width: auto;
  }

  .content-frame {
    padding: 16px;
    padding-bottom: 72px;
    min-width: auto;
  }

  .functional-pane {
    left: 16px;
    top: auto;
    bottom: 80px;
    transform: translateY(0) translateX(-100%);
    max-height: 60vh;
  }

  .functional-pane.open {
    transform: translateY(0) translateX(0);
  }
}
</style>
