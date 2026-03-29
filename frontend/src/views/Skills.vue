<template>
  <div class="skills-page">
    <!-- 操作栏 -->
    <div class="action-bar">
      <div class="action-left">
        <n-button type="primary" @click="showAddModal = true">
          <template #icon><n-icon><AddIcon /></n-icon></template>
          添加技能
        </n-button>
        <n-button @click="showImportModal = true">
          <template #icon><n-icon><DownloadIcon /></n-icon></template>
          导入技能
        </n-button>
        <n-button @click="reloadSkills" :loading="reloading">
          <template #icon><n-icon><RefreshIcon /></n-icon></template>
          重新加载
        </n-button>
      </div>
      <div class="action-right">
        <n-input v-model:value="searchText" placeholder="搜索技能..." clearable style="width: 200px">
          <template #prefix>
            <n-icon><SearchIcon /></n-icon>
          </template>
        </n-input>
      </div>
    </div>

    <!-- 分类标签 -->
    <div class="category-bar">
      <div
        v-for="cat in categories"
        :key="cat"
        :class="['category-item', { active: selectedCategory === cat }]"
        @click="selectedCategory = cat"
      >
        {{ cat }}
        <span class="count">{{ getCategoryCount(cat) }}</span>
      </div>
    </div>

    <!-- 技能卡片 -->
    <div class="skills-grid">
      <div v-for="skill in filteredSkills" :key="skill.id" class="skill-card">
        <div class="skill-header">
          <div class="skill-icon" :style="{ background: getCategoryColor(skill.category) }">
            <n-icon size="24">
              <component :is="getIcon(skill.icon)" />
            </n-icon>
          </div>
          <div class="skill-info">
            <div class="skill-name">{{ skill.name }}</div>
            <div class="skill-code">{{ skill.code }}</div>
          </div>
          <n-switch
            :value="skill.enabled"
            @update:value="(val: boolean) => toggleSkill(skill, val)"
          />
        </div>

        <div class="skill-desc">{{ skill.description }}</div>

        <div class="skill-footer">
          <div class="skill-tags">
            <span class="tag category" :style="{ background: getCategoryColor(skill.category) }">
              {{ skill.category }}
            </span>
            <span v-if="skill.isBuiltin" class="tag builtin">内置</span>
            <span v-else class="tag custom">自定义</span>
          </div>
          <div class="skill-actions">
            <n-button size="small" secondary @click="executeSkill(skill)">
              <template #icon><n-icon><PlayIcon /></n-icon></template>
              执行
            </n-button>
            <n-dropdown :options="getMoreOptions()" @select="(key: string) => handleMoreAction(key, skill)">
              <n-button size="small" quaternary>
                <template #icon><n-icon><EllipsisIcon /></n-icon></template>
              </n-button>
            </n-dropdown>
          </div>
        </div>
      </div>

      <n-empty v-if="filteredSkills.length === 0" description="暂无技能" />
    </div>

    <!-- 添加技能弹窗 -->
    <n-modal v-model:show="showAddModal" preset="card" title="添加技能" style="width: 600px">
      <n-form :model="newSkill" label-placement="left" label-width="80">
        <n-form-item label="编码" required>
          <n-input v-model:value="newSkill.code" placeholder="my_skill (唯一标识)" />
        </n-form-item>
        <n-form-item label="名称" required>
          <n-input v-model:value="newSkill.name" placeholder="我的技能" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input v-model:value="newSkill.description" type="textarea" :rows="2" placeholder="技能描述" />
        </n-form-item>
        <n-form-item label="分类">
          <n-select v-model:value="newSkill.category" :options="categoryOptions" />
        </n-form-item>
        <n-form-item label="图标">
          <n-input v-model:value="newSkill.icon" placeholder="rocket" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddModal = false">取消</n-button>
          <n-button type="primary" @click="addSkill">添加</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 导入技能弹窗 -->
    <n-modal v-model:show="showImportModal" preset="card" title="导入技能" style="width: 700px">
      <n-tabs v-model:value="importTab">
        <n-tab-pane name="json" tab="JSON导入">
          <div class="import-hint">
            从 skills.sh 或其他来源复制技能JSON配置，粘贴到下方
          </div>
          <n-input
            v-model:value="importJson"
            type="textarea"
            :rows="12"
            placeholder='{"code": "my_skill", "name": "我的技能", ...}'
            class="import-input"
          />
          <div class="import-actions">
            <n-button @click="importJson = exampleSkillJson">填充示例</n-button>
            <n-button type="primary" @click="importFromJson" :loading="importing">
              导入
            </n-button>
          </div>
        </n-tab-pane>
        <n-tab-pane name="market" tab="技能市场">
          <div class="market-info">
            <n-icon size="48" color="#FF8C00"><StoreIcon /></n-icon>
            <h3>skills.sh 技能市场</h3>
            <p>发现和分享AI技能，快速扩展你的Agent能力</p>
            <n-button type="primary" size="large" tag="a" href="https://skills.sh" target="_blank">
              访问技能市场
            </n-button>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-modal>

    <!-- 执行技能弹窗 -->
    <n-modal v-model:show="showExecuteModal" preset="card" :title="`执行技能: ${currentSkill?.name}`" style="width: 600px">
      <n-form label-placement="left" label-width="80">
        <n-form-item label="技能编码">
          <n-tag type="primary">{{ currentSkill?.code }}</n-tag>
        </n-form-item>
        <n-form-item label="参数(JSON)">
          <n-input
            v-model:value="executeParams"
            type="textarea"
            :rows="6"
            placeholder='{"key": "value"}'
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showExecuteModal = false">取消</n-button>
          <n-button type="primary" :loading="executing" @click="doExecute">执行</n-button>
        </n-space>
      </template>
    </n-modal>

    <!-- 执行结果弹窗 -->
    <n-modal v-model:show="showResultModal" preset="card" title="执行结果" style="width: 700px">
      <div class="result-header">
        <n-tag :type="executeResult?.success ? 'success' : 'error'">
          {{ executeResult?.success ? '成功' : '失败' }}
        </n-tag>
        <span class="duration">耗时: {{ executeResult?.totalDurationMs || 0 }}ms</span>
      </div>
      <n-divider />
      <div class="result-content">
        <pre>{{ JSON.stringify(executeResult, null, 2) }}</pre>
      </div>
      <template #footer>
        <n-button @click="showResultModal = false">关闭</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  NButton,
  NIcon,
  NSpace,
  NTag,
  NSwitch,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NSelect,
  NDropdown,
  NEmpty,
  NTabs,
  NTabPane,
  NDivider,
  useMessage
} from 'naive-ui'
import {
  AddOutline as AddIcon,
  RefreshOutline as RefreshIcon,
  DownloadOutline as DownloadIcon,
  SearchOutline as SearchIcon,
  PlayOutline as PlayIcon,
  EllipsisVertical as EllipsisIcon,
  CloudOutline as CloudIcon,
  ChatbubbleOutline as ChatIcon,
  MailOutline as MailIcon,
  FolderOutline as FolderIcon,
  RocketOutline as RocketIcon,
  StorefrontOutline as StoreIcon
} from '@vicons/ionicons5'
import { skillService } from '@/services/api'
import type { Skill } from '@/types'

