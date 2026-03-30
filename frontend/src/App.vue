<template>
  <n-config-provider :theme="naiveTheme" :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <div class="app-shell">
          <div class="app-shell__glow glow-a"></div>
          <div class="app-shell__glow glow-b"></div>

          <n-layout has-sider class="shell-layout">
            <n-layout-sider
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
                  <n-button quaternary circle class="icon-btn" @click="collapsed = !collapsed">
                    <template #icon>
                      <n-icon size="18">
                        <MenuIcon v-if="collapsed" />
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
                  <div class="header-chip">
                    <span class="header-chip__label">接口入口</span>
                    <strong>{{ menuOptions.length }}</strong>
                  </div>
                  <div class="header-chip header-chip--ghost">
                    <span class="header-chip__label">主题</span>
                    <strong>{{ actualTheme === 'dark' ? 'Dark' : 'Light' }}</strong>
                  </div>
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
  NIcon,
  NInput,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NMenu,
  NModal,
  NMessageProvider,
  NTooltip,
  createDiscreteApi,
  darkTheme
} from 'naive-ui'
import {
  BookOutline as KnowledgeIcon,
  FileTrayFullOutline as InboxIcon,
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
  SettingsOutline as SettingsIcon,
  SparklesOutline as SparklesIcon,
  FlashOutline as AutonomyIcon,
  ReaderOutline as ReportIcon,
  SunnyOutline as SunnyIcon,
  TimeOutline as TaskIcon,
  ConstructOutline as ToolIcon
} from '@vicons/ionicons5'
import { useThemeStore } from '@/stores/theme'
import { autonomyService, chatHistoryService, reportService } from '@/services/api'
import type { CommandPaletteItem } from '@/types'

const router = useRouter()
const route = useRoute()
const themeStore = useThemeStore()
const collapsed = ref(false)
const showCommandPalette = ref(false)
const commandQuery = ref('')
const { message } = createDiscreteApi(['message'])

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
    borderRadius: '18px',
    borderRadiusSmall: '12px',
    fontFamily: "'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif"
  },
  Card: {
    borderRadius: '24px'
  },
  Button: {
    borderRadiusMedium: '16px',
    borderRadiusSmall: '14px'
  },
  Input: {
    borderRadius: '16px'
  },
  Select: {
    peers: {
      InternalSelection: {
        borderRadius: '16px'
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
  { label: '系统设置', key: 'Settings', icon: renderIcon(SettingsIcon) }
]

const handleMenuClick = (key: string) => {
  router.push({ name: key })
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
    Settings: '/settings'
  }
  return map[key] || '/'
}

const openCommandPalette = () => {
  showCommandPalette.value = true
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

onMounted(() => {
  window.addEventListener('keydown', handleGlobalKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})
</script>

<style scoped>
.app-shell {
  position: relative;
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(251, 191, 36, 0.22), transparent 28%),
    radial-gradient(circle at 80% 10%, rgba(249, 115, 22, 0.18), transparent 24%),
    linear-gradient(145deg, var(--bg-base) 0%, color-mix(in srgb, var(--bg-base) 88%, #000 12%) 100%);
}

.app-shell__glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(70px);
  opacity: 0.45;
  pointer-events: none;
}

.glow-a {
  top: -120px;
  left: -80px;
  width: 320px;
  height: 320px;
  background: rgba(251, 191, 36, 0.24);
}

.glow-b {
  right: -80px;
  bottom: -140px;
  width: 360px;
  height: 360px;
  background: rgba(249, 115, 22, 0.18);
}

.shell-layout,
.main-panel {
  background: transparent;
  min-height: 100vh;
}

.app-sider {
  background: transparent;
  border-right: none !important;
}

.sider-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: 100%;
  margin: 18px 0 18px 18px;
  padding: 22px 16px;
  border: 1px solid var(--border-color);
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(255, 247, 237, 0.08), rgba(255, 247, 237, 0.02)),
    var(--bg-card);
  backdrop-filter: blur(18px);
  box-shadow: var(--shadow-lg);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 8px 14px;
  cursor: pointer;
}

.brand-mark {
  position: relative;
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 18px;
  color: #fff7ed;
  background: linear-gradient(135deg, #fb923c, #f59e0b 58%, #fcd34d);
  box-shadow: 0 12px 30px rgba(245, 158, 11, 0.32);
}

.brand-mark__spark {
  position: absolute;
  inset: 5px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.28);
}

.brand-copy {
  display: flex;
  flex-direction: column;
}

.brand-copy strong {
  font-size: 1.05rem;
  letter-spacing: 0.01em;
  color: var(--text-primary);
}

.brand-copy span,
.eyebrow,
.page-subtitle,
.header-kicker {
  color: var(--text-secondary);
}

.eyebrow,
.header-kicker,
.header-chip__label {
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.app-menu {
  flex: 1;
}

.sider-footer {
  display: grid;
  gap: 10px;
}

.status-pill,
.header-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-secondary);
}

.status-pill__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #f59e0b;
  box-shadow: 0 0 0 6px rgba(245, 158, 11, 0.12);
}

.status-pill__dot--soft {
  background: #fb923c;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 18px 18px 0 18px;
  padding: 18px 22px;
  border: 1px solid var(--border-color);
  border-radius: 26px;
  background: var(--bg-card);
  backdrop-filter: blur(18px);
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
}

.page-title {
  font-size: clamp(1.25rem, 2vw, 1.8rem);
  line-height: 1.2;
}

.page-subtitle {
  font-size: 0.92rem;
}

.header-right {
  gap: 12px;
}

.command-btn {
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.04);
  color: var(--text-secondary);
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
  background: rgba(255, 255, 255, 0.05);
  border-radius: 18px;
  padding: 14px 16px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.command-item:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--primary-color) 46%, white 18%);
  box-shadow: var(--shadow-glow);
}

.command-item strong {
  display: block;
  color: var(--text-primary);
}

.command-item span {
  display: block;
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.9rem;
}

.header-chip strong {
  color: var(--text-primary);
}

.header-chip--ghost {
  background: transparent;
}

.icon-btn {
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, 0.04);
}

.app-content {
  padding: 18px;
  background: transparent;
}

.content-frame {
  min-height: calc(100vh - 132px);
  padding: 10px 0 0;
}

@media (max-width: 1100px) {
  .header-chip {
    display: none;
  }
}

@media (max-width: 900px) {
  .sider-panel {
    margin-right: 0;
    border-radius: 0 24px 24px 0;
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
    min-height: calc(100vh - 168px);
  }

  .page-subtitle {
    display: none;
  }
}
</style>
