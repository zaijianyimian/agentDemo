<template>
  <div class="models-page">
    <!-- 操作栏 -->
    <n-card class="action-card" :bordered="false">
      <n-space>
        <n-button type="primary" @click="showCreateModal = true">
          <template #icon><n-icon><AddIcon /></n-icon></template>
          添加模型
        </n-button>
        <n-button @click="loadModels">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          刷新
        </n-button>
      </n-space>
    </n-card>

    <!-- 模型列表 -->
    <n-card class="model-list-card" :bordered="false">
      <n-data-table
        :columns="columns"
        :data="models"
        :loading="loading"
        :row-key="(row: AiModelConfig) => row.id"
        striped
      />
    </n-card>

    <!-- 创建/编辑模型弹窗 -->
    <n-modal v-model:show="showCreateModal" preset="card" :title="editingModel ? '编辑模型' : '添加模型'" style="width: 600px">
      <n-form ref="formRef" :model="form" :rules="formRules">
        <n-form-item label="模型名称" path="name">
          <n-input v-model:value="form.name" placeholder="如：通义千问、DeepSeek" />
        </n-form-item>
        <n-form-item label="提供商" path="provider">
          <n-select
            v-model:value="form.provider"
            :options="providerOptions"
            placeholder="选择提供商"
            @update:value="onProviderChange"
          />
        </n-form-item>
        <n-form-item label="API 地址" path="baseUrl">
          <n-input v-model:value="form.baseUrl" placeholder="如：https://api.openai.com/v1" />
        </n-form-item>
        <n-form-item label="模型名称" path="modelName">
          <n-input v-model:value="form.modelName" placeholder="如：gpt-4、qwen-plus、deepseek-chat" />
        </n-form-item>
        <n-form-item label="API Key" path="apiKey">
          <n-input
            v-model:value="form.apiKey"
            type="password"
            show-password-on="click"
            placeholder="输入 API Key"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="testConnection" :loading="testLoading">测试连接</n-button>
          <n-button @click="showCreateModal = false">取消</n-button>
          <n-button type="primary" @click="submitForm" :loading="submitLoading">
            {{ editingModel ? '更新' : '创建' }}
          </n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, h, onMounted } from 'vue'
import {
  NCard,
  NSpace,
  NButton,
  NIcon,
  NDataTable,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NTag,
  useMessage,
  useDialog,
  type DataTableColumns
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  RefreshOutline as RefreshIcon,
  StarOutline as StarIcon,
  Star as StarFilledIcon,
  TrashOutline as TrashIcon
} from '@vicons/ionicons5'
import axios from 'axios'
import dayjs from 'dayjs'

interface AiModelConfig {
  id: number
  name: string
  provider: string
  baseUrl: string
  modelName: string
  apiKeyPreview: string
  isDefault: boolean
  enabled: boolean
  createTime: string
  updateTime: string
}

const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const models = ref<AiModelConfig[]>([])
const showCreateModal = ref(false)
const editingModel = ref<AiModelConfig | null>(null)
const testLoading = ref(false)
const submitLoading = ref(false)
const formRef = ref()

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

