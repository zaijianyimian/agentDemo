<template>
  <aside class="global-rail" :class="{ 'rail-compact': compact }">
    <!-- Quick Access Section - Mission Control style (icon only) -->
    <div class="quick-access-section">
      <div class="quick-access-header">
        <span class="quick-access-label">Quick</span>
      </div>
      <div class="quick-access-items">
        <button
          v-for="item in topQuickAccess"
          :key="item.name"
          class="quick-access-item"
          :title="item.label"
          :aria-label="item.label"
          @click="handleQuickAccessClick(item)"
        >
          <div class="quick-icon-wrapper">
            <n-icon size="18">
              <component :is="getIconComponent(item.icon)" />
            </n-icon>
          </div>
        </button>
        <!-- Empty state -->
        <div v-if="topQuickAccess.length === 0" class="quick-access-empty">
          <n-icon size="16"><TimeOutline /></n-icon>
        </div>
      </div>
    </div>

    <!-- Divider -->
    <div class="rail-divider"></div>

    <!-- Category Icons - Dock style (NO TEXT, only centered icons) -->
    <div class="category-dock">
      <button
        v-for="category in categories"
        :key="category.id"
        :class="['dock-item', { active: activeCategory === category.id, current: isCurrentCategory(category) }]"
        :title="category.label"
        :aria-label="category.label"
        @click="handleCategoryClick(category.id)"
      >
        <div class="dock-icon-wrapper" :style="{ '--category-color': category.color }">
          <n-icon size="24">
            <component :is="getIconComponent(category.icon)" />
          </n-icon>
        </div>
        <div class="dock-indicator" v-if="isCurrentCategory(category)"></div>
      </button>
    </div>

    <!-- Divider -->
    <div class="rail-divider"></div>

    <!-- User Avatar - Bottom -->
    <div class="rail-footer">
      <button
        class="user-avatar-btn"
        :title="userDisplayName"
        :aria-label="userDisplayName"
        @click="handleUserClick"
      >
        <span class="avatar-circle">{{ userInitial }}</span>
      </button>
      <button
        class="theme-toggle-btn"
        :title="actualTheme === 'light' ? '切换深色主题' : '切换浅色主题'"
        :aria-label="actualTheme === 'light' ? '切换深色主题' : '切换浅色主题'"
        @click="toggleTheme"
      >
        <n-icon size="18">
          <MoonOutline v-if="actualTheme === 'light'" />
          <SunnyOutline v-else />
        </n-icon>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NIcon } from 'naive-ui'
import {
  GridOutline,
  CubeOutline,
  BookOutline,
  TimerOutline,
  ConstructOutline,
  PersonOutline,
  HomeOutline,
  FileTrayFullOutline,
  SparklesOutline,
  ReaderOutline,
  ChatbubblesOutline,
  FolderOutline,
  DocumentTextOutline,
  CalendarOutline,
  MailOutline,
  RocketOutline,
  CodeSlashOutline,
  SearchOutline,
  SettingsOutline,
  TimeOutline,
  MoonOutline,
  SunnyOutline,
  CogOutline,
  CloudOutline,
  CloudUploadOutline
} from '@vicons/ionicons5'
import { useMacNavStore } from '@/stores/mac-nav'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import type { QuickAccessItem, NavCategory } from '@/stores/mac-nav'

defineProps<{
  compact?: boolean
}>()

const emit = defineEmits<{
  (e: 'categorySelect', categoryId: string): void
  (e: 'userAction'): void
}>()

const router = useRouter()
const route = useRoute()
const macNavStore = useMacNavStore()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const categories = computed(() => macNavStore.categories)
const activeCategory = computed(() => macNavStore.activeCategory)
const topQuickAccess = computed(() => macNavStore.topQuickAccess)
const userDisplayName = computed(() => authStore.user?.displayName || authStore.user?.username || 'User')
const userInitial = computed(() => userDisplayName.value.slice(0, 1).toUpperCase())
const actualTheme = computed(() => {
  if (themeStore.mode === 'auto') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return themeStore.mode
})

// Icon mapping
const iconMap: Record<string, any> = {
  grid: GridOutline,
  cpu: CogOutline,
  brain: CloudOutline,
  timer: TimerOutline,
  construct: ConstructOutline,
  person: PersonOutline,
  home: HomeOutline,
  inbox: FileTrayFullOutline,
  sparkles: SparklesOutline,
  reader: ReaderOutline,
  cube: CubeOutline,
  settings: SettingsOutline,
  book: BookOutline,
  chatbubbles: ChatbubblesOutline,
  import: CloudUploadOutline,
  note: DocumentTextOutline,
  folder: FolderOutline,
  time: TimeOutline,
  calendar: CalendarOutline,
  mail: MailOutline,
  rocket: RocketOutline,
  code: CodeSlashOutline,
  search: SearchOutline
}

const getIconComponent = (iconName: string) => iconMap[iconName] || GridOutline

const handleQuickAccessClick = (item: QuickAccessItem) => {
  macNavStore.trackAccess(item.name, item.path, item.label, item.icon)
  router.push(item.path)
}

