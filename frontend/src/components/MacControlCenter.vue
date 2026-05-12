<template>
  <div class="control-center">
    <!-- Status Icons - macOS Control Center style -->
    <div class="status-icons">
      <!-- Model Status -->
      <button
        class="status-icon-btn"
        :class="{ active: modelStatus === 'active' }"
        @click="handleStatusClick('model')"
        title="模型状态"
      >
        <n-icon size="16">
          <CubeOutline />
        </n-icon>
        <span class="status-dot" v-if="modelStatus === 'active'"></span>
      </button>

      <!-- Qdrant Status -->
      <button
        class="status-icon-btn"
        :class="{ active: qdrantStatus === 'active' }"
        @click="handleStatusClick('qdrant')"
        title="向量数据库"
      >
        <n-icon size="16">
          <CloudOutline />
        </n-icon>
        <span class="status-dot" v-if="qdrantStatus === 'active'"></span>
      </button>

      <!-- Search Status -->
      <button
        class="status-icon-btn"
        :class="{ active: searchStatus === 'active' }"
        @click="handleStatusClick('search')"
        title="搜索服务"
      >
        <n-icon size="16">
          <SearchOutline />
        </n-icon>
        <span class="status-dot" v-if="searchStatus === 'active'"></span>
      </button>

      <!-- Notification Badge -->
      <button
        class="status-icon-btn has-badge"
        @click="handleStatusClick('notifications')"
        title="通知"
        v-if="notificationCount > 0"
      >
        <n-icon size="16">
          <NotificationsOutline />
        </n-icon>
        <span class="badge-count">{{ notificationCount > 9 ? '9+' : notificationCount }}</span>
      </button>
    </div>

    <!-- Divider -->
    <div class="control-divider"></div>

    <!-- Search Capsule -->
    <button class="search-capsule" @click="handleSearchClick">
      <n-icon size="14"><SearchOutline /></n-icon>
      <span class="search-placeholder">搜索...</span>
      <kbd class="search-kbd">⌘K</kbd>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { NIcon } from 'naive-ui'
import {
  CubeOutline,
  CloudOutline,
  SearchOutline,
  NotificationsOutline
} from '@vicons/ionicons5'

const emit = defineEmits<{
  (e: 'statusClick', type: string): void
  (e: 'searchClick'): void
}>()

// Props for status
const props = defineProps<{
  modelStatus?: 'active' | 'inactive' | 'error'
  qdrantStatus?: 'active' | 'inactive' | 'error'
  searchStatus?: 'active' | 'inactive' | 'error'
  notificationCount?: number
}>()

const modelStatus = computed(() => props.modelStatus || 'inactive')
const qdrantStatus = computed(() => props.qdrantStatus || 'inactive')
const searchStatus = computed(() => props.searchStatus || 'inactive')
const notificationCount = computed(() => props.notificationCount || 0)

const handleStatusClick = (type: string) => {
  emit('statusClick', type)
}

const handleSearchClick = () => {
  emit('searchClick')
}
</script>

<style scoped>
/* Control Center - Warm style */
.control-center {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-light);
}

.status-icons {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* Status Icon Button - macOS Control Center pill */
.status-icon-btn {
  position: relative;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: background-color var(--transition-fast), color var(--transition-fast), transform var(--transition-fast);
}

.status-icon-btn:hover {
  background: var(--bg-menu-item-hover);
  color: var(--text-secondary);
}

.status-icon-btn.active {
  color: var(--primary-color);
}

.status-icon-btn.active:hover {
  background: var(--bg-active);
}

.status-icon-btn:active {
  transform: scale(0.92);
}

/* Active dot indicator */
.status-dot {
  position: absolute;
  bottom: 4px;
  right: 4px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--success);
}

/* Badge for notifications */
.status-icon-btn.has-badge {
  position: relative;
}

.badge-count {
  position: absolute;
  top: 2px;
  right: 2px;
  display: grid;
  place-items: center;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  background: var(--error);
  color: white;
  font-size: 0.6rem;
  font-weight: 600;
  border-radius: var(--radius-full);
}

/* Divider */
.control-divider {
  width: 1px;
  height: 20px;
  background: var(--border-light);
}

/* Search Capsule */
.search-capsule {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-input);
  border: none;
  border-radius: var(--radius-md);
  color: var(--text-muted);
  cursor: pointer;
  transition: background-color var(--transition-fast), color var(--transition-fast), transform var(--transition-fast);
}

.search-capsule:hover {
  background: var(--bg-elevated);
  color: var(--text-secondary);
}

.search-capsule:active {
  transform: scale(0.98);
}

.search-placeholder {
  font-size: 0.8rem;
}

.search-kbd {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  background: var(--bg-elevated);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-xs);
  font-size: 0.7rem;
  font-family: var(--font-mono);
  color: var(--text-muted);
}
</style>
