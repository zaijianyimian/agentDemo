import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export type ThemeMode = 'dark' | 'light' | 'auto'

/**
 * 主题状态 Store
 *
 * 职责：管理全局 light/dark/auto 三种主题模式，并将其持久化与应用到 documentElement。
 * State 形状：
 *  - mode: ThemeMode - 用户选择的主题模式（持久化于 localStorage 'theme'）
 *
 * 副作用：注册 prefers-color-scheme 媒体监听以支持 auto 模式；通过 watch 自动应用主题。
 */
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

  /**
   * 根据当前 mode 计算最终 dark/light 主题，设置 data-theme 属性，
   * 并在切换时添加短暂过渡 class 后移除，以实现平滑的过渡动画。
   */
  const applyTheme = () => {
    let actualTheme: 'dark' | 'light'
    if (mode.value === 'auto') {
      // 自动模式：根据系统偏好设置
      actualTheme = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    } else {
      actualTheme = mode.value
    }

    // Add transition class for smooth theme change
    document.documentElement.classList.add('theme-transition')

    document.documentElement.setAttribute('data-theme', actualTheme)

    // Remove transition class after animation completes
    setTimeout(() => {
      document.documentElement.classList.remove('theme-transition')
    }, 400)
  }

  // 监听系统主题变化（用于 auto 模式）。
// 注意：Pinia store 没有卸载阶段，监听器会常驻进程——这是预期的（主题感知跨页面）。
const systemThemeQuery = window.matchMedia('(prefers-color-scheme: dark)')
systemThemeQuery.addEventListener('change', () => {
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