import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeMode = 'dark' | 'light' | 'auto'

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>((localStorage.getItem('theme') as ThemeMode) || 'dark')

  const toggleTheme = () => {
    mode.value = mode.value === 'dark' ? 'light' : 'dark'
    localStorage.setItem('theme', mode.value)
    applyTheme()
  }

  const setTheme = (newTheme: ThemeMode) => {
    mode.value = newTheme
    localStorage.setItem('theme', newTheme)
    applyTheme()
  }

  const applyTheme = () => {
    let actualTheme: 'dark' | 'light'
    if (mode.value === 'auto') {
      // 自动模式：根据系统偏好设置
      actualTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    } else {
      actualTheme = mode.value
    }
    document.documentElement.setAttribute('data-theme', actualTheme)
  }

  // 监听系统主题变化（用于 auto 模式）
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (mode.value === 'auto') {
      applyTheme()
    }
  })

  watch(mode, () => {
    applyTheme()
  }, { immediate: true })

  return {
    mode,
    toggleTheme,
    setTheme
  }
})