<template>
  <div class="settings-page">
    <header class="page-header">
      <div class="header-icon">
        <n-icon size="28"><SettingsIcon /></n-icon>
      </div>
      <div>
        <div class="page-eyebrow">Personal Preferences</div>
        <h1>设置</h1>
        <p>当前页面仅保存本机外观偏好，不包含平台级配置操作。</p>
      </div>
    </header>

    <main class="settings-grid">
      <section class="surface-panel">
        <div class="section-heading">
          <n-icon size="22"><MoonIcon /></n-icon>
          <div>
            <h2>外观主题</h2>
            <p>主题只保存在当前浏览器，不会修改共享系统设置。</p>
          </div>
        </div>

        <div class="theme-options" role="radiogroup" aria-label="外观主题">
          <button
            v-for="option in themeOptions"
            :key="option.value"
            type="button"
            class="theme-option"
            :class="{ active: themeStore.mode === option.value }"
            :aria-checked="themeStore.mode === option.value"
            role="radio"
            @click="themeStore.setTheme(option.value)"
          >
            <span class="theme-preview" :class="option.value"></span>
            <strong>{{ option.label }}</strong>
            <small>{{ option.hint }}</small>
          </button>
        </div>
      </section>

      <section class="surface-panel worker-panel">
        <div class="section-heading">
          <n-icon size="22"><TerminalIcon /></n-icon>
          <div>
            <h2>执行能力</h2>
            <p>Java 仅保留多用户安全的执行任务账本。</p>
          </div>
          <n-tag type="warning" size="small">不可用</n-tag>
        </div>

        <div class="worker-state">
          <strong>WORKER_UNAVAILABLE</strong>
          <p>
            当前没有具备独立文件、进程、环境和凭据隔离的 Worker。派发请求会记录失败，
            不会启动本机进程、共享 Agent、备用执行器或自行推理。
          </p>
        </div>
      </section>

      <section class="surface-panel boundary-panel">
        <div class="section-heading">
          <n-icon size="22"><ShieldIcon /></n-icon>
          <div>
            <h2>权限边界</h2>
            <p>系统配置、外部服务凭据及全量备份恢复仅由平台管理员在受控维护流程中操作。</p>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { NIcon, NTag } from 'naive-ui'
import {
  MoonOutline as MoonIcon,
  SettingsOutline as SettingsIcon,
  ShieldCheckmarkOutline as ShieldIcon,
  TerminalOutline as TerminalIcon
} from '@vicons/ionicons5'
import { useThemeStore, type ThemeMode } from '@/stores/theme'

const themeStore = useThemeStore()

const themeOptions: Array<{ value: ThemeMode; label: string; hint: string }> = [
  { value: 'light', label: '浅色', hint: '明亮背景' },
  { value: 'dark', label: '深色', hint: '低光环境' },
  { value: 'auto', label: '跟随系统', hint: '自动切换' }
]
</script>

<style scoped>
.settings-page {
  display: grid;
  gap: 1.25rem;
  padding: 1.5rem;
}

.page-header,
.section-heading {
  display: flex;
  align-items: flex-start;
  gap: 0.9rem;
}

.header-icon {
  display: grid;
  place-items: center;
  width: 3rem;
  height: 3rem;
  border-radius: 1rem;
  color: var(--primary-color);
  background: var(--color-accent-muted);
}

.page-eyebrow {
  color: var(--primary-color);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  margin-top: 0.2rem;
  font-size: 1.75rem;
}

.page-header p,
.section-heading p,
.worker-state p {
  margin-top: 0.35rem;
  color: var(--text-secondary);
  line-height: 1.65;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.surface-panel {
  padding: 1.25rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-card);
}

.section-heading h2 {
  font-size: 1rem;
}

.section-heading .n-tag {
  margin-left: auto;
}

.theme-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin-top: 1.25rem;
}

.theme-option {
  display: grid;
  gap: 0.35rem;
  padding: 0.75rem;
  color: var(--text-primary);
  text-align: left;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: transparent;
  cursor: pointer;
}

.theme-option.active {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px var(--color-accent-muted);
}

.theme-option small {
  color: var(--text-secondary);
}

.theme-preview {
  height: 2.5rem;
  border-radius: 0.5rem;
  border: 1px solid var(--color-border);
}

.theme-preview.light { background: #f8fafc; }
.theme-preview.dark { background: #111827; }
.theme-preview.auto { background: linear-gradient(110deg, #f8fafc 50%, #111827 50%); }

.worker-state {
  margin-top: 1.25rem;
  padding: 1rem;
  border-radius: var(--radius-md);
  background: var(--color-accent-muted);
}

.worker-state strong {
  color: #f59e0b;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.boundary-panel {
  grid-column: 1 / -1;
}

@media (max-width: 800px) {
  .settings-page { padding: 1rem; }
  .settings-grid { grid-template-columns: 1fr; }
  .theme-options { grid-template-columns: 1fr; }
  .boundary-panel { grid-column: auto; }
}
</style>
