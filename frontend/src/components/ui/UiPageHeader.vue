<template>
  <header class="ui-page-header">
    <div class="ui-page-header__main">
      <div v-if="eyebrow" class="ui-page-header__eyebrow">{{ eyebrow }}</div>
      <h1 class="ui-page-header__title">{{ title }}</h1>
      <p v-if="subtitle" class="ui-page-header__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="ui-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
// UI 页面头部组件：提供标题、副标题与面包屑式小标题，以及右侧操作槽位
defineProps<{
  title: string
  subtitle?: string
  eyebrow?: string
}>()
</script>
