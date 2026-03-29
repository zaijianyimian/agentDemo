<template>
  <div class="settings-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-content">
        <div class="header-icon">
          <n-icon size="28"><SettingsIcon /></n-icon>
        </div>
        <div class="header-text">
          <h1 class="header-title">系统设置</h1>
          <p class="header-subtitle">配置系统参数、模型参数、向量数据库等选项</p>
        </div>
      </div>
    </div>

    <!-- 设置主体 -->
    <div class="settings-container">
      <!-- 左侧导航 -->
      <div class="settings-nav">
        <div
          v-for="item in navItems"
          :key="item.key"
          :class="['nav-item', { active: activeSection === item.key }]"
          @click="activeSection = item.key"
        >
          <n-icon size="20"><component :is="item.icon" /></n-icon>
          <span>{{ item.label }}</span>
          <n-badge v-if="item.badge" :value="item.badge" type="info" />
        </div>
      </div>

      <!-- 右侧内容 -->
      <div class="settings-content">
        <!-- 系统设置 -->
        <div v-show="activeSection === 'system'" class="section-wrapper">
          <div class="section-header">
            <h2 class="section-title">
              <n-icon size="20"><GlobeIcon /></n-icon>
              系统设置
            </h2>
            <p class="section-desc">配置系统基本信息和外观</p>
          </div>

          <div class="settings-grid">
            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><GlobeIcon /></n-icon>
                <div>
                  <h3>站点名称</h3>
                  <p>显示在浏览器标题栏和页面顶部</p>
                </div>
              </div>
              <n-input v-model:value="systemSettings.site_name" placeholder="AI Agent" size="large" />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><ImageIcon /></n-icon>
                <div>
                  <h3>站点 Logo</h3>
                  <p>自定义系统 Logo 图片</p>
                </div>
              </div>
              <div class="logo-upload-area">
                <!-- Logo预览 -->
                <div class="logo-preview" v-if="systemSettings.site_logo">
                  <img :src="systemSettings.site_logo" alt="Logo" />
                  <n-button quaternary circle size="small" class="remove-btn" @click="removeLogo">
                    <template #icon><n-icon><CloseIcon /></n-icon></template>
                  </n-button>
                </div>
                <!-- 上传区域 -->
                <n-upload
                  v-else
                  accept="image/*"
                  :show-file-list="false"
                  :custom-request="handleLogoUpload"
                >
                  <n-button class="upload-btn">
                    <template #icon><n-icon><CloudUploadIcon /></n-icon></template>
                    上传 Logo
                  </n-button>
                </n-upload>
                <p class="upload-tip">支持 JPG、PNG、SVG 格式，建议尺寸 40x40</p>
              </div>
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><MoonIcon /></n-icon>
                <div>
                  <h3>默认主题</h3>
                  <p>选择系统默认的显示主题</p>
                </div>
              </div>
              <div class="theme-options">
                <div
                  :class="['theme-option', { active: systemSettings.default_theme === 'light' }]"
                  @click="selectTheme('light')"
                >
                  <div class="theme-preview light"></div>
                  <span>浅色</span>
                </div>
                <div
                  :class="['theme-option', { active: systemSettings.default_theme === 'dark' }]"
                  @click="selectTheme('dark')"
                >
                  <div class="theme-preview dark"></div>
                  <span>深色</span>
                </div>
                <div
                  :class="['theme-option', { active: systemSettings.default_theme === 'auto' }]"
                  @click="selectTheme('auto')"
                >
                  <div class="theme-preview auto"></div>
                  <span>自动</span>
                </div>
              </div>
            </div>
          </div>

          <div class="section-actions">
            <n-button type="primary" size="large" @click="saveSystemSettings" :loading="saving">
              <template #icon><n-icon><SaveIcon /></n-icon></template>
              保存设置
            </n-button>
          </div>
        </div>

        <!-- 模型参数 -->
        <div v-show="activeSection === 'model'" class="section-wrapper">
          <div class="section-header">
            <h2 class="section-title">
              <n-icon size="20"><CubeIcon /></n-icon>
              模型参数
            </h2>
            <p class="section-desc">配置 AI 模型的默认参数，影响对话质量和风格</p>
          </div>

          <div class="settings-grid">
            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><ThermometerIcon /></n-icon>
                <div>
                  <h3>温度 (Temperature)</h3>
                  <p>控制输出的随机性，值越高越有创造性</p>
                </div>
              </div>
              <div class="slider-wrapper">
                <n-slider
                  v-model:value="modelSettings.temperature"
                  :min="0"
                  :max="2"
                  :step="0.1"
                  :tooltip="true"
                />
                <div class="slider-labels">
                  <span>精确 (0)</span>
                  <span>均衡 (1)</span>
                  <span>创意 (2)</span>
                </div>
              </div>
              <n-input-number
                v-model:value="modelSettings.temperature"
                :min="0"
                :max="2"
                :step="0.1"
                size="small"
                style="width: 100px; margin-top: 8px"
              />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><DocumentIcon /></n-icon>
                <div>
                  <h3>最大 Tokens</h3>
                  <p>限制模型单次输出的最大长度</p>
                </div>
              </div>
              <n-input-number
                v-model:value="modelSettings.maxTokens"
                :min="256"
                :max="32768"
                :step="256"
                size="large"
                style="width: 100%"
              />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><ChatbubbleIcon /></n-icon>
                <div>
                  <h3>上下文记忆</h3>
                  <p>对话时保留的历史消息数量</p>
                </div>
              </div>
              <n-input-number
                v-model:value="modelSettings.memorySize"
                :min="5"
                :max="50"
                :step="1"
                size="large"
                style="width: 100%"
              />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><PulseIcon /></n-icon>
                <div>
                  <h3>Top P (核采样)</h3>
                  <p>控制输出的多样性，建议与温度二选一</p>
                </div>
              </div>
              <div class="slider-wrapper">
                <n-slider
                  v-model:value="modelSettings.topP"
                  :min="0"
                  :max="1"
                  :step="0.05"
                  :tooltip="true"
                />
                <div class="slider-labels">
                  <span>保守 (0)</span>
                  <span>适中 (0.5)</span>
                  <span>开放 (1)</span>
                </div>
              </div>
            </div>

            <div class="setting-card full-width">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><CreateIcon /></n-icon>
                <div>
                  <h3>系统提示词</h3>
                  <p>设置 AI 助手的默认角色和行为规范</p>
                </div>
              </div>
              <n-input
                v-model:value="modelSettings.systemPrompt"
                type="textarea"
                placeholder="你是一个有帮助的AI助手..."
                :rows="4"
                size="large"
              />
            </div>
          </div>

          <div class="section-actions">
            <n-button type="primary" size="large" @click="saveModelSettings" :loading="saving">
              <template #icon><n-icon><SaveIcon /></n-icon></template>
              保存设置
            </n-button>
          </div>
        </div>

        <!-- 向量数据库 -->
        <div v-show="activeSection === 'qdrant'" class="section-wrapper">
          <div class="section-header">
            <h2 class="section-title">
              <n-icon size="20"><ServerIcon /></n-icon>
              向量数据库
            </h2>
            <p class="section-desc">配置 Qdrant 向量数据库连接，用于 AI 记忆和知识库存储</p>
          </div>

          <div class="settings-grid">
            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><ServerIcon /></n-icon>
                <div>
                  <h3>服务地址</h3>
                  <p>Qdrant 服务器地址</p>
                </div>
              </div>
              <n-input v-model:value="qdrantSettings.host" placeholder="localhost" size="large" />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><AnalyticsIcon /></n-icon>
                <div>
                  <h3>服务端口</h3>
                  <p>Qdrant gRPC 端口</p>
                </div>
              </div>
              <n-input-number
                :value="Number(qdrantSettings.port)"
                @update:value="(val: number | null) => qdrantSettings.port = String(val || 6334)"
                :min="1"
                :max="65535"
                size="large"
                style="width: 100%"
              />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><FolderIcon /></n-icon>
                <div>
                  <h3>集合名称</h3>
                  <p>向量数据存储的集合名称</p>
                </div>
              </div>
              <n-input v-model:value="qdrantSettings.collection_name" placeholder="agent_memory" size="large" />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><SearchIcon /></n-icon>
                <div>
                  <h3>Top K</h3>
                  <p>检索时返回的最相似结果数量</p>
                </div>
              </div>
              <n-input-number
                :value="Number(qdrantSettings.top_k)"
                @update:value="(val: number | null) => qdrantSettings.top_k = String(val || 5)"
                :min="1"
                :max="100"
                size="large"
                style="width: 100%"
              />
            </div>

            <div class="setting-card full-width">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><AnalyticsIcon /></n-icon>
                <div>
                  <h3>最小相似度阈值</h3>
                  <p>低于此阈值的结果将被过滤</p>
                </div>
              </div>
              <div class="slider-wrapper">
                <n-slider
                  :value="Number(qdrantSettings.min_score)"
                  @update:value="(val: number) => qdrantSettings.min_score = String(val)"
                  :min="0"
                  :max="1"
                  :step="0.05"
                  :tooltip="true"
                />
                <div class="slider-labels">
                  <span>宽松 (0)</span>
                  <span>适中 (0.5)</span>
                  <span>严格 (1)</span>
                </div>
              </div>
            </div>
          </div>

          <div class="section-actions">
            <n-button @click="testQdrantConnection" :loading="testing">
              <template #icon><n-icon><PlayIcon /></n-icon></template>
              测试连接
            </n-button>
            <n-button type="primary" size="large" @click="saveQdrantSettings" :loading="saving">
              <template #icon><n-icon><SaveIcon /></n-icon></template>
              保存设置
            </n-button>
          </div>
        </div>

        <!-- 网络搜索 -->
        <div v-show="activeSection === 'search'" class="section-wrapper">
          <div class="section-header">
            <h2 class="section-title">
              <n-icon size="20"><SearchIcon /></n-icon>
              网络搜索
            </h2>
            <p class="section-desc">配置搜索引擎 API，让 AI 能够获取实时网络信息</p>
          </div>

          <div class="settings-grid">
            <div class="setting-card full-width">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><PowerIcon /></n-icon>
                <div>
                  <h3>启用搜索功能</h3>
                  <p>开启后 AI 将在需要时自动搜索网络信息</p>
                </div>
              </div>
              <n-switch
                v-model:value="searchSettings.enabled"
                checked-value="true"
                unchecked-value="false"
                size="large"
              />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><GlobeIcon /></n-icon>
                <div>
                  <h3>搜索引擎</h3>
                  <p>选择使用的搜索服务提供商</p>
                </div>
              </div>
              <n-select
                v-model:value="searchSettings.engine"
                :options="searchEngineOptions"
                size="large"
              />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><KeyIcon /></n-icon>
                <div>
                  <h3>API 密钥</h3>
                  <p>搜索引擎服务的 API 密钥</p>
                </div>
              </div>
              <n-input
                v-model:value="searchSettings.api_key"
                type="password"
                placeholder="输入 API 密钥"
                show-password-on="click"
                size="large"
              />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><ListIcon /></n-icon>
                <div>
                  <h3>最大结果数</h3>
                  <p>每次搜索返回的最大结果数量</p>
                </div>
              </div>
              <n-input-number
                :value="Number(searchSettings.max_results)"
                @update:value="(val: number | null) => searchSettings.max_results = String(val || 3)"
                :min="1"
                :max="20"
                size="large"
                style="width: 100%"
              />
            </div>
          </div>

          <div class="section-actions">
            <n-button @click="testSearchConnection" :loading="testing">
              <template #icon><n-icon><PlayIcon /></n-icon></template>
              测试搜索
            </n-button>
            <n-button type="primary" size="large" @click="saveSearchSettings" :loading="saving">
              <template #icon><n-icon><SaveIcon /></n-icon></template>
              保存设置
            </n-button>
          </div>
        </div>

        <!-- 日程管理 -->
        <div v-show="activeSection === 'schedule'" class="section-wrapper">
          <div class="section-header">
            <h2 class="section-title">
              <n-icon size="20"><CalendarIcon /></n-icon>
              日程管理
            </h2>
            <p class="section-desc">配置日程提醒和邮件通知功能</p>
          </div>

          <div class="settings-grid">
            <div class="setting-card full-width">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><PowerIcon /></n-icon>
                <div>
                  <h3>启用日程功能</h3>
                  <p>开启日程管理和提醒功能</p>
                </div>
              </div>
              <n-switch
                v-model:value="scheduleSettings.enabled"
                checked-value="true"
                unchecked-value="false"
                size="large"
              />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><FolderIcon /></n-icon>
                <div>
                  <h3>存储路径</h3>
                  <p>日程数据的存储目录</p>
                </div>
              </div>
              <n-input v-model:value="scheduleSettings.storage_path" placeholder="./data/schedules" size="large" />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><MailIcon /></n-icon>
                <div>
                  <h3>接收邮箱</h3>
                  <p>用于接收日程提醒的邮箱地址</p>
                </div>
              </div>
              <n-input v-model:value="scheduleSettings.user_email" placeholder="your@email.com" size="large" />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><MoonIcon /></n-icon>
                <div>
                  <h3>每日汇总时间</h3>
                  <p>发送每日日程汇总的时间 (Cron)</p>
                </div>
              </div>
              <n-input v-model:value="scheduleSettings.daily_summary_cron" placeholder="0 0 20 * * ?" size="large" />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><SunnyIcon /></n-icon>
                <div>
                  <h3>早晨提醒时间</h3>
                  <p>发送早晨日程提醒的时间 (Cron)</p>
                </div>
              </div>
              <n-input v-model:value="scheduleSettings.morning_reminder_cron" placeholder="0 0 8 * * ?" size="large" />
            </div>
          </div>

          <div class="section-actions">
            <n-button type="primary" size="large" @click="saveScheduleSettings" :loading="saving">
              <template #icon><n-icon><SaveIcon /></n-icon></template>
              保存设置
            </n-button>
          </div>
        </div>

        <!-- 文件上传 -->
        <div v-show="activeSection === 'file'" class="section-wrapper">
          <div class="section-header">
            <h2 class="section-title">
              <n-icon size="20"><FolderIcon /></n-icon>
              文件上传
            </h2>
            <p class="section-desc">配置文件上传和存储相关选项</p>
          </div>

          <div class="settings-grid">
            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><FolderIcon /></n-icon>
                <div>
                  <h3>上传目录</h3>
                  <p>文件存储的根目录</p>
                </div>
              </div>
              <n-input v-model:value="fileSettings.upload_dir" placeholder="./data/documents" size="large" />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><DocumentIcon /></n-icon>
                <div>
                  <h3>允许的文件类型</h3>
                  <p>用逗号分隔的文件扩展名</p>
                </div>
              </div>
              <n-input v-model:value="fileSettings.allowed_types" placeholder="txt,md,pdf,doc,docx" size="large" />
            </div>

            <div class="setting-card">
              <div class="setting-card-header">
                <n-icon size="24" class="setting-icon"><CloudUploadIcon /></n-icon>
                <div>
                  <h3>最大文件大小</h3>
                  <p>单个文件的最大上传限制</p>
                </div>
              </div>
              <n-input v-model:value="fileSettings.max_file_size" placeholder="10MB" size="large" />
            </div>
          </div>

          <div class="section-actions">
            <n-button type="primary" size="large" @click="saveFileSettings" :loading="saving">
              <template #icon><n-icon><SaveIcon /></n-icon></template>
              保存设置
            </n-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  NInput,
  NInputNumber,
  NSelect,
  NSwitch,
  NSlider,
  NButton,
  NIcon,
  NBadge,
  NUpload,
  useMessage
} from 'naive-ui'
import {
  SettingsOutline as SettingsIcon,
  GlobeOutline as GlobeIcon,
  ImageOutline as ImageIcon,
  MoonOutline as MoonIcon,
  SunnyOutline as SunnyIcon,
  SaveOutline as SaveIcon,
  CubeOutline as CubeIcon,
  ThermometerOutline as ThermometerIcon,
  DocumentTextOutline as DocumentIcon,
  ChatbubblesOutline as ChatbubbleIcon,
  AnalyticsOutline as AnalyticsIcon,
  CreateOutline as CreateIcon,
  ServerOutline as ServerIcon,
  FolderOutline as FolderIcon,
  SearchOutline as SearchIcon,
  KeyOutline as KeyIcon,
  ListOutline as ListIcon,
  PlayOutline as PlayIcon,
  PowerOutline as PowerIcon,
  MailOutline as MailIcon,
  CalendarOutline as CalendarIcon,
  CloudUploadOutline as CloudUploadIcon,
  PulseOutline as PulseIcon,
  CloseOutline as CloseIcon
} from '@vicons/ionicons5'
import { embeddingService, searchService, settingsService } from '@/services/api'
import { useThemeStore } from '@/stores/theme'