const message = useMessage()
const skills = ref<Skill[]>([])
const categories = ref<string[]>(['全部'])
const selectedCategory = ref('全部')
const searchText = ref('')
const reloading = ref(false)
const importing = ref(false)

// 弹窗状态
const showAddModal = ref(false)
const showImportModal = ref(false)
const showExecuteModal = ref(false)
const showResultModal = ref(false)
const importTab = ref('json')

// 表单数据
const currentSkill = ref<Skill | null>(null)
const executeParams = ref('{}')
const executing = ref(false)
const executeResult = ref<any>(null)
const importJson = ref('')

const newSkill = ref({
  code: '',
  name: '',
  description: '',
  category: 'custom',
  icon: 'rocket'
})

const categoryOptions = [
  { label: 'AI类', value: 'ai' },
  { label: '搜索类', value: 'search' },
  { label: '数据类', value: 'data' },
  { label: '系统类', value: 'system' },
  { label: '自定义', value: 'custom' }
]

// 示例技能JSON
const exampleSkillJson = JSON.stringify({
  code: "hello_world",
  name: "问候世界",
  description: "一个简单的示例技能",
  category: "ai",
  icon: "chat",
  enabled: true,
  isBuiltin: false,
  tools: []
}, null, 2)

// 过滤后的技能
const filteredSkills = computed(() => {
  let result = skills.value

  if (selectedCategory.value !== '全部') {
    result = result.filter(s => s.category === selectedCategory.value)
  }

  if (searchText.value) {
    const search = searchText.value.toLowerCase()
    result = result.filter(s =>
      s.name.toLowerCase().includes(search) ||
      s.code.toLowerCase().includes(search) ||
      s.description?.toLowerCase().includes(search)
    )
  }

  return result
})

