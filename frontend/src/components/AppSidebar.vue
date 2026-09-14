<template>
  <aside class="app-sidebar" :class="{ collapsed }">
    <div class="sidebar-brand">
      <button class="brand-button" type="button" @click="goHome" title="Agent Workspace">
        <span class="brand-mark">
          <img src="/resource/logo.png" alt="Agent" @error="hideBrokenImage" />
        </span>
        <span v-if="!collapsed" class="brand-copy">
          <strong>Agent Console</strong>
          <small>Personal workspace</small>
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
      <section class="nav-group">
        <button
          v-for="item in primaryRoutes"
          :key="item.path"
          type="button"
          :class="['nav-entry', { active: isActive(item.path) }]"
          :title="collapsed ? item.label : undefined"
          @click="navigate(item.path)"
        >
          <span class="nav-entry-icon">
            <n-icon size="18"><component :is="getIconComponent(item.icon)" /></n-icon>
          </span>
          <span v-if="!collapsed" class="nav-entry-copy">
            <strong>{{ item.label }}</strong>
          </span>
        </button>
      </section>

      <section class="nav-group module-group">
        <div v-if="!collapsed" class="nav-group-title">功能空间</div>
        <button
          v-for="item in moduleRoutes"
          :key="item.category"
          type="button"
          :class="['nav-entry', { active: isCategoryActive(item.category) }]"
          :title="collapsed ? item.label : undefined"
          @click="navigate(item.path)"
        >
          <span class="nav-entry-icon">
            <n-icon size="18"><component :is="getIconComponent(item.icon)" /></n-icon>
          </span>
          <span v-if="!collapsed" class="nav-entry-copy">
            <strong>{{ item.label }}</strong>
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
  ConstructOutline,
  FileTrayFullOutline,
  GridOutline,
  HomeOutline,
  SettingsOutline,
  SparklesOutline,
  SunnyOutline,
  MoonOutline
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

const primaryRoutes = [
  { path: '/', label: '工作台', icon: 'home' },
  { path: '/mvp', label: '讲解 MVP', icon: 'sparkles' },
  { path: '/chat', label: '对话', icon: 'chatbubbles' },
  { path: '/inbox', label: '统一收件箱', icon: 'inbox' },
  { path: '/settings', label: '设置', icon: 'settings' }
]

const moduleRoutes = [
  { category: 'productivity', path: '/schedule', label: '效率与日程', icon: 'calendar' },
  { category: 'knowledge', path: '/knowledge', label: '知识与文件', icon: 'book' },
  { category: 'agent', path: '/autonomy', label: 'Agent 能力', icon: 'sparkles' },
  { category: 'system', path: '/email', label: '系统服务', icon: 'construct' }
]

const iconMap: Record<string, any> = {
  home: HomeOutline,
  grid: GridOutline,
  inbox: FileTrayFullOutline,
  chatbubbles: ChatbubblesOutline,
  calendar: CalendarOutline,
  book: BookOutline,
  sparkles: SparklesOutline,
  construct: ConstructOutline,
  settings: SettingsOutline
}

const getIconComponent = (icon: string) => iconMap[icon] || GridOutline

const isActive = (path: string) => {
  if (path === '/') return route.path === '/'
  return route.path === path || route.path.startsWith(`${path}/`)
}

const isCategoryActive = (categoryId: string) => {
  if (['Dashboard', 'Chat', 'Inbox', 'Settings'].includes(String(route.name || ''))) return false
  return navStore.getCategoryForRoute(String(route.name || ''))?.id === categoryId
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
  flex: 0 0 190px;
  width: 190px;
  min-width: 190px;
  height: 100vh;
  padding: 12px 10px;
  background: color-mix(in srgb, var(--bg-input) 74%, var(--bg-card));
  border-right: 1px solid var(--workspace-border, var(--border-light));
  transition: width 180ms ease, min-width 180ms ease, flex-basis 180ms ease;
  overflow: hidden;
}

.app-sidebar.collapsed {
  flex-basis: 68px;
  width: 68px;
  min-width: 68px;
  padding-inline: 8px;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 56px;
  padding: 0 2px 10px;
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
  left: 49px;
  top: 24px;
  width: 24px;
  height: 24px;
  border: 1px solid var(--workspace-border, var(--border-light));
  background: var(--bg-card);
  z-index: 2;
}

.sidebar-nav {
  flex: 1;
  min-height: 0;
  padding: 12px 0;
  overflow-y: auto;
  scrollbar-width: thin;
}

.nav-group + .nav-group {
  margin-top: 14px;
}

.module-group {
  padding-top: 12px;
  border-top: 1px solid var(--workspace-border, var(--border-light));
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
  min-height: 42px;
  padding: 7px 10px;
  border-radius: 9px;
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
  font-size: 0.8rem;
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
