<template>
  <main :class="['ui-page', { 'ui-page--wide': wide }]">
    <slot />
  </main>
</template>

<script setup lang="ts">
// UI 页面容器组件：作为各业务页面的根容器，支持宽窄布局切换
defineProps<{
  wide?: boolean
}>()
</script>
