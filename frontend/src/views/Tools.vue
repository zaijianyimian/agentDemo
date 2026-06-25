<template>
  <div class="tools-page">
    <!-- 操作栏 -->
    <n-card class="action-card" :bordered="false">
      <n-space justify="space-between">
        <n-space>
          <n-button type="primary" @click="showAddModal = true">
            <template #icon><n-icon><AddIcon /></n-icon></template>
            添加工具
          </n-button>
          <n-button @click="showImportModal = true">
            <template #icon><n-icon><DownloadIcon /></n-icon></template>
            导入工具
          </n-button>
          <n-button @click="loadTools">
            <template #icon><n-icon><RefreshIcon /></n-icon></template>
            刷新
          </n-button>
        </n-space>
        <n-button type="warning" tag="a" href="https://mcp.so/zh" target="_blank">
          <template #icon><n-icon><GlobeIcon /></n-icon></template>
          MCP工具市场
        </n-button>
      </n-space>
    </n-card>

    <!-- 工具统计 -->
    <n-grid :cols="5" :x-gap="16">
      <n-gi>
        <div class="stat-box">
          <div class="stat-title">总工具数</div>
          <div class="stat-number">{{ tools.length }}</div>
        </div>
      </n-gi>
      <n-gi>
        <div class="stat-box enabled">
          <div class="stat-title">已启用</div>
          <div class="stat-number">{{ tools.filter(t => t.enabled).length }}</div>
        </div>
      </n-gi>
      <n-gi>
        <div class="stat-box http">
          <div class="stat-title">HTTP API</div>
          <div class="stat-number">{{ tools.filter(t => t.toolType === 'HTTP_API').length }}</div>
        </div>
      </n-gi>
      <n-gi>
        <div class="stat-box script">
          <div class="stat-title">本地脚本</div>
          <div class="stat-number">{{ tools.filter(t => t.toolType === 'LOCAL_SCRIPT').length }}</div>
        </div>
      </n-gi>
      <n-gi>
        <div class="stat-box mcp">
          <div class="stat-title">MCP 客户端</div>
          <div class="stat-number">{{ tools.filter(t => t.toolType === 'MCP_CLIENT').length }}</div>
        </div>
      </n-gi>
    </n-grid>

    <!-- 工具列表 -->
    <n-card class="list-card" :bordered="false">
      <n-data-table
        :columns="columns"
        :data="tools"
        :loading="loading"
        :row-key="(row: McpTool) => row.id"
        striped
      />
    </n-card>

    <!-- 添加工具弹窗 -->
    <n-modal v-model:show="showAddModal" preset="card" title="添加工具" style="width: 600px">
      <n-form :model="newTool" label-placement="left" label-width="100">
        <n-form-item label="工具名称">
          <n-input v-model:value="newTool.name" placeholder="weather_query" />
        </n-form-item>
        <n-form-item label="显示名称">
          <n-input v-model:value="newTool.displayName" placeholder="天气查询" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="newTool.description" type="textarea" placeholder="工具功能描述" />
        </n-form-item>
        <n-form-item label="工具类型">
          <n-select v-model:value="newTool.toolType" :options="typeOptions" />
        </n-form-item>
        <n-form-item label="配置(JSON)">
          <n-input v-model:value="newTool.config" type="textarea" :rows="4" placeholder='{"url": "https://..."}' />
        </n-form-item>
        <n-form-item label="输入模式(JSON)">
          <n-input v-model:value="newTool.inputSchema" type="textarea" :rows="4" placeholder='{"type": "object", ...}' />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddModal = false">取消</n-button>
          <n-button type="primary" @click="addTool">添加</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 编辑工具弹窗 -->
    <n-modal v-model:show="showEditModal" preset="card" title="编辑工具" style="width: 600px">
      <n-form :model="editingTool" label-placement="left" label-width="100">
        <n-form-item label="工具名称">
          <n-input v-model:value="editingTool.name" placeholder="weather_query" disabled />
        </n-form-item>
        <n-form-item label="显示名称">
          <n-input v-model:value="editingTool.displayName" placeholder="天气查询" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="editingTool.description" type="textarea" placeholder="工具功能描述" />
        </n-form-item>
        <n-form-item label="工具类型">
          <n-select v-model:value="editingTool.toolType" :options="typeOptions" />
        </n-form-item>
        <n-form-item label="配置(JSON)">
          <n-input v-model:value="editingTool.config" type="textarea" :rows="4" placeholder='{"url": "https://..."}' />
        </n-form-item>
        <n-form-item label="输入模式(JSON)">
          <n-input v-model:value="editingTool.inputSchema" type="textarea" :rows="4" placeholder='{"type": "object", ...}' />
        </n-form-item>
        <n-form-item label="备注">
          <n-input v-model:value="editingTool.remark" type="textarea" placeholder="备注信息" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showEditModal = false">取消</n-button>
          <n-button type="primary" @click="saveEditTool">保存</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 导入工具弹窗 -->
    <n-modal v-model:show="showImportModal" preset="card" title="导入工具" style="width: 700px">
      <n-tabs v-model:value="importTab">
        <n-tab-pane name="json" tab="JSON导入">
          <n-form label-placement="top">
            <n-form-item label="工具JSON配置">
              <n-input
                v-model:value="importJson"
                type="textarea"
                :rows="10"
                placeholder='粘贴从MCP工具市场导出的JSON配置...'
              />
            </n-form-item>
          </n-form>
          <n-space justify="end">
            <n-button @click="showImportModal = false">取消</n-button>
            <n-button type="primary" @click="importTool">导入</n-button>
          </n-space>
        </n-tab-pane>
        <n-tab-pane name="market" tab="MCP工具市场">
          <div class="market-info">
            <n-alert type="info" title="关于MCP工具市场">
              MCP (Model Context Protocol) 是一个开放协议，允许AI模型与外部工具和服务进行交互。
              访问 mcp.so 获取更多工具配置。
            </n-alert>
            <n-divider />
            <n-space vertical size="large">
              <n-button block type="primary" size="large" tag="a" href="https://mcp.so/zh" target="_blank">
                访问 MCP 工具市场
              </n-button>
              <n-button block tag="a" href="https://github.com/modelcontextprotocol" target="_blank">
                MCP GitHub 仓库
              </n-button>
            </n-space>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, h, onMounted } from 'vue'
