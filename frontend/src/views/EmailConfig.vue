<template>
  <div class="email-page">
    <!-- 操作栏 -->
    <n-card class="action-card" :bordered="false">
      <n-space>
        <n-button type="primary" @click="showAddModal = true">
          <template #icon><n-icon><AddIcon /></n-icon></template>
          添加邮箱
        </n-button>
        <n-button @click="loadConfigs">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          刷新
        </n-button>
      </n-space>
    </n-card>

    <!-- 邮箱列表 -->
    <n-grid :cols="2" :x-gap="16" :y-gap="16">
      <n-gi v-for="config in configs" :key="config.id">
        <n-card class="email-card" :bordered="false">
          <div class="email-header">
            <div class="email-info">
              <n-icon size="24" class="email-icon"><MailIcon /></n-icon>
              <div>
                <div class="email-address">{{ config.email }}</div>
                <div class="email-host">{{ config.host }}:{{ config.port }}</div>
              </div>
            </div>
            <n-switch
              :value="config.enabled"
              @update:value="(val: boolean) => toggleEnabled(config, val)"
            />
          </div>

          <n-divider />

          <div class="email-status">
            <n-tag :type="config.enabled ? 'success' : 'default'" size="small">
              {{ config.enabled ? '已启用' : '已禁用' }}
            </n-tag>
            <n-tag v-if="config.sslEnabled" type="info" size="small">SSL</n-tag>
            <n-tag :type="connectionStatus[config.id]?.success ? 'success' : (connectionStatus[config.id] ? 'error' : 'default')" size="small">
              {{ connectionStatus[config.id]?.message || '未测试' }}
            </n-tag>
          </div>

          <div class="email-actions">
            <n-button
              size="small"
              :loading="testingIds.includes(config.id)"
              @click="testConnection(config)"
            >
              <template #icon><n-icon><TestIcon /></n-icon></template>
              测试连接
            </n-button>
            <n-button
              size="small"
              :loading="checkingNetworkIds.includes(config.id)"
              @click="checkNetwork(config)"
            >
              网络检测
            </n-button>
            <n-button
              size="small"
              :type="listenerStatus[config.id] ? 'warning' : 'default'"
              @click="toggleListener(config)"
            >
              {{ listenerStatus[config.id] ? '停止监听' : '开始监听' }}
            </n-button>
            <n-button size="small" @click="editConfig(config)">编辑</n-button>
            <n-button size="small" type="error" @click="deleteConfig(config)">删除</n-button>
          </div>
        </n-card>
      </n-gi>
    </n-grid>

    <n-empty v-if="configs.length === 0" description="暂无邮箱配置" />

    <!-- 邮箱模板 -->
    <n-card title="常用邮箱服务器" class="template-card" :bordered="false">
      <n-data-table :columns="templateColumns" :data="emailTemplates" size="small" />
    </n-card>

    <!-- 添加/编辑弹窗 -->
    <n-modal v-model:show="showAddModal" preset="card" :title="editingConfig ? '编辑邮箱' : '添加邮箱'" style="width: 500px">
      <n-form ref="formRef" :model="formData" label-placement="left" label-width="100">
        <n-form-item label="邮箱地址" path="email">
          <n-input v-model:value="formData.email" placeholder="your@email.com" />
        </n-form-item>
        <n-form-item label="密码/授权码" path="password">
          <n-input v-model:value="formData.password" type="password" placeholder="输入密码或授权码" />
        </n-form-item>
        <n-form-item label="IMAP服务器" path="host">
          <n-input v-model:value="formData.host" placeholder="imap.example.com" />
        </n-form-item>
        <n-form-item label="IMAP端口" path="port">
          <n-input-number v-model:value="formData.port" :min="1" :max="65535" />
        </n-form-item>
        <n-form-item label="启用SSL" path="sslEnabled">
          <n-switch v-model:value="formData.sslEnabled" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="checkNewConfigNetwork" :loading="checkingNewNetwork">
            网络检测
          </n-button>
          <n-button @click="testNewConfig" :loading="testingNew">
            <template #icon><n-icon><TestIcon /></n-icon></template>
            测试连接
          </n-button>
          <n-button @click="showAddModal = false">取消</n-button>
          <n-button type="primary" @click="saveConfig">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 测试结果弹窗 -->
    <n-modal v-model:show="showTestResult" preset="card" title="测试结果" style="width: 400px">
      <n-result
        :status="testResult?.success ? 'success' : 'error'"
        :title="testResult?.success ? '连接成功' : '连接失败'"
        :description="testResult?.message"
      >
        <template #footer>
          <n-space vertical>
            <n-text v-if="testResult?.success">
              耗时: {{ testResult?.durationMs }}ms | 收件箱邮件数: {{ testResult?.messageCount }}
            </n-text>
            <n-text v-if="!testResult?.success && testResult?.errorDetail" type="error">
              错误详情: {{ testResult?.errorDetail }}
            </n-text>
            <n-button @click="showTestResult = false">关闭</n-button>
          </n-space>
        </template>
      </n-result>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  NCard,
  NGrid,
  NGi,
  NButton,
  NIcon,
  NSpace,
  NTag,
  NSwitch,
  NDivider,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NDataTable,
  NEmpty,
  NResult,
  NText,
  useMessage
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  RefreshOutline as RefreshIcon,
  MailOutline as MailIcon,
  BuildOutline as TestIcon
} from '@vicons/ionicons5'
import { emailService } from '@/services/api'
import type { EmailConfig } from '@/types'

