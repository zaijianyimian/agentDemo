<template>
  <UiPage>
    <UiPageHeader
      eyebrow="Mail Listener"
      title="邮件配置"
      subtitle="维护邮箱连接、认证方式、监听窗口和网络检测状态。"
    >
      <template #actions>
        <n-button type="primary" @click="openAddConfig">
          <template #icon><n-icon><AddIcon /></n-icon></template>
          添加邮箱
        </n-button>
        <n-button @click="loadConfigs">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          刷新
        </n-button>
      </template>
    </UiPageHeader>

    <!-- 邮箱列表 -->
    <n-grid :cols="2" :x-gap="16" :y-gap="16" responsive="screen">
      <n-gi v-for="config in configs" :key="config.id">
        <n-card class="email-card" :bordered="false">
          <div class="email-header">
            <div class="email-info">
              <n-icon size="24" class="email-icon"><MailIcon /></n-icon>
              <div>
                <div class="email-address">{{ config.email }}</div>
                <div class="email-host">{{ formatEndpoint(config) }}</div>
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
            <n-tag type="info" size="small">{{ providerLabel(config.provider) }}</n-tag>
            <n-tag type="default" size="small">{{ listenModeLabel(config.listenMode) }}</n-tag>
            <n-tag v-if="statusDetails[config.id]?.fallbackListenMode" type="warning" size="small">
              降级: {{ listenModeLabel(statusDetails[config.id]?.fallbackListenMode) }}
            </n-tag>
            <n-tag v-if="config.authType && config.authType !== 'password'" type="warning" size="small">
              {{ config.authType === 'oauth2_refresh_token' ? 'OAuth2-Refresh' : 'OAuth2-Access' }}
            </n-tag>
            <n-tag type="info" size="small">
              {{ formatListeningWindow(config) }}
            </n-tag>
            <n-tag :type="connectionStatus[config.id]?.success ? 'success' : (connectionStatus[config.id] ? 'error' : 'default')" size="small">
              {{ connectionStatus[config.id]?.message || '未测试' }}
            </n-tag>
            <n-tag v-if="statusDetails[config.id]?.lastError" type="error" size="small">
              {{ statusDetails[config.id]?.lastError }}
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
        <n-form-item label="认证方式" path="authType">
          <n-select v-model:value="formData.authType" :options="authTypeOptions" />
        </n-form-item>
        <n-form-item label="邮箱来源" path="provider">
          <n-select v-model:value="formData.provider" :options="providerOptions" @update:value="onProviderChanged" />
        </n-form-item>
        <n-form-item label="监听模式" path="listenMode">
          <n-select v-model:value="formData.listenMode" :options="listenModeOptions" />
        </n-form-item>
        <n-form-item v-if="formData.authType === 'password'" label="密码/授权码" path="password">
          <n-input
            v-model:value="formData.password"
            type="password"
            :placeholder="editingConfig?.passwordConfigured ? '已保存密码/授权码，留空表示保持不变' : '输入密码或授权码'"
          />
        </n-form-item>
        <n-form-item v-if="formData.authType === 'oauth2_access_token'" label="Access Token" path="oauthAccessToken">
          <n-input
            v-model:value="formData.oauthAccessToken"
            type="password"
            :placeholder="editingConfig?.oauthAccessTokenConfigured ? '已保存 access token，留空表示保持不变' : 'OAuth2 access_token（短期令牌）'"
          />
        </n-form-item>
        <n-form-item v-if="formData.authType === 'oauth2_refresh_token'" label="Client ID" path="oauthClientId">
          <n-input v-model:value="formData.oauthClientId" placeholder="OAuth2 client_id" />
        </n-form-item>
        <n-form-item v-if="formData.authType === 'oauth2_refresh_token'" label="Client Secret" path="oauthClientSecret">
          <n-input
            v-model:value="formData.oauthClientSecret"
            type="password"
            :placeholder="editingConfig?.oauthClientSecretConfigured ? '已保存 client_secret，留空表示保持不变' : 'OAuth2 client_secret（编辑时可留空以保持不变）'"
          />
        </n-form-item>
        <n-form-item v-if="formData.authType === 'oauth2_refresh_token'" label="Refresh Token" path="oauthRefreshToken">
          <n-input
            v-model:value="formData.oauthRefreshToken"
            type="password"
            :placeholder="editingConfig?.oauthRefreshTokenConfigured ? '已保存 refresh_token，留空表示保持不变' : 'OAuth2 refresh_token（编辑时可留空以保持不变）'"
          />
        </n-form-item>
        <n-form-item v-if="formData.authType === 'oauth2_refresh_token'" label="Token端点" path="oauthTokenEndpoint">
          <n-input
            v-model:value="formData.oauthTokenEndpoint"
            placeholder="可选，留空自动按 Gmail/Outlook 推断"
          />
        </n-form-item>
        <n-form-item v-if="formData.authType === 'oauth2_refresh_token'" label="Scope" path="oauthScope">
          <n-input
            v-model:value="formData.oauthScope"
            placeholder="可选，留空使用默认scope"
          />
        </n-form-item>
        <n-form-item v-if="requiresHost(formData)" label="邮件服务器" path="host">
          <n-input v-model:value="formData.host" placeholder="imap.example.com" />
        </n-form-item>
        <n-form-item v-if="requiresHost(formData)" label="服务器端口" path="port">
          <n-input-number v-model:value="formData.port" :min="1" :max="65535" />
        </n-form-item>
        <n-form-item v-if="requiresHost(formData)" label="启用SSL" path="sslEnabled">
          <n-switch v-model:value="formData.sslEnabled" />
        </n-form-item>
        <n-form-item label="监听文件夹" path="folder">
          <n-input v-model:value="formData.folder" placeholder="INBOX" />
        </n-form-item>
        <n-form-item label="轮询间隔" path="pollInterval">
          <n-input-number v-model:value="formData.pollInterval" :min="600" :max="3600">
            <template #suffix>秒</template>
          </n-input-number>
        </n-form-item>
        <n-form-item label="Provider配置" path="providerSettings">
          <n-input
            v-model:value="formData.providerSettings"
            type="textarea"
            :autosize="{ minRows: 2, maxRows: 6 }"
            placeholder='JSON，例如 {"topicName":"projects/x/topics/gmail","notificationUrl":"https://..."}'
          />
        </n-form-item>
        <n-form-item label="监听时间段">
          <n-switch v-model:value="formData.listenWindowEnabled">
            <template #checked>指定时间段</template>
            <template #unchecked>全天监听</template>
          </n-switch>
        </n-form-item>
        <n-form-item v-if="formData.listenWindowEnabled" label="开始时间" path="listenStartTime">
          <n-time-picker
            v-model:formatted-value="formData.listenStartTime"
            value-format="HH:mm:ss"
            format="HH:mm"
            clearable
          />
        </n-form-item>
        <n-form-item v-if="formData.listenWindowEnabled" label="结束时间" path="listenEndTime">
          <n-time-picker
            v-model:formatted-value="formData.listenEndTime"
            value-format="HH:mm:ss"
            format="HH:mm"
            clearable
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button v-if="requiresHost(formData)" @click="checkNewConfigNetwork" :loading="checkingNewNetwork">
            网络检测
          </n-button>
          <n-button v-if="requiresHost(formData)" @click="testNewConfig" :loading="testingNew">
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
  </UiPage>
