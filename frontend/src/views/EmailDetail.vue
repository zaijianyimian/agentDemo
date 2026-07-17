<template>
  <UiPage class="email-detail-page">
    <UiPageHeader
      eyebrow="Email Detail"
      :title="headerTitle"
      :subtitle="headerSubtitle"
    >
      <template #actions>
        <n-button @click="goBack" quaternary>返回</n-button>
        <n-button type="primary" @click="loadAnalyses" :loading="loading">刷新</n-button>
      </template>
    </UiPageHeader>

    <section v-if="email" class="meta-card surface-panel">
      <div class="meta-row">
        <span class="meta-label">主题</span>
        <strong>{{ email.subject || '(无主题)' }}</strong>
      </div>
      <div class="meta-row">
        <span class="meta-label">发件人</span>
        <span>{{ email.fromName || '' }} <span v-if="email.from" class="muted">&lt;{{ email.from }}&gt;</span></span>
      </div>
      <div class="meta-row">
        <span class="meta-label">收件人</span>
        <span class="muted">{{ (email.to || []).join(', ') || '-' }}</span>
      </div>
      <div class="meta-row">
        <span class="meta-label">时间</span>
        <span class="muted">{{ formatDate(email.sentDate) }}</span>
      </div>
      <div class="meta-row">
        <span class="meta-label">账号</span>
        <span class="muted">{{ email.accountEmail || '-' }}</span>
      </div>
    </section>

    <section class="surface-panel body-card">
      <div class="section-head">
        <div>
          <div class="page-eyebrow">Body</div>
          <h3>邮件正文</h3>
        </div>
        <n-radio-group v-model:value="bodyView" size="small">
          <n-radio-button value="text">纯文本</n-radio-button>
          <n-radio-button value="html">HTML</n-radio-button>
        </n-radio-group>
      </div>
      <div v-if="bodyView === 'text'" class="body-text">
        <pre v-if="email?.textContent">{{ email.textContent }}</pre>
        <EmptyStateWithGlow v-else>
          <template #icon><n-icon size="32"><DocumentIcon /></n-icon></template>
          这封邮件没有纯文本正文
        </EmptyStateWithGlow>
      </div>
      <div v-else class="body-html">
        <iframe
          v-if="email?.htmlContent"
          :srcdoc="email.htmlContent"
          sandbox=""
          referrerpolicy="no-referrer"
        />
        <EmptyStateWithGlow v-else>
          <template #icon><n-icon size="32"><DocumentIcon /></n-icon></template>
          这封邮件没有 HTML 正文
        </EmptyStateWithGlow>
      </div>
    </section>

    <section class="surface-panel">
      <div class="section-head">
        <div>
          <div class="page-eyebrow">Attachments</div>
          <h3>附件与 AI 解析 ({{ analyses.length }})</h3>
        </div>
      </div>

      <div v-if="loading" class="loading-block">
        <LoadingSpinner />
      </div>

      <EmptyStateWithGlow v-else-if="!analyses.length">
        <template #icon><n-icon size="48"><AttachIcon /></n-icon></template>
        这封邮件没有附件，或附件还在解析中
      </EmptyStateWithGlow>

      <div v-else class="attachment-list">
        <article
          v-for="item in analyses"
          :key="item.id"
          class="attachment-card"
          :class="`is-${item.status.toLowerCase()}`"
        >
          <div class="attachment-card__head">
            <div class="attachment-card__icon">
              <n-icon size="22"><component :is="iconForContentType(item.contentType)" /></n-icon>
            </div>
            <div class="attachment-card__meta">
              <strong>{{ item.fileName }}</strong>
              <div class="muted small">
                {{ item.contentType || 'unknown' }} · {{ formatSize(item.sizeBytes) }}
              </div>
            </div>
            <n-tag :type="statusTagType(item.status)" size="small" :bordered="false">
              {{ statusLabel(item.status) }}
            </n-tag>
          </div>

          <div v-if="item.status === 'SUCCESS' && item.summary" class="attachment-card__summary">
            <h4>AI 摘要</h4>
            <p>{{ item.summary }}</p>
            <div class="muted small" v-if="item.modelName">模型：{{ item.modelName }}</div>
          </div>

          <div v-else-if="item.status === 'SKIPPED_SIZE'" class="attachment-card__summary">
            <h4>跳过原因</h4>
            <p>{{ item.skipReason }}</p>
            <div class="muted small">可在「邮件配置」调整 max_attachment_size_bytes 后重试</div>
          </div>

          <div v-else-if="item.status === 'SKIPPED_TYPE'" class="attachment-card__summary">
            <h4>不支持的类型</h4>
            <p>{{ item.skipReason }}</p>
          </div>

          <div v-else-if="item.status === 'FAILED'" class="attachment-card__summary failed">
            <h4>解析失败</h4>
            <p>{{ item.errorDetail || item.skipReason || '未知错误' }}</p>
          </div>

          <div v-else-if="item.status === 'RUNNING' || item.status === 'PENDING'" class="attachment-card__summary">
            <n-spin size="small" />
            <span class="muted">正在解析...</span>
          </div>

          <div v-if="item.rawText" class="attachment-card__raw">
            <details>
              <summary>查看抽取的原文</summary>
              <pre>{{ item.rawText }}</pre>
            </details>
          </div>

          <div class="attachment-card__actions">
            <n-button
              v-if="isPreviewable(item.contentType)"
              size="small"
              @click="openPreview(item)"
            >
              <template #icon><n-icon><EyeIcon /></n-icon></template>
              预览
            </n-button>
            <n-button
              size="small"
              tag="a"
              :href="downloadUrl(item.id)"
              :download="item.fileName"
            >
              <template #icon><n-icon><DownloadIcon /></n-icon></template>
              下载
            </n-button>
            <n-button
              size="small"
              tertiary
              :loading="retryingId === item.id"
              @click="retryAnalysis(item)"
            >
              <template #icon><n-icon><RefreshIcon /></n-icon></template>
              重试
            </n-button>
          </div>
        </article>
      </div>
    </section>

    <n-modal v-model:show="showPreview" preset="card" :title="previewTitle" style="width: min(900px, 95vw)">
      <iframe v-if="previewUrl" :src="previewUrl" class="preview-frame" />
    </n-modal>
  </UiPage>