const message = useMessage()
const configs = ref<EmailConfig[]>([])
const listenerStatus = ref<Record<number, boolean>>({})
const connectionStatus = ref<Record<number, { success: boolean; message: string }>>({})
const testingIds = ref<number[]>([])
const checkingNetworkIds = ref<number[]>([])
const showAddModal = ref(false)
const editingConfig = ref<EmailConfig | null>(null)
const formData = ref({
  email: '',
  password: '',
  host: '',
  port: 993,
  sslEnabled: true,
  protocol: 'imap'
})
const testingNew = ref(false)
const checkingNewNetwork = ref(false)
const showTestResult = ref(false)
const testResult = ref<{
  success: boolean
  message: string
  durationMs: number
  messageCount: number
  errorDetail: string
} | null>(null)

// 邮箱模板
const emailTemplates = [
  { name: 'QQ邮箱', host: 'imap.qq.com', port: 993, ssl: true },
  { name: '163邮箱', host: 'imap.163.com', port: 993, ssl: true },
  { name: 'Gmail', host: 'imap.gmail.com', port: 993, ssl: true },
  { name: 'Outlook', host: 'outlook.office365.com', port: 993, ssl: true }
]

const templateColumns = [
  { title: '邮箱', key: 'name' },
  { title: 'IMAP服务器', key: 'host' },
  { title: '端口', key: 'port' },
  { title: 'SSL', key: 'ssl', render: (row: any) => row.ssl ? '是' : '否' }
]

const parseConfigList = (payload: any): EmailConfig[] => {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.data)) return payload.data
  return []
}

const trimText = (value: unknown): string => String(value ?? '').trim()

const normalizeConfigPayload = (payload: Partial<EmailConfig>): Partial<EmailConfig> => ({
  ...payload,
  email: payload.email == null ? payload.email : trimText(payload.email),
  password: payload.password == null ? payload.password : trimText(payload.password),
  host: payload.host == null ? payload.host : trimText(payload.host),
  protocol: payload.protocol == null ? payload.protocol : trimText(payload.protocol).toLowerCase(),
  folder: payload.folder == null ? payload.folder : trimText(payload.folder),
  remark: payload.remark == null ? payload.remark : trimText(payload.remark)
})

const parseObjectPayload = (payload: any): Record<string, any> => {
  if (payload && typeof payload === 'object' && !Array.isArray(payload)) return payload
  if (payload?.data && typeof payload.data === 'object' && !Array.isArray(payload.data)) return payload.data
  return {}
}

const parseResultPayload = <T extends Record<string, any>>(payload: any): T | null => {
  if (payload && typeof payload === 'object' && !Array.isArray(payload) && ('success' in payload || 'message' in payload || 'durationMs' in payload)) {
    return payload as T
  }
  if (payload?.data && typeof payload.data === 'object' && !Array.isArray(payload.data)) {
    return payload.data as T
  }
  return null
}

// 加载配置
const loadConfigs = async () => {
  try {
    const res = await emailService.listConfigs()
    configs.value = parseConfigList(res).map(item => normalizeConfigPayload(item) as EmailConfig)
    // 加载监听状态
    const statusRes = await emailService.getListenerStatus()
    const statusPayload = parseObjectPayload(statusRes)
    listenerStatus.value = {}
    for (const [key, value] of Object.entries(statusPayload)) {
      listenerStatus.value[Number(key)] = value === '已连接'
    }
  } catch (error) {
    message.error('加载失败')
  }
}

// 测试邮箱连接
const testConnection = async (config: EmailConfig) => {
  testingIds.value.push(config.id)
  try {
    const res = await emailService.testConfig(config.id)
    const data = parseResultPayload<{
      success: boolean
      message: string
      durationMs: number
      messageCount: number
      errorDetail: string
    }>(res)
    if (!data) {
      message.error('测试请求失败')
      return
    }
    testResult.value = data
    connectionStatus.value[config.id] = {
      success: data.success,
      message: data.success ? '连接正常' : '连接异常'
    }
    showTestResult.value = true
    if (data.success) {
      message.success(`测试成功，耗时 ${data.durationMs}ms`)
    } else {
      message.error(data.message)
    }
  } catch (error) {
    message.error('测试请求失败')
    connectionStatus.value[config.id] = {
      success: false,
      message: '测试失败'
    }
  } finally {
    testingIds.value = testingIds.value.filter(id => id !== config.id)
  }
}

