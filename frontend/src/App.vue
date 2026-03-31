<template>
  <n-config-provider :theme="naiveTheme" :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <router-view v-if="isAuthPage" />

        <div v-else :class="['app-shell', { 'app-shell--focus': focusMode }]">
          <n-layout has-sider class="shell-layout">
            <n-layout-sider
              v-if="!focusMode"
              bordered
              collapse-mode="width"
              :collapsed-width="72"
              :width="288"
              :native-scrollbar="false"
              :collapsed="collapsed"
              :show-trigger="false"
              class="app-sider"
            >
              <div class="sider-panel">
                <div class="brand-block" @click="router.push('/')">
                  <div class="brand-mark">
                    <span class="brand-mark__spark"></span>
                    <n-icon size="22"><SparklesIcon /></n-icon>
                  </div>
                  <div v-show="!collapsed" class="brand-copy">
                    <strong>Amber Agent</strong>
                    <span>Orange command center</span>
                  </div>
                </div>

                <n-menu
                  :value="currentRoute"
                  :options="menuOptions"
                  :collapsed="collapsed"
                  :collapsed-width="72"
                  :collapsed-icon-size="20"
                  class="app-menu"
                  @update:value="handleMenuClick"
                />

                <div v-show="!collapsed" class="sider-footer">
                  <div class="status-pill">
                    <span class="status-pill__dot"></span>
                    <span>{{ actualTheme === 'dark' ? '夜间橙焰' : '日间琥珀' }}</span>
                  </div>
                  <div class="status-pill">
                    <span class="status-pill__dot status-pill__dot--soft"></span>
                    <span>{{ route.path }}</span>
                  </div>
                </div>
              </div>
            </n-layout-sider>

            <n-layout class="main-panel">
              <n-layout-header class="app-header">
                <div class="header-left">
                  <n-button quaternary circle class="icon-btn" @click="toggleSidebarOrGoDashboard">
                    <template #icon>
                      <n-icon size="18">
                        <HomeIcon v-if="focusMode" />
                        <MenuIcon v-else-if="collapsed" />
                        <MenuOpenIcon v-else />
                      </n-icon>
                    </template>
                  </n-button>
                  <div class="header-copy">
                    <span class="header-kicker">{{ currentSection }}</span>
                    <h1 class="page-title">{{ currentTitle }}</h1>
                    <p class="page-subtitle">{{ currentDescription }}</p>
                  </div>
                </div>

                <div class="header-right">
                  <n-button quaternary class="command-btn" @click="openCommandPalette">
                    <span>Ctrl</span>
                    <span>K</span>
                  </n-button>
                  <n-button v-if="focusMode" quaternary class="command-btn" @click="exitFocusMode">
                    退出专注
                  </n-button>
                  <div class="header-chip">
                    <span class="header-chip__label">接口入口</span>
                    <strong>{{ menuOptions.length }}</strong>
                  </div>
                  <div class="header-chip header-chip--ghost">
                    <span class="header-chip__label">主题</span>
                    <strong>{{ actualTheme === 'dark' ? 'Dark' : 'Light' }}</strong>
                  </div>
                  <n-dropdown :options="userOptions" @select="handleUserAction">
                    <button class="user-chip" type="button">
                      <span class="user-chip__avatar">{{ userDisplayInitial }}</span>
                      <span class="user-chip__name">{{ userDisplayName }}</span>
                    </button>
                  </n-dropdown>
                  <n-tooltip trigger="hover">
                    <template #trigger>
                      <n-button quaternary circle class="icon-btn" @click="toggleTheme">
                        <template #icon>
                          <n-icon size="18">
                            <MoonIcon v-if="actualTheme === 'light'" />
                            <SunnyIcon v-else />
                          </n-icon>
                        </template>
                      </n-button>
                    </template>
                    {{ actualTheme === 'dark' ? '切换到日间模式' : '切换到夜间模式' }}
                  </n-tooltip>
                </div>
              </n-layout-header>

              <n-layout-content class="app-content">
                <div class="content-frame">
                  <router-view />
                </div>
              </n-layout-content>
            </n-layout>
          </n-layout>

          <n-modal v-model:show="showCommandPalette" preset="card" title="全局命令面板" style="width: min(720px, 92vw)">
            <div class="command-palette">
              <n-input
                v-model:value="commandQuery"
                placeholder="输入页面名称或操作，例如：收件箱、日报、扫描"
                clearable
              />
              <div class="command-list">
                <button
                  v-for="item in filteredCommands"
                  :key="item.id"
                  type="button"
                  class="command-item"
                  @click="executeCommand(item)"
                >
                  <strong>{{ item.label }}</strong>
                  <span>{{ item.description }}</span>
                </button>
              </div>
            </div>
          </n-modal>

          <n-modal v-model:show="showChangePasswordModal" preset="card" title="修改密码" style="width: min(520px, 92vw)">
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
                <n-button @click="showChangePasswordModal = false">取消</n-button>
                <n-button type="primary" :loading="changingPassword" @click="submitChangePassword">保存</n-button>
              </n-space>
            </template>
          </n-modal>
        </div>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed, h, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NButton,
  NConfigProvider,
  NDialogProvider,
  NDropdown,
  NIcon,
  NInput,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NMenu,
  NModal,
  NForm,
  NFormItem,
  NMessageProvider,
  NSpace,
  NTooltip,
  createDiscreteApi,
  darkTheme
} from 'naive-ui'
import {
  BookOutline as KnowledgeIcon,
  FileTrayFullOutline as InboxIcon,
  HomeOutline as HomeIcon,
  CalendarOutline as CalendarIcon,
  ChatbubblesOutline as ChatIcon,
  CodeSlashOutline as CodeIcon,
  CubeOutline as ModelIcon,
  DocumentTextOutline as NotebookIcon,
  FolderOutline as FolderIcon,
  GridOutline as DashboardIcon,
  MailOutline as MailIcon,
  MenuOutline as MenuIcon,
  MenuSharp as MenuOpenIcon,
  MoonOutline as MoonIcon,
  RocketOutline as SkillIcon,
  SearchOutline as SearchIcon,
  PersonCircleOutline as PersonalIcon,
  SettingsOutline as SettingsIcon,
  SparklesOutline as SparklesIcon,
  FlashOutline as AutonomyIcon,
  ReaderOutline as ReportIcon,
  SunnyOutline as SunnyIcon,
  TimeOutline as TaskIcon,
  ConstructOutline as ToolIcon
} from '@vicons/ionicons5'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { authService, autonomyService, chatHistoryService, reportService } from '@/services/api'
import type { CommandPaletteItem } from '@/types'
import { isFocusMode, setFocusMode } from '@/services/user-preferences'

