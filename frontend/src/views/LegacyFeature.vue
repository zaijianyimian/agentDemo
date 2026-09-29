<template>
  <main class="legacy-feature">
    <h1>此功能暂时不可用</h1>
    <p>此页面正在更新。你可以继续使用 Agent 对话、日程、提醒、邮箱和文件功能。</p>
    <router-link to="/chat">前往 Agent 对话</router-link>
  </main>
</template>

<style scoped>
.legacy-feature {
  max-width: 42rem;
  margin: 5rem auto;
  padding: 2rem;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-card);
}

p { margin: 1rem 0 1.5rem; color: var(--text-secondary); }
</style>
