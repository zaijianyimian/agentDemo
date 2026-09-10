<template>
  <div class="workspace-shell" :data-theme="actualTheme">
    <a href="#main-content" class="skip-link">跳转到主要内容</a>

    <AppSidebar :collapsed="sidebarCollapsed" @toggle="toggleSidebar" />

    <main id="main-content" class="workspace-main">
      <AppTopbar
        :title="currentTitle"
        :category="currentCategoryLabel"
        :user-name="userDisplayName"
        :user-initial="userInitial"
        :user-email="authStore.user?.email || '-'"
        :user-role="authStore.user?.role || 'USER'"
        :model-status="systemStatus.model"
        :qdrant-status="systemStatus.qdrant"
        :search-status="systemStatus.search"
        :notification-count="notificationCount"
        :logout-loading="logoutLoading"
        @search="openCommandPalette"
        @status="handleStatusClick"
        @logout="handleLogout"
        @user-action="handleUserMenuSelect"
      />

      <div class="workspace-content">
        <slot></slot>
      </div>
    </main>

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
            type="button"
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

    <nav v-if="isMobile" class="mobile-nav" aria-label="移动端主导航">
      <button
        v-for="item in mobileNavItems"
        :key="item.key"
        :class="['mobile-nav-item', { active: item.active }]"
        type="button"
        @click="router.push(item.path)"
      >
        <n-icon size="20"><component :is="item.icon" /></n-icon>
        <span>{{ item.label }}</span>
      </button>
    </nav>
  </div>
</template>

