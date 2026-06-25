<template>
  <div class="immersive-layout" :class="{ 'header-collapsed': !isNavVisible }">
    <!-- Header Container - uses CSS grid for smooth transitions -->
    <div class="header-container" :style="{ '--header-height': $props.headerHeight }">
      <div class="header-content">
        <!-- Header slot - where actual header content goes -->
        <slot name="header"></slot>

        <!-- Collapse Trigger Zone - at bottom edge of header -->
        <div class="collapse-trigger" @click="collapseHeader" title="收起导航栏">
          <div class="trigger-bar">
            <n-icon size="12"><ChevronUpOutline /></n-icon>
            <span class="trigger-text">收起</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Recovery Button - appears when header is collapsed -->
    <Transition name="recovery">
      <button v-if="!isNavVisible" class="recovery-btn" @click="expandHeader" title="恢复导航栏">
        <n-icon size="18"><ChevronDownOutline /></n-icon>
        <span class="recovery-text">恢复导航</span>
      </button>
    </Transition>

    <!-- Main Content Area - smoothly expands when header collapses -->
    <div class="content-container">
      <slot name="content"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { NIcon } from 'naive-ui'
import { ChevronUpOutline, ChevronDownOutline } from '@vicons/ionicons5'
import { useUIStore } from '@/stores/ui'

// Props
withDefaults(defineProps<{
  headerHeight?: string
}>(), {
  headerHeight: '80px'
})

// UI Store
const uiStore = useUIStore()

// Local state
const isNavVisible = ref(true)

// Collapse header
const collapseHeader = () => {
  isNavVisible.value = false
  uiStore.setImmersiveMode(true)
}

// Expand header
const expandHeader = () => {
  isNavVisible.value = true
  uiStore.setImmersiveMode(false)
}

// Watch uiStore changes to sync local state
watch(() => uiStore.immersiveMode, (newValue) => {
  isNavVisible.value = !newValue
})

// Expose methods for parent component
defineExpose({
  collapseHeader,
  expandHeader,
  isNavVisible
})
</script>

<style scoped>
/* ============================================
 * Immersive Header - SPA Flexbox Layout
 * Strict 100vh container, internal scrolling
 * ============================================ */

.immersive-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

/* Header Container - Fixed height, can collapse */
.header-container {
  flex-shrink: 0;
  overflow: hidden;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

/* When header is collapsed, hide it */
.immersive-layout.header-collapsed .header-container {
  height: 0;
  opacity: 0;
  margin: 0;
  padding: 0;
}

.header-content {
  min-height: var(--header-height);
  display: flex;
  flex-direction: column;
  position: relative;
}

/* Collapse Trigger Zone - minimal trigger area */
.collapse-trigger {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 80px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.trigger-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border-radius: 100px;
  background: var(--bg-card);
  color: var(--text-muted);
  font-size: 0.75rem;
  opacity: 0.6;
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.collapse-trigger:hover .trigger-bar {
  opacity: 1;
  background: var(--bg-elevated);
  color: var(--primary-color);
  transform: translateY(-2px);
}

.trigger-text {
  font-weight: 500;
  letter-spacing: 0.05em;
}

/* Recovery Button - Floating capsule with warm solid effect */
.recovery-btn {
  position: fixed;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border: 1px solid var(--border-accent);
  border-radius: 100px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
  cursor: pointer;
  box-shadow: var(--shadow-md);
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

.recovery-btn:hover {
  color: var(--primary-color);
  background: var(--bg-elevated);
  box-shadow: var(--shadow-lg);
  transform: translateX(-50%) translateY(-2px);
}

.recovery-text {
  letter-spacing: 0.03em;
}

/* Recovery Button Animation - Fade In with slight downward motion */
.recovery-enter-active {
  animation: recoveryFadeIn 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

.recovery-leave-active {
  animation: recoveryFadeOut 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes recoveryFadeIn {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(-16px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

@keyframes recoveryFadeOut {
  from {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
  to {
    opacity: 0;
    transform: translateX(-50%) translateY(-16px);
  }
}

/* Content Container - Fills remaining space, allows page scrolling */
.content-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

/* Accessibility - reduced motion */
@media (prefers-reduced-motion: reduce) {
  .immersive-layout,
  .header-container,
  .recovery-btn,
  .trigger-bar,
  .collapse-trigger {
    transition-duration: 0.01ms;
  }

  .recovery-enter-active,
  .recovery-leave-active {
    animation-duration: 0.01ms;
  }
}

/* Mobile adjustments */
@media (max-width: 768px) {
  .recovery-btn {
    padding: 8px 16px;
    font-size: 0.8rem;
    top: 12px;
  }

  .trigger-bar {
    padding: 3px 10px;
    font-size: 0.7rem;
  }

  .collapse-trigger {
    width: 60px;
    height: 20px;
  }
}
</style>