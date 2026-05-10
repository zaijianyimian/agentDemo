<template>
  <div class="empty-state-enhanced">
    <div class="empty-state__icon-wrapper">
      <div class="empty-state__glow"></div>
      <div class="empty-state__particles">
        <span class="empty-state__particle"></span>
        <span class="empty-state__particle"></span>
        <span class="empty-state__particle"></span>
        <span class="empty-state__particle"></span>
      </div>
      <div class="empty-state__icon">
        <slot name="icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M20 12V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2h6" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M15 9l6 6m0-6l-6 6" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </slot>
      </div>
    </div>
    <div v-if="$slots.default" class="empty-state__text">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
// 空状态组件 - 带有流光漂浮效果
// 使用方式：
// <EmptyStateWithGlow>
//   <template #icon>
//     <n-icon size="48"><SomeIcon /></n-icon>
//   </template>
//   暂无数据
// </EmptyStateWithGlow>
</script>

<style scoped>
.empty-state-enhanced {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: var(--text-muted);
  text-align: center;
  position: relative;
}

.empty-state__icon-wrapper {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  animation: emptyFloat 4s ease-in-out infinite;
}

.empty-state__icon {
  font-size: 48px;
  color: var(--text-muted);
  position: relative;
  z-index: 1;
  transition: color var(--transition-base);
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-state__icon::before {
  content: '';
  position: absolute;
  inset: -8px;
  border-radius: 50%;
  background: linear-gradient(
    135deg,
    transparent 0%,
    transparent 40%,
    rgba(255, 209, 102, 0.08) 45%,
    rgba(255, 107, 53, 0.12) 50%,
    rgba(255, 209, 102, 0.08) 55%,
    transparent 60%,
    transparent 100%
  );
  background-size: 200% 200%;
  animation: emptyLightFlow 3s ease-in-out infinite;
  z-index: 2;
  pointer-events: none;
  filter: blur(4px);
}

.empty-state__icon :deep(.n-icon) {
  position: relative;
}

.empty-state__icon :deep(.n-icon)::before {
  content: '';
  position: absolute;
  inset: -4px;
  background: linear-gradient(
    45deg,
    transparent 0%,
    rgba(255, 209, 102, 0.06) 25%,
    rgba(255, 107, 53, 0.1) 50%,
    rgba(255, 209, 102, 0.06) 75%,
    transparent 100%
  );
  background-size: 300% 300%;
  animation: emptyLightFlow 4s ease-in-out infinite;
  border-radius: 50%;
  filter: blur(6px);
  z-index: -1;
}

.empty-state__glow {
  position: absolute;
  inset: -16px;
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(255, 107, 53, 0.06) 0%,
    rgba(255, 209, 102, 0.03) 40%,
    transparent 70%
  );
  opacity: 0;
  animation: emptyGlowPulse 3s ease-in-out infinite;
  pointer-events: none;
  z-index: 0;
}

.empty-state__particles {
  position: absolute;
  inset: -24px;
  overflow: visible;
  pointer-events: none;
}

.empty-state__particle {
  position: absolute;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--accent-yellow);
  opacity: 0;
  animation: particleFloat 2.5s ease-in-out infinite;
}

.empty-state__particle:nth-child(1) {
  top: 10%;
  left: 20%;
  animation-delay: 0s;
}

.empty-state__particle:nth-child(2) {
  top: 20%;
  right: 25%;
  animation-delay: 0.5s;
}

.empty-state__particle:nth-child(3) {
  bottom: 30%;
  left: 30%;
  animation-delay: 1s;
}

.empty-state__particle:nth-child(4) {
  bottom: 15%;
  right: 15%;
  animation-delay: 1.5s;
}

.empty-state__text {
  color: var(--text-muted);
  font-size: 0.875rem;
  margin-top: 12px;
  animation: textFadeIn 2s ease-in-out infinite;
}
</style>