</template>

<script setup lang="ts">
/**
 * 邮件详情页：根据 messageId 展示邮件头、文本/HTML 正文，以及附件列表与每条附件的 AI 解析结果。
 * 附件原始数据来自邮件监听时落盘到 EmailMessage.attachments；AI 解析结果由后端 EmailAttachmentController 提供。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DocumentAttachOutline as AttachIcon,
  DocumentTextOutline as DocumentIcon,
  DownloadOutline as DownloadIcon,
  EyeOutline as EyeIcon,
  ImageOutline as ImageIcon,
  RefreshOutline as RefreshIcon
} from '@vicons/ionicons5'
import { NButton, NIcon, NRadioButton, NRadioGroup, NSpin, NTag, useMessage } from 'naive-ui'
import EmptyStateWithGlow from '@/components/EmptyStateWithGlow.vue'
import LoadingSpinner from '@/components/LoadingSpinner.vue'
import { UiPage, UiPageHeader } from '@/components/ui'
import {
  type EmailAttachmentAnalysis,
  type EmailAttachment,
  emailAttachmentService
} from '@/services/api/email-attachment'
import { emailService } from '@/services/api/email'

interface EmailDetail {
  messageId?: string
  subject?: string
  from?: string
  fromName?: string
  to?: string[]
  cc?: string[]
  textContent?: string
  htmlContent?: string
  sentDate?: string
  receivedDate?: string
  accountEmail?: string
  attachments?: EmailAttachment[]
}

const route = useRoute()
const router = useRouter()
const message = useMessage()

const messageId = computed(() => decodeURIComponent(String(route.params.messageId ?? '')))
const email = ref<EmailDetail | null>(null)
const analyses = ref<EmailAttachmentAnalysis[]>([])
const loading = ref(false)
const retryingId = ref<number | null>(null)
const bodyView = ref<'text' | 'html'>('text')
const showPreview = ref(false)
const previewUrl = ref<string | null>(null)
const previewTitle = ref('')

const headerTitle = computed(() => email.value?.subject || '邮件详情')
const headerSubtitle = computed(() =>
  email.value?.accountEmail ? `来自 ${email.value.accountEmail}` : messageId.value
)

const goBack = () => {
  if (window.history.length > 1) router.back()
  else router.push('/inbox')
}

const loadEmail = async () => {
  // EmailMessage 详情目前由 emailService 复用；这里使用 inbox 概要。
  // 若后端提供 /api/email/messages/{messageId} 详情接口，可在此替换。
  try {
    const res = await emailService.getMessage(messageId.value)
    if (res.success) email.value = res.data
  } catch {
    // 详情接口可能尚未在所有部署启用，留空即可
  }
}

const loadAnalyses = async () => {
  if (!messageId.value) return
  loading.value = true
  try {
    analyses.value = await emailAttachmentService.listAnalyses(messageId.value)
  } catch (e: any) {
    message.error(e?.message || '加载附件解析结果失败')
  } finally {
    loading.value = false
  }
}

const retryAnalysis = async (item: EmailAttachmentAnalysis) => {
  retryingId.value = item.id
  try {
    const res = await emailAttachmentService.retry(item.id)
    if (res.success) {
      message.success('已重新加入解析队列')
      setTimeout(loadAnalyses, 1500)
    } else {
      message.error(res.message || '重试失败')
    }
  } catch (e: any) {
    message.error(e?.message || '重试失败')
  } finally {
    retryingId.value = null
  }
}

const downloadUrl = (id: number) => emailAttachmentService.fileUrl(id)

const openPreview = (item: EmailAttachmentAnalysis) => {
  previewUrl.value = emailAttachmentService.fileUrl(item.id)
  previewTitle.value = item.fileName
  showPreview.value = true
}

const isPreviewable = (contentType?: string) => {
  if (!contentType) return false
  const c = contentType.toLowerCase()
  return c.startsWith('image/') || c === 'application/pdf' || c.startsWith('text/')
}

const iconForContentType = (contentType?: string) => {
  if (!contentType) return DocumentIcon
  const c = contentType.toLowerCase()
  if (c.startsWith('image/')) return ImageIcon
  return DocumentIcon
}

const statusLabel = (status: string) =>
  ({
    PENDING: '排队中',
    RUNNING: '解析中',
    SUCCESS: '成功',
    FAILED: '失败',
    SKIPPED_SIZE: '已跳过（超阈值）',
    SKIPPED_TYPE: '已跳过（类型）'
  } as Record<string, string>)[status] || status

const statusTagType = (status: string) =>
  ({
    SUCCESS: 'success',
    FAILED: 'error',
    RUNNING: 'info',
    PENDING: 'default',
    SKIPPED_SIZE: 'warning',
    SKIPPED_TYPE: 'warning'
  } as Record<string, 'default' | 'success' | 'info' | 'warning' | 'error'>)[status] || 'default'

const formatSize = (bytes?: number) => {
  if (!bytes || bytes < 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

const formatDate = (value?: string) => {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleString('zh-CN', { hour12: false })
  } catch {
    return value
  }
}

onMounted(() => {
  loadEmail()
  loadAnalyses()
})

watch(messageId, () => {
  email.value = null
  analyses.value = []
  loadEmail()
  loadAnalyses()
})
</script>

<style scoped>
.email-detail-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.meta-card,
.body-card {
  padding: 18px 22px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--border-light);
}
.meta-row:last-child {
  border-bottom: 0;
}

.meta-label {
  width: 80px;
  color: var(--text-secondary);
  font-size: 13px;
}

.muted {
  color: var(--text-secondary);
}

.small {
  font-size: 12px;
}

.body-text pre {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  background: var(--bg-soft);
  padding: 16px;
  border-radius: var(--radius-md);
  max-height: 60vh;
  overflow: auto;
}

.body-html iframe {
  width: 100%;
  min-height: 60vh;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  background: white;
}

.attachment-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

@media (max-width: 900px) {
  .attachment-list {
    grid-template-columns: 1fr;
  }
}

.attachment-card {
  border: 2px solid var(--border-light);
  border-radius: var(--radius-lg);
  padding: 16px;
  background: var(--bg-card);
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: border-color var(--transition-base);
}
.attachment-card.is-success {
  border-color: color-mix(in srgb, var(--primary-color) 35%, var(--border-light));
}
.attachment-card.is-failed {
  border-color: #f87171;
}
.attachment-card.is-skipped_size,
.attachment-card.is-skipped_type {
  border-color: #fbbf24;
}

.attachment-card__head {
  display: grid;
  grid-template-columns: 40px 1fr auto;
  gap: 12px;
  align-items: center;
}

.attachment-card__icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  display: grid;
  place-items: center;
  background: var(--warm-100);
  color: var(--primary-color);
}

.attachment-card__meta strong {
  word-break: break-all;
}

.attachment-card__summary {
  background: var(--bg-soft);
  padding: 12px 14px;
  border-radius: var(--radius-md);
}
.attachment-card__summary.failed {
  background: #fef2f2;
  color: #991b1b;
}
.attachment-card__summary h4 {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.attachment-card__summary p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
}

.attachment-card__raw details summary {
  cursor: pointer;
  color: var(--text-secondary);
  font-size: 12px;
}
.attachment-card__raw pre {
  max-height: 240px;
  overflow: auto;
  background: var(--bg-soft);
  padding: 10px;
  border-radius: var(--radius-md);
  font-size: 12px;
  white-space: pre-wrap;
}

.attachment-card__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.loading-block {
  display: grid;
  place-items: center;
  padding: 40px;
}

.preview-frame {
  width: 100%;
  min-height: 70vh;
  border: 0;
  background: white;
}
</style>