import {
  NCard,
  NGrid,
  NGi,
  NButton,
  NIcon,
  NSpace,
  NTag,
  NDataTable,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NSwitch,
  NTabs,
  NTabPane,
  NAlert,
  NDivider,
  useMessage,
  type DataTableColumns
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  RefreshOutline as RefreshIcon,
  DownloadOutline as DownloadIcon,
  GlobeOutline as GlobeIcon
} from '@vicons/ionicons5'
import { mcpToolService } from '@/services/api'
import type { McpTool } from '@/types'

const message = useMessage()
const loading = ref(false)
const tools = ref<McpTool[]>([])
const showAddModal = ref(false)
const showEditModal = ref(false)
const showImportModal = ref(false)
const importTab = ref('json')
const importJson = ref('')
const newTool = ref({
  name: '',
  displayName: '',
  description: '',
  toolType: 'HTTP_API',
  config: '',
  inputSchema: ''
})
const editingTool = ref<McpTool & { remark?: string }>({
  id: 0,
  name: '',
  displayName: '',
  description: '',
  toolType: 'HTTP_API',
  config: '',
  inputSchema: '',
  enabled: false,
  remark: '',
  createTime: '',
  updateTime: ''
})

const typeOptions = [
  { label: 'HTTP API', value: 'HTTP_API' },
  { label: '本地脚本', value: 'LOCAL_SCRIPT' },
  { label: 'MCP 客户端', value: 'MCP_CLIENT' }
]

const columns: DataTableColumns<McpTool> = [
  { title: '名称', key: 'name', width: 150 },
  { title: '显示名称', key: 'displayName', width: 150 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  {
    title: '类型',
    key: 'toolType',
    width: 120,
    render: (row) => {
      const typeConfig: Record<string, { type: 'info' | 'warning' | 'success'; label: string }> = {
        'HTTP_API': { type: 'info', label: 'HTTP API' },
        'LOCAL_SCRIPT': { type: 'warning', label: '本地脚本' },
        'MCP_CLIENT': { type: 'success', label: 'MCP 客户端' }
      }
      const config = typeConfig[row.toolType] || { type: 'info', label: row.toolType }
      return h(NTag, { size: 'small', type: config.type }, {
        default: () => config.label
      })
    }
  },
  {
    title: '状态',
    key: 'enabled',
    width: 100,
    render: (row) => h(NSwitch, {
      value: row.enabled,
      onUpdateValue: (val: boolean) => toggleTool(row, val)
    })
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row) => h(NSpace, {}, {
      default: () => [
        h(NButton, {
          size: 'small',
          type: 'primary',
          onClick: () => openEditModal(row)
        }, { default: () => '编辑' }),
        h(NButton, {
          size: 'small',
          onClick: () => exportTool(row)
        }, { default: () => '导出' }),
        h(NButton, {
          size: 'small',
          type: 'error',
          onClick: () => deleteTool(row)
        }, { default: () => '删除' })
      ]
    })
  }
]