const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const authStore = useAuthStore()
const collapsed = ref(false)
const focusMode = ref(isFocusMode())
const showCommandPalette = ref(false)
const commandQuery = ref('')
const { message } = createDiscreteApi(['message'])
const showChangePasswordModal = ref(false)
const changingPassword = ref(false)
const passwordForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const actualTheme = computed(() => {
  if (themeStore.mode === 'auto') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return themeStore.mode
})

const naiveTheme = computed(() => actualTheme.value === 'dark' ? darkTheme : null)

const commonTheme = {
  common: {
    primaryColor: '#f59e0b',
    primaryColorHover: '#fbbf24',
    primaryColorPressed: '#ea580c',
    primaryColorSuppl: '#fb923c',
    infoColor: '#f59e0b',
    successColor: '#10b981',
    warningColor: '#f59e0b',
    errorColor: '#ef4444',
    borderRadius: '14px',
    borderRadiusSmall: '10px',
    fontFamily: "'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif"
  },
  Card: {
    borderRadius: '18px'
  },
  Button: {
    borderRadiusMedium: '12px',
    borderRadiusSmall: '10px'
  },
  Input: {
    borderRadius: '12px'
  },
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: '12px'
      }
    }
  }
}

const darkThemeOverrides = {
  ...commonTheme,
  Layout: {
    color: '#160f07',
    siderColor: '#20140a',
    headerColor: 'rgba(31, 20, 10, 0.75)'
  },
  Menu: {
    itemTextColor: '#f5e8d8',
    itemTextColorHover: '#fff7ed',
    itemTextColorActive: '#fbbf24',
    itemTextColorActiveHover: '#fcd34d',
    itemColorHover: 'rgba(251, 191, 36, 0.08)',
    itemColorActive: 'rgba(251, 191, 36, 0.14)',
    itemColorActiveHover: 'rgba(251, 191, 36, 0.18)',
    itemBorderRadius: '16px'
  },
  Card: {
    color: 'rgba(46, 27, 11, 0.82)',
    borderColor: 'rgba(251, 191, 36, 0.14)',
    titleTextColor: '#fff7ed',
    textColor: '#f6ddc5'
  },
  DataTable: {
    thColor: 'rgba(63, 36, 11, 0.9)',
    tdColor: 'rgba(33, 21, 12, 0.78)',
    borderColor: 'rgba(251, 191, 36, 0.12)'
  }
}