// 获取分类数量
const getCategoryCount = (cat: string) => {
  if (cat === '全部') return skills.value.length
  return skills.value.filter(s => s.category === cat).length
}

// 获取分类颜色
const getCategoryColor = (category: string) => {
  const colors: Record<string, string> = {
    ai: 'linear-gradient(135deg, #FF6B00, #FFB733)',
    search: 'linear-gradient(135deg, #00D9FF, #0099CC)',
    data: 'linear-gradient(135deg, #00D9A5, #00B386)',
    system: 'linear-gradient(135deg, #FF4757, #FF6B81)',
    custom: 'linear-gradient(135deg, #8B5CF6, #A78BFA)'
  }
  return colors[category] || colors.custom
}

// 获取图标
const getIcon = (iconName: string) => {
  const icons: Record<string, any> = {
    search: SearchIcon,
    chat: ChatIcon,
    cloud: CloudIcon,
    email: MailIcon,
    folder: FolderIcon,
    rocket: RocketIcon
  }
  return icons[iconName] || RocketIcon
}

// 更多操作选项
const getMoreOptions = () => [
  { label: '导出配置', key: 'export' },
  { label: '复制编码', key: 'copy' },
  { type: 'divider', key: 'd1' },
  { label: '删除', key: 'delete', props: { style: 'color: #FF4757' } }
]

// 处理更多操作
const handleMoreAction = async (key: string, skill: Skill) => {
  switch (key) {
    case 'export':
      const res = await skillService.exportSkill(skill.id)
      if (res.success && res.data) {
        await navigator.clipboard.writeText(res.data)
        message.success('已复制到剪贴板')
      }
      break
    case 'copy':
      await navigator.clipboard.writeText(skill.code)
      message.success('已复制编码: ' + skill.code)
      break
    case 'delete':
      await deleteSkill(skill)
      break
  }
}

// 加载技能
const loadSkills = async () => {
  try {
    const [skillsRes, catsRes] = await Promise.all([
      skillService.list(),
      skillService.getCategories()
    ])
    // 兼容两种格式：直接返回数组 或 包装格式 { data: [...] }
    skills.value = Array.isArray(skillsRes) ? skillsRes : (skillsRes.data || [])
    const catsData = Array.isArray(catsRes) ? catsRes : (catsRes.data || [])
    categories.value = ['全部', ...catsData]
  } catch (error) {
    message.error('加载失败')
  }
}

// 重新加载技能
const reloadSkills = async () => {
  reloading.value = true
  try {
    const res = await skillService.reload()
    message.success(res.message || '重新加载成功')
    await loadSkills()
  } catch (error) {
    message.error('重新加载失败')
  } finally {
    reloading.value = false
  }
}

// 切换技能状态
const toggleSkill = async (skill: Skill, enabled: boolean) => {
  try {
    await skillService.update(skill.id, { enabled })
    message.success(enabled ? '已启用' : '已禁用')
    loadSkills()
  } catch (error) {
    message.error('操作失败')
  }
}

