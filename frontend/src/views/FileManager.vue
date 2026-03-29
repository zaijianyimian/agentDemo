<template>
  <div class="file-manager">
    <!-- 上传区域 -->
    <n-card class="upload-card" :bordered="false">
      <n-upload
        :custom-request="handleUpload"
        :show-file-list="false"
        accept=".txt,.md,.pdf,.doc,.docx"
        dragdir
      >
        <n-upload-dragger>
          <div class="upload-area">
            <n-icon size="48" class="upload-icon"><CloudUploadIcon /></n-icon>
            <p class="upload-text">点击或拖拽文件到此处上传</p>
            <p class="upload-hint">支持 txt, md, pdf, doc, docx 格式，最大 10MB</p>
          </div>
        </n-upload-dragger>
      </n-upload>
    </n-card>

    <!-- 筛选和搜索 -->
    <n-card class="filter-card" :bordered="false">
      <n-space>
        <n-input v-model:value="searchText" placeholder="搜索文件名..." clearable style="width: 200px">
          <template #prefix>
            <n-icon><SearchIcon /></n-icon>
          </template>
        </n-input>
        <n-select
          v-model:value="importanceFilter"
          :options="importanceOptions"
          placeholder="重要程度"
          clearable
          style="width: 150px"
        />
        <n-select
          v-model:value="typeFilter"
          :options="typeOptions"
          placeholder="文件类型"
          clearable
          style="width: 120px"
        />
        <n-button type="primary" @click="loadFiles">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          刷新
        </n-button>
      </n-space>
    </n-card>

    <!-- 文件列表 -->
    <n-card class="file-list-card" :bordered="false">
      <n-data-table
        :columns="columns"
        :data="filteredFiles"
        :loading="loading"
        :row-key="(row: Document) => row.id"
        striped
      />
    </n-card>

    <!-- 文件详情弹窗 -->
    <n-modal v-model:show="showDetail" preset="card" title="文件详情" style="width: 600px">
      <n-descriptions label-placement="left" :column="2" bordered>
        <n-descriptions-item label="文件名">{{ currentFile?.fileName }}</n-descriptions-item>
        <n-descriptions-item label="文件类型">{{ currentFile?.fileType }}</n-descriptions-item>
        <n-descriptions-item label="文件大小">{{ formatSize(currentFile?.fileSize || 0) }}</n-descriptions-item>
        <n-descriptions-item label="重要程度">
          <n-tag :type="getImportanceType(currentFile?.importance || 0)">
            {{ currentFile?.importance }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="情感倾向">
          <n-tag :type="getSentimentType(currentFile?.sentiment || '')">
            {{ currentFile?.sentiment }}
          </n-tag>
        </n-descriptions-item>
        <n-descriptions-item label="处理状态">{{ currentFile?.status }}</n-descriptions-item>
        <n-descriptions-item label="标签" :span="2">
          <n-space>
            <n-tag v-for="tag in currentFile?.tags?.split(',')" :key="tag" size="small" round>
              {{ tag }}
            </n-tag>
          </n-space>
        </n-descriptions-item>
        <n-descriptions-item label="摘要" :span="2">
          {{ currentFile?.summary }}
        </n-descriptions-item>
        <n-descriptions-item label="创建时间" :span="2">
          {{ formatTime(currentFile?.createTime || '') }}
        </n-descriptions-item>
      </n-descriptions>

      <n-divider>文件内容</n-divider>
      <n-scrollbar style="max-height: 300px">
        <pre class="file-content">{{ currentFile?.content }}</pre>
      </n-scrollbar>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, h, onMounted } from 'vue'
import {
  NCard,
  NUpload,
  NUploadDragger,
  NIcon,
  NInput,
  NSelect,
  NButton,
  NDataTable,
  NModal,
  NDescriptions,
  NDescriptionsItem,
  NTag,
  NSpace,
  NScrollbar,
  NDivider,
  useMessage,
  type DataTableColumns,
  type UploadCustomRequestOptions
} from 'naive-ui'
import {
  CloudUploadOutline as CloudUploadIcon,
  SearchOutline as SearchIcon,
  RefreshOutline as RefreshIcon,
  EyeOutline as EyeIcon,
  TrashOutline as TrashIcon
} from '@vicons/ionicons5'
import { fileService } from '@/services/api'
import type { Document } from '@/types'
import dayjs from 'dayjs'

const message = useMessage()
const loading = ref(false)
const files = ref<Document[]>([])
const searchText = ref('')
const importanceFilter = ref<number | undefined>(undefined)
const typeFilter = ref<string | null>(null)
const showDetail = ref(false)
const currentFile = ref<Document | null>(null)

