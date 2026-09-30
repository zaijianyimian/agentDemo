<template>
  <UiPage class="email-detail-page">
    <UiPageHeader
      eyebrow="Email Detail"
      :title="headerTitle"
      :subtitle="headerSubtitle"
    >
      <template #actions>
        <n-button @click="goBack" quaternary>返回</n-button>
      </template>
    </UiPageHeader>

    <section v-if="loadError" class="surface-panel detail-error">
      <n-alert type="error" :show-icon="true" :bordered="false">
        {{ loadError }}
      </n-alert>
      <div class="detail-error__actions">
        <n-button size="small" @click="loadEmail" :loading="loading">重新加载</n-button>
        <n-button size="small" quaternary @click="goBack">返回</n-button>
      </div>
    </section>

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

    <section v-if="!loadError" class="surface-panel body-card">
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

    <section v-if="!loadError" class="surface-panel">
      <div class="section-head">
        <div>
          <div class="page-eyebrow">Attachments</div>
          <h3>附件 ({{ attachments.length }})</h3>
        </div>
      </div>

      <div v-if="attachments.length" class="attachment-list">
        <article
          v-for="item in attachments"
          :key="item.storage_key || item.storageKey || item.fileName"
          class="attachment-card"
        >
          <div class="attachment-card__head">
            <div class="attachment-card__icon">
              <n-icon size="22"><component :is="iconForContentType(item.contentType)" /></n-icon>
            </div>
            <div class="attachment-card__meta">
              <strong>{{ item.fileName }}</strong>
              <div class="muted small">
                {{ item.contentType || 'unknown' }} · {{ formatSize(item.size) }}
              </div>
            </div>
          </div>
        </article>
      </div>

      <EmptyStateWithGlow v-else>
        <template #icon><n-icon size="48"><AttachIcon /></n-icon></template>
        这封邮件没有附件
      </EmptyStateWithGlow>
    </section>
  </UiPage>
</template>

<script setup lang="ts">
/**
 * 邮件详情页：根据 messageId 展示邮件头、文本/HTML 正文，以及邮件自带的附件清单。
 * 附件信息随 EmailMessage 由邮件监听落盘并随详情接口返回，不做服务端 AI 解析。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DocumentAttachOutline as AttachIcon,
  DocumentTextOutline as DocumentIcon,
  ImageOutline as ImageIcon
} from '@vicons/ionicons5'
import { NAlert, NButton, NIcon, NRadioButton, NRadioGroup, useMessage } from 'naive-ui'
import EmptyStateWithGlow from '@/components/EmptyStateWithGlow.vue'
import { UiPage, UiPageHeader } from '@/components/ui'
import { emailService } from '@/services/api/email'

interface EmailAttachment {
  user_id: number
  fileName: string
  contentType: string
  size: number
  storage_key: string
  storageKey?: string
  contentId: string
  disposition: string
}

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
const bodyView = ref<'text' | 'html'>('text')
const loadError = ref('')
const loading = ref(false)

const attachments = computed<EmailAttachment[]>(() => email.value?.attachments ?? [])

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
  loading.value = true
  loadError.value = ''
  try {
    const res = await emailService.getMessage(messageId.value)
    if (res.success && res.data) {
      email.value = res.data
    } else {
      email.value = null
      loadError.value = (res as any)?.message || '邮件详情加载失败，请稍后重试'
      message.error(loadError.value)
    }
  } catch (error) {
    console.error('加载邮件详情失败:', error)
    email.value = null
    loadError.value = '邮件详情加载失败，请检查网络后重试'
    message.error(loadError.value)
  } finally {
    loading.value = false
  }
}

const iconForContentType = (contentType?: string) => {
  if (!contentType) return DocumentIcon
  const c = contentType.toLowerCase()
  if (c.startsWith('image/')) return ImageIcon
  return DocumentIcon
}

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
})

watch(messageId, () => {
  email.value = null
  loadEmail()
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

.detail-error {
  padding: 18px 22px;
}

.detail-error__actions {
  display: flex;
  gap: 10px;
  margin-top: 14px;
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

.attachment-card__head {
  display: grid;
  grid-template-columns: 40px 1fr;
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

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
</style>
