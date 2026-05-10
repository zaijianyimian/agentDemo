<template>
  <!-- Naive UI Global Providers - Must wrap entire app -->
  <n-config-provider :theme-overrides="themeOverrides">
    <n-message-provider>
      <n-dialog-provider>
        <n-notification-provider>
          <!-- App Content -->
          <div class="app-root" :data-theme="actualTheme">
            <!-- Auth pages: direct render -->
            <router-view v-if="isAuthPage" />

            <!-- Main app: Mac-style shell -->
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
  NMessageProvider,
  NDialogProvider,
  NNotificationProvider,
  type GlobalThemeOverrides
} from 'naive-ui'
import { useThemeStore } from '@/stores/theme'
import { useAuthStore } from '@/stores/auth'
import { hasAccessToken } from '@/services/auth-token'
import MacAppShell from '@/components/MacAppShell.vue'

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

// Naive UI theme overrides for macOS style - Amber Gold Theme
// Dynamic based on actual theme for text visibility
const themeOverrides = computed<GlobalThemeOverrides>(() => {
  const isDark = actualTheme.value === 'dark'

  return {
    common: {
      primaryColor: isDark ? '#FB923C' : '#EA580C',
      primaryColorHover: isDark ? '#F97316' : '#F97316',
      primaryColorPressed: isDark ? '#EA580C' : '#C2410C',
      borderRadius: '14px',
      borderRadiusSmall: '10px',
      fontFamily: "'SF Pro Display', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif",
      // Critical: Ensure text is visible (warm dark on light, warm light on dark)
      textColor: isDark ? '#FFF7ED' : '#431407',
      textColor2: isDark ? '#FED7AA' : '#78350F',
      textColor3: isDark ? '#A16207' : '#A16207',
      textColorDisabled: isDark ? '#3D2E22' : '#FDE68A',
      // Background colors - warm cream/coffee
      bodyColor: isDark ? '#1C1410' : '#FDF6E3',
      cardColor: isDark ? '#2A1F18' : '#FFFBF0',
      inputColor: isDark ? '#3D2E22' : '#FEF3C7',
      borderColor: isDark ? '#3D2E22' : '#FDE68A'
    },
    Button: {
      borderRadiusMedium: '8px',
      textColor: isDark ? '#FFFFFF' : '#1D1D1F'
    },
    Card: {
      borderRadius: '16px',
      textColor: isDark ? '#FFFFFF' : '#1D1D1F'
    },
    Input: {
      borderRadius: '8px',
      textColor: isDark ? '#FFFFFF' : '#1D1D1F'
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
      textColor: isDark ? '#FFFFFF' : '#1D1D1F'
    },
    Form: {
      labelTextColor: isDark ? '#EBEBF5' : '#6E6E73'
    },
    DataTable: {
      textColor: isDark ? '#FFFFFF' : '#1D1D1F'
    }
  }
})

// Initialize auth store
onMounted(async () => {
  if (hasAccessToken() && !authStore.user && !authStore.initialized) {
    await authStore.hydrate()
  }
})

// Apply theme to document
watch(actualTheme, (theme) => {
  document.documentElement.setAttribute('data-theme', theme)
}, { immediate: true })

// Update document title
watch(() => route.meta.title, (title) => {
  document.title = `${title || 'Agent Grid'} - Dashboard`
})
</script>

<style>
/* Import macOS design variables */
@import '@/styles/variables.css';

/* App Root - Ensure proper height inheritance */
.app-root {
  height: 100vh;
  width: 100vw;
  overflow: hidden;
}
</style>