// 重要程度选项
const importanceOptions = [
  { label: '全部', value: undefined },
  { label: '非常重要 (8-10)', value: 8 },
  { label: '较重要 (5-7)', value: 5 },
  { label: '一般 (1-4)', value: 1 }
]

// 文件类型选项
const typeOptions = computed(() => {
  const types = [...new Set(files.value.map(f => f.fileType))]
  return types.map(t => ({ label: t.toUpperCase(), value: t }))
})

// 过滤后的文件列表
const filteredFiles = computed(() => {
  let result = files.value

  if (searchText.value) {
    result = result.filter(f =>
      f.fileName.toLowerCase().includes(searchText.value.toLowerCase())
    )
  }

  if (importanceFilter.value) {
    result = result.filter(f => f.importance >= importanceFilter.value!)
  }

  if (typeFilter.value) {
    result = result.filter(f => f.fileType === typeFilter.value)
  }

  return result
})

// 表格列配置
const columns: DataTableColumns<Document> = [
  {
    title: '文件名',
    key: 'fileName',
    ellipsis: { tooltip: true }
  },
  {
    title: '类型',
    key: 'fileType',
    width: 80,
    render: (row) => h(NTag, { size: 'small' }, { default: () => row.fileType.toUpperCase() })
  },
  {
    title: '大小',
    key: 'fileSize',
    width: 100,
    render: (row) => formatSize(row.fileSize)
  },
  {
    title: '重要度',
    key: 'importance',
    width: 100,
    render: (row) => h(NTag, { type: getImportanceType(row.importance), size: 'small' }, {
      default: () => row.importance
    })
  },
  {
    title: '情感',
    key: 'sentiment',
    width: 100,
    render: (row) => h(NTag, { type: getSentimentType(row.sentiment), size: 'small' }, {
      default: () => row.sentiment
    })
  },
  {
    title: '创建时间',
    key: 'createTime',
    width: 180,
    render: (row) => formatTime(row.createTime)
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row) => h(NSpace, null, {
      default: () => [
        h(NButton, {
          size: 'small',
          quaternary: true,
          onClick: () => viewDetail(row)
        }, { icon: () => h(NIcon, null, { default: () => h(EyeIcon) }) }),
        h(NButton, {
          size: 'small',
          quaternary: true,
          type: 'error',
          onClick: () => deleteFile(row)
        }, { icon: () => h(NIcon, null, { default: () => h(TrashIcon) }) })
      ]
    })
  }
]

// 加载文件列表
const loadFiles = async () => {
  loading.value = true
  try {
    const res = await fileService.list()
    files.value = res.data || []
  } catch (error) {
    message.error('加载文件列表失败')
  } finally {
    loading.value = false
  }
}

// 上传文件
const handleUpload = async ({ file }: UploadCustomRequestOptions) => {
  if (!file.file) return
  try {
    message.loading('正在上传并分析文件...')
    const res = await fileService.upload(file.file)
    if (res.success) {
      message.success('文件上传成功')
      loadFiles()
    } else {
      message.error(res.message || '上传失败')
    }
  } catch (error) {
    message.error('上传失败')
  }
}

// 查看详情
const viewDetail = (file: Document) => {
  currentFile.value = file
  showDetail.value = true
}

// 删除文件
const deleteFile = async (file: Document) => {
  try {
    const res = await fileService.delete(file.id)
    if (res.success) {
      message.success('删除成功')
      loadFiles()
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// 获取重要程度标签类型
function getImportanceType(importance: number) {
  if (importance >= 8) return 'error'
  if (importance >= 5) return 'warning'
  return 'success'
}

// 获取情感标签类型
function getSentimentType(sentiment: string) {
  if (sentiment === 'POSITIVE') return 'success'
  if (sentiment === 'NEGATIVE') return 'error'
  return 'default'
}

// 格式化文件大小
function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

// 格式化时间
function formatTime(time: string) {
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

onMounted(() => {
  loadFiles()
})
</script>

<style scoped>
.file-manager {
  display: grid;
  gap: 16px;
}

.upload-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.upload-area {
  padding: 40px;
  text-align: center;
}

.upload-icon {
  color: var(--primary-color);
  margin-bottom: 16px;
}

.upload-text {
  font-size: 16px;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.upload-hint {
  font-size: 12px;
  color: var(--text-secondary);
}

.filter-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.file-list-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.file-content {
  background: var(--bg-input);
  padding: 16px;
  border-radius: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  color: var(--text-primary);
}
</style>