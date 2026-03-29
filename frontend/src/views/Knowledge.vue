<template>
  <div class="knowledge-page">
    <!-- 知识库列表 -->
    <n-card class="kb-list-card" :bordered="false">
      <template #header>
        <n-space justify="space-between">
          <span class="card-title">知识库列表</span>
          <n-button type="primary" @click="showCreateModal = true">
            <template #icon><n-icon><AddIcon /></n-icon></template>
            创建知识库
          </n-button>
        </n-space>
      </template>

      <n-grid :cols="3" :x-gap="16" :y-gap="16" responsive="screen">
        <n-grid-item v-for="kb in knowledgeBases" :key="kb.id">
          <n-card class="kb-item" :bordered="true" hoverable @click="selectKb(kb)">
            <n-space vertical>
              <n-space justify="space-between">
                <span class="kb-name">{{ kb.name }}</span>
                <n-tag :type="kb.enabled ? 'success' : 'default'" size="small">
                  {{ kb.enabled ? '启用' : '禁用' }}
                </n-tag>
              </n-space>
              <p class="kb-desc">{{ kb.description || '暂无描述' }}</p>
              <n-space>
                <n-tag size="small">文档: {{ kb.documentCount || 0 }}</n-tag>
                <n-tag size="small">分块: {{ kb.chunkSize || 500 }}</n-tag>
              </n-space>
            </n-space>
          </n-card>
        </n-grid-item>
      </n-grid>

      <n-empty v-if="!knowledgeBases.length" description="暂无知识库" />
    </n-card>

    <!-- 创建知识库弹窗 -->
    <n-modal v-model:show="showCreateModal" preset="card" title="创建知识库" style="width: 500px">
      <n-form ref="createFormRef" :model="createForm" :rules="createRules">
        <n-form-item label="名称" path="name">
          <n-input v-model:value="createForm.name" placeholder="请输入知识库名称" />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input v-model:value="createForm.description" type="textarea" placeholder="请输入描述" />
        </n-form-item>
        <n-form-item label="分块大小" path="chunkSize">
          <n-input-number v-model:value="createForm.chunkSize" :min="100" :max="2000" />
        </n-form-item>
        <n-form-item label="分块重叠" path="chunkOverlap">
          <n-input-number v-model:value="createForm.chunkOverlap" :min="0" :max="200" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" @click="createKb" :loading="createLoading">创建</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 知识库详情 -->
    <n-card v-if="selectedKb" class="kb-detail-card" :bordered="false">
      <template #header>
        <n-space justify="space-between">
          <span class="card-title">{{ selectedKb.name }} - 文档管理</span>
          <n-space>
            <n-button type="primary" @click="showUploadModal = true">
              <template #icon><n-icon><UploadIcon /></n-icon></template>
              上传文档
            </n-button>
            <n-button @click="toggleKbEnabled">
              {{ selectedKb.enabled ? '禁用' : '启用' }}
            </n-button>
            <n-button type="error" @click="deleteKb">
              <template #icon><n-icon><TrashIcon /></n-icon></template>
              删除
            </n-button>
          </n-space>
        </n-space>
      </template>

      <!-- 文档列表 -->
      <n-data-table
        :columns="docColumns"
        :data="documents"
        :loading="docLoading"
        :row-key="(row: KnowledgeDocument) => row.id"
        striped
      />

      <!-- 上传文档弹窗 -->
      <n-modal v-model:show="showUploadModal" preset="card" title="上传文档" style="width: 500px">
        <n-upload
          :custom-request="handleUpload"
          :show-file-list="true"
          accept=".txt,.md"
          :max="5"
        >
          <n-upload-dragger>
            <div class="upload-area">
              <n-icon size="48" class="upload-icon"><UploadIcon /></n-icon>
              <p class="upload-text">点击或拖拽文件到此处上传</p>
              <p class="upload-hint">支持 txt, md 格式，文件会被分块并向量化存储</p>
            </div>
          </n-upload-dragger>
        </n-upload>
      </n-modal>
    </n-card>

    <!-- RAG 问答 -->
    <n-card v-if="selectedKb" class="query-card" :bordered="false">
      <template #header>
        <span class="card-title">RAG 智能问答</span>
      </template>

      <n-space vertical>
        <n-input
          v-model:value="queryText"
          type="textarea"
          placeholder="输入问题，系统会从知识库中检索相关内容..."
          :rows="3"
        />
        <n-space>
          <n-input-number v-model:value="queryTopK" :min="1" :max="20" label="返回条数" />
          <n-button type="primary" @click="executeQuery" :loading="queryLoading">
            搜索
          </n-button>
        </n-space>

        <!-- 搜索结果 -->
        <n-divider v-if="searchResults.length">搜索结果</n-divider>
        <n-list v-if="searchResults.length" bordered>
          <n-list-item v-for="(item, index) in searchResults" :key="index">
            <n-space vertical>
              <n-space justify="space-between">
                <n-tag type="info" size="small">{{ item.docName }}</n-tag>
                <n-tag size="small">相关度: {{ (item.score * 100).toFixed(1) }}%</n-tag>
              </n-space>
              <n-ellipsis :line-clamp="3" expand-trigger="click">
                {{ item.text }}
              </n-ellipsis>
            </n-space>
          </n-list-item>
        </n-list>
      </n-space>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, h, onMounted } from 'vue'