<script setup lang="ts">
/**
 * Agent Workspace 应用主框架。
 *
 * 负责全局导航、顶部状态、命令面板和账户级操作。业务页面通过默认 slot 渲染，
 * Shell 不再承担二级浮动 Pane 与页面业务逻辑。
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useWindowSize } from '@vueuse/core'
import {
  NButton,
  NForm,
  NFormItem,
  NIcon,
  NInput,
  NModal,
  NSpace,
  createDiscreteApi
} from 'naive-ui'
import {
  BookOutline,
  CalendarOutline,
  ChatbubblesOutline,
  CloudOutline,
  CloudUploadOutline,
  CodeSlashOutline,
  ConstructOutline,
  CubeOutline,
  DocumentTextOutline,
  FileTrayFullOutline,
  FolderOutline,
  GridOutline,
  HomeOutline,
  MailOutline,
  NotificationsOutline,
  PersonOutline,
  ReaderOutline,
  RocketOutline,
  SearchOutline,
  SendOutline,
  SettingsOutline,
  SparklesOutline,
  TimeOutline
} from '@vicons/ionicons5'
import AppSidebar from '@/components/AppSidebar.vue'
import AppTopbar from '@/components/AppTopbar.vue'
import { useMacNavStore } from '@/stores/mac-nav'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { authService } from '@/services/api/auth'
import { inboxService } from '@/services/api/inbox'
import { modelService } from '@/services/api/model'
import { searchService } from '@/services/api/search'
import { settingsService } from '@/services/api/settings'

interface CommandItem {
  id: string
  label: string
  description: string
  icon: string
  path: string
}

type ServiceStatus = 'active' | 'inactive' | 'error'

const router = useRouter()
const route = useRoute()
const { width: windowWidth } = useWindowSize()
const navStore = useMacNavStore()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const { message } = createDiscreteApi(['message'])

const logoutLoading = ref(false)
const sidebarCollapsed = ref(localStorage.getItem('workspace.sidebar.collapsed') === 'true')
const isMobile = computed(() => windowWidth.value < 768)

const actualTheme = computed(() => {
  if (themeStore.mode === 'auto') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return themeStore.mode
})

const currentTitle = computed(() => (route.meta.title as string) || 'Agent Workspace')
const currentCategoryLabel = computed(() =>
  navStore.getCategoryForRoute(route.name as string)?.label || 'Workspace'
)
const userDisplayName = computed(() => authStore.user?.displayName || authStore.user?.username || 'User')
const userInitial = computed(() => userDisplayName.value.slice(0, 1).toUpperCase())

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

const showCommandPalette = ref(false)
const commandQuery = ref('')
const selectedIndex = ref(0)
const commandInputRef = ref<HTMLInputElement | null>(null)

const allCommands = computed<CommandItem[]>(() =>
  navStore.categories.flatMap(category =>
    category.routes.map(item => ({
      id: item.name,
      label: item.label,
      description: item.description,
      icon: item.icon,
      path: item.path
    }))
  )
)

const filteredCommands = computed(() => {
  const query = commandQuery.value.trim().toLowerCase()
  if (!query) return allCommands.value.slice(0, 12)
  return allCommands.value.filter(item =>
    item.label.toLowerCase().includes(query)
    || item.description.toLowerCase().includes(query)
  )
})

const showPasswordModal = ref(false)
const changingPassword = ref(false)
const passwordForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const mobileNavItems = computed(() => [
  { key: 'home', label: '首页', icon: HomeOutline, path: '/', active: route.path === '/' },
  { key: 'chat', label: 'Agent', icon: ChatbubblesOutline, path: '/chat', active: route.path.startsWith('/chat') },
  { key: 'inbox', label: '收件箱', icon: FileTrayFullOutline, path: '/inbox', active: route.path.startsWith('/inbox') },
  { key: 'schedule', label: '日程', icon: CalendarOutline, path: '/schedule', active: route.path.startsWith('/schedule') },
  { key: 'settings', label: '设置', icon: SettingsOutline, path: '/settings', active: route.path.startsWith('/settings') }
])

const iconMap: Record<string, any> = {
  home: HomeOutline,
  grid: GridOutline,
  inbox: FileTrayFullOutline,
  chatbubbles: ChatbubblesOutline,
  calendar: CalendarOutline,
  time: TimeOutline,
  timer: TimeOutline,
  note: DocumentTextOutline,
  reader: ReaderOutline,
  book: BookOutline,
  brain: CloudOutline,
  search: SearchOutline,
  folder: FolderOutline,
  import: CloudUploadOutline,
  sparkles: SparklesOutline,
  dispatch: SendOutline,
  construct: ConstructOutline,
  rocket: RocketOutline,
  document: DocumentTextOutline,
  code: CodeSlashOutline,
  cube: CubeOutline,
  cpu: CubeOutline,
  mail: MailOutline,
  bell: NotificationsOutline,
  push: NotificationsOutline,
  settings: SettingsOutline,
  person: PersonOutline
}

const getIconComponent = (icon: string) => iconMap[icon] || GridOutline

const toggleSidebar = () => {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem('workspace.sidebar.collapsed', String(sidebarCollapsed.value))
}

const handleStatusClick = (type: string) => {
  if (type === 'model') router.push('/models')
  else if (type === 'qdrant') router.push('/settings')
  else if (type === 'search') router.push('/search')
  else if (type === 'notifications') router.push('/inbox')
}

const handleUserMenuSelect = async (key: string) => {
  if (key === 'password') {
    passwordForm.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
    showPasswordModal.value = true
  } else if (key === 'personal') {
    await router.push('/personal')
  }
}

const handleLogout = async () => {
  if (logoutLoading.value) return
  logoutLoading.value = true
  try {
    await authStore.logout()
    await router.replace('/login')
    message.success('已退出登录')
  } catch (error: any) {
    authStore.clearSession()
    await router.replace('/login')
    message.warning(error?.message || '已清除本地登录状态')
  } finally {
    logoutLoading.value = false
  }
}

const openCommandPalette = () => {
  showCommandPalette.value = true
  selectedIndex.value = 0
  commandQuery.value = ''
  nextTick(() => commandInputRef.value?.focus())
}

const handleCommandKeydown = (event: KeyboardEvent) => {
  if (event.key === 'ArrowDown') {
    selectedIndex.value = Math.min(selectedIndex.value + 1, filteredCommands.value.length - 1)
  } else if (event.key === 'ArrowUp') {
    selectedIndex.value = Math.max(selectedIndex.value - 1, 0)
  } else if (event.key === 'Enter' && filteredCommands.value[selectedIndex.value]) {
    executeCommand(filteredCommands.value[selectedIndex.value])
  } else if (event.key === 'Escape') {
    showCommandPalette.value = false
  }
}

const executeCommand = (command: CommandItem) => {
  showCommandPalette.value = false
  router.push(command.path)
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
    const response = await authService.changePassword({
      currentPassword: passwordForm.value.currentPassword,
      newPassword: passwordForm.value.newPassword
    })
    if (response.success) {
      message.success(response.message || '密码修改成功')
      showPasswordModal.value = false
    } else {
      message.error(response.message || '密码修改失败')
    }
  } catch {
    message.error('密码修改失败')
  } finally {
    changingPassword.value = false
  }
}

const handleGlobalKeydown = (event: KeyboardEvent) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    openCommandPalette()
  }
}

watch(() => route.name, name => {
  if (!name) return
  const routeInfo = navStore.getRouteByName(name as string)
  if (routeInfo) {
    navStore.trackAccess(name as string, routeInfo.path, routeInfo.label, routeInfo.icon)
  }
})

watch(actualTheme, theme => {
  document.documentElement.setAttribute('data-theme', theme)
})

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown)
  document.documentElement.setAttribute('data-theme', actualTheme.value)
  loadSystemStatus()
  statusRefreshTimer = window.setInterval(loadSystemStatus, 60000)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  if (statusRefreshTimer) window.clearInterval(statusRefreshTimer)
})
</script>

<style scoped>
.workspace-shell {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-base);
  color: var(--text-primary);
}

.workspace-main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
}

.workspace-content {
  flex: 1;
  min-height: 0;
  padding: 24px 28px 32px;
  overflow: auto;
}

.skip-link {
  position: fixed;
  left: 16px;
  top: -60px;
  z-index: 9999;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--primary-color);
  color: #fff;
  text-decoration: none;
  transition: top 120ms ease;
}

.skip-link:focus {
  top: 12px;
}

.command-palette {
  width: min(660px, calc(100vw - 32px));
  max-height: min(72vh, 680px);
  overflow: hidden;
  border: 1px solid var(--workspace-border, var(--border-light));
  border-radius: 16px;
  background: var(--bg-card);
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.18);
}

.command-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--workspace-border, var(--border-light));
  color: var(--text-muted);
}

.command-input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
}

.command-header kbd,
.command-footer kbd {
  padding: 2px 6px;
  border: 1px solid var(--workspace-border, var(--border-light));
  border-radius: 6px;
  background: var(--bg-input);
  color: var(--text-muted);
  font-family: inherit;
  font-size: 0.68rem;
}

.command-list {
  max-height: 480px;
  padding: 8px;
  overflow-y: auto;
}

.command-item {
  display: grid;
  grid-template-columns: 24px minmax(120px, 0.7fr) minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 44px;
  padding: 8px 10px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.command-item.selected,
.command-item:hover {
  background: var(--bg-input);
}

.command-label {
  color: var(--text-primary);
  font-size: 0.8rem;
  font-weight: 650;
}

.command-desc {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 0.72rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.command-footer {
  display: flex;
  justify-content: flex-end;
  gap: 16px;
  padding: 9px 14px;
  border-top: 1px solid var(--workspace-border, var(--border-light));
  color: var(--text-muted);
  font-size: 0.68rem;
}

.mobile-nav {
  display: none;
}

@media (max-width: 767px) {
  .workspace-content {
    padding: 16px 12px 82px;
  }

  .mobile-nav {
    position: fixed;
    left: 10px;
    right: 10px;
    bottom: 10px;
    z-index: 100;
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 4px;
    padding: 6px;
    border: 1px solid var(--workspace-border, var(--border-light));
    border-radius: 14px;
    background: color-mix(in srgb, var(--bg-card) 94%, transparent);
    box-shadow: 0 12px 34px rgba(15, 23, 42, 0.14);
    backdrop-filter: blur(14px);
  }

  .mobile-nav-item {
    display: grid;
    place-items: center;
    gap: 3px;
    min-height: 48px;
    border: 0;
    border-radius: 9px;
    background: transparent;
    color: var(--text-muted);
    font: inherit;
    font-size: 0.6rem;
  }

  .mobile-nav-item.active {
    background: color-mix(in srgb, var(--primary-color) 12%, var(--bg-card));
    color: var(--primary-color);
  }
}
</style>
