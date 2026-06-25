<template>
  <div class="loading-container" :style="{ padding: `${size}px` }">
    <div class="loading-spinner" :style="{ width: `${spinnerSize}px`, height: `${spinnerSize}px` }">
      <div class="loading-spinner__ring"></div>
    </div>
    <div v-if="showText" class="loading-text">{{ text }}</div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  size?: number
  spinnerSize?: number
  showText?: boolean
  text?: string
}

withDefaults(defineProps<Props>(), {
  size: 48,
  spinnerSize: 48,
  showText: false,
  text: '加载中...'
})
</script>

<style scoped>
.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 16px;
}

.loading-spinner {
  position: relative;
  border-radius: 50%;
}

.loading-spinner__ring {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 3px solid transparent;
  border-top-color: var(--primary-color);
  animation: spin 1s linear infinite;
}

.loading-spinner__ring::before {
  content: '';
  position: absolute;
  inset: 4px;
  border-radius: 50%;
  border: 3px solid transparent;
  border-top-color: var(--accent-yellow);
  animation: spin 2s linear infinite reverse;
}

.loading-spinner__ring::after {
  content: '';
  position: absolute;
  inset: 10px;
  border-radius: 50%;
  border: 2px solid transparent;
  border-top-color: var(--primary-light);
  animation: spin 1.5s linear infinite;
}

.loading-text {
  color: var(--text-secondary);
  font-size: 0.875rem;
  letter-spacing: 0.05em;
  animation: textPulse 2s ease-in-out infinite;
}
</style>
