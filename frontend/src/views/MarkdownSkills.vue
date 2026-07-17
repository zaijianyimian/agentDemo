<template>
  <div class="markdown-skills-page">
    <!-- 顶部标题栏 -->
    <header class="page-header">
      <div class="header-info">
        <nav class="breadcrumb">
          <span class="breadcrumb-item">自动化</span>
          <span class="breadcrumb-separator">/</span>
          <span class="breadcrumb-current">Markdown Skills</span>
        </nav>
        <p class="header-subtitle">
          类似 Claude Skills 的 <code class="inline-code">SKILL.md</code> 机制：把指导写在 markdown 里，启动时自动加载并暴露给 LLM 使用
        </p>
      </div>
      <div class="header-actions">
        <n-input v-model:value="filterText" placeholder="搜索 skill 名称 / 描述" clearable style="width: 240px" />
        <n-button secondary @click="loadSkills" :loading="loading">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          刷新列表
        </n-button>
        <n-button secondary @click="triggerReload" :loading="reloading">
          <template #icon><n-icon><ReloadIcon /></n-icon></template>
          重新加载文件
        </n-button>
      </div>
    </header>

    <!-- 统计卡片 -->
    <section class="stats-row">
      <div class="stat-card">
        <div class="stat-icon"><n-icon size="22"><DocumentIcon /></n-icon></div>
        <div class="stat-data">
          <span class="stat-label">已加载 skill</span>
          <strong class="stat-value">{{ filteredSkills.length }}</strong>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon"><n-icon size="22"><FolderIcon /></n-icon></div>
        <div class="stat-data">
          <span class="stat-label">磁盘路径</span>
          <strong class="stat-value path-value">./skills/</strong>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon"><n-icon size="22"><SparklesIcon /></n-icon></div>
        <div class="stat-data">
          <span class="stat-label">自动注入</span>
          <strong class="stat-value">{{ autoInject ? '已开启' : '已关闭' }}</strong>
        </div>
      </div>
    </section>

    <!-- skill 网格 -->
    <section class="skills-grid">
      <div v-if="loading" class="empty-state">
        <n-spin />
        <p>加载中...</p>
      </div>
      <div v-else-if="filteredSkills.length === 0" class="empty-state">
        <n-icon size="48"><DocumentIcon /></n-icon>
        <p>暂无 markdown skill</p>
        <span class="empty-hint">把 <code class="inline-code">&lt;skill-name&gt;/SKILL.md</code> 放到 <code class="inline-code">./skills/</code> 目录即可自动加载</span>
      </div>

      <article
        v-for="skill in filteredSkills"
        :key="skill.name"
        class="skill-card"
        @click="openSkill(skill)"
      >
        <header class="skill-card-header">
          <div class="skill-icon">
            <n-icon size="20"><DocumentIcon /></n-icon>
          </div>
          <div class="skill-title-block">
            <strong class="skill-name">{{ skill.name }}</strong>
            <div v-if="skill.tags && skill.tags.length > 0" class="skill-tags">
              <span v-for="tag in skill.tags" :key="tag" class="tag-chip">{{ tag }}</span>
            </div>
          </div>
        </header>
        <p class="skill-description">{{ skill.description }}</p>
        <footer class="skill-card-footer">
          <span v-if="skill.license" class="meta-item">{{ skill.license }}</span>
          <span class="meta-item">{{ formatSize(skill.sizeBytes) }}</span>
          <span v-if="skill.compatibility" class="meta-item hint" :title="skill.compatibility">
            <n-icon size="12"><WarningIcon /></n-icon>
            {{ skill.compatibility }}
          </span>
        </footer>
      </article>
    </section>

    <!-- skill 详情抽屉 -->
    <n-drawer v-model:show="drawerVisible" :width="720" placement="right">
      <n-drawer-content v-if="currentSkill" :title="`Skill: ${currentSkill.name}`" closable>
        <div v-if="detailLoading" class="empty-state">
          <n-spin />
        </div>
        <template v-else-if="currentDetail">
          <section class="detail-section">
            <h4 class="detail-heading">描述</h4>
            <p>{{ currentDetail.description }}</p>
          </section>
          <section v-if="currentDetail.tags && currentDetail.tags.length > 0" class="detail-section">
            <h4 class="detail-heading">标签</h4>
            <div class="skill-tags">
              <span v-for="tag in currentDetail.tags" :key="tag" class="tag-chip">{{ tag }}</span>
            </div>
          </section>
          <section class="detail-section">
            <h4 class="detail-heading">正文</h4>
            <pre class="body-pre">{{ currentDetail.body }}</pre>
          </section>
          <section class="detail-section">
            <h4 class="detail-heading">文件路径</h4>
            <code class="inline-code path-value">{{ currentDetail.filePath }}</code>
          </section>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
/**
 * Markdown Skills 管理页。
 * 展示当前已加载的所有 markdown skill，支持搜索、重新加载文件、查看详情。
 */
import { ref, computed, onMounted } from 'vue'
import {
  NButton,
  NDrawer,
  NDrawerContent,
  NIcon,
  NInput,
  NSpin,
  useMessage
} from 'naive-ui'
import {
  RefreshOutline as RefreshIcon,
  DocumentTextOutline as DocumentIcon,
  FolderOpenOutline as FolderIcon,
  SparklesOutline as SparklesIcon,
  WarningOutline as WarningIcon,
  SyncOutline as ReloadIcon
} from '@vicons/ionicons5'
import { markdownSkillService } from '@/services/api/markdown-skill'
import type { MarkdownSkillSummary, MarkdownSkillDetail } from '@/services/api/markdown-skill'

const message = useMessage()

