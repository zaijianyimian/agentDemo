<template>
  <aside
    class="functional-pane"
    :class="{ open: isOpen, animating: animating, loading: isLoading }"
    :style="{ '--pane-category-color': categoryColor }"
  >
    <!-- Pane Header -->
    <div class="pane-header">
      <div class="pane-category-info">
        <div class="pane-category-icon">
          <n-icon size="20" v-if="!isLoading">
            <component :is="getIconComponentSafe(categoryIcon)" />
          </n-icon>
          <div v-else class="icon-skeleton"></div>
        </div>
        <div class="pane-category-text">
          <span class="pane-category-label" v-if="!isLoading">{{ categoryLabel }}</span>
          <span class="pane-category-label skeleton-text" v-else>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>
          <span class="pane-category-count" v-if="!isLoading">{{ routeCount }} 项</span>
          <span class="pane-category-count skeleton-text" v-else>&nbsp;&nbsp;</span>
        </div>
      </div>
      <button class="pane-close-btn" @click="handleClose" title="关闭">
        <n-icon size="16"><ChevronBackOutline /></n-icon>
      </button>
    </div>

    <!-- Pane Routes -->
    <div class="pane-routes">
      <!-- Skeleton loading during animation -->
      <template v-if="isLoading">
        <div class="pane-skeleton-wrapper">
          <div class="route-skeleton" v-for="i in 3" :key="i">
            <div class="icon-skeleton small"></div>
            <div class="content-skeleton">
              <div class="label-skeleton"></div>
              <div class="desc-skeleton"></div>
            </div>
          </div>
        </div>
      </template>

      <!-- Actual routes when loaded -->
      <template v-else-if="routes.length > 0">
        <button
          v-for="navRoute in routes"
          :key="navRoute.name"
          :class="['pane-route-item', { active: currentRoute === navRoute.name, hasInspector: navRoute.hasInspector }]"
          @click="handleRouteClick(navRoute)"
        >
          <div class="route-icon">
            <n-icon size="18">
              <component :is="getIconComponentSafe(navRoute.icon)" />
            </n-icon>
          </div>
          <div class="route-content">
            <span class="route-label">{{ navRoute.label }}</span>
            <span class="route-desc">{{ navRoute.description }}</span>
          </div>
          <div class="route-indicator" v-if="currentRoute === navRoute.name">
            <n-icon size="14"><ChevronForwardOutline /></n-icon>
          </div>
          <button
            v-if="navRoute.hasInspector"
            class="inspector-toggle"
            @click.stop="handleInspectorToggle(navRoute)"
            title="参数面板"
          >
            <n-icon size="14"><CogOutline /></n-icon>
          </button>
        </button>
      </template>

      <!-- Empty state -->
      <div v-else-if="isOpen && !isLoading" class="pane-empty">
        <n-icon size="24"><GridOutline /></n-icon>
        <span>暂无内容</span>
      </div>
    </div>

    <!-- Pane Footer - subtle branding -->
    <div class="pane-footer">
      <span class="pane-brand">Agent Grid</span>
      <span class="pane-version">v2.0</span>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { NIcon } from 'naive-ui'
import {
  ChevronBackOutline,
  ChevronForwardOutline,
  CogOutline,
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
  CloudOutline,
  CloudUploadOutline
} from '@vicons/ionicons5'
import { useMacNavStore } from '@/stores/mac-nav'
import type { NavRoute } from '@/stores/mac-nav'

const emit = defineEmits<{
  (e: 'routeSelect', route: NavRoute): void
  (e: 'inspectorToggle', route: NavRoute): void
  (e: 'close'): void
}>()

const router = useRouter()
const route = useRoute()
const macNavStore = useMacNavStore()

const isOpen = computed(() => macNavStore.activeCategory !== null)
const animating = computed(() => macNavStore.paneAnimating)
const currentCategory = computed(() => macNavStore.currentCategory)
const currentRoute = computed(() => route.name as string)

// Loading state - show skeleton during animation and data fetch
const isLoading = computed(() => animating.value || !currentCategory.value)

