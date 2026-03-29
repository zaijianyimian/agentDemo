<template>
  <div class="page-shell">
    <section class="page-hero hero-grid">
      <div>
        <div class="page-eyebrow">Knowledge Workspace</div>
        <h2>把知识库、文档和检索结果放到同一张工作台。</h2>
        <p>这里直接承接后端知识库接口，支持建库、上传文档、切换启用状态以及语义搜索。</p>
      </div>
      <div class="hero-side">
        <div class="hero-stat"><span>知识库</span><strong>{{ knowledgeBases.length }}</strong></div>
        <div class="hero-stat"><span>当前文档</span><strong>{{ documents.length }}</strong></div>
        <div class="hero-stat"><span>当前选中</span><strong>{{ selectedKb?.name || '未选择' }}</strong></div>
      </div>
    </section>

    <section class="section-grid">
      <div class="surface-panel span-4">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Library</div>
            <h3>知识库列表</h3>
          </div>
          <n-button type="primary" @click="showCreateModal = true">
            <template #icon><n-icon><AddIcon /></n-icon></template>
            创建
          </n-button>
        </div>

        <div v-if="knowledgeBases.length" class="kb-list">
          <button
            v-for="kb in knowledgeBases"
            :key="kb.id"
            type="button"
            :class="['kb-card', { active: selectedKb?.id === kb.id }]"
            @click="selectKb(kb)"
          >
            <div class="kb-card__head">
              <strong>{{ kb.name }}</strong>
              <n-tag :type="kb.enabled ? 'success' : 'default'" size="small" round>
                {{ kb.enabled ? '启用' : '禁用' }}
              </n-tag>
            </div>
            <p>{{ kb.description || '暂无描述' }}</p>
            <div class="kb-card__meta">
              <span>文档 {{ kb.documentCount || 0 }}</span>
              <span>分块 {{ kb.chunkSize || 500 }}</span>
            </div>
          </button>
        </div>
        <n-empty v-else description="暂无知识库" />
      </div>

      <div class="surface-panel span-8">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Documents</div>
            <h3>{{ selectedKb ? `${selectedKb.name} 文档区` : '请先选择知识库' }}</h3>
          </div>
          <n-space v-if="selectedKb">
            <n-button type="primary" @click="showUploadModal = true">
              <template #icon><n-icon><UploadIcon /></n-icon></template>
              上传文档
            </n-button>
            <n-button @click="toggleKbEnabled">{{ selectedKb.enabled ? '禁用' : '启用' }}</n-button>
            <n-button type="error" @click="deleteKb">
              <template #icon><n-icon><TrashIcon /></n-icon></template>
              删除
            </n-button>
          </n-space>
        </div>

        <n-data-table
          v-if="selectedKb"
          :columns="docColumns"
          :data="documents"
          :loading="docLoading"
          :row-key="(row: KnowledgeDocument) => row.id"
          striped
        />
        <n-empty v-else description="左侧选择一个知识库后即可查看文档" />
      </div>

      <div class="surface-panel span-12" v-if="selectedKb">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Semantic Search</div>
            <h3>RAG 搜索</h3>
          </div>
        </div>
        <div class="query-box">
          <n-input v-model:value="queryText" type="textarea" :rows="3" placeholder="输入问题，系统会从知识库中检索相关片段..." />
          <div class="query-actions">
            <n-input-number v-model:value="queryTopK" :min="1" :max="20" />
            <n-button type="primary" @click="executeQuery" :loading="queryLoading">搜索</n-button>
          </div>
        </div>
        <div v-if="searchResults.length" class="search-grid">
          <div v-for="(item, index) in searchResults" :key="index" class="search-card">
            <div class="search-card__head">
              <n-tag size="small" type="info" round>{{ item.docName }}</n-tag>
              <span>{{ (item.score * 100).toFixed(1) }}%</span>
            </div>
            <p>{{ item.text }}</p>
          </div>
        </div>
      </div>
    </section>

    <n-modal v-model:show="showCreateModal" preset="card" title="创建知识库" style="width: min(540px, 92vw)">
      <n-form ref="createFormRef" :model="createForm" :rules="createRules" label-placement="top">
        <n-form-item label="名称" path="name">
          <n-input v-model:value="createForm.name" placeholder="请输入知识库名称" />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input v-model:value="createForm.description" type="textarea" placeholder="请输入描述" />
        </n-form-item>
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="分块大小" path="chunkSize">
            <n-input-number v-model:value="createForm.chunkSize" :min="100" :max="2000" style="width:100%" />
          </n-form-item-gi>
          <n-form-item-gi label="分块重叠" path="chunkOverlap">
            <n-input-number v-model:value="createForm.chunkOverlap" :min="0" :max="200" style="width:100%" />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" @click="createKb" :loading="createLoading">创建</n-button>
        </n-space>
      </template>
    </n-modal>

    <n-modal v-model:show="showUploadModal" preset="card" title="上传文档" style="width: min(560px, 92vw)">
      <n-upload :custom-request="handleUpload" :show-file-list="true" accept=".txt,.md" :max="5">
        <n-upload-dragger>
          <div class="upload-area">
            <n-icon size="44" class="upload-icon"><UploadIcon /></n-icon>
            <p>点击或拖拽文件到此处上传</p>
            <span>支持 txt、md，上传后将自动分块并向量化。</span>
          </div>
        </n-upload-dragger>
      </n-upload>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, h, onMounted } from 'vue'
