<template>
  <section :class="['ui-panel', { 'ui-panel--flush': flush }]">
    <div v-if="title || eyebrow || subtitle || $slots.actions" class="ui-panel__header">
      <div>
        <div v-if="eyebrow" class="ui-eyebrow">{{ eyebrow }}</div>
        <h2 v-if="title" class="ui-panel__title">{{ title }}</h2>
        <p v-if="subtitle" class="ui-panel__subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="ui-toolbar__group">
        <slot name="actions" />
      </div>
    </div>
    <slot />
  </section>
</template>

<script setup lang="ts">
// UI 面板组件：统一的卡片式容器，支持标题、小标题、副标题与操作槽位
defineProps<{
  title?: string
  subtitle?: string
  eyebrow?: string
  flush?: boolean
}>()
</script>