const lightThemeOverrides = {
  ...commonTheme,
  Layout: {
    color: '#fff7ed',
    siderColor: '#fffaf2',
    headerColor: 'rgba(255, 251, 245, 0.76)'
  },
  Menu: {
    itemTextColor: '#7c4a14',
    itemTextColorHover: '#451a03',
    itemTextColorActive: '#c2410c',
    itemTextColorActiveHover: '#9a3412',
    itemColorHover: 'rgba(245, 158, 11, 0.08)',
    itemColorActive: 'rgba(245, 158, 11, 0.14)',
    itemColorActiveHover: 'rgba(245, 158, 11, 0.18)',
    itemBorderRadius: '16px'
  },
  Card: {
    color: 'rgba(255, 255, 255, 0.9)',
    borderColor: 'rgba(249, 115, 22, 0.12)',
    titleTextColor: '#451a03',
    textColor: '#7c2d12'
  },
  DataTable: {
    thColor: 'rgba(255, 247, 237, 0.95)',
    tdColor: 'rgba(255, 255, 255, 0.92)',
    borderColor: 'rgba(249, 115, 22, 0.12)'
  }
}

const themeOverrides = computed(() => actualTheme.value === 'dark' ? darkThemeOverrides : lightThemeOverrides)

const currentRoute = computed(() => route.name as string)
const isAuthPage = computed(() => route.path === '/login')
const userDisplayName = computed(() => authStore.user?.displayName || authStore.user?.username || '未登录')
const userDisplayInitial = computed(() => userDisplayName.value.slice(0, 1).toUpperCase())
const userOptions = computed(() => [
  { key: 'email', label: authStore.user?.email || '-' },
  { key: 'role', label: `角色: ${authStore.user?.role || 'USER'}` },
  { type: 'divider', key: 'd1' },
  { key: 'password', label: '修改密码' },
  { key: 'logout', label: '退出登录' }
])
const currentTitle = computed(() => route.meta.title as string || '仪表盘')
const currentDescription = computed(() => route.meta.description as string || '统一管理 AI Agent 的核心能力与配置。')
const currentSection = computed(() => {
  const matched = menuOptions.find(item => item.key === currentRoute.value)
  return matched?.label || 'Control'
})

const renderIcon = (icon: any) => () => h(NIcon, null, { default: () => h(icon) })

const menuOptions = [
  { label: '仪表盘', key: 'Dashboard', icon: renderIcon(DashboardIcon) },
  { label: '统一收件箱', key: 'Inbox', icon: renderIcon(InboxIcon) },
  { label: '自治中心', key: 'Autonomy', icon: renderIcon(AutonomyIcon) },
  { label: '日报周报', key: 'Reports', icon: renderIcon(ReportIcon) },
  { label: 'AI 聊天', key: 'Chat', icon: renderIcon(ChatIcon) },
  { label: '模型配置', key: 'Models', icon: renderIcon(ModelIcon) },
  { label: '知识库', key: 'Knowledge', icon: renderIcon(KnowledgeIcon) },
  { label: '定时任务', key: 'Tasks', icon: renderIcon(TaskIcon) },
  { label: '文件管理', key: 'Files', icon: renderIcon(FolderIcon) },
  { label: '日程管理', key: 'Schedule', icon: renderIcon(CalendarIcon) },
  { label: '邮件配置', key: 'Email', icon: renderIcon(MailIcon) },
  { label: '网络搜索', key: 'Search', icon: renderIcon(SearchIcon) },
  { label: '工具管理', key: 'Tools', icon: renderIcon(ToolIcon) },
  { label: '技能管理', key: 'Skills', icon: renderIcon(SkillIcon) },
  { label: '笔记', key: 'Notes', icon: renderIcon(NotebookIcon) },
  { label: '代码片段', key: 'Snippets', icon: renderIcon(CodeIcon) },
  { label: '系统设置', key: 'Settings', icon: renderIcon(SettingsIcon) },
  { label: '单用户中心', key: 'Personal', icon: renderIcon(PersonalIcon) }
]

const handleMenuClick = (key: string) => {
  router.push({ name: key })
}

const toggleSidebarOrGoDashboard = async () => {
  if (focusMode.value) {
    if (route.path !== '/') {
      await router.push('/')
    }
    return
  }
  collapsed.value = !collapsed.value
}

const exitFocusMode = () => {
  focusMode.value = false
  setFocusMode(false)
  window.dispatchEvent(new CustomEvent('focus-mode-changed', { detail: { enabled: false } }))
}

const toggleTheme = () => {
  themeStore.toggleTheme()
}

