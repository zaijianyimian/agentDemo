<template>
  <div class="page-shell">
    <section class="page-hero hero-grid motion-hero">
      <div>
        <div class="page-eyebrow">Solo Mode</div>
        <h2>单用户增强中心</h2>
        <p>一站完成专注、自动化模板、备份恢复、离线缓存与知识库策略配置。</p>
      </div>
      <div class="hero-side">
        <div class="hero-stat"><span>启用任务</span><strong>{{ insights?.enabledTasks ?? 0 }}</strong></div>
        <div class="hero-stat"><span>今日日程</span><strong>{{ insights?.todaySchedules ?? 0 }}</strong></div>
        <div class="hero-stat"><span>Token 总量</span><strong>{{ insights?.totalTokenUsage ?? 0 }}</strong></div>
      </div>
    </section>

    <section class="section-grid">
      <div class="surface-panel span-6 motion-card">
        <div class="section-head">
          <div><div class="page-eyebrow">Templates</div><h3>自动化模板中心</h3></div>
        </div>
        <div class="template-list">
          <div v-for="item in templates" :key="item.id" class="template-card">
            <div>
              <strong>{{ item.name }}</strong>
              <p>{{ item.description }}</p>
              <small>{{ item.cronExpression }}</small>
            </div>
            <n-button size="small" type="primary" @click="applyTemplate(item.id)">一键启用</n-button>
          </div>
        </div>
      </div>

      <div class="surface-panel span-6 motion-card">
        <div class="section-head">
          <div><div class="page-eyebrow">Mode</div><h3>专注与缓存</h3></div>
        </div>
        <div class="setting-row">
          <span>专注模式（收起侧栏，减少干扰）</span>
          <n-switch v-model:value="focusMode" @update:value="onFocusModeToggle" />
        </div>
        <div class="setting-row">
          <span>离线缓存（仪表盘/收件箱可回退）</span>
          <n-switch v-model:value="offlineCacheEnabled" @update:value="onOfflineCacheToggle" />
        </div>
        <div class="setting-row">
          <span>知识库去重上传</span>
          <n-switch v-model:value="knowledgeDedupeEnabled" />
        </div>
        <div class="setting-row">
          <span>知识库增量上传</span>
          <n-switch v-model:value="knowledgeIncrementalEnabled" />
        </div>
        <n-button size="small" tertiary @click="saveKnowledgeSettings">保存知识库策略</n-button>
      </div>

      <div class="surface-panel span-6 motion-card">
        <div class="section-head">
          <div><div class="page-eyebrow">Face 2FA</div><h3>人脸二次验证</h3></div>
        </div>
        <div class="setting-row">
          <span>已绑定人脸</span>
          <strong>{{ faceStatus?.enrolled ? '是' : '否' }}</strong>
        </div>
        <div class="setting-row">
          <span>强制登录二次验证</span>
          <n-switch :value="faceRequired" :disabled="!faceStatus?.enrolled" @update:value="toggleFaceRequired" />
        </div>
        <div class="backup-actions">
          <input type="file" accept="image/*" @change="onFaceFileSelect" />
          <n-button size="small" type="primary" :loading="faceSaving" @click="saveFaceProfile">上传并绑定人脸</n-button>
        </div>
      </div>

      <div class="surface-panel span-6 motion-card">
        <div class="section-head">
          <div><div class="page-eyebrow">Backup</div><h3>数据备份与恢复</h3></div>
        </div>
        <div class="backup-actions">
          <n-button type="primary" @click="exportBackup">导出备份 JSON</n-button>
          <n-upload :show-file-list="false" accept=".json,application/json" :custom-request="handleImportUpload">
            <n-button>导入备份 JSON</n-button>
          </n-upload>
          <div class="setting-row">
            <span>导入前覆盖现有数据</span>
            <n-switch v-model:value="replaceExisting" />
          </div>
        </div>
      </div>

      <div class="surface-panel span-6 motion-card">
        <div class="section-head">
          <div><div class="page-eyebrow">Reminder</div><h3>提醒快速预设</h3></div>
        </div>
        <div class="preset-list">
          <n-button size="small" @click="applyReminderPreset('morning')">晨间 08:00</n-button>
          <n-button size="small" @click="applyReminderPreset('noon')">午间 12:00</n-button>
          <n-button size="small" @click="applyReminderPreset('evening')">晚间 20:00</n-button>
        </div>
      </div>

      <div class="surface-panel span-12 motion-card">
        <div class="section-head">
          <div><div class="page-eyebrow">History</div><h3>最近操作记录</h3></div>
          <n-button text @click="clearHistory">清空</n-button>
        </div>
        <div v-if="recentActions.length" class="history-list">
          <div v-for="item in recentActions" :key="`${item.time}-${item.title}`" class="history-item">
            <strong>{{ item.title }}</strong>
            <p>{{ item.detail || '-' }}</p>
            <small>{{ item.time }}</small>
          </div>
        </div>
        <n-empty v-else description="暂无操作记录" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NButton, NEmpty, NSwitch, NUpload, useMessage } from 'naive-ui'