const message = useMessage()
const themeStore = useThemeStore()
const activeSection = ref('system')
const saving = ref(false)
const testing = ref(false)

// 导航项
const navItems = [
  { key: 'system', label: '系统设置', icon: GlobeIcon },
  { key: 'model', label: '模型参数', icon: CubeIcon, badge: 'New' },
  { key: 'qdrant', label: '向量数据库', icon: ServerIcon },
  { key: 'search', label: '网络搜索', icon: SearchIcon },
  { key: 'schedule', label: '日程管理', icon: CalendarIcon },
  { key: 'file', label: '文件上传', icon: FolderIcon }
]

// 各类设置
const systemSettings = ref<Record<string, string>>({
  site_name: 'AI Agent',
  site_logo: '',
  default_theme: 'light'
})

const modelSettings = ref({
  temperature: 0.7,
  maxTokens: 4096,
  topP: 0.9,
  memorySize: 20,
  systemPrompt: '你是一个有帮助的AI助手，请用简洁、准确的语言回答问题。'
})

const qdrantSettings = ref<Record<string, string>>({
  host: 'localhost',
  port: '6334',
  collection_name: 'agent_memory',
  top_k: '5',
  min_score: '0.5'
})

const searchSettings = ref<Record<string, string>>({
  enabled: 'true',
  engine: 'serper',
  api_key: '',
  max_results: '3'
})

