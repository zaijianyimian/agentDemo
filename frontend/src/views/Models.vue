<template>
  <div class="page-shell">
    <section class="page-hero hero-grid">
      <div>
        <div class="page-eyebrow">Model Registry</div>
        <h2>管理默认模型、连接状态和提供商切换。</h2>
        <p>这里统一承接后端的模型 CRUD、连接测试和单模型启用切换，让模型能力入口更清晰。</p>
      </div>
      <div class="hero-actions">
        <div class="hero-stat">
          <span>总模型</span>
          <strong>{{ models.length }}</strong>
        </div>
        <div class="hero-stat">
          <span>启用中</span>
          <strong>{{ enabledCount }}</strong>
        </div>
        <div class="hero-stat">
          <span>默认</span>
          <strong>{{ defaultModel?.name || '未设置' }}</strong>
        </div>
      </div>
    </section>

    <section class="section-grid">
      <div class="surface-panel span-3">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Actions</div>
            <h3>快速操作</h3>
          </div>
        </div>
        <div class="quick-actions">
          <n-button type="primary" size="large" @click="openCreateModal">
            <template #icon><n-icon><AddIcon /></n-icon></template>
            添加模型
          </n-button>
          <n-button size="large" @click="loadModels">
            <template #icon><n-icon><RefreshIcon /></n-icon></template>
            刷新列表
          </n-button>
          <div class="provider-pills">
            <span v-for="provider in providerOptions.slice(0, 4)" :key="provider.value" class="provider-pill">
              {{ provider.label }}
            </span>
          </div>
        </div>
      </div>

      <div class="surface-panel span-9">
        <div class="section-head">
          <div>
            <div class="page-eyebrow">Overview</div>
            <h3>模型面板</h3>
          </div>
        </div>
        <div class="model-table-wrap">
          <n-data-table
            :columns="columns"
            :data="models"
            :loading="loading"
            :row-key="(row: AiModelConfig) => row.id"
            :scroll-x="1320"
            striped
          />
        </div>
      </div>
    </section>

    <n-modal v-model:show="showCreateModal" preset="card" :title="editingModel ? '编辑模型' : '添加模型'" style="width: min(680px, 92vw)">
      <n-form ref="formRef" :model="form" :rules="formRules" label-placement="top">
        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="展示名称" path="name">
            <n-input v-model:value="form.name" placeholder="如：通义千问 / DeepSeek" />
          </n-form-item-gi>
          <n-form-item-gi label="提供商" path="provider">
            <n-select
              v-model:value="form.provider"
              :options="providerOptions"
              placeholder="选择提供商"
              @update:value="onProviderChange"
            />
          </n-form-item-gi>
          <n-form-item-gi span="2" label="API 地址" path="baseUrl">
            <n-input v-model:value="form.baseUrl" placeholder="如：https://api.openai.com/v1" />
          </n-form-item-gi>
          <n-form-item-gi label="模型名称" path="modelName">
            <n-input v-model:value="form.modelName" placeholder="如：gpt-4.1 / qwen-plus" />
          </n-form-item-gi>
          <n-form-item-gi label="API Key" path="apiKey">
            <n-input v-model:value="form.apiKey" type="password" show-password-on="click" placeholder="输入 API Key" />
          </n-form-item-gi>
        </n-grid>
      </n-form>
      <template #footer>
        <n-space justify="space-between">
          <n-button @click="testConnection" :loading="testLoading">测试连接</n-button>
          <n-space>
            <n-button @click="showCreateModal = false">取消</n-button>
            <n-button type="primary" @click="submitForm" :loading="submitLoading">
              {{ editingModel ? '更新' : '创建' }}
            </n-button>
          </n-space>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import {
  NButton,
  NDataTable,
  NForm,
  NFormItemGi,
  NGrid,
  NIcon,
  NInput,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useDialog,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  RefreshOutline as RefreshIcon,
  Star as StarFilledIcon,
  StarOutline as StarIcon,
  TrashOutline as TrashIcon
} from '@vicons/ionicons5'
import dayjs from 'dayjs'
import type { AiModelConfig } from '@/types'
import { modelService } from '@/services/api'

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const models = ref<AiModelConfig[]>([])
const showCreateModal = ref(false)
const editingModel = ref<AiModelConfig | null>(null)
const testLoading = ref(false)
const submitLoading = ref(false)
const formRef = ref()
const providerOptions = ref<Array<{ label: string; value: string; baseUrl: string }>>([])

const form = ref({
  name: '',
  provider: 'aliyun',
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  modelName: '',
  apiKey: ''
})

const formRules = {
  name: { required: true, message: '请输入模型名称', trigger: 'blur' },
  provider: { required: true, message: '请选择提供商', trigger: 'change' },
  baseUrl: { required: true, message: '请输入 API 地址', trigger: 'blur' },
  modelName: { required: true, message: '请输入模型名称', trigger: 'blur' },
  apiKey: { required: true, message: '请输入 API Key', trigger: 'blur' }
}

const enabledCount = computed(() => models.value.filter(item => item.enabled).length)
const defaultModel = computed(() => models.value.find(item => item.isDefault))