// 添加技能
const addSkill = async () => {
  if (!newSkill.value.code || !newSkill.value.name) {
    message.warning('请填写编码和名称')
    return
  }
  try {
    await skillService.create({
      ...newSkill.value,
      enabled: true,
      isBuiltin: false
    })
    message.success('添加成功')
    showAddModal.value = false
    loadSkills()
  } catch (error) {
    message.error('添加失败')
  }
}

// 删除技能
const deleteSkill = async (skill: Skill) => {
  if (skill.isBuiltin) {
    message.warning('内置技能不能删除')
    return
  }
  try {
    await skillService.delete(skill.id)
    message.success('删除成功')
    loadSkills()
  } catch (error) {
    message.error('删除失败')
  }
}

// 执行技能
const executeSkill = (skill: Skill) => {
  currentSkill.value = skill
  executeParams.value = '{}'
  showExecuteModal.value = true
}

// 执行技能
const doExecute = async () => {
  if (!currentSkill.value) return
  executing.value = true
  try {
    const params = JSON.parse(executeParams.value || '{}')
    const res = await skillService.execute(currentSkill.value.code, params)
    executeResult.value = res.data
    showExecuteModal.value = false
    showResultModal.value = true
  } catch (error: any) {
    message.error('执行失败: ' + (error.message || '未知错误'))
  } finally {
    executing.value = false
  }
}

// 从JSON导入
const importFromJson = async () => {
  if (!importJson.value.trim()) {
    message.warning('请输入技能JSON')
    return
  }
  importing.value = true
  try {
    const res = await skillService.importSkill(importJson.value)
    if (res.success) {
      message.success('导入成功')
      showImportModal.value = false
      importJson.value = ''
      loadSkills()
    } else {
      message.error(res.message || '导入失败')
    }
  } catch (error: any) {
    message.error('导入失败: ' + (error.message || '格式错误'))
  } finally {
    importing.value = false
  }
}

onMounted(() => {
  loadSkills()
})
</script>

<style scoped>
.skills-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}

.action-left {
  display: flex;
  gap: 12px;
}

.category-bar {
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow-x: auto;
}

.category-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  color: var(--text-secondary);
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.category-item:hover {
  background: var(--bg-input);
}

.category-item.active {
  background: linear-gradient(135deg, var(--gradient-start), var(--gradient-end));
  color: white;
}

.category-item .count {
  font-size: 12px;
  opacity: 0.7;
}

.skills-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.skill-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  padding: 20px;
  transition: all 0.3s ease;
}

.skill-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 8px 24px rgba(255, 139, 0, 0.12);
  transform: translateY(-2px);
}

.skill-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.skill-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
}

.skill-info {
  flex: 1;
  min-width: 0;
}

.skill-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.skill-code {
  font-size: 12px;
  color: var(--text-muted);
  font-family: monospace;
}

.skill-desc {
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.6;
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.skill-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.skill-tags {
  display: flex;
  gap: 6px;
}

.tag {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  color: white;
}

.tag.builtin {
  background: #00D9A5;
}

.tag.custom {
  background: #8B5CF6;
}

.skill-actions {
  display: flex;
  gap: 4px;
}

/* 导入弹窗 */
.import-hint {
  padding: 12px;
  background: var(--bg-input);
  border-radius: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--text-secondary);
}

.import-input {
  font-family: monospace;
}

.import-actions {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
}

.market-info {
  text-align: center;
  padding: 40px 20px;
}

.market-info h3 {
  margin: 16px 0 8px;
  font-size: 20px;
  color: var(--text-primary);
}

.market-info p {
  color: var(--text-secondary);
  margin-bottom: 24px;
}

/* 执行结果 */
.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.duration {
  font-size: 13px;
  color: var(--text-secondary);
}

.result-content {
  background: var(--bg-input);
  border-radius: 8px;
  padding: 16px;
  max-height: 400px;
  overflow: auto;
}

.result-content pre {
  margin: 0;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>