const categoryLabel = computed(() => currentCategory.value?.label || '')
const categoryIcon = computed(() => currentCategory.value?.icon || 'grid')
const categoryColor = computed(() => currentCategory.value?.color || 'var(--primary-color)')
const routes = computed(() => currentCategory.value?.routes || [])
const routeCount = computed(() => routes.value.length)

// Icon mapping with all icons defined
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

// Safe icon component getter - always returns a valid component
const getIconComponentSafe = (iconName: string | undefined): any => {
  if (!iconName) return GridOutline
  return iconMap[iconName] || GridOutline
}

const handleRouteClick = (navRoute: NavRoute) => {
  macNavStore.trackAccess(navRoute.name, navRoute.path, navRoute.label, navRoute.icon)
  router.push(navRoute.path)
  emit('routeSelect', navRoute)
}

const handleInspectorToggle = (navRoute: NavRoute) => {
  macNavStore.toggleInspector(navRoute.name)
  emit('inspectorToggle', navRoute)
}

const handleClose = () => {
  macNavStore.closePane()
  emit('close')
}

// Sync category state with current route
watch(() => route.name, (routeName) => {
  if (routeName && !macNavStore.activeCategory) {
    const category = macNavStore.getCategoryForRoute(routeName as string)
    if (category) {
      // Optionally auto-open pane for current route
      // macNavStore.setActiveCategory(category.id)
    }
  }
}, { immediate: true })

// Ensure pane content is ready before rendering
watch(() => macNavStore.activeCategory, async (newCategory) => {
  if (newCategory) {
    // Wait for animation to complete before showing content
    await nextTick()
  }
})
</script>

<style scoped>
/* ============================================
 * Functional Pane - macOS Finder Sidebar
 * Fixed 240px width, proper flex proportions
 * ============================================ */
.functional-pane {
  position: fixed;
  left: 96px;
  top: 50%;
  transform: translateY(-50%) translateX(-100%);
  z-index: 40;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 240px;
  height: auto;
  max-height: calc(100vh - 48px);
  padding: 14px 12px;
  background: var(--bg-card);
  border: 2px solid var(--border-light);
  border-radius: var(--radius-xl);
  box-shadow: var(--shadow-lg);
  opacity: 0;
  visibility: hidden;
  transition:
    visibility 0s 0.2s,
    opacity 0.2s ease,
    transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  pointer-events: none;
}

.functional-pane.open {
  transform: translateY(-50%) translateX(0);
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transition:
    visibility 0s 0s,
    opacity 0.2s ease,
    transform 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.functional-pane.animating,
.functional-pane.loading {
  pointer-events: none;
}

/* Pane Header */
.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--border-light);
}

.pane-category-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pane-category-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: linear-gradient(180deg, rgba(217, 119, 6, 0.12) 0%, rgba(217, 119, 6, 0.06) 100%);
  color: var(--pane-category-color);
}