const scheduleSettings = ref<Record<string, string>>({
  enabled: 'true',
  storage_path: './data/schedules',
  user_email: '',
  daily_summary_cron: '0 0 20 * * ?',
  morning_reminder_cron: '0 0 8 * * ?'
})

const fileSettings = ref<Record<string, string>>({
  upload_dir: './data/documents',
  allowed_types: 'txt,md,pdf,doc,docx',
  max_file_size: '10MB'
})

// 搜索引擎选项
const searchEngineOptions = [
  { label: 'Serper (推荐)', value: 'serper' },
  { label: 'Tavily', value: 'tavily' },
  { label: 'Bing', value: 'bing' }
]

// 加载所有设置
const loadAllSettings = async () => {
  try {
    const res = await settingsService.getAll()
    if (res.success && res.data) {
      const data = res.data

      if (data.system) {
        systemSettings.value = { ...systemSettings.value, ...data.system }
        // 同步主题设置
        if (data.system.default_theme) {
          themeStore.setTheme(data.system.default_theme as 'light' | 'dark' | 'auto')
        }
      }
      if (data.model) {
        modelSettings.value = { ...modelSettings.value, ...data.model }
      }
      if (data.qdrant) {
        qdrantSettings.value = { ...qdrantSettings.value, ...data.qdrant }
      }
      if (data.search) {
        searchSettings.value = { ...searchSettings.value, ...data.search }
      }
      if (data.schedule) {
        scheduleSettings.value = { ...scheduleSettings.value, ...data.schedule }
      }
      if (data.file) {
        fileSettings.value = { ...fileSettings.value, ...data.file }
      }
    }
  } catch (error: any) {
    console.error('加载设置失败', error)
    if (error.response?.status === 500) {
      message.warning('请先执行数据库建表SQL: src/main/resources/sql/system_settings.sql')
    }
  }
}

