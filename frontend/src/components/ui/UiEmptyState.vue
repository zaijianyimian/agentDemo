<template>
  <div class="ui-empty">
    <slot name="icon" />
    <p class="ui-empty__title">{{ title }}</p>
    <p v-if="description" class="ui-empty__description">{{ description }}</p>
    <slot name="actions" />
  </div>
</template>

<script setup lang="ts">
// UI 空状态组件：统一的占位提示，支持标题、说明与自定义图标/操作
defineProps<{
  title: string
  description?: string
}>()
</script>