import {
  NCard,
  NGrid,
  NGridItem,
  NSpace,
  NButton,
  NIcon,
  NTag,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NDataTable,
  NUpload,
  NUploadDragger,
  NDivider,
  NList,
  NListItem,
  NEllipsis,
  NEmpty,
  useMessage,
  type DataTableColumns,
  type UploadCustomRequestOptions
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  CloudUploadOutline as UploadIcon,
  TrashOutline as TrashIcon
} from '@vicons/ionicons5'
import axios from 'axios'
import dayjs from 'dayjs'

interface KnowledgeBase {
  id: number
  name: string
  description: string
  collectionName: string
  chunkSize: number
  chunkOverlap: number
  documentCount: number
  enabled: boolean
  createTime: string
}

interface KnowledgeDocument {
  id: number
  baseId: number
  fileName: string
  fileType: string
  fileSize: number
  chunkCount: number
  status: string
  createTime: string
}

interface SearchResult {
  score: number
  text: string
  docName: string
  docId: string
}

const message = useMessage()

// 知识库列表
const knowledgeBases = ref<KnowledgeBase[]>([])
const kbLoading = ref(false)

// 选中的知识库
const selectedKb = ref<KnowledgeBase | null>(null)
const documents = ref<KnowledgeDocument[]>([])
const docLoading = ref(false)

// 创建知识库
const showCreateModal = ref(false)
const createLoading = ref(false)
const createFormRef = ref()
const createForm = ref({
  name: '',
  description: '',
  chunkSize: 500,
  chunkOverlap: 50
})
const createRules = {
  name: { required: true, message: '请输入名称', trigger: 'blur' }
}

// 上传文档
const showUploadModal = ref(false)

// RAG 问答
const queryText = ref('')
const queryTopK = ref(5)
const queryLoading = ref(false)
const searchResults = ref<SearchResult[]>([])

// 文档表格列
const docColumns: DataTableColumns<KnowledgeDocument> = [
  { title: '文件名', key: 'fileName', ellipsis: { tooltip: true } },
  { title: '类型', key: 'fileType', width: 80, render: row => h(NTag, { size: 'small' }, { default: () => row.fileType.toUpperCase() }) },
  { title: '大小', key: 'fileSize', width: 100, render: row => formatSize(row.fileSize) },
  { title: '分块数', key: 'chunkCount', width: 80 },
  { title: '状态', key: 'status', width: 100, render: row => h(NTag, { type: getStatusType(row.status), size: 'small' }, { default: () => row.status }) },
  { title: '创建时间', key: 'createTime', width: 160, render: row => formatTime(row.createTime) },
  {
    title: '操作',
    key: 'actions',
    width: 80,
    render: row => h(NButton, {
      size: 'small',
      quaternary: true,
      type: 'error',
      onClick: () => deleteDoc(row)
    }, { icon: () => h(NIcon, null, { default: () => h(TrashIcon) }) })
  }
]

// 加载知识库列表
const loadKnowledgeBases = async () => {
  kbLoading.value = true
  try {
    const res = await axios.get('/api/knowledge/list')
    knowledgeBases.value = res.data.data || []
  } catch (error) {
    message.error('加载知识库失败')
  } finally {
    kbLoading.value = false
  }
}

