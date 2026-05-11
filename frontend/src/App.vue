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
      borderRadius: '12px',
      borderRadiusSmall: '8px',
      fontFamily: "'SF Pro Display', -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif",
      // Critical: Ensure text is visible (warm dark on light, warm light on dark)
      textColor: isDark ? '#FFF7ED' : '#321207',
      textColor2: isDark ? '#FED7AA' : '#6F3312',
      textColor3: isDark ? '#D6A45F' : '#9A5A16',
      textColorDisabled: isDark ? '#7B5F44' : '#CBAA68',
      // Background colors - warm cream/coffee
      bodyColor: isDark ? '#1B140F' : '#FFF9EC',
      cardColor: isDark ? '#2B2018' : '#FFFDF7',
      inputColor: isDark ? '#3D2E22' : '#FFF7E6',
      borderColor: isDark ? '#4A3526' : '#F3DBA4'
    },
    Button: {
      borderRadiusMedium: '8px',
      textColor: isDark ? '#FFF7ED' : '#321207'
    },
    Card: {
      borderRadius: '14px',
      textColor: isDark ? '#FFF7ED' : '#321207'
    },
    Input: {
      borderRadius: '8px',
      textColor: isDark ? '#FFF7ED' : '#321207'
    },
    Tag: {
      borderRadius: '100px'
    },
    Menu: {
      textColor: isDark ? '#FFF7ED' : '#321207',
      itemTextColor: isDark ? '#FFF7ED' : '#321207'
    },
    Dropdown: {
      textColor: isDark ? '#FFF7ED' : '#321207'
    },
    Modal: {
      textColor: isDark ? '#FFF7ED' : '#321207'
    },
    Form: {
      labelTextColor: isDark ? '#FED7AA' : '#6F3312'
    },
    DataTable: {
      textColor: isDark ? '#FFF7ED' : '#321207'
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
