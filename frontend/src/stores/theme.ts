import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type ThemeMode = 'dark' | 'light' | 'auto'

/** 管理主题偏好；无有效偏好时默认日间，auto 模式响应系统变化。 */
export const useThemeStore = defineStore('theme', () => {
  let savedTheme: string | null = null
  try {
    savedTheme = localStorage.getItem('theme')
  } catch {
    // 存储不可用时仍可切换主题。
  }
  const mode = ref<ThemeMode>(
    savedTheme === 'dark' || savedTheme === 'light' || savedTheme === 'auto'
      ? savedTheme
      : 'light'
  )
  const systemThemeQuery = window.matchMedia('(prefers-color-scheme: dark)')
  const systemDark = ref(systemThemeQuery.matches)
  const actualTheme = computed(() =>
    mode.value === 'auto' ? (systemDark.value ? 'dark' : 'light') : mode.value
  )

  const setTheme = (newTheme: ThemeMode) => {
    mode.value = newTheme
    try {
      localStorage.setItem('theme', newTheme)
    } catch {
      // 当前会话仍应用选择，不要求浏览器允许持久化。
    }
  }
  const toggleTheme = () => {
    setTheme(actualTheme.value === 'dark' ? 'light' : 'dark')
  }

  systemThemeQuery.addEventListener('change', event => {
    systemDark.value = event.matches
  })

  watch(actualTheme, theme => {
    document.documentElement.setAttribute('data-theme', theme)
    document.documentElement.style.colorScheme = theme
  }, { immediate: true, flush: 'sync' })

  return { mode, actualTheme, toggleTheme, setTheme }
})