const commandItems = computed<CommandPaletteItem[]>(() => [
  ...menuOptions.map(item => ({
    id: `route-${item.key}`,
    label: item.label as string,
    description: '打开页面',
    type: 'route' as const,
    route: routePathByKey(item.key as string)
  })),
  { id: 'action-scan', label: '运行自治扫描', description: '立刻执行项目扫描', type: 'action' },
  { id: 'action-daily', label: '生成日报', description: '生成一份今日报告并跳到报告页', type: 'action' },
  { id: 'action-weekly', label: '生成周报', description: '生成最近七天的总结报告', type: 'action' },
  { id: 'action-session', label: '新建聊天会话', description: '立即创建一个空白聊天会话', type: 'action' }
])

const filteredCommands = computed(() => {
  const query = commandQuery.value.trim().toLowerCase()
  if (!query) return commandItems.value
  return commandItems.value.filter(item =>
    item.label.toLowerCase().includes(query) || item.description.toLowerCase().includes(query)
  )
})

const routePathByKey = (key: string) => {
  const map: Record<string, string> = {
    Dashboard: '/',
    Inbox: '/inbox',
    Autonomy: '/autonomy',
    Reports: '/reports',
    Chat: '/chat',
    Models: '/models',
    Knowledge: '/knowledge',
    Tasks: '/tasks',
    Files: '/files',
    Schedule: '/schedule',
    Email: '/email',
    Search: '/search',
    Tools: '/tools',
    Skills: '/skills',
    Notes: '/notes',
    Snippets: '/snippets',
    Settings: '/settings',
    Personal: '/personal'
  }
  return map[key] || '/'
}

const openCommandPalette = () => {
  showCommandPalette.value = true
}

const handleUserAction = async (key: string) => {
  if (key === 'password') {
    passwordForm.value = {
      currentPassword: '',
      newPassword: '',
      confirmPassword: ''
    }
    showChangePasswordModal.value = true
    return
  }
  if (key === 'logout') {
    await authStore.logout()
    await router.replace('/login')
    message.success('已退出登录')
  }
}

const submitChangePassword = async () => {
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
      showChangePasswordModal.value = false
    } else {
      message.error(res.message || '密码修改失败')
    }
  } catch (error) {
    message.error('密码修改失败')
  } finally {
    changingPassword.value = false
  }
}

const executeCommand = async (item: CommandPaletteItem) => {
  showCommandPalette.value = false
  commandQuery.value = ''
  if (item.type === 'route' && item.route) {
    await router.push(item.route)
    return
  }

  try {
    if (item.id === 'action-scan') {
      await autonomyService.scan()
      await router.push('/autonomy')
      message.success('已触发自治扫描')
    } else if (item.id === 'action-daily') {
      await reportService.generate('daily')
      await router.push('/reports')
      message.success('日报已生成')
    } else if (item.id === 'action-weekly') {
      await reportService.generate('weekly')
      await router.push('/reports')
      message.success('周报已生成')
    } else if (item.id === 'action-session') {
      await chatHistoryService.createSession()
      await router.push('/chat')
      message.success('已创建新会话')
    }
  } catch (error) {
    message.error('命令执行失败')
  }
}

const handleGlobalKeydown = (event: KeyboardEvent) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    openCommandPalette()
  }
}

const handleFocusModeChanged = (event: Event) => {
  const detail = (event as CustomEvent<{ enabled: boolean }>).detail
  focusMode.value = !!detail?.enabled
  if (focusMode.value) {
    collapsed.value = true
  }
}

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown)
  window.addEventListener('focus-mode-changed', handleFocusModeChanged)
  if (focusMode.value) {
    collapsed.value = true
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  window.removeEventListener('focus-mode-changed', handleFocusModeChanged)
})
</script>

<style scoped>
.app-shell {
  position: relative;
  height: 100vh;
  background: linear-gradient(180deg, var(--bg-base) 0%, color-mix(in srgb, var(--bg-base) 94%, #000 6%) 100%);
  overflow: hidden;
}

.app-shell--focus .app-header {
  margin-left: 12px;
}

.app-shell--focus .app-content {
  padding-top: 8px;
}

.shell-layout,
.main-panel {
  background: transparent;
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
}

.app-sider {
  background: transparent;
  border-right: none !important;
  height: 100vh;
  overflow: hidden;
}

.sider-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  height: calc(100vh - 40px);
  margin: 20px 0 20px 20px;
  padding: 24px 18px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-2xl);
  background:
    linear-gradient(180deg, rgba(255, 247, 237, 0.06), rgba(255, 247, 237, 0.02)),
    var(--bg-card);
  backdrop-filter: blur(var(--blur-lg));
  box-shadow: var(--shadow-md);
  position: relative;
  overflow: hidden;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 8px 16px;
  cursor: pointer;
  transition: transform var(--transition-base);
  border-radius: var(--radius-lg);
}

