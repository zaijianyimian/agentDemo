<template>
  <div class="ui-stat">
    <div>
      <div class="ui-stat__label">{{ label }}</div>
      <strong class="ui-stat__value">{{ value }}</strong>
      <div v-if="hint" class="ui-stat__hint">{{ hint }}</div>
    </div>
    <div v-if="$slots.icon" class="ui-stat__icon">
      <slot name="icon" />
    </div>
  </div>
</template>

<script setup lang="ts">
// UI 统计卡片组件：展示指标标签、数值、提示信息与图标
defineProps<{
  label: string
  value: string | number
  hint?: string
}>()
</script>