const loading = ref(false)
const reloading = ref(false)
const skills = ref<MarkdownSkillSummary[]>([])
const filterText = ref('')
const autoInject = ref(true)

const drawerVisible = ref(false)
const detailLoading = ref(false)
const currentSkill = ref<MarkdownSkillSummary | null>(null)
const currentDetail = ref<MarkdownSkillDetail | null>(null)

/**
 * 过滤后的 skill 列表：按关键词模糊匹配 name / description。
 */
const filteredSkills = computed(() => {
  if (!filterText.value.trim()) {
    return skills.value
  }
  const kw = filterText.value.toLowerCase()
  return skills.value.filter(s =>
    s.name.toLowerCase().includes(kw) ||
    s.description.toLowerCase().includes(kw)
  )
})

const formatSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const loadSkills = async () => {
  loading.value = true
  try {
    const res = await markdownSkillService.list()
    if (res.success) {
      skills.value = res.data || []
    } else {
      message.error(res.message || '加载失败')
    }
  } catch (e) {
    console.error('加载 markdown skills 失败:', e)
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

const triggerReload = async () => {
  reloading.value = true
  try {
    const res = await markdownSkillService.reload()
    if (res.success) {
      message.success(`重新加载完成，共 ${res.data} 个 skill`)
      await loadSkills()
    } else {
      message.error(res.message || '重新加载失败')
    }
  } catch (e) {
    console.error('重新加载失败:', e)
    message.error('重新加载失败')
  } finally {
    reloading.value = false
  }
}

const openSkill = async (skill: MarkdownSkillSummary) => {
  currentSkill.value = skill
  currentDetail.value = null
  drawerVisible.value = true
  detailLoading.value = true
  try {
    const res = await markdownSkillService.get(skill.name)
    if (res.success && res.data) {
      currentDetail.value = res.data
    } else {
      message.error(res.message || '加载 skill 详情失败')
      message.error(res.message || '加载 skill 详情失败')
    }
  } catch (e) {
    console.error('加载 skill 详情失败:', e)
    message.error('加载 skill 详情失败')
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => {
  loadSkills()
})
</script>

<style scoped>
.markdown-skills-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 0;
  min-height: calc(100vh - 200px);
}

/* Header */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}
.header-info { display: flex; flex-direction: column; gap: 8px; }
.breadcrumb { display: flex; align-items: center; gap: 8px; font-size: 0.85rem; }
.breadcrumb-item { color: var(--text-muted); }
.breadcrumb-separator { color: var(--text-muted); opacity: 0.5; }
.breadcrumb-current { color: var(--text-primary); font-weight: 500; }
.header-subtitle { font-size: 0.85rem; color: var(--text-secondary); margin: 0; }
.header-actions { display: flex; gap: 12px; align-items: center; }
.inline-code {
  font-family: var(--font-mono);
  font-size: 0.85em;
  padding: 1px 6px;
  background: var(--bg-input);
  border-radius: 4px;
  color: var(--text-secondary);
}

/* Stats */
.stats-row { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 20px;
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 2px solid var(--border-light);
}
.stat-icon {
  width: 44px; height: 44px;
  display: grid; place-items: center;
  border-radius: var(--radius-md);
  background: var(--bg-input);
  color: var(--text-secondary);
}
.stat-data { display: flex; flex-direction: column; gap: 2px; }
.stat-label { font-size: 0.72rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.05em; }
.stat-value { font-size: 1.4rem; font-weight: 700; color: var(--text-primary); line-height: 1; }
.stat-value.path-value { font-family: var(--font-mono); font-size: 1.1rem; }

/* Grid */
.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.skill-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  background: var(--bg-card);
  border: 2px solid var(--border-light);
  border-radius: var(--radius-lg);
  cursor: pointer;
  transition: all 0.2s;
}
.skill-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
  border-color: var(--primary-light);
}
.skill-card-header { display: flex; align-items: center; gap: 12px; }
.skill-icon {
  width: 40px; height: 40px;
  display: grid; place-items: center;
  border-radius: var(--radius-md);
  background: var(--gradient-sunset);
  color: white;
}
.skill-title-block { display: flex; flex-direction: column; gap: 4px; flex: 1; min-width: 0; }
.skill-name { font-size: 0.95rem; font-weight: 600; color: var(--text-primary); }
.skill-tags { display: flex; gap: 4px; flex-wrap: wrap; }
.tag-chip {
  font-size: 0.7rem;
  padding: 1px 8px;
  border-radius: 100px;
  background: var(--bg-input);
  color: var(--text-secondary);
}
.skill-description {
  font-size: 0.85rem;
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.skill-card-footer { display: flex; gap: 10px; flex-wrap: wrap; padding-top: 8px; border-top: 1px solid var(--border-light); }
.meta-item {
  font-size: 0.72rem;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 4px;
}
.meta-item.hint { color: #FF9500; }

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 60px 20px;
  color: var(--text-muted);
}
.empty-hint { font-size: 0.8rem; }

/* Detail Drawer */
.detail-section { margin-bottom: 20px; }
.detail-heading {
  font-size: 0.78rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text-muted);
  margin: 0 0 8px;
  font-weight: 600;
}
.body-pre {
  font-family: var(--font-mono);
  font-size: 0.82rem;
  white-space: pre-wrap;
  word-break: break-word;
  background: var(--bg-base);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 16px;
  max-height: 60vh;
  overflow-y: auto;
  color: var(--text-primary);
}

@media (max-width: 768px) {
  .stats-row { grid-template-columns: 1fr; }
  .skills-grid { grid-template-columns: 1fr; }
  .header-actions { flex-wrap: wrap; }
}
</style>