.brand-block:hover {
  transform: translateX(4px);
}

.brand-mark {
  position: relative;
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: var(--radius-lg);
  color: #fff7ed;
  background: var(--gradient-warm);
  box-shadow: var(--shadow-md), var(--shadow-glow-sm);
  transition: all var(--transition-base);
}

.brand-mark:hover {
  transform: scale(1.05);
  box-shadow: var(--shadow-lg), var(--shadow-glow);
}

.brand-mark__spark {
  position: absolute;
  inset: 6px;
  border-radius: var(--radius-md);
  border: 1px solid rgba(255, 255, 255, 0.25);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.brand-copy strong {
  font-size: 1.08rem;
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--text-primary);
}

.brand-copy span {
  font-size: 0.82rem;
  color: var(--text-muted);
}

.app-menu {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 4px;
  scrollbar-width: none;
}

.app-menu::-webkit-scrollbar {
  width: 0;
  height: 0;
}

.app-menu::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 999px;
}

.sider-panel:hover .app-menu {
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--primary-color) 34%, transparent) transparent;
}

.sider-panel:hover .app-menu::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.sider-panel:hover .app-menu::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--primary-color) 34%, transparent);
}

.sider-footer {
  display: grid;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--border-color);
}

.status-pill,
.header-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-secondary);
  font-size: 0.88rem;
  transition: all var(--transition-base);
}

.status-pill:hover,
.header-chip:hover {
  border-color: var(--border-glow);
  background: var(--bg-hover);
}

.status-pill__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--primary-color);
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.15);
  animation: pulse 2s ease-in-out infinite;
}

.status-pill__dot--soft {
  background: var(--primary-light);
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 20px 20px 0 20px;
  padding: 18px 24px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-2xl);
  background: var(--bg-card);
  backdrop-filter: blur(var(--blur-lg));
  box-shadow: var(--shadow-sm);
  position: relative;
  position: sticky;
  top: 20px;
  z-index: 30;
}

.header-left,
.header-right,
.header-copy {
  display: flex;
  align-items: center;
}

.header-left {
  gap: 14px;
}

.header-copy {
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.page-title {
  font-size: clamp(1.25rem, 2vw, 1.8rem);
  font-weight: 700;
  line-height: 1.2;
  background: var(--text-gradient);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.page-subtitle {
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.header-right {
  gap: 12px;
}

.command-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--border-color);
  background: var(--bg-input);
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  font-size: 0.82rem;
  transition: all var(--transition-base);
}

.command-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.command-btn span {
  padding: 2px 6px;
  background: var(--bg-hover);
  border-radius: var(--radius-xs);
  font-weight: 600;
}

.command-palette {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.command-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 420px;
  overflow: auto;
}

.command-item {
  border: 1px solid var(--border-color);
  background: var(--gradient-card);
  border-radius: var(--radius-lg);
  padding: 16px 18px;
  text-align: left;
  cursor: pointer;
  transition: all var(--transition-base);
}

.command-item:hover {
  border-color: var(--border-glow);
  box-shadow: var(--shadow-glow-sm);
}

.command-item strong {
  display: block;
  color: var(--text-primary);
  font-weight: 600;
}

.command-item span {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.88rem;
}

.header-chip strong {
  color: var(--text-primary);
  font-weight: 600;
}

.header-chip--ghost {
  background: transparent;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--border-color);
  background: var(--bg-input);
  color: var(--text-primary);
  border-radius: 999px;
  padding: 6px 10px;
  cursor: pointer;
  transition: all var(--transition-base);
}

.user-chip:hover {
  border-color: var(--primary-color);
  box-shadow: var(--shadow-glow-sm);
}

.user-chip__avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 0.78rem;
  color: #fff;
  background: var(--gradient-warm);
}

.user-chip__name {
  max-width: 112px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.icon-btn {
  border: 1px solid var(--border-color);
  background: var(--bg-input);
  transition: all var(--transition-base);
}

.icon-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
  transform: scale(1.05);
}

.app-content {
  padding: 20px;
  background: transparent;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.content-frame {
  min-height: auto;
  padding: 8px 0 0;
  animation: fadeInUp 0.4s ease;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1100px) {
  .header-chip {
    display: none;
  }
}

@media (max-width: 900px) {
  .sider-panel {
    margin-right: 0;
    border-radius: 0 var(--radius-2xl) var(--radius-2xl) 0;
  }

  .app-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .header-right {
    width: 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .content-frame {
    min-height: calc(100vh - 180px);
  }

  .page-subtitle {
    display: none;
  }
}
</style>
