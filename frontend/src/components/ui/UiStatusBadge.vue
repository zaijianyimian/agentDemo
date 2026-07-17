<template>
  <span :class="['ui-status-badge', `ui-status-badge--${tone || 'neutral'}`]">
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup lang="ts">
// UI 状态徽章组件：通过 tone 颜色展示成功/警告/错误等语义状态
defineProps<{
  label?: string
  tone?: 'success' | 'warning' | 'error' | 'info' | 'neutral'
}>()
</script>