import {
  NButton,
  NDataTable,
  NEmpty,
  NForm,
  NFormItem,
  NFormItemGi,
  NGrid,
  NSpace,
  NIcon,
  NTag,
  NModal,
  NInput,
  NInputNumber,
  NUpload,
  NUploadDragger,
  useMessage,
  type DataTableColumns,
  type UploadCustomRequestOptions
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  CloudUploadOutline as UploadIcon,
  TrashOutline as TrashIcon
} from '@vicons/ionicons5'
import dayjs from 'dayjs'
import type { KnowledgeBase, KnowledgeDocument } from '@/types'
import { knowledgeService } from '@/services/api'

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
  { title: '类型', key: 'fileType', width: 80, render: row => h(NTag, { size: 'small' }, { default: () => (row.fileType || '-').toUpperCase() }) },
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
    const res = await knowledgeService.list()
    knowledgeBases.value = res.data || []
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
    const res = await knowledgeService.listDocuments(baseId)
    documents.value = res.data || []
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
    const res = await knowledgeService.create(createForm.value)
    if (res.success) {
      message.success('创建成功')
      showCreateModal.value = false
      createForm.value = { name: '', description: '', chunkSize: 500, chunkOverlap: 50 }
      loadKnowledgeBases()
    } else {
      message.error(res.message || '创建失败')
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
    const res = await knowledgeService.upload(selectedKb.value.id, file.file as File)
    if (res.success) {
      message.success('上传成功')
      showUploadModal.value = false
      loadDocuments(selectedKb.value.id)
      loadKnowledgeBases()
    } else {
      message.error(res.message || '上传失败')
    }
  } catch (error) {
    message.error('上传失败')
  }
}

// 删除文档
const deleteDoc = async (doc: KnowledgeDocument) => {
  try {
    const res = await knowledgeService.deleteDocument(doc.id)
    if (res.success) {
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
    const res = await knowledgeService.toggle(selectedKb.value.id)
    if (res.success) {
      selectedKb.value = res.data || null
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
    const res = await knowledgeService.delete(selectedKb.value.id)
    if (res.success) {
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
    const res = await knowledgeService.search(selectedKb.value.id, queryText.value, queryTopK.value)
    searchResults.value = res.data || []
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

function formatTime(time?: string) {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-'
}

onMounted(() => {
  loadKnowledgeBases()
})
</script>

<style scoped>
.hero-grid { display:grid; grid-template-columns:minmax(0,1.35fr) minmax(260px,.85fr); gap:20px; }
.hero-side { display:grid; gap:12px; }
.hero-stat, .kb-card, .search-card { border:1px solid var(--border-color); background:rgba(255,255,255,.05); }
.hero-stat { padding:16px 18px; border-radius:20px; }
.hero-stat span { color:var(--text-secondary); font-size:.86rem; }
.hero-stat strong { display:block; margin-top:6px; font-size:1.3rem; }
.kb-list, .search-grid { display:grid; gap:12px; }
.kb-card { padding:16px; border-radius:22px; text-align:left; cursor:pointer; transition:all .25s ease; }
.kb-card.active, .kb-card:hover { border-color:var(--primary-color); box-shadow:var(--shadow-glow); transform:translateY(-2px); }
.kb-card__head, .search-card__head, .query-actions { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.kb-card p, .kb-card__meta, .search-card p, .upload-area span { color:var(--text-secondary); }
.kb-card__meta { display:flex; gap:12px; margin-top:10px; font-size:.86rem; }
.query-box { display:grid; gap:14px; margin-bottom:16px; }
.query-actions { justify-content:flex-end; }
.search-grid { grid-template-columns:repeat(2,minmax(0,1fr)); }
.search-card { padding:16px; border-radius:20px; }
.upload-area { padding:36px; text-align:center; }
.upload-area p { margin:10px 0 6px; }
.upload-icon { color:var(--primary-color); }
@media (max-width: 900px) { .hero-grid, .search-grid { grid-template-columns:1fr; } }
</style>