const handleCategoryClick = (categoryId: string) => {
  macNavStore.setActiveCategory(categoryId)
  emit('categorySelect', categoryId)

  // Navigate to first route in the category for immediate activation
  const firstRoute = macNavStore.getFirstRouteOfCategory(categoryId)
  if (firstRoute) {
    macNavStore.trackAccess(firstRoute.name, firstRoute.path, firstRoute.label, firstRoute.icon)
    router.push(firstRoute.path)
  }
}

const isCurrentCategory = (category: NavCategory) => {
  return category.routes.some(r => r.name === route.name)
}

const handleUserClick = () => {
  emit('userAction')
}

const toggleTheme = () => {
  themeStore.toggleTheme()
}
</script>

<style scoped>
/* ============================================
 * Global Rail - macOS Dock style
 * Fixed 72px width, ICON ONLY, no text labels
 * ============================================ */
.global-rail {
  position: fixed;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 50;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 72px;
  padding: 16px 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-rail);
  transition: width var(--transition-slide), padding var(--transition-slide), background-color var(--transition-base), border-color var(--transition-base);
  pointer-events: auto;
}

.global-rail.rail-compact {
  width: 56px;
  padding: 12px 6px;
}

/* Quick Access Section - Icon only, centered */
.quick-access-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 100%;
}

.quick-access-header {
  display: flex;
  justify-content: center;
  padding: 2px 0;
}

.quick-access-label {
  font-size: 0.6rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.quick-access-items {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px; /* Increased spacing for breathing room */
  width: 100%;
}

.quick-access-item {
  display: grid;
  place-items: center;
  width: 100%;
  padding: 6px;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast), transform var(--transition-fast);
}

.quick-access-item:hover {
  background: var(--bg-menu-item-hover);
}

.quick-access-item:active {
  background: var(--bg-menu-item-active);
  transform: scale(0.94);
}

.quick-icon-wrapper {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--warm-100);
  color: var(--primary-color);
  transition: background-color var(--transition-fast), border-color var(--transition-fast), transform var(--transition-fast);
  border: 1px solid var(--border-light);
}

.quick-access-item:hover .quick-icon-wrapper {
  background: var(--warm-200);
  transform: translateY(-1px) scale(1.025);
  border-color: var(--primary-light);
}

.quick-access-empty {
  display: grid;
  place-items: center;
  padding: 8px;
  color: var(--text-muted);
}

/* Divider */
.rail-divider {
  width: 32px;
  height: 1px;
  background: var(--border-light);
  margin: 4px 0;
}

/* Category Dock - macOS Dock style
 * NO TEXT LABELS - Only centered amber icons
 * Increased vertical gap for breathing room */
.category-dock {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px; /* Increased vertical spacing for breathing room */
  width: 100%;
}

.dock-item {
  position: relative;
  display: grid;
  place-items: center;
  width: 100%;
  padding: 8px 6px;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background-color var(--transition-fast), transform var(--transition-fast);
}

.dock-item:hover {
  background: var(--bg-menu-item-hover);
}

.dock-item.active {
  background: var(--bg-menu-item-active);
}

.dock-item:active {
  transform: scale(0.93);
}

.dock-icon-wrapper {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  background: var(--warm-100);
  color: var(--primary-color);
  transition: background-color var(--transition-fast), border-color var(--transition-fast), transform var(--transition-fast), box-shadow var(--transition-fast);
  border: 1px solid transparent;
}

.dock-item:hover .dock-icon-wrapper {
  background: var(--bg-elevated);
  border-color: var(--primary-light);
  box-shadow: 0 4px 12px var(--primary-glow);
  transform: translateY(-1px) scale(1.025);
}

.dock-item.active .dock-icon-wrapper {
  background: linear-gradient(135deg, var(--warm-200) 0%, var(--warm-100) 100%);
  border-color: var(--primary-light);
  box-shadow: 0 4px 16px var(--primary-glow);
}

/* Current category indicator - warm dot below */
.dock-indicator {
  position: absolute;
  bottom: 2px;
  left: 50%;
  transform: translateX(-50%);
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--primary-color);
  box-shadow: 0 0 6px var(--primary-color);
}

/* Rail Footer */
.rail-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  width: 100%;
  margin-top: auto;
}

.user-avatar-btn {
  display: grid;
  place-items: center;
  width: 100%;
  padding: 6px;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
}

.user-avatar-btn:hover {
  background: var(--bg-menu-item-hover);
}

.avatar-circle {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: var(--radius-md);
  background: var(--gradient-sunset);
  color: white;
  font-weight: 700;
  font-size: 0.85rem;
  transition: transform var(--transition-fast), box-shadow var(--transition-fast);
  box-shadow: 0 2px 8px var(--primary-glow);
  text-shadow: 0 1px 2px rgba(0,0,0,0.1);
}

.user-avatar-btn:hover .avatar-circle {
  transform: translateY(-1px) scale(1.025);
  box-shadow: 0 4px 14px rgba(234, 88, 12, 0.3);
}

.theme-toggle-btn {
  display: grid;
  place-items: center;
  width: 100%;
  padding: 6px;
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  color: var(--text-muted);
  cursor: pointer;
  transition: background-color var(--transition-fast), color var(--transition-fast);
}

.theme-toggle-btn:hover {
  background: var(--bg-menu-item-hover);
  color: var(--primary-color);
}
</style>
