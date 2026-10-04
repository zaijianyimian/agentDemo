<template>
  <section class="surface-panel backup-panel">
    <div class="section-heading">
      <n-icon size="22"><ArchiveIcon /></n-icon>
      <div>
        <h2>数据备份</h2>
        <p>将当前数据库全部表导出为 ZIP 快照，可下载留档或删除。</p>
      </div>
    </div>

    <div class="backup-toolbar">
      <n-button :loading="creating" @click="create">创建备份</n-button>
      <n-button :loading="creating" @click="createAndDownload">创建并下载</n-button>
      <n-button :loading="cleaning" @click="cleanup">清理过期备份</n-button>
      <n-button quaternary :loading="loading" @click="refresh">刷新列表</n-button>
    </div>

    <p v-if="policy" class="backup-policy">保留策略：最近 {{ policy.keepMostRecent }} 份 / {{ policy.retentionDays }} 天内</p>
    <p v-if="error" class="backup-error">{{ error }}</p>

    <p v-if="loading" class="backup-empty">正在加载备份列表…</p>
    <p v-else-if="!backups.length" class="backup-empty">暂无备份文件。</p>

    <ul v-else class="backup-list">
      <li v-for="item in backups" :key="item.fileName" class="backup-item">
        <div class="backup-meta">
          <strong>{{ item.fileName }}</strong>
          <small>{{ formatSize(item.fileSize) }} · {{ formatTime(item.createdAt) }}</small>
        </div>
        <div class="backup-actions">
          <n-button size="small" :loading="!!downloadingName && downloadingName === item.fileName" @click="download(item.fileName)">下载</n-button>
          <n-button size="small" type="error" ghost @click="remove(item.fileName)">删除</n-button>
        </div>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { NButton, NIcon, useMessage } from 'naive-ui'
import { ArchiveOutline as ArchiveIcon } from '@vicons/ionicons5'
import { backupService, type BackupFileInfo } from '@/services/api/backup'
import { formatFileSize } from '@/utils/file-format'

const backups = ref<BackupFileInfo[]>([])
const loading = ref(false)
const creating = ref(false)
const cleaning = ref(false)
const downloadingName = ref('')
const error = ref('')
const message = useMessage()

// 与后端 BackupProperties 的默认值一致；仅用于界面提示，实际清理策略由服务端执行。
const policy = { keepMostRecent: 10, retentionDays: 30 }

const refresh = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await backupService.list()
    backups.value = response.data ?? []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '无法加载备份列表'
  } finally {
    loading.value = false
  }
}

const create = async () => {
  creating.value = true
  error.value = ''
  try {
    const response = await backupService.create()
    if (!response.success) {
      error.value = response.message || '创建备份失败'
      return
    }
    message.success('备份已创建')
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '创建备份失败'
  } finally {
    creating.value = false
  }
}

// 下载是二进制流：拿到 Blob 后用临时 <a> 触发浏览器保存，并及时释放对象 URL。
const saveBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

const download = async (fileName: string) => {
  downloadingName.value = fileName
  error.value = ''
  try {
    saveBlob(await backupService.download(fileName), fileName)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '下载失败'
  } finally {
    downloadingName.value = ''
  }
}

const createAndDownload = async () => {
  creating.value = true
  error.value = ''
  try {
    const response = await backupService.create()
    if (!response.success || !response.data) {
      error.value = response.message || '创建备份失败'
      return
    }
    saveBlob(await backupService.download(response.data.fileName), response.data.fileName)
    message.success('备份已创建并开始下载')
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    creating.value = false
  }
}

const remove = async (fileName: string) => {
  error.value = ''
  try {
    const response = await backupService.delete(fileName)
    if (!response.success) {
      error.value = response.message || '删除失败'
      return
    }
    message.success('已删除')
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '删除失败'
  }
}

const cleanup = async () => {
  cleaning.value = true
  error.value = ''
  try {
    const response = await backupService.cleanup()
    if (!response.success) {
      error.value = response.message || '清理失败'
      return
    }
    message.success(response.message || '清理完成')
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '清理失败'
  } finally {
    cleaning.value = false
  }
}

const formatSize = (bytes: number) => formatFileSize(bytes)

const formatTime = (value: string) => {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN')
}

onMounted(refresh)
</script>

<style scoped>
.backup-panel {
  display: grid;
  gap: 1rem;
}

.backup-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.backup-policy,
.backup-empty {
  color: var(--text-muted, #6b7280);
  font-size: 0.85rem;
}

.backup-error {
  color: #b91c1c;
  font-size: 0.85rem;
}

.backup-list {
  display: grid;
  gap: 0.5rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.backup-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 0.9rem;
  border: 1px solid var(--border-light, #e5e7eb);
  border-radius: 12px;
}

.backup-meta {
  display: grid;
  gap: 0.2rem;
  min-width: 0;
}

.backup-meta strong {
  overflow-wrap: anywhere;
  font-size: 0.9rem;
}

.backup-meta small {
  color: var(--text-muted, #6b7280);
}

.backup-actions {
  display: flex;
  flex-shrink: 0;
  gap: 0.5rem;
}

@media (max-width: 600px) {
  .backup-item {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