const columns: DataTableColumns<AiModelConfig> = [
  { title: '名称', key: 'name', width: 140, ellipsis: { tooltip: true } },
  { title: '提供商', key: 'provider', width: 120, render: row => h(NTag, { size: 'small', round: true }, { default: () => row.provider }) },
  { title: '模型', key: 'modelName', width: 150, ellipsis: { tooltip: true } },
  { title: 'API 地址', key: 'baseUrl', width: 230, ellipsis: { tooltip: true } },
  { title: 'Key 预览', key: 'apiKeyPreview', width: 130 },
  {
    title: '默认',
    key: 'isDefault',
    width: 80,
    render: row => h(NButton, {
      quaternary: true,
      circle: true,
      type: row.isDefault ? 'warning' : 'default',
      onClick: () => setDefault(row)
    }, { icon: () => h(NIcon, null, { default: () => h(row.isDefault ? StarFilledIcon : StarIcon) }) })
  },
  {
    title: '状态',
    key: 'enabled',
    width: 90,
    render: row => h(NTag, { type: row.enabled ? 'success' : 'default', size: 'small', round: true }, { default: () => row.enabled ? '启用' : '禁用' })
  },
  { title: '创建时间', key: 'createTime', width: 145, render: row => formatTime(row.createTime) },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    fixed: 'right',
    render: row => h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', quaternary: true, onClick: () => editModel(row) }, { default: () => '编辑' }),
        h(NButton, { size: 'small', quaternary: true, type: row.enabled ? 'default' : 'success', onClick: () => toggleEnabled(row) }, { default: () => row.enabled ? '禁用' : '启用' }),
        h(NButton, { size: 'small', quaternary: true, type: 'error', onClick: () => confirmDelete(row) }, { icon: () => h(NIcon, null, { default: () => h(TrashIcon) }) })
      ]
    })
  }
]

const loadModels = async () => {
  loading.value = true
  try {
    const res = await modelService.list()
    models.value = res.data || []
  } catch {
    message.error('加载模型失败')
  } finally {
    loading.value = false
  }
}

const loadProviders = async () => {
  const res = await modelService.providers()
  providerOptions.value = res.data || []
}

const onProviderChange = (value: string) => {
  const provider = providerOptions.value.find(item => item.value === value)
  if (provider?.baseUrl) form.value.baseUrl = provider.baseUrl
}

const openCreateModal = () => {
  editingModel.value = null
  form.value = {
    name: '',
    provider: 'aliyun',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    modelName: '',
    apiKey: ''
  }
  showCreateModal.value = true
}

const editModel = (model: AiModelConfig) => {
  editingModel.value = model
  form.value = {
    name: model.name,
    provider: model.provider,
    baseUrl: model.baseUrl,
    modelName: model.modelName,
    apiKey: ''
  }
  showCreateModal.value = true
}

const testConnection = async () => {
  await formRef.value?.validate()
  testLoading.value = true
  try {
    const res = await modelService.test(form.value)
    const msg = res.data || res.message || '测试完成'
    if (msg.includes('连接失败')) message.warning(msg)
    else message.success(msg)
  } finally {
    testLoading.value = false
  }
}

const submitForm = async () => {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const res = editingModel.value
      ? await modelService.update(editingModel.value.id, form.value)
      : await modelService.create(form.value)
    if (res.success) {
      message.success(editingModel.value ? '更新成功' : '创建成功')
      showCreateModal.value = false
      loadModels()
    } else {
      message.error(res.message || '保存失败')
    }
  } finally {
    submitLoading.value = false
  }
}

const toggleEnabled = async (model: AiModelConfig) => {
  const res = await modelService.toggle(model.id)
  if (res.success) {
    message.success(model.enabled ? '已禁用当前模型' : '已切换为当前启用模型（其他模型已自动禁用）')
    loadModels()
  } else message.error(res.message || '操作失败')
}

const setDefault = async (model: AiModelConfig) => {
  const res = await modelService.setDefault(model.id)
  if (res.success) {
    message.success('已设置为默认模型')
    loadModels()
  } else message.error(res.message || '设置失败')
}

const confirmDelete = (model: AiModelConfig) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除模型 "${model.name}" 吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      const res = await modelService.delete(model.id)
      if (res.success) {
        message.success('删除成功')
        loadModels()
      } else message.error(res.message || '删除失败')
    }
  })
}

const formatTime = (time?: string) => time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-'

onMounted(async () => {
  await Promise.all([loadModels(), loadProviders()])
})
</script>

<style scoped>
.hero-grid { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(280px, 0.9fr); gap: 20px; }
.hero-actions, .quick-actions { display: grid; gap: 14px; }
.hero-stat, .provider-pill { border: 1px solid var(--border-color); background: rgba(255,255,255,.05); }
.hero-stat { padding: 16px 18px; border-radius: 20px; }
.hero-stat span { color: var(--text-secondary); font-size: .86rem; }
.hero-stat strong { display:block; margin-top:6px; font-size:1.3rem; }
.provider-pills { display:flex; flex-wrap:wrap; gap:10px; }
.provider-pill { padding:8px 12px; border-radius:999px; color:var(--text-secondary); font-size:.86rem; }
.span-3 { grid-column: span 3; }
.span-9 { grid-column: span 9; }
.model-table-wrap {
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
}
@media (max-width: 900px) { .hero-grid { grid-template-columns: 1fr; } }
</style>