// 选择知识库
const selectKb = async (kb: KnowledgeBase) => {
  selectedKb.value = kb
  await loadDocuments(kb.id)
}

// 加载文档列表
const loadDocuments = async (baseId: number) => {
  docLoading.value = true
  try {
    const res = await axios.get(`/api/knowledge/${baseId}/documents`)
    documents.value = res.data.data || []
  } catch (error) {
    message.error('加载文档失败')
  } finally {
    docLoading.value = false
  }
}

// 创建知识库
const createKb = async () => {
  try {
    await createFormRef.value?.validate()
    createLoading.value = true
    const res = await axios.post('/api/knowledge', createForm.value)
    if (res.data.success) {
      message.success('创建成功')
      showCreateModal.value = false
      createForm.value = { name: '', description: '', chunkSize: 500, chunkOverlap: 50 }
      loadKnowledgeBases()
    } else {
      message.error(res.data.message || '创建失败')
    }
  } catch (error) {
    message.error('创建失败')
  } finally {
    createLoading.value = false
  }
}

// 上传文档
const handleUpload = async ({ file }: UploadCustomRequestOptions) => {
  if (!selectedKb.value) return
  try {
    message.loading('正在上传并处理文档...')
    const formData = new FormData()
    formData.append('file', file.file as File)
    const res = await axios.post(`/api/knowledge/${selectedKb.value.id}/upload`, formData)
    if (res.data.success) {
      message.success('上传成功')
      showUploadModal.value = false
      loadDocuments(selectedKb.value.id)
      loadKnowledgeBases()
    } else {
      message.error(res.data.message || '上传失败')
    }
  } catch (error) {
    message.error('上传失败')
  }
}

// 删除文档
const deleteDoc = async (doc: KnowledgeDocument) => {
  try {
    const res = await axios.delete(`/api/knowledge/document/${doc.id}`)
    if (res.data.success) {
      message.success('删除成功')
      loadDocuments(selectedKb.value!.id)
      loadKnowledgeBases()
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// 切换启用状态
const toggleKbEnabled = async () => {
  if (!selectedKb.value) return
  try {
    const res = await axios.put(`/api/knowledge/${selectedKb.value.id}/toggle`)
    if (res.data.success) {
      selectedKb.value = res.data.data
      loadKnowledgeBases()
    }
  } catch (error) {
    message.error('操作失败')
  }
}

// 删除知识库
const deleteKb = async () => {
  if (!selectedKb.value) return
  try {
    const res = await axios.delete(`/api/knowledge/${selectedKb.value.id}`)
    if (res.data.success) {
      message.success('删除成功')
      selectedKb.value = null
      documents.value = []
      loadKnowledgeBases()
    }
  } catch (error) {
    message.error('删除失败')
  }
}

// RAG 搜索
const executeQuery = async () => {
  if (!selectedKb.value || !queryText.value) return
  queryLoading.value = true
  try {
    const res = await axios.get(`/api/knowledge/${selectedKb.value.id}/search`, {
      params: { query: queryText.value, topK: queryTopK.value }
    })
    searchResults.value = res.data.data || []
    if (!searchResults.value.length) {
      message.info('未找到相关内容')
    }
  } catch (error) {
    message.error('搜索失败')
  } finally {
    queryLoading.value = false
  }
}

function getStatusType(status: string) {
  if (status === 'completed') return 'success'
  if (status === 'processing') return 'warning'
  if (status === 'failed') return 'error'
  return 'default'
}

function formatSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function formatTime(time: string) {
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

onMounted(() => {
  loadKnowledgeBases()
})
</script>

<style scoped>
.knowledge-page {
  display: grid;
  gap: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.kb-list-card,
.kb-detail-card,
.query-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.kb-item {
  cursor: pointer;
  transition: all 0.3s;
}

.kb-item:hover {
  border-color: var(--primary-color);
}

.kb-name {
  font-size: 14px;
  font-weight: 600;
}

.kb-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0;
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
  font-size: 14px;
  color: var(--text-primary);
  margin-bottom: 8px;
}

.upload-hint {
  font-size: 12px;
  color: var(--text-secondary);
}
</style>