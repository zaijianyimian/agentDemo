<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <EmailNotificationBridge />
          <div class="app-root" :data-theme="actualTheme">
            <router-view v-if="isAuthPage" />
            <MacAppShell v-else>
              <router-view />
            </MacAppShell>
          </div>
        </n-notification-provider>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  NConfigProvider,
  NDialogProvider,
  NMessageProvider,
  NNotificationProvider,
  type GlobalThemeOverrides
} from 'naive-ui'
import EmailNotificationBridge from '@/components/EmailNotificationBridge.vue'
import MacAppShell from '@/components/MacAppShell.vue'
import { hasAccessToken } from '@/services/auth-token'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'

const route = useRoute()
const themeStore = useThemeStore()
const authStore = useAuthStore()

const isAuthPage = computed(() => route.path === '/login' || route.path === '/oauth/github/callback')

const actualTheme = computed(() => {
  if (themeStore.mode === 'auto') {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  }
  return themeStore.mode
})

// Agent Workspace 使用克制的橙色作为强调色，业务页面统一复用 Naive UI 主题变量。
const themeOverrides = computed<GlobalThemeOverrides>(() => {
  const isDark = actualTheme.value === 'dark'

  return {
    common: {
      primaryColor: isDark ? '#FB923C' : '#EA580C',
      primaryColorHover: '#F97316',
      primaryColorPressed: isDark ? '#EA580C' : '#C2410C',
      primaryColorSuppl: isDark ? '#FDBA74' : '#F97316',
      borderRadius: '12px',
      borderRadiusSmall: '8px',
      fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif",
      textColor: isDark ? '#F8FAFC' : '#111827',
      textColor2: isDark ? '#CBD5E1' : '#475569',
      textColor3: isDark ? '#94A3B8' : '#64748B',
      textColorDisabled: isDark ? '#64748B' : '#94A3B8',
      bodyColor: isDark ? '#111318' : '#F6F7F9',
      cardColor: isDark ? '#1F2430' : '#FFFFFF',
      inputColor: isDark ? '#262B36' : '#F3F4F6',
      modalColor: isDark ? '#1F2430' : '#FFFFFF',
      popoverColor: isDark ? '#1F2430' : '#FFFFFF',
      tableColor: isDark ? '#1F2430' : '#FFFFFF',
      borderColor: isDark ? '#2D3748' : '#E2E8F0',
      dividerColor: isDark ? '#2D3748' : '#E2E8F0'
    },
    Button: {
      borderRadiusMedium: '8px',
      textColor: isDark ? '#F8FAFC' : '#111827',
      textColorHover: isDark ? '#FFFFFF' : '#0F172A',
      textColorPressed: isDark ? '#FFFFFF' : '#0F172A',
      borderHover: isDark ? '1px solid #FB923C' : '1px solid #EA580C'
    },
    Card: {
      borderRadius: '12px',
      color: isDark ? '#1F2430' : '#FFFFFF',
      textColor: isDark ? '#F8FAFC' : '#111827',
      borderColor: isDark ? '#2D3748' : '#E2E8F0'
    },
    Input: {
      borderRadius: '8px',
      color: isDark ? '#262B36' : '#F3F4F6',
      colorFocus: isDark ? '#1F2430' : '#FFFFFF',
      textColor: isDark ? '#F8FAFC' : '#111827',
      borderFocus: isDark ? '1px solid #FB923C' : '1px solid #EA580C',
      boxShadowFocus: isDark ? '0 0 0 3px rgba(251, 146, 60, 0.22)' : '0 0 0 3px rgba(234, 88, 12, 0.18)'
    },
    Tag: {
      borderRadius: '100px'
    },
    Menu: {
      textColor: isDark ? '#FFFFFF' : '#1D1D1F',
      itemTextColor: isDark ? '#FFFFFF' : '#1D1D1F'
    },
    Dropdown: {
      textColor: isDark ? '#FFFFFF' : '#1D1D1F'
    },
    Modal: {
      color: isDark ? '#1F2430' : '#FFFFFF',
      textColor: isDark ? '#F8FAFC' : '#111827'
    },
    Form: {
      labelTextColor: isDark ? '#EBEBF5' : '#6E6E73'
    },
    DataTable: {
      textColor: isDark ? '#FFFFFF' : '#1D1D1F'
    }
  }
})

onMounted(async () => {
  if (hasAccessToken() && !authStore.user && !authStore.initialized) {
    await authStore.hydrate()
  }
})

watch(actualTheme, theme => {
  document.documentElement.setAttribute('data-theme', theme)
}, { immediate: true })

watch(() => route.meta.title, title => {
  document.title = `${title || 'Agent Workspace'} - Agent Workspace`
})
</script>

<style>
@import '@/styles/variables.css';

.app-root {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
</style>
