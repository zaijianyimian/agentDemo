<template>
  <header class="app-topbar">
    <div class="topbar-title">
      <span class="topbar-category">{{ category }}</span>
      <h1>{{ title }}</h1>
    </div>

    <button class="command-button" type="button" @click="$emit('search')">
      <n-icon size="17"><SearchOutline /></n-icon>
      <span>搜索页面或操作</span>
      <kbd>⌘ K</kbd>
    </button>

    <div class="topbar-actions">
      <div class="service-statuses" aria-label="系统状态">
        <button type="button" class="status-pill" @click="$emit('status', 'model')" title="模型状态">
          <span :class="['status-dot', modelStatus]"></span>
          <span class="status-label">Model</span>
        </button>
        <button type="button" class="status-pill" @click="$emit('status', 'qdrant')" title="Qdrant 状态">
          <span :class="['status-dot', qdrantStatus]"></span>
          <span class="status-label">Memory</span>
        </button>
        <button type="button" class="status-pill" @click="$emit('status', 'search')" title="搜索状态">
          <span :class="['status-dot', searchStatus]"></span>
          <span class="status-label">Search</span>
        </button>
      </div>

      <button class="notification-button" type="button" @click="$emit('status', 'notifications')" title="收件箱">
        <n-icon size="18"><NotificationsOutline /></n-icon>
        <span v-if="notificationCount > 0" class="notification-badge">{{ notificationCount > 99 ? '99+' : notificationCount }}</span>
      </button>

      <n-dropdown :options="userMenuOptions" @select="handleUserSelect">
        <button class="user-button" type="button">
          <span class="user-avatar">{{ userInitial }}</span>
          <span class="user-copy">
            <strong>{{ userName }}</strong>
            <small>{{ userRole }}</small>
          </span>
        </button>
      </n-dropdown>

      <n-button quaternary circle :loading="logoutLoading" @click="$emit('logout')" title="退出登录">
        <template #icon><n-icon><LogOutOutline /></n-icon></template>
      </n-button>
    </div>
  </header>
</template>

<script setup lang="ts">
/**
 * Agent Workspace 顶部栏。
 *
 * 展示当前页面、全局命令入口、基础服务状态和当前用户入口。
 */
import { computed } from 'vue'
import { NButton, NDropdown, NIcon } from 'naive-ui'
import {
  LogOutOutline,
  NotificationsOutline,
  SearchOutline
} from '@vicons/ionicons5'

type ServiceStatus = 'active' | 'inactive' | 'error'

const props = withDefaults(defineProps<{
  title: string
  category: string
  userName: string
  userInitial: string
  userEmail?: string
  userRole?: string
  modelStatus?: ServiceStatus
  qdrantStatus?: ServiceStatus
  searchStatus?: ServiceStatus
  notificationCount?: number
  logoutLoading?: boolean
}>(), {
  userEmail: '-',
  userRole: 'USER',
  modelStatus: 'inactive',
  qdrantStatus: 'inactive',
  searchStatus: 'inactive',
  notificationCount: 0,
  logoutLoading: false
})

const emit = defineEmits<{
  search: []
  status: [type: string]
  logout: []
  userAction: [key: string]
}>()

const userMenuOptions = computed(() => [
  { key: 'email', label: props.userEmail || '-' },
  { key: 'role', label: `角色: ${props.userRole}` },
  { type: 'divider', key: 'divider' },
  { key: 'password', label: '修改密码' },
  { key: 'personal', label: '个人中心' }
])

const handleUserSelect = (key: string) => {
  emit('userAction', key)
}
</script>

<style scoped>
.app-topbar {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(260px, 420px) minmax(300px, 1fr);
  align-items: center;
  gap: 18px;
  min-height: 68px;
  padding: 10px 20px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--workspace-border, var(--border-light));
}

.topbar-title {
  min-width: 0;
}

.topbar-category {
  display: block;
  margin-bottom: 2px;
  color: var(--text-muted);
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.topbar-title h1 {
  margin: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 1.05rem;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.command-button {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  height: 38px;
  padding: 0 10px 0 12px;
  border: 1px solid var(--workspace-border, var(--border-light));
  border-radius: 10px;
  background: var(--bg-input);
  color: var(--text-muted);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.command-button:hover {
  border-color: color-mix(in srgb, var(--primary-color) 42%, var(--workspace-border, var(--border-light)));
  color: var(--text-primary);
}

.command-button span {
  flex: 1;
  overflow: hidden;
  text-align: left;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.command-button kbd {
  padding: 2px 6px;
  border: 1px solid var(--workspace-border, var(--border-light));
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-muted);
  font-family: inherit;
  font-size: 0.66rem;
}

.topbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 0;
}

.service-statuses {
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-pill,
.notification-button,
.user-button {
  border: 0;
  font: inherit;
  cursor: pointer;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 8px;
  border-radius: 8px;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.68rem;
}

.status-pill:hover {
  background: var(--bg-input);
  color: var(--text-primary);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
}

.status-dot.active {
  background: #22c55e;
}

.status-dot.error {
  background: #ef4444;
}

.notification-button {
  position: relative;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
}

.notification-button:hover {
  background: var(--bg-input);
  color: var(--text-primary);
}

.notification-badge {
  position: absolute;
  top: 3px;
  right: 2px;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border: 2px solid var(--bg-card);
  border-radius: 99px;
  background: var(--primary-color);
  color: white;
  font-size: 0.56rem;
  font-weight: 700;
  line-height: 11px;
}

.user-button {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 4px 7px 4px 4px;
  border-radius: 10px;
  background: transparent;
  color: inherit;
}

.user-button:hover {
  background: var(--bg-input);
}

.user-avatar {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--primary-color) 16%, var(--bg-input));
  color: var(--primary-color);
  font-size: 0.78rem;
  font-weight: 800;
}

.user-copy {
  display: grid;
  min-width: 0;
  text-align: left;
}

.user-copy strong,
.user-copy small {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.user-copy strong {
  max-width: 110px;
  color: var(--text-primary);
  font-size: 0.72rem;
}

.user-copy small {
  color: var(--text-muted);
  font-size: 0.6rem;
}

@media (max-width: 1180px) {
  .app-topbar {
    grid-template-columns: minmax(150px, 1fr) minmax(220px, 320px) auto;
  }

  .status-label,
  .user-copy {
    display: none;
  }
}

@media (max-width: 820px) {
  .app-topbar {
    grid-template-columns: minmax(0, 1fr) auto;
    min-height: 58px;
    padding: 8px 12px;
  }

  .command-button,
  .service-statuses {
    display: none;
  }
}
</style>