const providerOptions = ref([
  { label: 'OpenAI', value: 'openai', baseUrl: 'https://api.openai.com/v1' },
  { label: '阿里云（通义千问）', value: 'aliyun', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
  { label: 'DeepSeek', value: 'deepseek', baseUrl: 'https://api.deepseek.com/v1' },
  { label: 'Anthropic（Claude）', value: 'anthropic', baseUrl: 'https://api.anthropic.com/v1' },
  { label: '智谱AI（GLM）', value: 'glm', baseUrl: 'https://open.bigmodel.cn/api/paas/v4' },
  { label: 'Moonshot（Kimi）', value: 'moonshot', baseUrl: 'https://api.moonshot.cn/v1' },
  { label: '自定义', value: 'other', baseUrl: '' }
])

const columns: DataTableColumns<AiModelConfig> = [
  { title: '名称', key: 'name', width: 120 },
  { title: '提供商', key: 'provider', width: 100, render: row => h(NTag, { size: 'small' }, { default: () => row.provider }) },
  { title: '模型', key: 'modelName', width: 120 },
  { title: 'API 地址', key: 'baseUrl', ellipsis: { tooltip: true } },
  { title: 'API Key', key: 'apiKeyPreview', width: 120 },
  {
    title: '默认',
    key: 'isDefault',
    width: 80,
    render: row => h(NButton, {
      size: 'small',
      quaternary: true,
      type: row.isDefault ? 'warning' : 'default',
      onClick: () => setDefault(row)
    }, { icon: () => h(NIcon, null, { default: () => h(row.isDefault ? StarFilledIcon : StarIcon) }) })
  },
  {
    title: '状态',
    key: 'enabled',
    width: 80,
    render: row => h(NTag, { type: row.enabled ? 'success' : 'default', size: 'small' }, { default: () => row.enabled ? '启用' : '禁用' })
  },
  { title: '创建时间', key: 'createTime', width: 160, render: row => formatTime(row.createTime) },
  {
    title: '操作',
    key: 'actions',
    width: 150,
    render: row => h(NSpace, null, {
      default: () => [
        h(NButton, { size: 'small', quaternary: true, onClick: () => toggleEnabled(row) }, { default: () => row.enabled ? '禁用' : '启用' }),
        h(NButton, { size: 'small', quaternary: true, type: 'error', onClick: () => confirmDelete(row) }, { icon: () => h(NIcon, null, { default: () => h(TrashIcon) }) })
      ]
    })
  }
]

const loadModels = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/model/list')
    models.value = res.data.data || []
  } catch (error) {
    message.error('加载模型失败')
  } finally {
    loading.value = false
  }
}

const loadProviders = async () => {
  try {
    const res = await axios.get('/api/model/providers')
    if (res.data.success && res.data.data) {
      providerOptions.value = res.data.data.map((p: any) => ({
        label: p.label,
        value: p.value,
        baseUrl: p.baseUrl
      }))
    }
  } catch (error) {
    console.error('加载提供商失败', error)
  }
}

const onProviderChange = (value: string) => {
  const provider = providerOptions.value.find(p => p.value === value)
  if (provider && provider.baseUrl) {
    form.value.baseUrl = provider.baseUrl
  }
}

const testConnection = async () => {
  try {
    await formRef.value?.validate()
    testLoading.value = true
    const res = await axios.post('/api/model/test', form.value)
    if (res.data.success) {
      message.success(res.data.data)
    } else {
      message.error(res.data.message || '测试失败')
    }
  } catch (error: any) {
    message.error('测试失败: ' + (error.response?.data?.message || error.message))
  } finally {
    testLoading.value = false
  }
}

const submitForm = async () => {
  try {
    await formRef.value?.validate()
    submitLoading.value = true

    if (editingModel.value) {
      const res = await axios.put(`/api/model/${editingModel.value.id}`, form.value)
      if (res.data.success) {
        message.success('更新成功')
        showCreateModal.value = false
        loadModels()
      } else {
        message.error(res.data.message || '更新失败')
      }
    } else {
      const res = await axios.post('/api/model', form.value)
      if (res.data.success) {
        message.success('创建成功')
        showCreateModal.value = false
        loadModels()
      } else {
        message.error(res.data.message || '创建失败')
      }
    }
  } catch (error: any) {
    message.error('操作失败: ' + (error.response?.data?.message || error.message))
  } finally {
    submitLoading.value = false
  }
}

const toggleEnabled = async (model: AiModelConfig) => {
  try {
    const res = await axios.put(`/api/model/${model.id}/toggle`)
    if (res.data.success) {
      message.success(model.enabled ? '已禁用' : '已启用')
      loadModels()
    }
  } catch (error) {
    message.error('操作失败')
  }
}

const setDefault = async (model: AiModelConfig) => {
  try {
    const res = await axios.put(`/api/model/${model.id}/default`)
    if (res.data.success) {
      message.success('已设置为默认模型')
      loadModels()
    }
  } catch (error) {
    message.error('操作失败')
  }
}

const confirmDelete = (model: AiModelConfig) => {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除模型 "${model.name}" 吗？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        const res = await axios.delete(`/api/model/${model.id}`)
        if (res.data.success) {
          message.success('删除成功')
          loadModels()
        }
      } catch (error) {
        message.error('删除失败')
      }
    }
  })
}

function formatTime(time: string) {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm')
}

onMounted(() => {
  loadModels()
  loadProviders()
})
</script>

<style scoped>
.models-page {
  display: grid;
  gap: 16px;
}

.action-card,
.model-list-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}
</style>