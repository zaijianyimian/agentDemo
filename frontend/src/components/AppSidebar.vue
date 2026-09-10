<template>
  <aside class="app-sidebar" :class="{ collapsed }">
    <div class="sidebar-brand">
      <button class="brand-button" type="button" @click="goHome" title="Agent Workspace">
        <span class="brand-mark">
          <img src="/resource/logo.png" alt="Agent" @error="hideBrokenImage" />
        </span>
        <span v-if="!collapsed" class="brand-copy">
          <strong>Agent Workspace</strong>
          <small>Personal AI Console</small>
        </span>
      </button>
      <button class="collapse-button" type="button" @click="$emit('toggle')" :title="collapsed ? '展开导航' : '收起导航'">
        <n-icon size="17">
          <ChevronForwardOutline v-if="collapsed" />
          <ChevronBackOutline v-else />
        </n-icon>
      </button>
    </div>

    <nav class="sidebar-nav" aria-label="主导航">
      <section v-for="category in navStore.categories" :key="category.id" class="nav-group">
        <div v-if="!collapsed" class="nav-group-title">{{ category.label }}</div>
        <button
          v-for="item in category.routes"
          :key="item.name"
          type="button"
          :class="['nav-entry', { active: isActive(item.path) }]"
          :title="collapsed ? item.label : item.description"
          @click="navigate(item.path)"
        >
          <span class="nav-entry-icon">
            <n-icon size="18"><component :is="getIconComponent(item.icon)" /></n-icon>
          </span>
          <span v-if="!collapsed" class="nav-entry-copy">
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
        </button>
      </section>
    </nav>

    <div class="sidebar-footer">
      <button type="button" class="footer-action" @click="themeStore.toggleTheme()" :title="collapsed ? '切换主题' : undefined">
        <n-icon size="18"><MoonOutline v-if="themeStore.mode !== 'dark'" /><SunnyOutline v-else /></n-icon>
        <span v-if="!collapsed">切换主题</span>
      </button>
      <button type="button" class="footer-action" @click="navigate('/settings')" :title="collapsed ? '系统设置' : undefined">
        <n-icon size="18"><SettingsOutline /></n-icon>
        <span v-if="!collapsed">系统设置</span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
/**
 * Agent Workspace 一级导航。
 *
 * 只负责全局模块分组、路由跳转和侧边栏收起状态展示，不承载业务请求。
 */
import { useRoute, useRouter } from 'vue-router'
import { NIcon } from 'naive-ui'
import {
  BookOutline,
  CalendarOutline,
  ChatbubblesOutline,
  ChevronBackOutline,
  ChevronForwardOutline,
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
  SunnyOutline,
  MoonOutline,
  TimeOutline
} from '@vicons/ionicons5'
import { useMacNavStore } from '@/stores/mac-nav'
import { useThemeStore } from '@/stores/theme'

withDefaults(defineProps<{ collapsed?: boolean }>(), {
  collapsed: false
})

defineEmits<{
  toggle: []
}>()

const route = useRoute()
const router = useRouter()
const navStore = useMacNavStore()
const themeStore = useThemeStore()

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

const isActive = (path: string) => {
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(`${path}/`)
}

const navigate = (path: string) => {
  if (!isActive(path)) router.push(path)
}

const goHome = () => navigate('/')

const hideBrokenImage = (event: Event) => {
  const target = event.target as HTMLImageElement
  target.style.display = 'none'
}
</script>

<style scoped>
.app-sidebar {
  display: flex;
  flex-direction: column;
  flex: 0 0 244px;
  width: 244px;
  min-width: 244px;
  height: 100vh;
  padding: 16px 12px;
  background: var(--bg-card);
  border-right: 1px solid var(--workspace-border, var(--border-light));
  transition: width 180ms ease, min-width 180ms ease, flex-basis 180ms ease;
  overflow: hidden;
}

.app-sidebar.collapsed {
  flex-basis: 72px;
  width: 72px;
  min-width: 72px;
  padding-inline: 10px;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 52px;
  padding: 0 2px 12px;
  border-bottom: 1px solid var(--workspace-border, var(--border-light));
}

.brand-button,
.collapse-button,
.nav-entry,
.footer-action {
  border: 0;
  font: inherit;
  color: inherit;
  cursor: pointer;
}

.brand-button {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  padding: 6px;
  background: transparent;
  text-align: left;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--primary-color) 14%, var(--bg-input));
  color: var(--primary-color);
  font-weight: 800;
}

.brand-mark img {
  width: 22px;
  height: 22px;
  object-fit: contain;
}

.brand-copy {
  display: grid;
  min-width: 0;
}

.brand-copy strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 0.9rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.brand-copy small {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 0.68rem;
}

.collapse-button {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 8px;
  background: var(--bg-input);
  color: var(--text-secondary);
}

.collapsed .sidebar-brand {
  justify-content: center;
}

.collapsed .brand-button {
  flex: 0 0 auto;
  padding: 0;
}

.collapsed .collapse-button {
  position: absolute;
  left: 52px;
  top: 28px;
  width: 24px;
  height: 24px;
  border: 1px solid var(--workspace-border, var(--border-light));
  background: var(--bg-card);
  z-index: 2;
}

.sidebar-nav {
  flex: 1;
  min-height: 0;
  padding: 14px 0;
  overflow-y: auto;
  scrollbar-width: thin;
}

.nav-group + .nav-group {
  margin-top: 18px;
}

.nav-group-title {
  padding: 0 10px 7px;
  color: var(--text-muted);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.nav-entry {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 44px;
  padding: 7px 9px;
  border-radius: 10px;
  background: transparent;
  text-align: left;
  transition: background 140ms ease, color 140ms ease;
}

.nav-entry + .nav-entry {
  margin-top: 3px;
}

.nav-entry:hover {
  background: var(--bg-input);
}

.nav-entry.active {
  background: color-mix(in srgb, var(--primary-color) 12%, var(--bg-card));
  color: var(--primary-color);
}

.nav-entry.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 11px;
  bottom: 11px;
  width: 3px;
  border-radius: 0 99px 99px 0;
  background: var(--primary-color);
}

.nav-entry-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  color: var(--text-secondary);
}

.nav-entry.active .nav-entry-icon {
  color: var(--primary-color);
}

.nav-entry-copy {
  display: grid;
  min-width: 0;
}

.nav-entry-copy strong {
  color: var(--text-primary);
  font-size: 0.82rem;
  font-weight: 650;
}

.nav-entry.active .nav-entry-copy strong {
  color: var(--primary-color);
}

.nav-entry-copy small {
  margin-top: 2px;
  overflow: hidden;
  color: var(--text-muted);
  font-size: 0.66rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.collapsed .nav-entry {
  justify-content: center;
  padding-inline: 6px;
}

.sidebar-footer {
  display: grid;
  gap: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--workspace-border, var(--border-light));
}

.footer-action {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 38px;
  padding: 8px 10px;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 0.78rem;
}

.footer-action:hover {
  background: var(--bg-input);
  color: var(--text-primary);
}

.collapsed .footer-action {
  justify-content: center;
  padding-inline: 0;
}

@media (max-width: 767px) {
  .app-sidebar {
    display: none;
  }
}
</style>