import { authService, personalService, settingsService } from '@/services/api'
import type { FaceStatusResponse, PersonalInsight, TaskTemplate } from '@/types'
import {
  clearRecentActions,
  isFocusMode,
  isOfflineCacheEnabled,
  pushRecentAction,
  readRecentActions,
  setFocusMode,
  setOfflineCacheEnabled,
  type RecentActionItem
} from '@/services/user-preferences'

const message = useMessage()

const insights = ref<PersonalInsight | null>(null)
const templates = ref<TaskTemplate[]>([])
const focusMode = ref(isFocusMode())
const offlineCacheEnabled = ref(isOfflineCacheEnabled())
const knowledgeDedupeEnabled = ref(true)
const knowledgeIncrementalEnabled = ref(true)
const replaceExisting = ref(false)
const recentActions = ref<RecentActionItem[]>(readRecentActions())
const faceStatus = ref<FaceStatusResponse | null>(null)
const faceRequired = ref(false)
const faceImageBase64 = ref('')
const faceSaving = ref(false)

const loadData = async () => {
  const [insightRes, templateRes, settingsRes, faceStatusRes] = await Promise.all([
    personalService.insights(),
    personalService.listTaskTemplates(),
    settingsService.getSystem(),
    authService.faceStatus()
  ])

  if (insightRes.success && insightRes.data) {
    insights.value = insightRes.data
  }
  if (templateRes.success && templateRes.data) {
    templates.value = templateRes.data
  }
  if (settingsRes.success && settingsRes.data) {
    knowledgeDedupeEnabled.value = settingsRes.data.knowledge_dedupe_enabled !== 'false'
    knowledgeIncrementalEnabled.value = settingsRes.data.knowledge_incremental_enabled !== 'false'
  }
  if (faceStatusRes.success && faceStatusRes.data) {
    faceStatus.value = faceStatusRes.data
    faceRequired.value = faceStatusRes.data.required
  }
}

const syncRecentActions = () => {
  recentActions.value = readRecentActions()
}

const appendAction = (title: string, detail?: string) => {
  pushRecentAction({
    time: new Date().toLocaleString(),
    title,
    detail
  })
  syncRecentActions()
}

const applyTemplate = async (templateId: string) => {
  const res = await personalService.createTaskFromTemplate(templateId)
  if (res.success) {
    message.success('模板任务已创建')
    appendAction('启用任务模板', templateId)
  } else {
    message.error(res.message || '模板创建失败')
  }
}

const exportBackup = async () => {
  const res = await personalService.exportBackup()
  if (!res.success || !res.data) {
    message.error(res.message || '导出失败')
    return
  }
  const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `agent-backup-${new Date().toISOString().replace(/[:.]/g, '-')}.json`
  a.click()
  URL.revokeObjectURL(url)
  message.success('备份已导出')
  appendAction('导出备份', 'JSON 文件已下载')
}

const handleImportUpload = async ({ file }: { file: { file: File | null } }) => {
  if (!file.file) return
  try {
    const raw = await file.file.text()
    const payload = JSON.parse(raw)
    const res = await personalService.importBackup(payload, replaceExisting.value)
    if (res.success) {
      message.success('备份导入成功')
      appendAction('导入备份', `replace=${replaceExisting.value}`)
      await loadData()
    } else {
      message.error(res.message || '导入失败')
    }
  } catch {
    message.error('备份文件解析失败')
  }
}

const onFocusModeToggle = (value: boolean) => {
  setFocusMode(value)
  window.dispatchEvent(new CustomEvent('focus-mode-changed', { detail: { enabled: value } }))
  appendAction('切换专注模式', value ? '开启' : '关闭')
}

const onOfflineCacheToggle = (value: boolean) => {
  setOfflineCacheEnabled(value)
  appendAction('切换离线缓存', value ? '开启' : '关闭')
}

