import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * UI State Store
 * Manages global UI states like immersive mode, focus mode, etc.
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