// 测试新配置（未保存的）
const testNewConfig = async () => {
  formData.value = normalizeConfigPayload(formData.value) as typeof formData.value
  if (!formData.value.email || !formData.value.password || !formData.value.host) {
    message.warning('请填写完整的邮箱配置')
    return
  }
  testingNew.value = true
  try {
    const res = await emailService.testNewConfig(normalizeConfigPayload({
      email: formData.value.email,
      password: formData.value.password,
      host: formData.value.host,
      port: formData.value.port,
      sslEnabled: formData.value.sslEnabled,
      protocol: formData.value.protocol
    }))
    const data = parseResultPayload<{
      success: boolean
      message: string
      durationMs: number
      messageCount: number
      errorDetail: string
    }>(res)
    if (!data) {
      message.error('测试请求失败')
      return
    }
    testResult.value = data
    showTestResult.value = true
    if (data.success) {
      message.success(`测试成功，耗时 ${data.durationMs}ms`)
    } else {
      message.error(data.message)
    }
  } catch (error) {
    message.error('测试请求失败')
  } finally {
    testingNew.value = false
  }
}

const checkNetwork = async (config: EmailConfig) => {
  checkingNetworkIds.value.push(config.id)
  try {
    const res = await emailService.checkNetwork(config.id)
    const data = parseResultPayload<{
      success: boolean
      message: string
      durationMs: number
      resolvedIp: string
      errorDetail: string
    }>(res)
    if (!data) {
      message.error('网络检测失败')
      return
    }
    if (data.success) {
      message.success(`网络连通，耗时 ${data.durationMs}ms，IP: ${data.resolvedIp || '-'}`)
    } else {
      message.error(data.message || '网络不通')
    }
  } catch (error: any) {
    message.error(error?.response?.data?.message || '网络检测失败')
  } finally {
    checkingNetworkIds.value = checkingNetworkIds.value.filter(id => id !== config.id)
  }
}

const checkNewConfigNetwork = async () => {
  formData.value = normalizeConfigPayload(formData.value) as typeof formData.value
  if (!formData.value.host || !formData.value.port) {
    message.warning('请先填写主机和端口')
    return
  }
  checkingNewNetwork.value = true
  try {
    const res = await emailService.checkNewConfigNetwork(normalizeConfigPayload({
      host: formData.value.host,
      port: formData.value.port,
      protocol: formData.value.protocol
    }))
    const data = parseResultPayload<{
      success: boolean
      message: string
      durationMs: number
      resolvedIp: string
      errorDetail: string
    }>(res)
    if (!data) {
      message.error('网络检测失败')
      return
    }
    if (data.success) {
      message.success(`网络连通，耗时 ${data.durationMs}ms，IP: ${data.resolvedIp || '-'}`)
    } else {
      message.error(data.message || '网络不通')
    }
  } catch (error: any) {
    message.error(error?.response?.data?.message || '网络检测失败')
  } finally {
    checkingNewNetwork.value = false
  }
}

// 切换启用状态
const toggleEnabled = async (config: EmailConfig, enabled: boolean) => {
  try {
    await emailService.updateConfig(normalizeConfigPayload({ ...config, enabled }))
    message.success('更新成功')
    loadConfigs()
  } catch (error) {
    message.error('更新失败')
  }
}

// 切换监听状态
const toggleListener = async (config: EmailConfig) => {
  try {
    if (listenerStatus.value[config.id]) {
      await emailService.stopListener(config.id)
      listenerStatus.value[config.id] = false
      message.success('已停止监听')
    } else {
      await emailService.startListener(config.id)
      listenerStatus.value[config.id] = true
      message.success('已开始监听')
    }
  } catch (error) {
    message.error('操作失败')
  }
}

// 编辑配置
const editConfig = (config: EmailConfig) => {
  editingConfig.value = config
  formData.value = {
    email: trimText(config.email),
    password: '',
    host: trimText(config.host),
    port: config.port,
    sslEnabled: config.sslEnabled,
    protocol: trimText(config.protocol)
  }
  showAddModal.value = true
}

// 保存配置
const saveConfig = async () => {
  formData.value = normalizeConfigPayload(formData.value) as typeof formData.value
  try {
    if (editingConfig.value) {
      await emailService.updateConfig(normalizeConfigPayload({
        id: editingConfig.value.id,
        ...formData.value
      }))
    } else {
      await emailService.createConfig(normalizeConfigPayload(formData.value))
    }
    message.success('保存成功')
    showAddModal.value = false
    loadConfigs()
  } catch (error) {
    message.error('保存失败')
  }
}

// 删除配置
const deleteConfig = async (config: EmailConfig) => {
  try {
    await emailService.deleteConfig(config.id)
    message.success('删除成功')
    loadConfigs()
  } catch (error) {
    message.error('删除失败')
  }
}

onMounted(() => {
  loadConfigs()
})
</script>

<style scoped>
.email-page {
  display: grid;
  gap: 16px;
}

.action-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.email-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.email-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.email-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.email-icon {
  color: var(--primary-color);
}

.email-address {
  font-size: 16px;
  font-weight: 500;
  color: var(--text-primary);
}

.email-host {
  font-size: 12px;
  color: var(--text-secondary);
}

.email-status {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.email-actions {
  display: flex;
  gap: 8px;
}

.template-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}
</style>