const saveKnowledgeSettings = async () => {
  const res = await settingsService.updateSystem({
    knowledge_dedupe_enabled: knowledgeDedupeEnabled.value ? 'true' : 'false',
    knowledge_incremental_enabled: knowledgeIncrementalEnabled.value ? 'true' : 'false'
  })
  if (res.success) {
    message.success('知识库策略已保存')
    appendAction('保存知识库策略')
  } else {
    message.error(res.message || '保存失败')
  }
}

const applyReminderPreset = async (type: 'morning' | 'noon' | 'evening') => {
  const mapping = {
    morning: '0 0 8 * * ?',
    noon: '0 0 12 * * ?',
    evening: '0 0 20 * * ?'
  }
  const cron = mapping[type]
  const res = await settingsService.updateSchedule({
    morning_reminder_cron: cron
  })
  if (res.success) {
    message.success('提醒预设已应用')
    appendAction('应用提醒预设', `${type} -> ${cron}`)
  } else {
    message.error(res.message || '应用失败')
  }
}

const clearHistory = () => {
  clearRecentActions()
  syncRecentActions()
  message.success('历史记录已清空')
}

const toBase64 = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })

const onFaceFileSelect = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) {
    faceImageBase64.value = ''
    return
  }
  if (!file.type.startsWith('image/')) {
    message.warning('请选择图片文件')
    target.value = ''
    return
  }
  faceImageBase64.value = await toBase64(file)
}

const saveFaceProfile = async () => {
  if (!faceImageBase64.value) {
    message.warning('请先选择人脸图片')
    return
  }
  faceSaving.value = true
  try {
    const res = await authService.registerFace({ imageBase64: faceImageBase64.value })
    if (!res.success || !res.data) {
      throw new Error(res.message || '绑定失败')
    }
    faceStatus.value = res.data
    faceRequired.value = res.data.required
    message.success('人脸绑定成功')
    appendAction('绑定人脸二次验证')
  } catch (e: any) {
    message.error(e?.message || '绑定失败')
  } finally {
    faceSaving.value = false
  }
}

const toggleFaceRequired = async (value: boolean) => {
  try {
    const res = await authService.toggleFaceRequired({ required: value })
    if (!res.success || !res.data) {
      throw new Error(res.message || '更新失败')
    }
    faceStatus.value = res.data
    faceRequired.value = res.data.required
    message.success(value ? '已开启人脸二次验证' : '已关闭人脸二次验证')
    appendAction('切换人脸二次验证', value ? '开启' : '关闭')
  } catch (e: any) {
    faceRequired.value = !!faceStatus.value?.required
    message.error(e?.message || '更新失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.35fr) minmax(260px, .85fr);
  gap: 20px;
}

.motion-hero {
  animation: riseIn .45s cubic-bezier(.22, 1, .36, 1);
}

.motion-card {
  animation: riseIn .46s cubic-bezier(.22, 1, .36, 1) both;
}

.section-grid > .motion-card:nth-child(1) {
  animation-delay: .06s;
}

.section-grid > .motion-card:nth-child(2) {
  animation-delay: .11s;
}

.section-grid > .motion-card:nth-child(3) {
  animation-delay: .16s;
}

.section-grid > .motion-card:nth-child(4) {
  animation-delay: .21s;
}

.section-grid > .motion-card:nth-child(5) {
  animation-delay: .26s;
}

.hero-side, .backup-actions, .template-list, .history-list {
  display: grid;
  gap: 12px;
}
.hero-stat, .template-card, .history-item, .setting-row {
  border: 1px solid var(--border-color);
  background: rgba(255, 255, 255, .05);
  border-radius: 16px;
}
.hero-stat, .template-card, .history-item {
  padding: 14px 16px;
}
.hero-stat span, .template-card p, .history-item p, .history-item small {
  color: var(--text-secondary);
}
.hero-stat strong {
  display: block;
  margin-top: 6px;
  font-size: 1.2rem;
}
.template-card {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
}
.template-card:hover {
  transform: translateY(-2px);
  border-color: rgba(245, 158, 11, .38);
  box-shadow: 0 10px 28px rgba(245, 158, 11, .16);
}
.template-card small {
  color: var(--text-muted);
}
.setting-row {
  padding: 12px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.preset-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.history-item {
  transition: transform .2s ease, border-color .2s ease;
}

.history-item:hover {
  transform: translateX(3px);
  border-color: rgba(245, 158, 11, .32);
}

@keyframes riseIn {
  from {
    opacity: 0;
    transform: translateY(14px) scale(.99);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .motion-hero,
  .motion-card {
    animation: none;
  }

  .template-card,
  .history-item {
    transition: none;
  }
}

@media (max-width: 900px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }
}
</style>