const loadTools = async () => {
  loading.value = true
  try {
    const res = await mcpToolService.list()
    // 兼容两种格式：直接返回数组 或 包装格式 { data: [...] }
    tools.value = Array.isArray(res) ? res : (res.data || [])
  } catch (error) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

const toggleTool = async (tool: McpTool, enabled: boolean) => {
  try {
    await mcpToolService.toggle(tool.id)
    message.success(enabled ? '已启用' : '已禁用')
    loadTools()
  } catch (error) {
    message.error('操作失败')
  }
}

const addTool = async () => {
  try {
    await mcpToolService.create({
      ...newTool.value,
      enabled: true
    })
    message.success('添加成功')
    showAddModal.value = false
    loadTools()
    // 重置表单
    newTool.value = {
      name: '',
      displayName: '',
      description: '',
      toolType: 'HTTP_API',
      config: '',
      inputSchema: ''
    }
  } catch (error) {
    message.error('添加失败')
  }
}

const openEditModal = (tool: McpTool) => {
  editingTool.value = {
    ...tool,
    remark: tool.remark || ''
  }
  showEditModal.value = true
}

const saveEditTool = async () => {
  try {
    await mcpToolService.update(editingTool.value.id, {
      displayName: editingTool.value.displayName,
      description: editingTool.value.description,
      toolType: editingTool.value.toolType,
      config: editingTool.value.config,
      inputSchema: editingTool.value.inputSchema,
      remark: editingTool.value.remark
    })
    message.success('保存成功')
    showEditModal.value = false
    loadTools()
  } catch (error) {
    message.error('保存失败')
  }
}

const importTool = async () => {
  if (!importJson.value.trim()) {
    message.warning('请输入工具JSON配置')
    return
  }
  try {
    const input = JSON.parse(importJson.value)

    // 检测是否是 MCP Server 配置格式
    if (input.mcpServers) {
      // 处理 mcpServers 格式
      const serverEntries = Object.entries(input.mcpServers)
      let successCount = 0

      for (const [serverName, serverConfig] of serverEntries) {
        const config = serverConfig as { command?: string; args?: string[]; env?: Record<string, string> }

        // 构建 MCP Client 工具配置
        const toolData = {
          name: serverName,
          displayName: serverName,
          description: `MCP Server: ${serverName}`,
          toolType: 'MCP_CLIENT',
          config: JSON.stringify({
            serverName: serverName,
            command: config.command || 'npx',
            args: config.args || [],
            env: config.env || {}
          }),
          inputSchema: JSON.stringify({
            type: 'object',
            properties: {},
            required: []
          }),
          enabled: true
        }

        try {
          await mcpToolService.create(toolData)
          successCount++
        } catch (e) {
          console.error(`Failed to import ${serverName}:`, e)
        }
      }

      if (successCount > 0) {
        message.success(`成功导入 ${successCount} 个 MCP Server 配置`)
        showImportModal.value = false
        importJson.value = ''
        loadTools()
      } else {
        message.error('导入失败')
      }
    } else {
      // 传统工具格式
      await mcpToolService.create({
        ...input,
        enabled: true
      })
      message.success('导入成功')
      showImportModal.value = false
      importJson.value = ''
      loadTools()
    }
  } catch (error) {
    message.error('导入失败，请检查JSON格式')
  }
}

const exportTool = (tool: McpTool) => {
  const exportData = {
    name: tool.name,
    displayName: tool.displayName,
    description: tool.description,
    toolType: tool.toolType,
    config: tool.config,
    inputSchema: tool.inputSchema
  }
  const json = JSON.stringify(exportData, null, 2)
  navigator.clipboard.writeText(json).then(() => {
    message.success('已复制到剪贴板')
  }).catch(() => {
    // 创建下载
    const blob = new Blob([json], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${tool.name}.json`
    a.click()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  })
}

const deleteTool = async (tool: McpTool) => {
  try {
    await mcpToolService.delete(tool.id)
    message.success('删除成功')
    loadTools()
  } catch (error) {
    message.error('删除失败')
  }
}

onMounted(() => {
  loadTools()
})
</script>

<style scoped>
.tools-page {
  display: grid;
  gap: 16px;
}

.action-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.stat-box {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 20px;
  text-align: center;
}

.stat-box.enabled {
  border-color: var(--success);
}

.stat-box.disabled {
  border-color: var(--error);
}

.stat-box.http {
  border-color: var(--primary);
}

.stat-box.script {
  border-color: var(--warning);
}

.stat-box.mcp {
  border-color: var(--success);
}

.stat-title {
  font-size: 14px;
  color: var(--text-secondary);
}

.stat-number {
  font-size: 32px;
  font-weight: 700;
  color: var(--text-primary);
}

.list-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.market-info {
  padding: 16px;
}

.market-info :deep(.n-alert) {
  margin-bottom: 16px;
}
</style>