.pane-category-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.pane-category-label {
  font-size: 0.9rem;
  font-weight: 600;
  /* Explicit color with fallback for visibility */
  color: var(--text-primary, #1D1D1F);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.pane-category-count {
  font-size: 0.7rem;
  /* Explicit color with fallback */
  color: var(--text-muted, #AEAEB2);
}

.pane-close-btn {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.pane-close-btn:hover {
  background: var(--bg-menu-item-hover);
  color: var(--primary-color);
}

/* Pane Routes - Proper flex layout */
.pane-routes {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  overflow-y: auto;
  padding: 10px 4px; /* Side padding for capsule margins */
  scrollbar-width: thin;
}

/* Route Item - Flex layout: [icon 20px] + [gap 12px] + [text flex:1]
 * Capsule background with 8px side margins
 * Left padding adjusted to align text with category header */
.pane-route-item {
  display: flex;
  align-items: center;
  gap: 12px; /* Icon to text spacing */
  width: calc(100% - 8px); /* Leave 4px margin on each side */
  margin: 0 4px; /* Capsule margins */
  padding: 10px 10px 10px 10px; /* Inner padding - left aligned with category */
  background: transparent;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
}

.pane-route-item:hover {
  background: var(--bg-menu-item-hover);
}

/* Active state - Warm capsule background */
.pane-route-item.active {
  background: var(--bg-menu-item-active);
  box-shadow: inset 0 0 0 1.5px var(--border-accent);
}

.pane-route-item:active {
  transform: scale(0.97);
}

/* Route Icon - Fixed 20px, amber color */
.route-icon {
  display: grid;
  place-items: center;
  width: 20px; /* Fixed icon width */
  height: 20px;
  flex-shrink: 0;
  color: var(--pane-category-color);
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.pane-route-item.active .route-icon {
  color: var(--primary-color);
}

/* Route Content - Flex: 1, takes remaining space */
.route-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0; /* Allow text to shrink */
}

/* Route Label - No truncation, proper space */
.route-label {
  font-size: 0.85rem;
  font-weight: 500;
  /* Explicit color with fallback for visibility */
  color: var(--text-primary, #1D1D1F);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.pane-route-item.active .route-label {
  color: var(--text-strong, #1D1D1F);
}

/* Route Description - Prevent overflow */
.route-desc {
  font-size: 0.72rem;
  /* Explicit color with fallback */
  color: var(--text-muted, #AEAEB2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.2;
}

/* Route Indicator - Chevron for active */
.route-indicator {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: var(--pane-category-color);
}

/* Inspector Toggle Button */
.inspector-toggle {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  background: var(--warm-100);
  border: none;
  border-radius: var(--radius-xs);
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  flex-shrink: 0;
}

.inspector-toggle:hover {
  background: var(--warm-200);
  color: var(--primary-color);
}

/* Inspector badge on route item */
.pane-route-item.hasInspector::after {
  content: '';
  position: absolute;
  right: 8px;
  bottom: 8px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--pane-category-color);
  opacity: 0.5;
}

.pane-route-item.hasInspector:hover::after,
.pane-route-item.hasInspector.active::after {
  opacity: 1;
}

/* Pane Footer */
.pane-footer {
  display: flex;
  justify-content: space-between;
  padding-top: 10px;
  border-top: 2px solid var(--border-light);
}

.pane-brand {
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--text-muted);
}

.pane-version {
  font-size: 0.62rem;
  color: var(--text-muted);
}

/* Empty state */
.pane-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: var(--text-muted);
  font-size: 0.8rem;
}

/* Skeleton Loading - macOS amber style */
.pane-skeleton-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 4px;
}

.route-skeleton {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  margin: 0 4px;
}

.icon-skeleton {
  width: 20px;
  height: 20px;
  border-radius: var(--radius-xs);
  background: linear-gradient(
    90deg,
    rgba(217, 119, 6, 0.08) 0%,
    rgba(217, 119, 6, 0.12) 50%,
    rgba(217, 119, 6, 0.08) 100%
  );
  animation: skeletonPulse 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

.icon-skeleton.small {
  width: 16px;
  height: 16px;
}

.icon-skeleton.large {
  width: 32px;
  height: 32px;
}

.content-skeleton {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.label-skeleton {
  width: 50%;
  height: 12px;
  border-radius: 3px;
  background: linear-gradient(
    90deg,
    rgba(217, 119, 6, 0.06) 0%,
    rgba(217, 119, 6, 0.10) 50%,
    rgba(217, 119, 6, 0.06) 100%
  );
  animation: skeletonPulse 1.5s ease-in-out infinite;
}

.desc-skeleton {
  width: 70%;
  height: 8px;
  border-radius: 3px;
  background: linear-gradient(
    90deg,
    rgba(217, 119, 6, 0.04) 0%,
    rgba(217, 119, 6, 0.08) 50%,
    rgba(217, 119, 6, 0.04) 100%
  );
  animation: skeletonPulse 1.5s ease-in-out infinite;
  animation-delay: 0.2s;
}

.skeleton-text {
  background: linear-gradient(
    90deg,
    rgba(217, 119, 6, 0.06) 0%,
    rgba(217, 119, 6, 0.10) 50%,
    rgba(217, 119, 6, 0.06) 100%
  );
  animation: skeletonPulse 1.5s ease-in-out infinite;
  border-radius: 3px;
}

@keyframes skeletonPulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

/* Responsive */
@media (max-width: 1024px) {
  .functional-pane {
    left: 88px;
  }
}

@media (max-width: 768px) {
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