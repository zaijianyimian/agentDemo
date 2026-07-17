import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 全局 UI 状态 Store
 *
 * 职责：管理跨页面共享的 UI 开关状态，例如沉浸模式与侧边栏折叠状态。
 * State 形状：
 *  - immersiveMode: boolean - 是否启用沉浸模式（隐藏头部等装饰元素，专注内容）
 *  - sidebarCollapsed: boolean - 侧边栏是否折叠
 */
export const useUIStore = defineStore('ui', () => {
  // Immersive mode - hides header for distraction-free chat
  const immersiveMode = ref(false)

  const toggleImmersiveMode = () => {
    immersiveMode.value = !immersiveMode.value
  }

  const setImmersiveMode = (value: boolean) => {
    immersiveMode.value = value
  }

  // Sidebar collapse state (persisted per route)
  const sidebarCollapsed = ref(false)

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return {
    immersiveMode,
    toggleImmersiveMode,
    setImmersiveMode,
    sidebarCollapsed,
    toggleSidebar
  }
})