// 选择主题 - 立即应用
const selectTheme = (theme: 'light' | 'dark' | 'auto') => {
  systemSettings.value.default_theme = theme
  themeStore.setTheme(theme)
}

// Logo上传处理
const handleLogoUpload = async ({ file }: { file: { file: File | null } }) => {
  if (!file.file) return
  try {
    const res = await settingsService.uploadImage(file.file)
    const uploadData = res.data as any
    const logoUrl = typeof uploadData === 'string' ? uploadData : uploadData?.url
    if (res.success && logoUrl) {
      systemSettings.value.site_logo = logoUrl
      message.success('Logo上传成功')
    } else {
      message.error(res.message || '上传失败')
    }
  } catch (error: any) {
    message.error('上传失败: ' + (error.response?.data?.message || error.message))
  }
}

// 删除Logo
const removeLogo = () => {
  systemSettings.value.site_logo = ''
}

// 保存系统设置
const saveSystemSettings = async () => {
  saving.value = true
  try {
    await settingsService.updateSystem(systemSettings.value)
    message.success('保存成功')
  } catch (error) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 保存模型设置
const saveModelSettings = async () => {
  saving.value = true
  try {
    await settingsService.updateModel(modelSettings.value)
    message.success('保存成功')
  } catch (error) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 保存向量数据库设置
const saveQdrantSettings = async () => {
  saving.value = true
  try {
    await settingsService.updateQdrant(qdrantSettings.value)
    message.success('保存成功')
  } catch (error) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 测试 Qdrant 连接
const testQdrantConnection = async () => {
  testing.value = true
  try {
    const res = await embeddingService.test()
    if (res.success) {
      message.success('向量数据库连接正常')
    } else {
      message.error(res.message || '连接失败')
    }
  } catch (error: any) {
    message.error('连接失败: ' + (error.response?.data?.message || error.message))
  } finally {
    testing.value = false
  }
}

// 保存搜索设置
const saveSearchSettings = async () => {
  saving.value = true
  try {
    await settingsService.updateSearch(searchSettings.value)
    message.success('保存成功')
  } catch (error) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 测试搜索
const testSearchConnection = async () => {
  testing.value = true
  try {
    const res = await searchService.test()
    if (res.success) {
      message.success('搜索功能正常')
    } else {
      message.error(res.message || '搜索失败')
    }
  } catch (error: any) {
    message.error('测试失败: ' + (error.response?.data?.message || error.message))
  } finally {
    testing.value = false
  }
}

// 保存日程设置
const saveScheduleSettings = async () => {
  saving.value = true
  try {
    await settingsService.updateSchedule(scheduleSettings.value)
    message.success('保存成功')
  } catch (error) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

// 保存文件设置
const saveFileSettings = async () => {
  saving.value = true
  try {
    await settingsService.updateFile(fileSettings.value)
    message.success('保存成功')
  } catch (error) {
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadAllSettings()
})
</script>

<style scoped>
.settings-page {
  min-height: 100%;
}

/* 页面头部 */
.page-header {
  background: linear-gradient(135deg, var(--primary-color) 0%, var(--primary-light, #fb923c) 100%);
  padding: 32px;
  border-radius: 16px;
  margin-bottom: 24px;
  position: relative;
  overflow: hidden;
}

.page-header::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -20%;
  width: 400px;
  height: 400px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 50%;
}

.header-content {
  display: flex;
  align-items: center;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.header-icon {
  width: 56px;
  height: 56px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.header-title {
  font-size: 28px;
  font-weight: 700;
  color: white;
  margin: 0;
}

.header-subtitle {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.8);
  margin: 4px 0 0;
}

/* 设置容器 */
.settings-container {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 24px;
}

/* 左侧导航 */
.settings-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
}

.nav-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.nav-item.active {
  background: var(--primary-color);
  color: white;
  box-shadow: 0 4px 12px rgba(249, 115, 22, 0.3);
}

/* 右侧内容 */
.settings-content {
  min-height: 600px;
}

.section-wrapper {
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.section-header {
  margin-bottom: 24px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 8px;
}

.section-title .n-icon {
  color: var(--primary-color);
}

.section-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}

/* 设置网格 */
.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

/* 设置卡片 */
.setting-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 20px;
  transition: all 0.2s ease;
}

.setting-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.setting-card.full-width {
  grid-column: span 2;
}

.setting-card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 16px;
}

.setting-icon {
  color: var(--primary-color);
  flex-shrink: 0;
}

.setting-card-header h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.setting-card-header p {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.4;
}

/* 主题选项 */
.theme-options {
  display: flex;
  gap: 12px;
}

.theme-option {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border: 2px solid var(--border-color);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.theme-option:hover {
  border-color: var(--primary-color);
}

.theme-option.active {
  border-color: var(--primary-color);
  background: rgba(249, 115, 22, 0.1);
}

.theme-preview {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  border: 1px solid var(--border-color);
}

.theme-preview.light {
  background: linear-gradient(135deg, #ffffff 50%, #f5f5f5 50%);
}

.theme-preview.dark {
  background: linear-gradient(135deg, #1c1917 50%, #292524 50%);
}

.theme-preview.auto {
  background: linear-gradient(135deg, #ffffff 50%, #1c1917 50%);
}

.theme-option span {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Logo上传 */
.logo-upload-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.logo-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--bg-hover);
  border-radius: 12px;
}

.logo-preview img {
  width: 48px;
  height: 48px;
  object-fit: contain;
  border-radius: 8px;
}

.logo-preview .remove-btn {
  color: var(--text-secondary);
}

.logo-preview .remove-btn:hover {
  color: var(--primary-color);
}

.upload-btn {
  width: 100%;
  height: 48px;
  border: 2px dashed var(--border-color);
  border-radius: 12px;
  background: transparent;
  color: var(--text-secondary);
  transition: all 0.2s ease;
}

.upload-btn:hover {
  border-color: var(--primary-color);
  color: var(--primary-color);
}

.upload-tip {
  font-size: 12px;
  color: var(--text-muted);
  margin: 0;
}

/* 滑块 */
.slider-wrapper {
  padding: 8px 0;
}

.slider-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-muted);
}

/* 操作按钮 */
.section-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--border-color);
}

/* 响应式 */
@media (max-width: 900px) {
  .settings-container {
    grid-template-columns: 1fr;
  }

  .settings-nav {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 8px;
  }

  .nav-item {
    flex: 1;
    min-width: 120px;
    justify-content: center;
  }

  .settings-grid {
    grid-template-columns: 1fr;
  }

  .setting-card.full-width {
    grid-column: span 1;
  }
}
</style>