</template>

<script setup lang="ts">
/**
 * 邮件监听配置页面：维护邮箱账号、认证方式、监听模式、测试连接与网络检测。
 */
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
  NSelect,
  NTimePicker,
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
import { emailService } from '@/services/api/email'
import type { EmailConfig } from '@/types'
import { UiPage, UiPageHeader } from '@/components/ui'

const message = useMessage()
const configs = ref<EmailConfig[]>([])
const listenerStatus = ref<Record<number, boolean>>({})
const statusDetails = ref<Record<number, any>>({})
const connectionStatus = ref<Record<number, { success: boolean; message: string }>>({})
const testingIds = ref<number[]>([])
const checkingNetworkIds = ref<number[]>([])
const showAddModal = ref(false)
const editingConfig = ref<EmailConfig | null>(null)
const formData = ref({
  email: '',
  password: '',
  authType: 'password' as 'password' | 'oauth2_access_token' | 'oauth2_refresh_token',
  oauthClientId: '',
  oauthClientSecret: '',
  oauthRefreshToken: '',
  oauthAccessToken: '',
  oauthTokenEndpoint: '',
  oauthScope: '',
  provider: 'GENERIC_IMAP' as NonNullable<EmailConfig['provider']>,
  listenMode: 'POLLING' as NonNullable<EmailConfig['listenMode']>,
  fallbackListenMode: null as EmailConfig['fallbackListenMode'],
  providerSettings: '',
  host: '',
  port: 993,
  sslEnabled: true,
  protocol: 'imap',
  folder: 'INBOX',
  pollInterval: 600,
  listenWindowEnabled: false,
  listenStartTime: null as string | null,
  listenEndTime: null as string | null
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
const emailTemplates = ref<any[]>([])

const templateColumns = [
  { title: '邮箱', key: 'name' },
  { title: 'Provider', key: 'provider', render: (row: any) => providerLabel(row.provider) },
  { title: '监听', key: 'listenMode', render: (row: any) => listenModeLabel(row.listenMode) },
  { title: '服务器', key: 'host', render: (row: any) => row.host || '-' },
  { title: '端口', key: 'port' },
  { title: 'SSL', key: 'sslEnabled', render: (row: any) => row.sslEnabled ? '是' : '否' }
]

const authTypeOptions = [
  { label: '密码/授权码', value: 'password' },
  { label: 'OAuth2 Access Token', value: 'oauth2_access_token' },
  { label: 'OAuth2 Refresh Token', value: 'oauth2_refresh_token' }
]

const providerOptions = [
  { label: '通用 IMAP', value: 'GENERIC_IMAP' },
  { label: '通用 POP3', value: 'GENERIC_POP3' },
  { label: 'Gmail API', value: 'GMAIL_API' },
  { label: 'Microsoft Graph', value: 'MICROSOFT_GRAPH' }
]

const listenModeOptions = [
  { label: '轮询', value: 'POLLING' },
  { label: 'IMAP IDLE', value: 'IMAP_IDLE' },
  { label: 'Webhook', value: 'WEBHOOK' },
  { label: 'Delta Sync', value: 'DELTA_SYNC' }
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
  provider: payload.provider == null ? payload.provider : trimText(payload.provider) as any,
  listenMode: payload.listenMode == null ? payload.listenMode : trimText(payload.listenMode) as any,
  fallbackListenMode: payload.fallbackListenMode == null ? payload.fallbackListenMode : trimText(payload.fallbackListenMode) as any,
  providerSettings: payload.providerSettings == null ? payload.providerSettings : trimText(payload.providerSettings),
  authType: payload.authType == null ? 'password' : trimText(payload.authType) as any,
  oauthClientId: payload.oauthClientId == null ? payload.oauthClientId : trimText(payload.oauthClientId),
  oauthClientSecret: payload.oauthClientSecret == null ? payload.oauthClientSecret : trimText(payload.oauthClientSecret),
  oauthRefreshToken: payload.oauthRefreshToken == null ? payload.oauthRefreshToken : trimText(payload.oauthRefreshToken),
  oauthAccessToken: payload.oauthAccessToken == null ? payload.oauthAccessToken : trimText(payload.oauthAccessToken),
  oauthTokenEndpoint: payload.oauthTokenEndpoint == null ? payload.oauthTokenEndpoint : trimText(payload.oauthTokenEndpoint),
  oauthScope: payload.oauthScope == null ? payload.oauthScope : trimText(payload.oauthScope),
  folder: payload.folder == null ? payload.folder : trimText(payload.folder),
  listenStartTime: payload.listenStartTime || null,
  listenEndTime: payload.listenEndTime || null,
  remark: payload.remark == null ? payload.remark : trimText(payload.remark)
})

const buildSavePayload = () => {
  const payload = normalizeConfigPayload({
    ...formData.value,
    folder: formData.value.folder || 'INBOX',
    pollInterval: formData.value.pollInterval || 600,
    provider: formData.value.provider,
    listenMode: formData.value.listenMode,
    fallbackListenMode: formData.value.listenMode === 'IMAP_IDLE' ? 'POLLING' : formData.value.fallbackListenMode,
    providerSettings: formData.value.providerSettings,
    listenStartTime: formData.value.listenWindowEnabled ? formData.value.listenStartTime : null,
    listenEndTime: formData.value.listenWindowEnabled ? formData.value.listenEndTime : null
  }) as Partial<EmailConfig>
  delete (payload as any).listenWindowEnabled
  return payload
}

const requiresHost = (config: Partial<EmailConfig> | typeof formData.value): boolean => {
  const provider = (config.provider || 'GENERIC_IMAP').toString()
  return provider === 'GENERIC_IMAP' || provider === 'GENERIC_POP3'
}

const providerLabel = (provider?: string | null): string => {
  const option = providerOptions.find(item => item.value === provider)
  return option?.label || provider || '通用 IMAP'
}

const listenModeLabel = (mode?: string | null): string => {
  const option = listenModeOptions.find(item => item.value === mode)
  return option?.label || mode || '轮询'
}

const formatEndpoint = (config: EmailConfig): string => {
  if (!requiresHost(config)) return providerLabel(config.provider)
  return `${config.host || '-'}:${config.port || '-'}`
}

const onProviderChanged = (value: string) => {
  if (value === 'GENERIC_POP3') {
    formData.value.protocol = 'pop3'
    formData.value.listenMode = 'POLLING'
    formData.value.port = 995
  } else if (value === 'GENERIC_IMAP') {
    formData.value.protocol = 'imap'
    formData.value.listenMode = formData.value.listenMode === 'WEBHOOK' || formData.value.listenMode === 'DELTA_SYNC' ? 'POLLING' : formData.value.listenMode
    formData.value.port = formData.value.port || 993
  } else if (value === 'GMAIL_API' || value === 'MICROSOFT_GRAPH') {
    formData.value.protocol = 'imap'
    formData.value.listenMode = 'WEBHOOK'
    formData.value.authType = 'oauth2_refresh_token'
    formData.value.host = ''
    formData.value.port = 0
  }
}

const formatListeningWindow = (config: EmailConfig): string => {
  if (!config.listenStartTime || !config.listenEndTime) return '全天监听'
  return `${config.listenStartTime.slice(0, 5)}-${config.listenEndTime.slice(0, 5)}`
}

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

const resetFormData = () => {
  editingConfig.value = null
  formData.value = {
    email: '',
    password: '',
    authType: 'password',
    oauthClientId: '',
    oauthClientSecret: '',
    oauthRefreshToken: '',
    oauthAccessToken: '',
    oauthTokenEndpoint: '',
    oauthScope: '',
    provider: 'GENERIC_IMAP',
    listenMode: 'POLLING',
    fallbackListenMode: null,
    providerSettings: '',
    host: '',
    port: 993,
    sslEnabled: true,
    protocol: 'imap',
    folder: 'INBOX',
    pollInterval: 600,
    listenWindowEnabled: false,
    listenStartTime: null,
    listenEndTime: null
  }
}

const openAddConfig = () => {
  resetFormData()
  showAddModal.value = true
}

// 加载配置
const loadConfigs = async () => {
  try {
    const res = await emailService.listConfigs()
    configs.value = parseConfigList(res).map(item => {
      const config = normalizeConfigPayload(item) as EmailConfig
      if (!config.authType) {
        config.authType = 'password'
      }
      return config
    })
    // 加载监听状态
    const statusRes = await emailService.getListenerStatus()
    const statusPayload = parseObjectPayload(statusRes)
    listenerStatus.value = {}
    statusDetails.value = {}
    for (const [key, value] of Object.entries(statusPayload)) {
      const item = value as any
      statusDetails.value[Number(key)] = item
      listenerStatus.value[Number(key)] = item === '已连接' || item?.connected === true || item?.status === '已连接' || item?.status === 'RUNNING' || item?.status === 'FALLBACK'
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

/** 对尚未保存的表单配置直接调用测试连接接口。 */
const testNewConfig = async () => {
  formData.value = normalizeConfigPayload(formData.value) as typeof formData.value
  if (!formData.value.email || (requiresHost(formData.value) && !formData.value.host)) {
    message.warning('请填写邮箱地址和服务器')
    return
  }
  if (formData.value.authType === 'password' && !formData.value.password) {
    message.warning('请填写密码或授权码')
    return
  }
  if (formData.value.authType === 'oauth2_access_token' && !formData.value.oauthAccessToken) {
    message.warning('请填写 OAuth2 access token')
    return
  }
  if (formData.value.authType === 'oauth2_refresh_token' && (!formData.value.oauthClientId || !formData.value.oauthRefreshToken)) {
    message.warning('请填写 OAuth2 client_id 和 refresh_token')
    return
  }
  testingNew.value = true
  try {
    const res = await emailService.testNewConfig(normalizeConfigPayload({
      email: formData.value.email,
      password: formData.value.password,
      authType: formData.value.authType,
      oauthClientId: formData.value.oauthClientId,
      oauthClientSecret: formData.value.oauthClientSecret,
      oauthRefreshToken: formData.value.oauthRefreshToken,
      oauthAccessToken: formData.value.oauthAccessToken,
      oauthTokenEndpoint: formData.value.oauthTokenEndpoint,
      oauthScope: formData.value.oauthScope,
      provider: formData.value.provider,
      listenMode: formData.value.listenMode,
      fallbackListenMode: formData.value.listenMode === 'IMAP_IDLE' ? 'POLLING' : formData.value.fallbackListenMode,
      providerSettings: formData.value.providerSettings,
      host: formData.value.host,
      port: formData.value.port,
      sslEnabled: formData.value.sslEnabled,
      protocol: formData.value.protocol,
      folder: formData.value.folder || 'INBOX'
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

/** 检测已保存邮箱到邮件服务器的网络连通性（DNS+端口）。 */
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

/** 检测新配置（未保存）的网络可达性，仅对需要 host 的提供商有效。 */
const checkNewConfigNetwork = async () => {
  formData.value = normalizeConfigPayload(formData.value) as typeof formData.value
  if (!requiresHost(formData.value)) {
    message.info('API provider 不需要邮件服务器网络检测')
    return
  }
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

const loadTemplates = async () => {
  try {
    const res = await emailService.getTemplates()
    const payload = Array.isArray(res) ? res : (Array.isArray((res as any)?.data) ? (res as any).data : [])
    emailTemplates.value = payload
  } catch {
    emailTemplates.value = []
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
    const err = error as any
    message.error(err?.response?.data || err?.response?.data?.message || '操作失败')
  }
}

// 编辑配置
const editConfig = (config: EmailConfig) => {
  editingConfig.value = config
  formData.value = {
    email: trimText(config.email),
    password: '',
    authType: (config.authType || 'password') as 'password' | 'oauth2_access_token' | 'oauth2_refresh_token',
    oauthClientId: trimText(config.oauthClientId),
    oauthClientSecret: '',
    oauthRefreshToken: '',
    oauthAccessToken: '',
    oauthTokenEndpoint: trimText(config.oauthTokenEndpoint),
    oauthScope: trimText(config.oauthScope),
    provider: config.provider || 'GENERIC_IMAP',
    listenMode: config.listenMode || 'POLLING',
    fallbackListenMode: config.fallbackListenMode || null,
    providerSettings: trimText(config.providerSettings),
    host: trimText(config.host),
    port: config.port,
    sslEnabled: config.sslEnabled,
    protocol: trimText(config.protocol || 'imap'),
    folder: trimText(config.folder || 'INBOX'),
    pollInterval: Math.max(600, config.pollInterval || 600),
    listenWindowEnabled: Boolean(config.listenStartTime && config.listenEndTime),
    listenStartTime: config.listenStartTime || null,
    listenEndTime: config.listenEndTime || null
  }
  showAddModal.value = true
}

// 保存配置
const saveConfig = async () => {
  formData.value = normalizeConfigPayload(formData.value) as typeof formData.value
  try {
    if (formData.value.listenWindowEnabled && (!formData.value.listenStartTime || !formData.value.listenEndTime)) {
      message.warning('请选择完整的监听时间段')
      return
    }
    const payload = buildSavePayload()
    if (editingConfig.value) {
      await emailService.updateConfig(normalizeConfigPayload({
        id: editingConfig.value.id,
        ...payload
      }))
    } else {
      await emailService.createConfig(payload)
    }
    message.success('保存成功')
    showAddModal.value = false
    loadConfigs()
  } catch (error) {
    const err = error as any
    message.error(err?.response?.data || err?.response?.data?.message || '保存失败')
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
  loadTemplates()
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
